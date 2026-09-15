package com.timerapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.timerapp.core.TimerEngine
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val result = goAsync()
        TimerEngine.scope.launch {
            try {
                if (TimerEngine.recomputeAfterBoot()) {
                    TimerService.ensureRunning(context)
                }
            } finally {
                result.finish()
            }
        }
    }
}
