package com.timerapp.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "app_data")

private val KEY_TIMERS = stringPreferencesKey("timers")
private val KEY_PRESETS = stringPreferencesKey("presets")
private val KEY_ALERT_MODE = stringPreferencesKey("alert_mode")
private val KEY_WIZARD_SHOWN = booleanPreferencesKey("wizard_shown")

private val json = Json { ignoreUnknownKeys = true }

class AppStore(private val context: Context) {

    // ---- 运行中计时器快照 ----

    suspend fun loadTimers(): List<TimerSnapshot> =
        context.dataStore.data.first()[KEY_TIMERS]?.let {
            runCatching { json.decodeFromString<List<TimerSnapshot>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()

    suspend fun saveTimers(list: List<TimerSnapshot>) {
        context.dataStore.edit { it[KEY_TIMERS] = json.encodeToString(list) }
    }

    // ---- 预设 ----

    val presetsFlow: Flow<List<Preset>> = context.dataStore.data.map { prefs ->
        prefs[KEY_PRESETS]?.let {
            runCatching { json.decodeFromString<List<Preset>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    /** 同名同时长的预设不重复添加,返回是否新增 */
    suspend fun addPreset(name: String, durationMs: Long): Boolean {
        var added = false
        context.dataStore.edit { prefs ->
            val list = prefs[KEY_PRESETS]?.let {
                runCatching { json.decodeFromString<List<Preset>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            if (list.any { it.name == name && it.durationMs == durationMs }) return@edit
            val id = (list.maxOfOrNull { it.id } ?: 0) + 1
            prefs[KEY_PRESETS] = json.encodeToString(list + Preset(id, name, durationMs))
            added = true
        }
        return added
    }

    suspend fun deletePreset(id: Int) {
        context.dataStore.edit { prefs ->
            val list = prefs[KEY_PRESETS]?.let {
                runCatching { json.decodeFromString<List<Preset>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            prefs[KEY_PRESETS] = json.encodeToString(list.filter { it.id != id })
        }
    }

    // ---- 设置 ----

    val alertModeFlow: Flow<AlertMode> = context.dataStore.data.map { prefs ->
        prefs[KEY_ALERT_MODE]?.let { runCatching { AlertMode.valueOf(it) }.getOrNull() }
            ?: AlertMode.LOOP_SOUND_VIBRATE
    }

    suspend fun alertMode(): AlertMode = alertModeFlow.first()

    suspend fun setAlertMode(mode: AlertMode) {
        context.dataStore.edit { it[KEY_ALERT_MODE] = mode.name }
    }

    suspend fun wizardShown(): Boolean = context.dataStore.data.first()[KEY_WIZARD_SHOWN] ?: false

    suspend fun setWizardShown() {
        context.dataStore.edit { it[KEY_WIZARD_SHOWN] = true }
    }
}
