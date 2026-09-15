package com.timerapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.timerapp.core.TimerEngine
import com.timerapp.ui.AddTimerScreen
import com.timerapp.ui.PermissionScreen
import com.timerapp.ui.SettingsScreen
import com.timerapp.ui.TimerAppTheme
import com.timerapp.ui.TimerListScreen
import com.timerapp.ui.notificationsGranted
import com.timerapp.ui.overlayGranted

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TimerAppTheme { AppRoot() }
        }
    }
}

@Composable
private fun AppRoot() {
    var screen by rememberSaveable { mutableStateOf("list") }
    val context = LocalContext.current

    // 首次启动且缺关键权限 → 直接进入权限引导
    LaunchedEffect(Unit) {
        val store = TimerEngine.store
        if (!store.wizardShown()) {
            store.setWizardShown()
            if (!overlayGranted(context) || !notificationsGranted(context)) {
                screen = "perm"
            }
        }
    }

    BackHandler(enabled = screen != "list") { screen = "list" }

    when (screen) {
        "list" -> TimerListScreen(
            onAdd = { screen = "add" },
            onSettings = { screen = "settings" },
            onPermissions = { screen = "perm" },
        )
        "add" -> AddTimerScreen(onBack = { screen = "list" })
        "settings" -> SettingsScreen(
            onBack = { screen = "list" },
            onPermissions = { screen = "perm" },
        )
        "perm" -> PermissionScreen(onBack = { screen = "list" })
    }
}
