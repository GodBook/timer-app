package com.timerapp.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.ServiceCompat
import com.timerapp.MainActivity
import com.timerapp.R
import com.timerapp.core.TimerEngine
import com.timerapp.data.TimerSnapshot
import com.timerapp.data.TimerState
import com.timerapp.overlay.BannerStack
import com.timerapp.overlay.OverlayBanner
import com.timerapp.sound.AlertPlayer
import com.timerapp.ui.AlarmActivity
import com.timerapp.ui.formatMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimerService : Service() {

    companion object {
        const val ACTION_DISMISS = "com.timerapp.action.DISMISS"
        const val EXTRA_TIMER_ID = "timer_id"
        const val CHANNEL_RUNNING = "running"
        const val CHANNEL_ALERT = "alert"
        const val CHANNEL_ALERT_QUIET = "alert_quiet"
        private const val NOTIF_ONGOING = 1
        private const val NOTIF_ALERT_BASE = 1000

        fun ensureRunning(context: Context) {
            context.startForegroundService(Intent(context, TimerService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var alertPlayer: AlertPlayer
    private lateinit var overlay: OverlayBanner
    private var alertedIds = emptySet<Int>()
    private var stopping = false

    // 锁屏响铃页被滑掉但计时器仍在响 → 解锁瞬间补挂横幅
    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshPresentation(TimerEngine.timers.value)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        alertPlayer = AlertPlayer(this)
        overlay = OverlayBanner(this)
        registerReceiver(
            userPresentReceiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            RECEIVER_NOT_EXPORTED,
        )
        serviceScope.launch {
            TimerEngine.awaitLoaded()
            TimerEngine.timers.collect { onState(it) }
        }
        serviceScope.launch {
            while (isActive) {
                delay(1000)
                val list = TimerEngine.timers.value
                if (!stopping && list.any { it.state == TimerState.RUNNING }) {
                    notifyOngoing(list)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            NOTIF_ONGOING,
            buildOngoing(TimerEngine.timers.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (intent?.action == ACTION_DISMISS) {
            val id = intent.getIntExtra(EXTRA_TIMER_ID, -1)
            if (id != -1) TimerEngine.scope.launch { TimerEngine.dismiss(id) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(userPresentReceiver) }
        overlay.hide()
        alertPlayer.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun onState(list: List<TimerSnapshot>) {
        if (stopping) return
        if (list.isEmpty()) {
            stopping = true
            cleanupAlerts(keep = emptySet())
            overlay.hide()
            alertPlayer.stop()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val ringing = list.filter { it.state == TimerState.RINGING }
        val ringingIds = ringing.map { it.id }.toSet()
        val newIds = ringingIds - alertedIds

        if (ringingIds.isEmpty()) {
            alertPlayer.stop()
        } else if (newIds.isNotEmpty()) {
            // 新的计时器到点:重新开始提醒(「响一次」模式也要为新计时器再响一遍)
            serviceScope.launch { alertPlayer.restart(TimerEngine.store.alertMode()) }
        }

        ringing.filter { it.id in newIds }.forEach { postAlert(it) }
        cleanupAlerts(keep = ringingIds)
        alertedIds = ringingIds

        refreshPresentation(list)
        notifyOngoing(list)
    }

    private fun refreshPresentation(list: List<TimerSnapshot>) {
        if (stopping) return
        val ringing = list.filter { it.state == TimerState.RINGING }
        if (ringing.isEmpty()) {
            overlay.hide()
            return
        }
        val pm = getSystemService(PowerManager::class.java)
        val km = getSystemService(KeyguardManager::class.java)
        val unlocked = pm.isInteractive && !km.isKeyguardLocked
        if (unlocked && Settings.canDrawOverlays(this)) {
            overlay.show { BannerStack() }
        }
    }

    // ---- 通知 ----

    private fun postAlert(t: TimerSnapshot) {
        val pm = getSystemService(PowerManager::class.java)
        val km = getSystemService(KeyguardManager::class.java)
        val unlocked = pm.isInteractive && !km.isKeyguardLocked
        // 解锁态由悬浮横幅承担提醒,通知只做留底(安静渠道);
        // 锁屏/熄屏走全屏通知;无悬浮窗权限时退化为高优先级横幅通知
        val useHigh = !unlocked || !Settings.canDrawOverlays(this)
        val channel = if (useHigh) CHANNEL_ALERT else CHANNEL_ALERT_QUIET

        val alarmPI = PendingIntent.getActivity(
            this,
            t.id,
            Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val dismissPI = PendingIntent.getService(
            this,
            t.id,
            Intent(this, TimerService::class.java)
                .setAction(ACTION_DISMISS)
                .putExtra(EXTRA_TIMER_ID, t.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = Notification.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("${t.name} 倒计时结束")
            .setContentText("点按查看,或按「停止」结束提醒")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(alarmPI)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_stat_timer),
                    "停止",
                    dismissPI,
                ).build()
            )
        if (!unlocked) {
            builder.setFullScreenIntent(alarmPI, true)
        }
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIF_ALERT_BASE + t.id, builder.build())
        }
    }

    private fun cleanupAlerts(keep: Set<Int>) {
        val nm = getSystemService(NotificationManager::class.java)
        (alertedIds - keep).forEach { nm.cancel(NOTIF_ALERT_BASE + it) }
    }

    private fun notifyOngoing(list: List<TimerSnapshot>) {
        if (stopping || list.isEmpty()) return
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIF_ONGOING, buildOngoing(list))
        }
    }

    private fun buildOngoing(list: List<TimerSnapshot>): Notification {
        val now = SystemClock.elapsedRealtime()
        val ringingCount = list.count { it.state == TimerState.RINGING }
        val running = list.filter { it.state == TimerState.RUNNING }
            .minByOrNull { it.endElapsed }
        val title = when {
            ringingCount > 0 -> "$ringingCount 个计时器响铃中"
            running != null -> {
                val suffix = if (list.size > 1) " · 共 ${list.size} 个" else ""
                "${running.name} 剩余 ${formatMs(running.endElapsed - now)}$suffix"
            }
            list.isNotEmpty() -> "计时器已暂停"
            else -> "计时器"
        }
        val mainPI = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(mainPI)
            .build()
    }
}
