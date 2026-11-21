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
    /** Integração com as agendas do celular (Outlook, Google, Samsung…). */
    val deviceCalendarsEnabled: Boolean = false,
    val deviceCalendarIds: Set<Long> = emptySet(),
    val lastCalendarSync: Long = 0,
    /** Conta no painel web (sessão salva depois do primeiro login). */
    val panelUrl: String = "",
    val sessionToken: String = "",
    val accountUser: String = "",
    val chatGptEnabled: Boolean = false,
    /** O usuário escolheu usar sem conta (não mostra o login de novo). */
    val offlineMode: Boolean = false,
) {
    val loggedIn: Boolean get() = sessionToken.isNotBlank() && panelUrl.isNotBlank()
}

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
        deviceCalendarsEnabled = prefs.getBoolean("deviceCalendarsEnabled", false),
        deviceCalendarIds = prefs.getStringSet("deviceCalendarIds", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
        lastCalendarSync = prefs.getLong("lastCalendarSync", 0),
        panelUrl = prefs.getString("panelUrl", com.piper.secretaria.BuildConfig.PANEL_URL) ?: "",
        sessionToken = prefs.getString("sessionToken", "") ?: "",
        accountUser = prefs.getString("accountUser", "") ?: "",
        chatGptEnabled = prefs.getBoolean("chatGptEnabled", false),
        offlineMode = prefs.getBoolean("offlineMode", false),
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
            .putBoolean("deviceCalendarsEnabled", s.deviceCalendarsEnabled)
            .putStringSet("deviceCalendarIds", s.deviceCalendarIds.map { it.toString() }.toSet())
            .putLong("lastCalendarSync", s.lastCalendarSync)
            .putString("panelUrl", s.panelUrl)
            .putString("sessionToken", s.sessionToken)
            .putString("accountUser", s.accountUser)
            .putBoolean("chatGptEnabled", s.chatGptEnabled)
            .putBoolean("offlineMode", s.offlineMode)
            .apply()
        _state.value = s
    }
}
