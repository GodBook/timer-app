package com.timerapp.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val tick = rememberResumeTick() // 从系统设置返回时刷新状态

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val nm = context.getSystemService(NotificationManager::class.java)
    val pm = context.getSystemService(PowerManager::class.java)
    val pkgUri = Uri.parse("package:${context.packageName}")

    // tick 变化触发整块重组,以下状态即时刷新
    @Suppress("UNUSED_EXPRESSION") tick
    val items = listOf(
        PermItem(
            "通知权限",
            "显示计时进度和结束提醒通知,必须开启",
            notificationsGranted(context),
        ) { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        PermItem(
            "悬浮窗权限",
            "倒计时结束时在其他应用上方弹出横幅,核心功能",
            overlayGranted(context),
        ) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)
            )
        },
        PermItem(
            "全屏通知",
            "锁屏/熄屏时点亮屏幕显示响铃页",
            nm.canUseFullScreenIntent(),
        ) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri)
            )
        },
        PermItem(
            "忽略电池优化",
            "降低系统休眠对提醒准时性的影响",
            pm.isIgnoringBatteryOptimizations(context.packageName),
        ) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)
            )
        },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("权限设置") },
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
            items.forEach { item ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                item.desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        if (item.granted) {
                            Text("已开启", color = MaterialTheme.colorScheme.primary)
                        } else {
                            TextButton(onClick = item.onRequest) { Text("去开启") }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("ColorOS / OriginOS 保活设置", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "OPPO/vivo 系统会主动清理后台,请在应用信息页完成:\n" +
                            "1. 「自启动」和「关联启动」→ 允许\n" +
                            "2. 「电量使用」→ 允许完全后台行为 / 无限制\n" +
                            "3. 最近任务界面下拉本应用卡片 → 锁定\n\n" +
                            "即使应用被清理,已设定的闹钟到点仍会由系统拉起提醒,\n" +
                            "但完成以上设置可以保证进行中的通知不消失。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
                        )
                    }) { Text("打开应用信息页") }
                }
            }
        }
    }
}

private data class PermItem(
    val title: String,
    val desc: String,
    val granted: Boolean,
    val onRequest: () -> Unit,
)
