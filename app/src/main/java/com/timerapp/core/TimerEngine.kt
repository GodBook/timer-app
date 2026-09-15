package com.timerapp.core

import android.content.Context
import android.os.SystemClock
import com.timerapp.alarm.AlarmScheduler
import com.timerapp.data.AppStore
import com.timerapp.data.TimerSnapshot
import com.timerapp.data.TimerState
import com.timerapp.service.TimerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 计时核心,单一事实来源。所有状态变更经由此处并同步持久化,
 * 到点判定完全交给 AlarmManager,进程死活不影响准确性。
 */
object TimerEngine {

    lateinit var appContext: Context
        private set
    lateinit var store: AppStore
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var loadJob: Job? = null

    private val _timers = MutableStateFlow<List<TimerSnapshot>>(emptyList())
    val timers: StateFlow<List<TimerSnapshot>> = _timers.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private var nextId = 1

    fun init(context: Context) {
        appContext = context.applicationContext
        store = AppStore(appContext)
        loadJob = scope.launch {
            val list = store.loadTimers()
            mutex.withLock {
                _timers.value = list
                nextId = (list.maxOfOrNull { it.id } ?: 0) + 1
            }
            _loaded.value = true
        }
    }

    suspend fun awaitLoaded() {
        loadJob?.join()
    }

    suspend fun start(name: String, durationMs: Long) {
        if (durationMs <= 0) return
        awaitLoaded()
        mutex.withLock {
            val now = SystemClock.elapsedRealtime()
            val timer = TimerSnapshot(
                id = nextId++,
                name = name.ifBlank { "计时器" },
                totalMs = durationMs,
                endElapsed = now + durationMs,
                endWallClock = System.currentTimeMillis() + durationMs,
                state = TimerState.RUNNING,
            )
            update(_timers.value + timer)
            AlarmScheduler.schedule(appContext, timer)
        }
        TimerService.ensureRunning(appContext)
    }

    suspend fun pause(id: Int) {
        awaitLoaded()
        mutex.withLock {
            val t = _timers.value.find { it.id == id } ?: return
            if (t.state != TimerState.RUNNING) return
            AlarmScheduler.cancel(appContext, id)
            val remaining = (t.endElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0)
            replace(t.copy(state = TimerState.PAUSED, remainingMs = remaining))
        }
    }

    suspend fun resume(id: Int) {
        awaitLoaded()
        mutex.withLock {
            val t = _timers.value.find { it.id == id } ?: return
            if (t.state != TimerState.PAUSED) return
            val now = SystemClock.elapsedRealtime()
            val nt = t.copy(
                state = TimerState.RUNNING,
                endElapsed = now + t.remainingMs,
                endWallClock = System.currentTimeMillis() + t.remainingMs,
                remainingMs = 0,
            )
            replace(nt)
            AlarmScheduler.schedule(appContext, nt)
        }
        TimerService.ensureRunning(appContext)
    }

    suspend fun cancel(id: Int) {
        awaitLoaded()
        mutex.withLock {
            val t = _timers.value.find { it.id == id } ?: return
            AlarmScheduler.cancel(appContext, id)
            update(_timers.value - t)
        }
    }

    /** AlarmReceiver 到点回调 */
    suspend fun onFired(id: Int) {
        awaitLoaded()
        mutex.withLock {
            val t = _timers.value.find { it.id == id } ?: return
            if (t.state != TimerState.RUNNING) return
            replace(t.copy(state = TimerState.RINGING))
        }
    }

    /** 用户划掉横幅 / 响铃页关闭 / 通知「停止」*/
    suspend fun dismiss(id: Int) {
        awaitLoaded()
        mutex.withLock {
            val t = _timers.value.find { it.id == id } ?: return
            update(_timers.value - t)
        }
    }

    /** 开机恢复:elapsedRealtime 已重置,用墙钟重算。返回是否还有计时器需要服务运行 */
    suspend fun recomputeAfterBoot(): Boolean {
        awaitLoaded()
        mutex.withLock {
            val now = SystemClock.elapsedRealtime()
            val wall = System.currentTimeMillis()
            val newList = _timers.value.map { t ->
                if (t.state == TimerState.PAUSED) t
                else {
                    val remainWall = t.endWallClock - wall
                    val nt = t.copy(
                        endElapsed = now + remainWall,
                        state = if (remainWall <= 0) TimerState.RINGING else TimerState.RUNNING,
                    )
                    if (nt.state == TimerState.RUNNING) AlarmScheduler.schedule(appContext, nt)
                    nt
                }
            }
            update(newList)
            return newList.isNotEmpty()
        }
    }

    private suspend fun replace(t: TimerSnapshot) {
        update(_timers.value.map { if (it.id == t.id) t else it })
    }

    private suspend fun update(list: List<TimerSnapshot>) {
        _timers.value = list
        store.saveTimers(list)
    }
}
