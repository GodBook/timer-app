package com.timerapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.timerapp.core.TimerEngine
import com.timerapp.service.TimerService

class TimerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TimerEngine.init(this)
        createChannels()
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                TimerService.CHANNEL_RUNNING,
                "计时进行中",
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        // 声音和震动由 AlertPlayer 自己播,渠道一律静音,避免双重提示音
        nm.createNotificationChannel(
            NotificationChannel(
                TimerService.CHANNEL_ALERT,
                "倒计时结束(锁屏全屏提醒)",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                TimerService.CHANNEL_ALERT_QUIET,
                "倒计时结束(通知栏留底)",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
    }
}
