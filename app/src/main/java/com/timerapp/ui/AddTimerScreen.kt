package com.timerapp.ui

import android.widget.NumberPicker
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.timerapp.core.TimerEngine
import com.timerapp.data.Preset
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun AddTimerScreen(onBack: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var h by rememberSaveable { mutableIntStateOf(0) }
    var m by rememberSaveable { mutableIntStateOf(5) }
    var s by rememberSaveable { mutableIntStateOf(0) }
    val presets by TimerEngine.store.presetsFlow.collectAsState(initial = emptyList())
    var deleting by remember { mutableStateOf<Preset?>(null) }

    val durationMs = ((h * 3600L + m * 60L + s) * 1000L)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新建计时器") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称(可选)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PickerColumn("时", 23, h) { h = it }
                PickerColumn("分", 59, m) { m = it }
                PickerColumn("秒", 59, s) { s = it }
            }
            Spacer(Modifier.height(16.dp))

            if (presets.isNotEmpty()) {
                Text("常用预设(点按开始,长按删除)", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presets.forEach { p ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    TimerEngine.scope.launch {
                                        TimerEngine.start(p.name, p.durationMs)
                                    }
                                    onBack()
                                },
                                onLongClick = { deleting = p },
                            ),
                        ) {
                            Text(
                                "${p.name} ${formatMs(p.durationMs)}",
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            OutlinedButton(
                onClick = {
                    if (durationMs > 0) {
                        val presetName = name.ifBlank { formatMs(durationMs) }
                        TimerEngine.scope.launch {
                            TimerEngine.store.addPreset(presetName, durationMs)
                        }
                    }
                },
                enabled = durationMs > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存为预设") }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    TimerEngine.scope.launch { TimerEngine.start(name, durationMs) }
                    onBack()
                },
                enabled = durationMs > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("开始") }
            Spacer(Modifier.height(24.dp))
        }
    }

    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除预设") },
            text = { Text("删除预设「${p.name} ${formatMs(p.durationMs)}」?") },
            confirmButton = {
                TextButton(onClick = {
                    TimerEngine.scope.launch { TimerEngine.store.deletePreset(p.id) }
                    deleting = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun PickerColumn(label: String, max: Int, value: Int, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AndroidView(
            factory = { ctx ->
                NumberPicker(ctx).apply {
                    minValue = 0
                    maxValue = max
                    setFormatter { v -> "%02d".format(v) }
                }
            },
            update = { np ->
                np.setOnValueChangedListener { _, _, new -> onChange(new) }
                if (np.value != value) np.value = value
            },
        )
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
