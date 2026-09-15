package com.timerapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.timerapp.alarm.AlarmScheduler
import com.timerapp.core.TimerEngine
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_FIRE) return
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_TIMER_ID, -1)
        if (id == -1) return
        val result = goAsync()
        TimerEngine.scope.launch {
            try {
                TimerEngine.onFired(id)
                // 精确闹钟广播自带前台服务启动豁免;进程若已被杀,这里会重新拉起服务
                TimerService.ensureRunning(context)
            } finally {
                result.finish()
            }
        }
    }
}
