package com.piper.secretaria.data

import com.piper.secretaria.reminders.ReminderScheduler
import com.piper.secretaria.util.Dates
import java.time.LocalDate

/** Ponto único de escrita: toda alteração já (re)agenda ou cancela o lembrete correspondente. */
class PiperRepository(
    private val db: PiperDatabase,
    private val scheduler: ReminderScheduler,
) {
    val tasks = db.tasks().observeAll()
    val events = db.events().observeAll()
    val notes = db.notes().observeAll()
    val notifications = db.notifications().observeAll()

    suspend fun saveTask(task: TaskEntity): TaskEntity {
        val saved = if (task.id == 0L) task.copy(id = db.tasks().insert(task)) else task.also { db.tasks().update(it) }
        scheduler.syncTask(saved)
        return saved
    }

    suspend fun toggleTask(task: TaskEntity) = saveTask(
        task.copy(done = !task.done, completedAt = if (!task.done) System.currentTimeMillis() else null),
    )

    suspend fun completeTask(id: Long) {
        val t = db.tasks().get(id) ?: return
        if (!t.done) toggleTask(t)
    }

    suspend fun deleteTask(task: TaskEntity) {
        scheduler.cancelTask(task.id)
        db.tasks().delete(task)
    }

    suspend fun getTask(id: Long) = db.tasks().get(id)

    suspend fun saveEvent(event: EventEntity): EventEntity {
        val saved = if (event.id == 0L) event.copy(id = db.events().insert(event)) else event.also { db.events().update(it) }
        scheduler.syncEvent(saved)
        return saved
    }

    suspend fun deleteEvent(event: EventEntity) {
        scheduler.cancelEvent(event.id)
        db.events().delete(event)
    }

    suspend fun getEvent(id: Long) = db.events().get(id)

    suspend fun eventsOn(day: LocalDate): List<EventEntity> =
        db.events().between(Dates.startOfDay(day), Dates.startOfDay(day.plusDays(1)))

    suspend fun pendingTasks() = db.tasks().pending()

    suspend fun addNote(text: String) = db.notes().insert(NoteEntity(text = text))
    suspend fun deleteNote(note: NoteEntity) = db.notes().delete(note)

    suspend fun log(title: String, message: String, category: NotificationCategory) =
        db.notifications().insert(NotificationEntity(title = title, message = message, category = category))

    suspend fun markNotificationsRead() = db.notifications().markAllRead()
    suspend fun clearNotifications() = db.notifications().clear()

    /** Reagenda tudo (após reboot, troca de fuso, atualização do app ou permissão concedida). */
    suspend fun rescheduleAll() {
        val now = System.currentTimeMillis()
        db.tasks().pendingReminders(now).forEach { scheduler.syncTask(it) }
        db.events().upcoming(now).forEach { scheduler.syncEvent(it) }
        scheduler.scheduleDailySummary()
    }
}
