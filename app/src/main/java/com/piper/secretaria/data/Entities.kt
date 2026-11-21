package com.piper.secretaria.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Uma tarefa (to-do). [remindAt] é o momento do lembrete, em epoch millis. */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val remindAt: Long? = null,
    val starred: Boolean = false,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
)

enum class EventType(val label: String) {
    MEETING("Reunião online"),
    CALL("Ligação"),
    TASK("Trabalho"),
    PERSONAL("Pessoal"),
}

/** Um compromisso da agenda. O lembrete dispara [reminderMinutes] antes de [startAt] (-1 = sem lembrete). */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val startAt: Long,
    val durationMinutes: Int = 60,
    val type: EventType = EventType.MEETING,
    val reminderMinutes: Int = 30,
    /** Quando o usuário toca em "Adiar 15 min", o próximo aviso fica guardado aqui. */
    val snoozedUntil: Long? = null,
    /** Preenchido quando o evento veio de uma agenda externa (Outlook, Google…): "cal:<idEvento>:<início>". */
    val externalId: String? = null,
    /** Nome da agenda de origem, ex.: "Outlook · voce@empresa.com". Nulo = criado na Piper. */
    val source: String? = null,
) {
    fun reminderAt(): Long? = snoozedUntil ?: if (reminderMinutes < 0) null else startAt - reminderMinutes * 60_000L
}

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class NotificationCategory { AGENDA, TAREFAS, SISTEMA }

/** Histórico do que a Piper avisou (tela Notificações). */
@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val message: String,
    val category: NotificationCategory,
    val createdAt: Long = System.currentTimeMillis(),
    val read: Boolean = false,
)

enum class MemoryKind { FACT, REPLY, HABIT }

/**
 * O que a Piper aprendeu com o usuário. FACT = fato/preferência; REPLY = resposta ensinada
 * ("quando eu disser [cue], responda [text]"); HABIT = hábito percebido na agenda.
 * Nunca guarda senhas ou chaves (filtrado em MemoryBrain).
 */
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val kind: String = MemoryKind.FACT.name,
    val cue: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
