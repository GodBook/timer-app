package com.timerapp.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import com.timerapp.data.AlertMode

/** 响铃 + 震动,全局单例语义:只要有任一计时器在响就播放,全部处理完才停 */
class AlertPlayer(private val context: Context) {

    private var player: MediaPlayer? = null
    private var vibrating = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var active = false

    fun restart(mode: AlertMode) {
        stop()
        start(mode)
    }

    fun start(mode: AlertMode) {
        if (active) return
        active = true

        wakeLock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "timerapp:alert")
            .apply { acquire(30 * 60 * 1000L) }

        if (mode == AlertMode.LOOP_SOUND_VIBRATE || mode == AlertMode.ONCE_SOUND_VIBRATE) {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (uri != null) {
                runCatching {
                    player = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        setDataSource(context, uri)
                        isLooping = mode == AlertMode.LOOP_SOUND_VIBRATE
                        setOnCompletionListener { mp -> if (!mp.isLooping) runCatching { mp.release() } }
                        prepare()
                        start()
                    }
                }
            }
        }

        if (mode != AlertMode.SILENT) {
            runCatching {
                val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
                val repeat = if (mode == AlertMode.ONCE_SOUND_VIBRATE) -1 else 0
                val effect = VibrationEffect.createWaveform(longArrayOf(0, 800, 600), repeat)
                vibrator.vibrate(
                    effect,
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
                )
                vibrating = true
            }
        }
    }

    fun stop() {
        if (!active) return
        active = false
        runCatching {
            player?.let { if (it.isPlaying) it.stop(); it.release() }
        }
        player = null
        if (vibrating) {
            runCatching {
                context.getSystemService(VibratorManager::class.java).defaultVibrator.cancel()
            }
            vibrating = false
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
