package com.timerapp.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.timerapp.data.TimerSnapshot
import com.timerapp.service.AlarmReceiver

object AlarmScheduler {

    const val ACTION_FIRE = "com.timerapp.action.ALARM_FIRE"
    const val EXTRA_TIMER_ID = "timer_id"

    fun schedule(context: Context, timer: TimerSnapshot) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            timer.endElapsed,
            pending(context, timer.id),
        )
    }

    fun cancel(context: Context, id: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, id))
    }

    private fun pending(context: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, AlarmReceiver::class.java)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_TIMER_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
