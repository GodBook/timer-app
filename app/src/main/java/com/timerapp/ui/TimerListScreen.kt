package com.timerapp.ui

import android.Manifest
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timerapp.core.TimerEngine
import com.timerapp.data.TimerSnapshot
import com.timerapp.data.TimerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimerListScreen(onAdd: () -> Unit, onSettings: () -> Unit, onPermissions: () -> Unit) {
    val timers by TimerEngine.timers.collectAsState()
    val presets by TimerEngine.store.presetsFlow.collectAsState(initial = emptyList())
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            now = SystemClock.elapsedRealtime()
        }
    }
    val context = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (!notificationsGranted(context)) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // now 每 500ms 变化会触发重组,权限状态顺带刷新
    val missingCritical = !overlayGranted(context) || !notificationsGranted(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("计时器") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "新建计时器")
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (missingCritical) {
                Surface(
                    color = Color(0xFFFFF3CD),
                    contentColor = Color(0xFF664D03),
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onPermissions),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("缺少悬浮窗或通知权限,提醒可能失效,点击设置", fontSize = 14.sp)
                    }
                }
            }
            if (presets.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(12.dp))
                    Text("常用预设(点按开始)", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        presets.forEach { p ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.clickable {
                                    TimerEngine.scope.launch {
                                        TimerEngine.start(p.name, p.durationMs)
                                    }
                                },
                            ) {
                                Text(
                                    "${p.name} ${formatMs(p.durationMs)}",
                                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            if (timers.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (presets.isEmpty()) "还没有计时器,点右下角 + 新建"
                        else "点击预设开始,或点右下角 + 新建",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(timers, key = { it.id }) { t -> TimerCard(t, now) }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TimerCard(t: TimerSnapshot, now: Long) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(t.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            val remaining = when (t.state) {
                TimerState.RUNNING -> (t.endElapsed - now).coerceAtLeast(0)
                TimerState.PAUSED -> t.remainingMs
                TimerState.RINGING -> 0L
            }
            if (t.state == TimerState.RINGING) {
                Text(
                    "响铃中 · 已超时 ${formatMs((now - t.endElapsed).coerceAtLeast(0))}",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatMs(remaining),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (t.state == TimerState.PAUSED)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else Color.Unspecified,
                    )
                    if (t.state == TimerState.PAUSED) {
                        Spacer(Modifier.width(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                "已暂停",
                                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = {
                    if (t.totalMs <= 0) 0f
                    else (remaining.toFloat() / t.totalMs).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                when (t.state) {
                    TimerState.RUNNING -> {
                        TextButton(onClick = { TimerEngine.scope.launch { TimerEngine.pause(t.id) } }) { Text("暂停") }
                        TextButton(onClick = { TimerEngine.scope.launch { TimerEngine.cancel(t.id) } }) { Text("取消") }
                    }
                    TimerState.PAUSED -> {
                        TextButton(onClick = { TimerEngine.scope.launch { TimerEngine.resume(t.id) } }) { Text("继续") }
                        TextButton(onClick = { TimerEngine.scope.launch { TimerEngine.cancel(t.id) } }) { Text("取消") }
                    }
                    TimerState.RINGING -> {
                        TextButton(onClick = { TimerEngine.scope.launch { TimerEngine.dismiss(t.id) } }) { Text("停止") }
                    }
                }
            }
        }
    }
}
