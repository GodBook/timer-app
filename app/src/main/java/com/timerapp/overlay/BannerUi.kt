package com.timerapp.overlay

import android.os.SystemClock
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timerapp.core.TimerEngine
import com.timerapp.data.TimerSnapshot
import com.timerapp.data.TimerState
import com.timerapp.ui.formatMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BannerStack() {
    val timers by TimerEngine.timers.collectAsState()
    val ringing = timers.filter { it.state == TimerState.RINGING }
    MaterialTheme {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            ringing.forEach { t ->
                key(t.id) {
                    BannerCard(t) { TimerEngine.scope.launch { TimerEngine.dismiss(t.id) } }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun BannerCard(t: TimerSnapshot, onDismiss: () -> Unit) {
    var offsetY by remember { mutableFloatStateOf(0f) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = SystemClock.elapsedRealtime()
        }
    }
    val overdue = (now - t.endElapsed).coerceAtLeast(0)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xF21C1C1E),
        contentColor = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = offsetY
                alpha = (1f + offsetY / 400f).coerceIn(0f, 1f)
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { if (offsetY < -100f) onDismiss() else offsetY = 0f },
                    onDragCancel = { offsetY = 0f },
                ) { _, dragAmount ->
                    offsetY = (offsetY + dragAmount).coerceAtMost(0f)
                }
            },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFFFFC107))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${t.name} 倒计时结束", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "已超时 ${formatMs(overdue)} · 上划关闭",
                    fontSize = 13.sp,
                    color = Color(0xFFBBBBBB),
                )
            }
            TextButton(onClick = onDismiss) { Text("停止", color = Color(0xFFFFC107)) }
        }
    }
}
