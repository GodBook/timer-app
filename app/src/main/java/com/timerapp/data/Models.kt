package com.timerapp.data

import kotlinx.serialization.Serializable

enum class TimerState { RUNNING, PAUSED, RINGING }

@Serializable
data class TimerSnapshot(
    val id: Int,
    val name: String,
    val totalMs: Long,
    // 计时基准:elapsedRealtime 的目标值,剩余时间永远现算,不依赖 tick 累减
    val endElapsed: Long = 0L,
    // 对应的墙钟时间,仅用于重启后恢复
    val endWallClock: Long = 0L,
    // 仅 PAUSED 状态有意义
    val remainingMs: Long = 0L,
    val state: TimerState = TimerState.RUNNING,
)

@Serializable
data class Preset(
    val id: Int,
    val name: String,
    val durationMs: Long,
)

enum class AlertMode(val label: String) {
    LOOP_SOUND_VIBRATE("循环响铃 + 震动(默认)"),
    ONCE_SOUND_VIBRATE("响铃一次 + 震动"),
    VIBRATE_ONLY("仅震动"),
    SILENT("静音"),
}
