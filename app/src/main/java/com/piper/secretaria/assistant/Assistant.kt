package com.piper.secretaria.assistant

import com.piper.secretaria.data.EventEntity
import com.piper.secretaria.data.EventType
import com.piper.secretaria.data.PiperRepository
import com.piper.secretaria.data.SettingsStore
import com.piper.secretaria.data.TaskEntity
import com.piper.secretaria.util.Dates
import java.time.LocalDate
import java.time.LocalDateTime

/** Executa o comando entendido e devolve a resposta falada/escrita da Piper. */
class Assistant(private val repo: PiperRepository, private val settings: SettingsStore) {

    suspend fun handle(text: String, now: LocalDateTime = LocalDateTime.now()): String =
        when (val c = CommandParser.parse(text, now)) {
            is Command.CreateTask -> {
                repo.saveTask(TaskEntity(title = c.title, remindAt = c.remindAt?.let(Dates::toMillis)))
                if (c.remindAt != null) "Pronto! Tarefa \"${c.title}\" criada. Te aviso ${when_(c.remindAt)}."
                else "Pronto! Anotei a tarefa \"${c.title}\"."
            }
            is Command.CreateReminder -> {
                if (c.at == null) {
                    "Quando devo te lembrar de \"${c.title}\"? Diga por exemplo: \"me lembra de ${c.title.lowercase()} amanhã às 9h\"."
                } else {
                    repo.saveTask(TaskEntity(title = c.title, remindAt = Dates.toMillis(c.at)))
                    "Combinado! Vou te lembrar de \"${c.title}\" ${when_(c.at)}."
                }
            }
            is Command.CreateEvent -> {
                if (c.at == null) {
                    "Para quando é \"${c.title}\"? Diga por exemplo: \"agendar ${c.title.lowercase()} sexta às 14h\"."
                } else {
                    val minutes = settings.current.defaultReminderMinutes
                    val type = if (c.title.contains(Regex("lig|telefon", RegexOption.IGNORE_CASE))) EventType.CALL else EventType.MEETING
                    repo.saveEvent(EventEntity(title = c.title, startAt = Dates.toMillis(c.at), type = type, reminderMinutes = minutes))
                    "Agendado: \"${c.title}\" ${when_(c.at)}. Te aviso $minutes minutos antes."
                }
            }
            is Command.AddNote -> {
                repo.addNote(c.text)
                "Anotado! Guardei sua ideia em Anotações."
            }
            is Command.Summary -> summary(c.day, now.toLocalDate())
            Command.ListTasks -> {
                val tasks = repo.pendingTasks()
                if (tasks.isEmpty()) "Você não tem tarefas pendentes. Muito bem!"
                else "Você tem ${tasks.size} tarefa(s) pendente(s): " + tasks.take(6).joinToString("; ") { it.title } + "."
            }
            Command.Greeting -> {
                val name = settings.current.userName.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""
                "Oi$name! Como posso te ajudar hoje? Posso criar tarefas, agendar compromissos e te lembrar de tudo."
            }
            Command.Help -> HELP
        }

    private suspend fun summary(day: LocalDate, today: LocalDate): String {
        val events = repo.eventsOn(day)
        val label = if (day == today) "hoje" else "amanhã"
        val tasks = repo.pendingTasks()
        val ev = if (events.isEmpty()) "Você não tem compromissos $label."
        else "Você tem ${events.size} compromisso(s) $label: " + events.joinToString("; ") { "${Dates.hhmm(it.startAt)} ${it.title}" } + "."
        return if (day == today) "$ev E ${tasks.size} tarefa(s) pendente(s)." else ev
    }

    private fun when_(at: LocalDateTime): String {
        val d = Dates.relativeDay(at.toLocalDate()).lowercase()
        val dayText = if (d.contains("/")) "em $d" else d
        return "$dayText às %02d:%02d".format(at.hour, at.minute)
    }

    companion object {
        const val HELP = "Ainda não entendi. Experimente: \"me lembra de ligar pro João amanhã às 15h\", " +
            "\"agendar reunião sexta às 14h\", \"criar tarefa enviar relatório\", \"o que tenho hoje?\" ou \"anota ideia: ...\"."
    }
}
