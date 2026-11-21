package com.piper.secretaria.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PiperSettings(
    val userName: String = "",
    val defaultReminderMinutes: Int = 30,
    val dailySummaryEnabled: Boolean = true,
    val dailySummaryHour: Int = 8,
    val dailySummaryMinute: Int = 0,
    val voiceReplies: Boolean = true,
    val onboarded: Boolean = false,
)

/** Preferências simples guardadas em SharedPreferences e expostas como StateFlow. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("piper_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<PiperSettings> = _state.asStateFlow()
    val current: PiperSettings get() = _state.value

    private fun load() = PiperSettings(
        userName = prefs.getString("userName", "") ?: "",
        defaultReminderMinutes = prefs.getInt("defaultReminderMinutes", 30),
        dailySummaryEnabled = prefs.getBoolean("dailySummaryEnabled", true),
        dailySummaryHour = prefs.getInt("dailySummaryHour", 8),
        dailySummaryMinute = prefs.getInt("dailySummaryMinute", 0),
        voiceReplies = prefs.getBoolean("voiceReplies", true),
        onboarded = prefs.getBoolean("onboarded", false),
    )

    fun update(transform: (PiperSettings) -> PiperSettings) {
        val s = transform(_state.value)
        prefs.edit()
            .putString("userName", s.userName)
            .putInt("defaultReminderMinutes", s.defaultReminderMinutes)
            .putBoolean("dailySummaryEnabled", s.dailySummaryEnabled)
            .putInt("dailySummaryHour", s.dailySummaryHour)
            .putInt("dailySummaryMinute", s.dailySummaryMinute)
            .putBoolean("voiceReplies", s.voiceReplies)
            .putBoolean("onboarded", s.onboarded)
            .apply()
        _state.value = s
    }
}
