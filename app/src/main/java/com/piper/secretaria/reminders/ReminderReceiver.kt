package com.piper.secretaria.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.piper.secretaria.data.NotificationCategory
import com.piper.secretaria.piper
import com.piper.secretaria.util.Dates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.piper
        val kind = intent.getStringExtra(EXTRA_KIND) ?: return
        val id = intent.getLongExtra(EXTRA_ID, 0)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, ReminderScheduler.requestCode(kind, id))
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> fire(context, kind, id)
                    ACTION_SNOOZE -> {
                        app.notifier.cancel(notificationId)
                        val until = app.scheduler.snooze(kind, id)
                        if (kind == KIND_EVENT) {
                            app.repository.getEvent(id)?.let { app.database.events().update(it.copy(snoozedUntil = until)) }
                        } else if (kind == KIND_TASK) {
                            app.repository.getTask(id)?.let { app.database.tasks().update(it.copy(remindAt = until)) }
                        }
                    }
                    ACTION_DONE -> {
                        app.notifier.cancel(notificationId)
                        app.repository.completeTask(id)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire(context: Context, kind: String, id: Long) {
        val app = context.piper
        val repo = app.repository
        val code = ReminderScheduler.requestCode(kind, id)
        when (kind) {
            KIND_EVENT -> {
                val e = repo.getEvent(id) ?: return
                if (e.snoozedUntil != null) app.database.events().update(e.copy(snoozedUntil = null))
                val minutes = ((e.startAt - System.currentTimeMillis()) / 60_000.0).roundToInt()
                val headline = when {
                    minutes <= 0 -> "Começando agora"
                    minutes < 60 -> "${e.type.label} em $minutes minutos"
                    else -> "${e.type.label} às ${Dates.hhmm(e.startAt)}"
                }
                app.notifier.eventReminder(code, e.id, headline, e.title)
                repo.log("Lembrete: ${e.title}", "$headline · ${Dates.hhmm(e.startAt)}", NotificationCategory.AGENDA)
            }
            KIND_TASK -> {
                val t = repo.getTask(id) ?: return
                if (t.done) return
                app.notifier.taskReminder(code, t.id, t.title, t.notes)
                repo.log("Tarefa pendente", t.title, NotificationCategory.TAREFAS)
            }
            KIND_DAILY -> {
                val today = LocalDate.now()
                val events = repo.eventsOn(today)
                val tasks = repo.pendingTasks()
                val name = app.settings.current.userName.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""
                val title = "Bom dia$name! Sua pauta de hoje"
                val lines = buildList {
                    add("Você tem ${events.size} compromisso(s) e ${tasks.size} tarefa(s) pendente(s).")
                    events.take(4).forEach { add("• ${Dates.hhmm(it.startAt)} ${it.title}") }
                }
                app.notifier.summary(title, lines.joinToString("\n"))
                repo.log("Resumo do dia", lines.first(), NotificationCategory.SISTEMA)
                app.scheduler.scheduleDailySummary()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.piper.secretaria.FIRE"
        const val ACTION_SNOOZE = "com.piper.secretaria.SNOOZE"
        const val ACTION_DONE = "com.piper.secretaria.DONE"
        const val EXTRA_KIND = "kind"
        const val EXTRA_ID = "id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val KIND_EVENT = "event"
        const val KIND_TASK = "task"
        const val KIND_DAILY = "daily"
    }
}
