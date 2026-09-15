package com.timerapp.ui

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timerapp.core.TimerEngine
import com.timerapp.data.TimerSnapshot
import com.timerapp.data.TimerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 锁屏之上的全屏响铃页(由全屏通知拉起) */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            val loaded by TimerEngine.loaded.collectAsState()
            val timers by TimerEngine.timers.collectAsState()
            val ringing = timers.filter { it.state == TimerState.RINGING }
            LaunchedEffect(loaded, ringing.isEmpty()) {
                if (loaded && ringing.isEmpty()) finish()
            }
            AlarmScreen(ringing) {
                val ids = ringing.map { it.id }
                TimerEngine.scope.launch { ids.forEach { TimerEngine.dismiss(it) } }
            }
        }
    }
}

@Composable
private fun AlarmScreen(ringing: List<TimerSnapshot>, onDismissAll: () -> Unit) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = SystemClock.elapsedRealtime()
        }
    }
    Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            ringing.forEach { t ->
                Text(
                    t.name,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "倒计时结束 · 已超时 ${formatMs((now - t.endElapsed).coerceAtLeast(0))}",
                    fontSize = 16.sp,
                    color = Color(0xFF9E9E9E),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
            }
            Spacer(Modifier.weight(1f))
            SlideToStop(onDismissAll)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SlideToStop(onTriggered: () -> Unit) {
    var offset by remember { mutableFloatStateOf(0f) }
    var travelPx by remember { mutableFloatStateOf(1f) }
    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(Color(0xFF2A2A2A))
            .onSizeChanged {
                travelPx = (it.width - with(density) { 72.dp.toPx() }).coerceAtLeast(1f)
            },
    ) {
        Text(
            "滑动关闭提醒",
            Modifier.align(Alignment.Center),
            color = Color(0xFF9E9E9E),
            fontSize = 15.sp,
        )
        Box(
            Modifier
                .padding(4.dp)
                .size(64.dp)
                .offset { IntOffset(offset.roundToInt(), 0) }
                .clip(CircleShape)
                .background(Color(0xFFFFC107))
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { d ->
                        offset = (offset + d).coerceIn(0f, travelPx)
                    },
                    onDragStopped = {
                        if (offset > travelPx * 0.85f) onTriggered() else offset = 0f
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.Black)
        }
    }
}
