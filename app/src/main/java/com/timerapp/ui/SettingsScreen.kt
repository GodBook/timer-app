package com.timerapp.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.timerapp.BuildConfig
import com.timerapp.core.TimerEngine
import com.timerapp.data.AlertMode
import com.timerapp.update.ApkInstaller
import com.timerapp.update.UpdateChecker
import kotlinx.coroutines.launch

private sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data class Found(val info: UpdateChecker.ReleaseInfo) : UpdateUi
    data class Downloading(val progress: Int) : UpdateUi
    data class Error(val msg: String) : UpdateUi
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onPermissions: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val alertMode by TimerEngine.store.alertModeFlow.collectAsState(
        initial = AlertMode.LOOP_SOUND_VIBRATE
    )
    var updateUi by remember { mutableStateOf<UpdateUi>(UpdateUi.Idle) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
                .padding(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("提醒方式", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    AlertMode.entries.forEach { mode ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                scope.launch { TimerEngine.store.setAlertMode(mode) }
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = alertMode == mode,
                                onClick = {
                                    scope.launch { TimerEngine.store.setAlertMode(mode) }
                                },
                            )
                            Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Text(
                        "铃声使用系统默认闹钟铃声",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("权限与保活", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onPermissions) { Text("权限设置与 ColorOS 保活引导") }
                }
            }

            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("更新", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "当前版本 v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        onClick = {
                            updateUi = UpdateUi.Checking
                            scope.launch {
                                updateUi = runCatching { UpdateChecker.fetchLatest() }.fold(
                                    onSuccess = {
                                        if (UpdateChecker.isNewer(it.version, BuildConfig.VERSION_NAME)) {
                                            UpdateUi.Found(it)
                                        } else {
                                            UpdateUi.UpToDate
                                        }
                                    },
                                    onFailure = {
                                        UpdateUi.Error(it.message ?: "网络错误,GitHub 可能无法直连")
                                    },
                                )
                            }
                        },
                        enabled = updateUi !is UpdateUi.Checking && updateUi !is UpdateUi.Downloading,
                    ) {
                        Text(if (updateUi is UpdateUi.Checking) "检查中…" else "检查更新")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("关于", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.REPO_URL))
                        )
                    }) { Text("GitHub 仓库") }
                }
            }
        }
    }

    when (val ui = updateUi) {
        is UpdateUi.UpToDate -> AlertDialog(
            onDismissRequest = { updateUi = UpdateUi.Idle },
            title = { Text("检查更新") },
            text = { Text("已是最新版本") },
            confirmButton = {
                TextButton(onClick = { updateUi = UpdateUi.Idle }) { Text("确定") }
            },
        )
        is UpdateUi.Error -> AlertDialog(
            onDismissRequest = { updateUi = UpdateUi.Idle },
            title = { Text("检查更新失败") },
            text = { Text(ui.msg) },
            confirmButton = {
                TextButton(onClick = { updateUi = UpdateUi.Idle }) { Text("确定") }
            },
        )
        is UpdateUi.Found -> AlertDialog(
            onDismissRequest = { updateUi = UpdateUi.Idle },
            title = { Text("发现新版本 v${ui.info.version}") },
            text = {
                Column {
                    if (ui.info.notes.isNotBlank()) {
                        Text(ui.info.notes, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                    }
                    if (!ApkInstaller.canInstall(context)) {
                        Text(
                            "首次更新需先允许本应用安装未知应用,点「下载安装」会先跳转授权页",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val url = ui.info.apkUrl
                        if (url == null) {
                            updateUi = UpdateUi.Error("该版本没有附带 APK 文件")
                            return@TextButton
                        }
                        if (!ApkInstaller.canInstall(context)) {
                            ApkInstaller.requestInstallPermission(context)
                            return@TextButton
                        }
                        updateUi = UpdateUi.Downloading(0)
                        scope.launch {
                            runCatching {
                                UpdateChecker.downloadApk(context, url) { p ->
                                    updateUi = UpdateUi.Downloading(p)
                                }
                            }.fold(
                                onSuccess = {
                                    updateUi = UpdateUi.Idle
                                    ApkInstaller.install(context, it)
                                },
                                onFailure = {
                                    updateUi = UpdateUi.Error(it.message ?: "下载失败")
                                },
                            )
                        }
                    },
                ) { Text("下载安装") }
            },
            dismissButton = {
                TextButton(onClick = { updateUi = UpdateUi.Idle }) { Text("取消") }
            },
        )
        is UpdateUi.Downloading -> AlertDialog(
            onDismissRequest = { },
            title = { Text("正在下载 ${ui.progress}%") },
            text = {
                LinearProgressIndicator(
                    progress = { ui.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { },
        )
        else -> {}
    }
}
