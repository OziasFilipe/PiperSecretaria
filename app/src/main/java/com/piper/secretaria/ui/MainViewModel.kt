package com.piper.secretaria.ui

import android.app.Application
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.piper.secretaria.assistant.Assistant
import com.piper.secretaria.data.EventEntity
import com.piper.secretaria.data.NoteEntity
import com.piper.secretaria.data.PiperSettings
import com.piper.secretaria.data.TaskEntity
import com.piper.secretaria.piper
import com.piper.secretaria.util.Dates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

data class ChatMessage(val fromUser: Boolean, val text: String, val id: Long = System.nanoTime())

@OptIn(kotlinx.coroutines.FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val piper = app.piper
    private val repo = piper.repository
    private val assistant = Assistant(repo, piper.settings, piper.panel).also { a ->
        a.onMemoryChanged = { pushMemory() }
    }

    private fun <T> kotlinx.coroutines.flow.Flow<List<T>>.state() =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tasks = repo.tasks.state()
    val events = repo.events.state()
    val notes = repo.notes.state()
    val notifications = repo.notifications.state()
    val settings: StateFlow<PiperSettings> = piper.settings.state

    private val _chat = MutableStateFlow(listOf(ChatMessage(false, "Oi! Pode falar, estou ouvindo. Como posso ajudar?")))
    val chat = _chat.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    val speaking = _speaking.asStateFlow()

    private var ttsReady = false
    private val tts = TextToSpeech(app) { status ->
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) initTts()
    }

    private fun initTts() {
        tts.setLanguage(Dates.ptBR)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { _speaking.value = true }
            override fun onDone(utteranceId: String?) { _speaking.value = false }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { _speaking.value = false }
        })
    }

    private val _thinking = MutableStateFlow(false)
    val thinking = _thinking.asStateFlow()

    fun send(text: String, speak: Boolean = settings.value.voiceReplies) {
        if (text.isBlank()) return
        val history = _chat.value.drop(1).map { it.fromUser to it.text }
        _chat.update { it + ChatMessage(true, text.trim()) }
        viewModelScope.launch {
            _thinking.value = true
            val reply = try { assistant.handle(text, history) } finally { _thinking.value = false }
            _chat.update { it + ChatMessage(false, reply) }
            if (speak) say(reply)
        }
    }

    fun say(text: String) {
        if (!ttsReady) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "piper-${System.nanoTime()}")
    }

    fun stopSpeaking() {
        tts.stop(); _speaking.value = false
    }

    fun saveTask(task: TaskEntity) = viewModelScope.launch { repo.saveTask(task) }
    /** Aumenta quando uma tarefa é concluída: a Piper comemora. */
    private val _celebrations = MutableStateFlow(0)
    val celebrations = _celebrations.asStateFlow()

    fun toggleTask(task: TaskEntity) = viewModelScope.launch {
        repo.toggleTask(task)
        if (!task.done) _celebrations.update { it + 1 }
    }
    fun deleteTask(task: TaskEntity) = viewModelScope.launch { repo.deleteTask(task) }
    fun saveEvent(event: EventEntity) = viewModelScope.launch { repo.saveEvent(event.copy(snoozedUntil = null)) }
    fun deleteEvent(event: EventEntity) = viewModelScope.launch { repo.deleteEvent(event) }
    fun addNote(text: String) = viewModelScope.launch { if (text.isNotBlank()) repo.addNote(text.trim()) }
    fun deleteNote(note: NoteEntity) = viewModelScope.launch { repo.deleteNote(note) }
    fun markNotificationsRead() = viewModelScope.launch { repo.markNotificationsRead() }
    fun clearNotifications() = viewModelScope.launch { repo.clearNotifications() }

    fun updateSettings(transform: (PiperSettings) -> PiperSettings) {
        piper.settings.update(transform)
        piper.scheduler.scheduleDailySummary()
    }

    suspend fun findEvent(id: Long) = repo.getEvent(id)
    suspend fun findTask(id: Long) = repo.getTask(id)

    fun rescheduleAll() = viewModelScope.launch { repo.rescheduleAll() }

    fun testNotification() = piper.notifier.summary("Teste da Piper", "Tudo certo! É assim que eu vou te avisar.")

    fun canScheduleExact() = piper.scheduler.canScheduleExact()
    fun canNotify() = piper.notifier.canNotify()

    // --- Conta no painel web + memória ---
    val memories = repo.memories.state()
    private val _loginState = MutableStateFlow<String?>(null)
    val loginState = _loginState.asStateFlow()
    private val _loggingIn = MutableStateFlow(false)
    val loggingIn = _loggingIn.asStateFlow()

    fun login(url: String, user: String, password: String) = viewModelScope.launch {
        _loggingIn.value = true
        _loginState.value = null
        try {
            val base = com.piper.secretaria.cloud.PanelClient.normalizeUrl(url)
            val r = piper.panel.login(base, user.trim(), password)
            piper.settings.update {
                it.copy(
                    panelUrl = base, sessionToken = r.token, accountUser = r.username, chatGptEnabled = r.chatgpt,
                    offlineMode = false, userName = it.userName.ifBlank { r.name },
                )
            }
            syncCloud()
        } catch (e: Exception) {
            _loginState.value = e.message ?: "Não foi possível entrar."
        } finally {
            _loggingIn.value = false
        }
    }

    fun useOffline() = piper.settings.update { it.copy(offlineMode = true) }

    fun logout() = viewModelScope.launch {
        piper.panel.logout()
        piper.settings.update { it.copy(sessionToken = "", accountUser = "", chatGptEnabled = false, offlineMode = false) }
    }

    fun deleteMemory(m: com.piper.secretaria.data.MemoryEntity) = viewModelScope.launch { repo.deleteMemory(m); pushMemory() }
    fun clearMemories() = viewModelScope.launch { repo.clearMemories(); pushMemory() }

    private suspend fun pushMemory() {
        if (!settings.value.loggedIn) return
        runCatching {
            val arr = org.json.JSONArray()
            repo.allMemories().forEach { m ->
                arr.put(org.json.JSONObject().put("text", m.text).put("kind", m.kind).put("cue", m.cue ?: org.json.JSONObject.NULL).put("createdAt", m.createdAt))
            }
            piper.panel.putMemory(arr)
        }
    }

    /**
     * Ao abrir o app logado: confere a sessão, aplica o que foi combinado no painel web
     * e junta a memória do painel com a do celular.
     */
    fun syncCloud() = viewModelScope.launch {
        assistant.refreshHabits()
        if (!settings.value.loggedIn) return@launch
        try {
            val me = piper.panel.me()
            piper.settings.update { it.copy(chatGptEnabled = me.optBoolean("chatgpt")) }
            applyInbox()
            val remote = piper.panel.getMemory()
            val local = repo.allMemories()
            val known = local.map { it.text.lowercase() to it.cue }.toSet()
            for (i in 0 until remote.length()) {
                val r = remote.getJSONObject(i)
                val text = r.optString("text")
                val cue = r.optString("cue").takeIf { it.isNotBlank() && it != "null" }
                if (text.isNotBlank() && (text.lowercase() to cue) !in known) {
                    repo.addMemory(com.piper.secretaria.data.MemoryEntity(text = text, kind = r.optString("kind", "FACT"), cue = cue, createdAt = r.optLong("createdAt", System.currentTimeMillis())))
                }
            }
            pushMemory()
            pushSnapshot()
        } catch (e: com.piper.secretaria.cloud.PanelException) {
            if (e.unauthorized) piper.settings.update { it.copy(sessionToken = "") }
        }
    }

    // --- Sincronização com o painel web (Supabase) ---
    private val syncLock = kotlinx.coroutines.sync.Mutex()
    private var pollJob: kotlinx.coroutines.Job? = null

    /** Busca no painel o que foi combinado lá (agendar, criar tarefa…) e aplica no celular. */
    private suspend fun applyInbox(): Int = syncLock.withLock {
        val inbox = piper.panel.pullInbox()
        var applied = 0
        for (i in 0 until inbox.length()) if (assistant.applyAction(inbox.getJSONObject(i))) applied++
        if (applied > 0) {
            repo.log("Painel web", "$applied item(ns) combinados no painel foram aplicados.", com.piper.secretaria.data.NotificationCategory.SISTEMA)
        }
        applied
    }

    /** Envia a agenda, as tarefas e as anotações para o painel acompanhar pelo navegador. */
    private suspend fun pushSnapshot() {
        if (!settings.value.loggedIn) return
        runCatching {
            val from = System.currentTimeMillis() - 30L * 86_400_000L
            val events = org.json.JSONArray()
            repo.eventsSince(from).forEach { e ->
                events.put(
                    org.json.JSONObject().put("id", e.id).put("title", e.title).put("description", e.description)
                        .put("startAt", e.startAt).put("durationMinutes", e.durationMinutes).put("type", e.type.name)
                        .put("reminderMinutes", e.reminderMinutes).put("source", e.source ?: org.json.JSONObject.NULL),
                )
            }
            val tasks = org.json.JSONArray()
            repo.allTasks().forEach { t ->
                tasks.put(
                    org.json.JSONObject().put("id", t.id).put("title", t.title).put("notes", t.notes)
                        .put("remindAt", t.remindAt ?: org.json.JSONObject.NULL).put("starred", t.starred).put("done", t.done)
                        .put("createdAt", t.createdAt).put("completedAt", t.completedAt ?: org.json.JSONObject.NULL),
                )
            }
            val notes = org.json.JSONArray()
            repo.allNotes().forEach { n -> notes.put(org.json.JSONObject().put("id", n.id).put("text", n.text).put("createdAt", n.createdAt)) }
            piper.panel.pushSnapshot(
                org.json.JSONObject()
                    .put("device", "Android ${android.os.Build.MODEL}")
                    .put("appVersion", com.piper.secretaria.BuildConfig.VERSION_NAME)
                    .put("events", events).put("tasks", tasks).put("notes", notes),
            )
        }.onFailure { e ->
            if (e is com.piper.secretaria.cloud.PanelException && e.unauthorized) piper.settings.update { it.copy(sessionToken = "") }
        }
    }

    /** App na frente: a cada minuto busca o que foi combinado no painel. */
    fun onForeground() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000)
                if (settings.value.loggedIn) runCatching { applyInbox() }
            }
        }
    }

    fun onBackground() {
        pollJob?.cancel()
        pollJob = null
    }

    init {
        // Sempre que a agenda, as tarefas ou as anotações mudam no celular, envia para o painel (agrupa mudanças de 3 s).
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(repo.tasks, repo.events, repo.notes) { t, e, n -> Triple(t, e, n) }
                .drop(1)
                .debounce(3_000)
                .collect { pushSnapshot() }
        }
    }

    // --- Integrações (agendas do celular: Outlook, Google…) ---
    val deviceCalendars = piper.deviceCalendars
    private val _calendars = MutableStateFlow<List<com.piper.secretaria.integrations.DeviceCalendar>>(emptyList())
    val calendars = _calendars.asStateFlow()
    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus = _syncStatus.asStateFlow()

    fun loadCalendars() = viewModelScope.launch { _calendars.value = deviceCalendars.list() }

    fun setCalendarsEnabled(on: Boolean) {
        piper.settings.update { s ->
            // Na primeira vez, já marca todas as agendas encontradas
            val ids = if (on && s.deviceCalendarIds.isEmpty()) _calendars.value.map { it.id }.toSet() else s.deviceCalendarIds
            s.copy(deviceCalendarsEnabled = on, deviceCalendarIds = ids)
        }
        syncCalendars()
    }

    fun toggleCalendar(id: Long) {
        piper.settings.update { s -> s.copy(deviceCalendarIds = if (id in s.deviceCalendarIds) s.deviceCalendarIds - id else s.deviceCalendarIds + id) }
        syncCalendars()
    }

    fun syncCalendars(silent: Boolean = false) = viewModelScope.launch {
        if (!silent) _syncStatus.value = "Sincronizando…"
        val n = runCatching { deviceCalendars.sync() }
        if (!silent) _syncStatus.value = n.fold({ "$it compromisso(s) sincronizado(s) ✓" }, { "Não consegui ler as agendas do celular." })
    }

    // --- Atualização do app ---
    val updater = piper.updater
    val updateState = updater.state

    /** Pop-up "Atualizar agora / Depois": volta a aparecer sempre que o app é reaberto. */
    private val _updatePopup = MutableStateFlow(false)
    val updatePopup = _updatePopup.asStateFlow()

    fun checkUpdateOnOpen() = viewModelScope.launch {
        val s = updater.checkOnOpen()
        if (s is com.piper.secretaria.update.UpdateState.Available || s is com.piper.secretaria.update.UpdateState.ReadyToInstall) {
            _updatePopup.value = true
        }
    }

    fun checkUpdateNow() = viewModelScope.launch { updater.check() }

    fun postponeUpdate() { _updatePopup.value = false }

    /** Baixa (se preciso) e abre o instalador; pede antes a permissão de instalar apps. */
    fun startUpdate() = viewModelScope.launch {
        _updatePopup.value = false
        when (val s = updater.state.value) {
            is com.piper.secretaria.update.UpdateState.Available -> {
                updater.download(s.info)
                (updater.state.value as? com.piper.secretaria.update.UpdateState.ReadyToInstall)?.let { installOrAsk(it.file) }
            }
            is com.piper.secretaria.update.UpdateState.ReadyToInstall -> installOrAsk(s.file)
            else -> {}
        }
    }

    private fun installOrAsk(file: java.io.File) {
        if (updater.canInstall()) updater.install(file) else updater.openInstallPermissionSettings()
    }

    override fun onCleared() {
        tts.shutdown()
    }
}
