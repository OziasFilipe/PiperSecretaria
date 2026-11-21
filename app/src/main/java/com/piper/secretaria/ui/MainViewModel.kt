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
import kotlinx.coroutines.launch

data class ChatMessage(val fromUser: Boolean, val text: String, val id: Long = System.nanoTime())

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val piper = app.piper
    private val repo = piper.repository
    private val assistant = Assistant(repo, piper.settings)

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

    fun send(text: String, speak: Boolean = settings.value.voiceReplies) {
        if (text.isBlank()) return
        _chat.update { it + ChatMessage(true, text.trim()) }
        viewModelScope.launch {
            val reply = assistant.handle(text)
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
    fun toggleTask(task: TaskEntity) = viewModelScope.launch { repo.toggleTask(task) }
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

    override fun onCleared() {
        tts.shutdown()
    }
}
