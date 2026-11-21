package com.piper.secretaria.assistant

import com.piper.secretaria.cloud.PanelClient
import com.piper.secretaria.cloud.PanelException
import com.piper.secretaria.data.EventEntity
import com.piper.secretaria.data.EventType
import com.piper.secretaria.data.MemoryEntity
import com.piper.secretaria.data.MemoryKind
import com.piper.secretaria.data.PiperRepository
import com.piper.secretaria.data.SettingsStore
import com.piper.secretaria.data.TaskEntity
import com.piper.secretaria.util.Dates
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Cérebro da Piper.
 * - Conectada ao painel com ChatGPT: manda a conversa, a agenda, as tarefas e a memória; executa as ações devolvidas.
 * - Sem conexão: entende comandos em português (CommandParser), aprende com o usuário (MemoryRules)
 *   e usa a memória para responder de um jeito natural.
 */
class Assistant(
    private val repo: PiperRepository,
    private val settings: SettingsStore,
    private val panel: PanelClient,
) {
    /** Chamado quando a memória muda, para sincronizar com o painel. */
    var onMemoryChanged: (suspend () -> Unit)? = null

    suspend fun handle(text: String, history: List<Pair<Boolean, String>> = emptyList(), now: LocalDateTime = LocalDateTime.now()): String {
        val s = settings.current
        if (s.loggedIn && s.chatGptEnabled) {
            try {
                return cloud(text, history, now)
            } catch (e: PanelException) {
                if (e.unauthorized) settings.update { it.copy(sessionToken = "") }
                // cai para o modo offline abaixo
            }
        }
        return offline(text, now)
    }

    // ---------------- ChatGPT (painel) ----------------

    private suspend fun cloud(text: String, history: List<Pair<Boolean, String>>, now: LocalDateTime): String {
        val messages = JSONArray()
        history.takeLast(14).forEach { (fromUser, t) ->
            messages.put(JSONObject().put("role", if (fromUser) "user" else "assistant").put("content", t))
        }
        messages.put(JSONObject().put("role", "user").put("content", text))
        val result = panel.chat(messages, buildContext(now))
        val actions = result.optJSONArray("actions") ?: JSONArray()
        for (i in 0 until actions.length()) applyAction(actions.getJSONObject(i))
        return result.optString("reply").ifBlank { "Certo!" }
    }

    suspend fun buildContext(now: LocalDateTime): JSONObject {
        val nowMs = Dates.toMillis(now)
        val events = JSONArray()
        repo.upcomingEvents(nowMs - 7 * 86_400_000L, nowMs + 60 * 86_400_000L).forEach { e ->
            events.put(
                JSONObject().put("id", e.id).put("start", iso(e.startAt)).put("title", e.title)
                    .put("duration", e.durationMinutes).put("type", e.type.name)
                    .put("source", e.source ?: "Piper").put("notes", e.description.take(200)),
            )
        }
        val tasks = JSONArray()
        repo.allTasks().filter { !it.done || (it.completedAt ?: 0) > nowMs - 3 * 86_400_000L }.take(80).forEach { t ->
            tasks.put(
                JSONObject().put("id", t.id).put("title", t.title).put("remindAt", t.remindAt?.let { iso(it) } ?: JSONObject.NULL)
                    .put("done", t.done).put("starred", t.starred),
            )
        }
        return JSONObject()
            .put("now", "%04d-%02d-%02dT%02d:%02d (%s)".format(now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute, Dates.longDay(now.toLocalDate())))
            .put("timezone", ZoneId.systemDefault().id)
            .put("userName", settings.current.userName)
            .put("memories", JSONArray(memoryLines()))
            .put("events", events)
            .put("tasks", tasks)
    }

    private suspend fun memoryLines(): List<String> = repo.allMemories().map { m ->
        if (m.kind == MemoryKind.REPLY.name && m.cue != null) "Quando o usuário disser \"${m.cue}\", responda \"${m.text}\"" else m.text
    }

    /** Executa uma ação vinda do ChatGPT (ou da caixa de entrada do painel). */
    suspend fun applyAction(a: JSONObject): Boolean = runCatching {
        when (a.optString("action")) {
            "create_event" -> {
                val start = parseIso(a.getString("start")) ?: return false
                repo.saveEvent(
                    EventEntity(
                        title = a.getString("title"),
                        startAt = start,
                        durationMinutes = a.optInt("duration_minutes", 60),
                        type = runCatching { EventType.valueOf(a.optString("type")) }.getOrDefault(guessType(a)),
                        reminderMinutes = if (a.has("reminder_minutes")) a.getInt("reminder_minutes") else settings.current.defaultReminderMinutes,
                        description = a.optString("description"),
                    ),
                )
            }
            "update_event" -> {
                val e = repo.getEvent(a.getLong("event_id")) ?: return false
                repo.saveEvent(
                    e.copy(
                        title = a.optString("title").ifBlank { e.title },
                        startAt = a.optString("start").takeIf { it.isNotBlank() }?.let(::parseIso) ?: e.startAt,
                        durationMinutes = if (a.has("duration_minutes")) a.getInt("duration_minutes") else e.durationMinutes,
                        reminderMinutes = if (a.has("reminder_minutes")) a.getInt("reminder_minutes") else e.reminderMinutes,
                        description = if (a.has("description")) a.getString("description") else e.description,
                        snoozedUntil = null,
                    ),
                )
            }
            "delete_event" -> repo.getEvent(a.getLong("event_id"))?.let { repo.deleteEvent(it) }
            "create_task" -> repo.saveTask(
                TaskEntity(
                    title = a.getString("title"),
                    notes = a.optString("notes"),
                    starred = a.optBoolean("starred"),
                    remindAt = a.optString("remind_at").takeIf { it.isNotBlank() }?.let(::parseIso),
                ),
            )
            "complete_task" -> repo.completeTask(a.getLong("task_id"))
            "add_note" -> repo.addNote(a.getString("text"))
            "remember" -> {
                val fact = a.getString("fact")
                if (!MemoryRules.isSecret(fact)) {
                    repo.addMemory(MemoryEntity(text = fact))
                    onMemoryChanged?.invoke()
                }
            }
            else -> return false
        }
        true
    }.getOrDefault(false)

    private fun guessType(a: JSONObject): EventType {
        val t = a.optString("title").lowercase()
        return when {
            "lig" in t || "telefon" in t -> EventType.CALL
            "aniver" in t || "médico" in t || "medico" in t -> EventType.PERSONAL
            else -> EventType.MEETING
        }
    }

    private fun iso(ms: Long): String {
        val d = Dates.toDateTime(ms)
        return "%04d-%02d-%02dT%02d:%02d".format(d.year, d.monthValue, d.dayOfMonth, d.hour, d.minute)
    }

    private fun parseIso(s: String): Long? = runCatching { Dates.toMillis(LocalDateTime.parse(s.take(16))) }.getOrNull()

    // ---------------- Offline ----------------

    private suspend fun offline(text: String, now: LocalDateTime): String {
        val name = settings.current.userName.takeIf { it.isNotBlank() }

        // 1) Ensinar/aprender
        when (val l = MemoryRules.learn(text)) {
            is Learning.Fact -> {
                repo.addMemory(MemoryEntity(text = l.text))
                onMemoryChanged?.invoke()
                return pick("Anotado na memória! Vou lembrar disso.", "Entendi, vou levar isso em conta daqui pra frente.", "Guardei isso comigo. 😉")
            }
            is Learning.Reply -> {
                repo.addMemory(MemoryEntity(text = l.answer, kind = MemoryKind.REPLY.name, cue = l.cue))
                onMemoryChanged?.invoke()
                return "Combinado! Quando você disser \"${l.cue}\", eu respondo assim."
            }
            is Learning.Name -> {
                settings.update { it.copy(userName = l.name) }
                repo.addMemory(MemoryEntity(text = "O usuário gosta de ser chamado de ${l.name}"))
                onMemoryChanged?.invoke()
                return "Prazer, ${l.name}! Vou te chamar assim."
            }
            is Learning.Forget -> {
                val gone = repo.allMemories().filter { MemoryRules.similarity(it.text + " " + (it.cue ?: ""), l.about) >= 0.5 }
                gone.forEach { repo.deleteMemory(it) }
                if (gone.isNotEmpty()) onMemoryChanged?.invoke()
                return if (gone.isEmpty()) "Não encontrei nada sobre isso na minha memória." else "Pronto, esqueci ${gone.size} coisa(s) sobre isso."
            }
            Learning.WhatDoYouKnow -> {
                val mem = repo.allMemories().filter { it.kind != MemoryKind.REPLY.name }.take(8)
                return if (mem.isEmpty()) "Ainda estou te conhecendo! Me conte coisas como \"lembre que eu prefiro reuniões à tarde\"."
                else "Isso é o que eu sei: " + mem.joinToString("; ") { it.text } + "."
            }
            Learning.RefusedSecret -> return "Por segurança eu não guardo senhas, chaves ou documentos. Prefira um gerenciador de senhas."
            null -> {}
        }

        // 2) Respostas que o usuário me ensinou
        repo.allMemories().filter { it.kind == MemoryKind.REPLY.name && it.cue != null }
            .maxByOrNull { MemoryRules.similarity(text, it.cue!!) }
            ?.takeIf { MemoryRules.similarity(text, it.cue!!) >= 0.75 }
            ?.let { return it.text }

        // 3) Comandos de agenda/tarefas
        return when (val c = CommandParser.parse(text, now)) {
            is Command.CreateTask -> {
                repo.saveTask(TaskEntity(title = c.title, remindAt = c.remindAt?.let(Dates::toMillis)))
                if (c.remindAt != null) "Pronto! Tarefa \"${c.title}\" criada. Te aviso ${when_(c.remindAt)}."
                else pick("Pronto! Anotei a tarefa \"${c.title}\".", "Feito! \"${c.title}\" está na sua lista.")
            }
            is Command.CreateReminder -> {
                if (c.at == null) {
                    "Quando devo te lembrar de \"${c.title}\"? Diga por exemplo: \"me lembra de ${c.title.lowercase()} amanhã às 9h\"."
                } else {
                    repo.saveTask(TaskEntity(title = c.title, remindAt = Dates.toMillis(c.at)))
                    pick("Combinado! Vou te lembrar de \"${c.title}\" ${when_(c.at)}.", "Deixa comigo: \"${c.title}\" ${when_(c.at)}.")
                }
            }
            is Command.CreateEvent -> {
                if (c.at == null) {
                    val habit = repo.allMemories().firstOrNull { it.kind == MemoryKind.HABIT.name }?.text
                    "Para quando é \"${c.title}\"? Diga por exemplo: \"agendar ${c.title.lowercase()} sexta às 14h\"." +
                        (habit?.let { " ($it.)" } ?: "")
                } else {
                    val minutes = settings.current.defaultReminderMinutes
                    val type = if (c.title.contains(Regex("lig|telefon", RegexOption.IGNORE_CASE))) EventType.CALL else EventType.MEETING
                    val clash = repo.eventsOn(c.at.toLocalDate()).firstOrNull { kotlin.math.abs(it.startAt - Dates.toMillis(c.at)) < 60 * 60_000L }
                    repo.saveEvent(EventEntity(title = c.title, startAt = Dates.toMillis(c.at), type = type, reminderMinutes = minutes))
                    "Agendado: \"${c.title}\" ${when_(c.at)}. Te aviso $minutes minutos antes." +
                        (clash?.let { " Atenção: perto desse horário você já tem \"${it.title}\" às ${Dates.hhmm(it.startAt)}." } ?: "")
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
                val greet = when (now.hour) { in 5..11 -> "Bom dia"; in 12..17 -> "Boa tarde"; else -> "Boa noite" }
                val today = repo.eventsOn(now.toLocalDate()).count { it.startAt > System.currentTimeMillis() }
                "$greet${name?.let { ", $it" } ?: ""}! " +
                    if (today > 0) "Você ainda tem $today compromisso(s) hoje. Quer que eu te diga quais?" else "Sua agenda de hoje está tranquila. Como posso ajudar?"
            }
            Command.Help -> fallback(text, name)
        }
    }

    /** Quando não é comando: tenta dúvida sobre o app, depois a memória, depois uma resposta simpática. */
    private suspend fun fallback(text: String, name: String?): String {
        MemoryRules.faq(text)?.let { return it }
        val n = CommandParser.normalize(text)
        if (Regex("""\b(obrigad|valeu|brigad)""").containsMatchIn(n)) return pick("Por nada${name?.let { ", $it" } ?: ""}! Estou aqui.", "Disponha! 😊")
        if (Regex("""\b(tudo bem|como voce esta|como vai)""").containsMatchIn(n)) return "Tudo ótimo por aqui, pronta para organizar seu dia! E você?"
        if (Regex("""\b(quem e voce|seu nome|o que voce faz)""").containsMatchIn(n)) {
            return "Eu sou a Piper, sua secretária. Agendo compromissos, crio tarefas, te aviso na hora certa e aprendo com você."
        }
        repo.allMemories().filter { it.kind != MemoryKind.REPLY.name }
            .maxByOrNull { MemoryRules.similarity(text, it.text) }
            ?.takeIf { MemoryRules.similarity(text, it.text) >= 0.5 }
            ?.let { return "Pelo que você me contou: ${it.text}." }
        return HELP
    }

    /** Agenda de um dia: compromissos + tarefas com lembrete naquele dia (hoje inclui as atrasadas/sem data). */
    private suspend fun summary(day: LocalDate, today: LocalDate): String {
        val events = repo.eventsOn(day)
        val pending = repo.pendingTasks()
        val start = Dates.startOfDay(day)
        val end = Dates.startOfDay(day.plusDays(1))
        val dayTasks = pending.filter { t ->
            val at = t.remindAt
            if (day == today) at == null || at < end else at != null && at >= start && at < end
        }.sortedBy { it.remindAt ?: Long.MAX_VALUE }

        val label = when (day) {
            today -> "hoje"
            today.plusDays(1) -> "amanhã"
            today.plusDays(2) -> "depois de amanhã"
            else -> "em ${Dates.longDay(day).lowercase()}"
        }
        if (events.isEmpty() && dayTasks.isEmpty()) return "Você não tem nada marcado $label. Agenda livre!"

        val sb = StringBuilder()
        if (events.isEmpty()) sb.append("Nenhum compromisso $label.")
        else {
            sb.append("${label.replaceFirstChar { it.uppercase() }} você tem ${events.size} compromisso${if (events.size > 1) "s" else ""}:")
            events.forEach { sb.append("\n• ${Dates.hhmm(it.startAt)} ${it.title}") }
        }
        if (dayTasks.isNotEmpty()) {
            sb.append("\nE ${dayTasks.size} tarefa${if (dayTasks.size > 1) "s" else ""}:")
            dayTasks.take(8).forEach { t -> sb.append("\n• ${t.remindAt?.let { Dates.hhmm(it) + " " } ?: ""}${t.title}") }
        }
        return sb.toString()
    }

    /** Percebe hábitos na agenda (ex.: horário habitual das reuniões) e guarda como memória HABIT. */
    suspend fun refreshHabits() {
        val now = System.currentTimeMillis()
        val past = repo.upcomingEvents(now - 60 * 86_400_000L, now + 30 * 86_400_000L)
        val meetings = past.filter { it.type == EventType.MEETING }
        val old = repo.allMemories().filter { it.kind == MemoryKind.HABIT.name }
        val fresh = buildList {
            if (meetings.size >= 3) {
                val (hour, count) = meetings.groupingBy { Dates.toDateTime(it.startAt).hour }.eachCount().maxBy { it.value }
                if (count >= 3) add("Você costuma marcar reuniões por volta das ${hour}h")
            }
        }
        if (old.map { it.text } == fresh) return
        old.forEach { repo.deleteMemory(it) }
        fresh.forEach { repo.addMemory(MemoryEntity(text = it, kind = MemoryKind.HABIT.name)) }
    }

    private fun pick(vararg options: String) = options.random()

    private fun when_(at: LocalDateTime): String {
        val d = Dates.relativeDay(at.toLocalDate()).lowercase()
        val dayText = if (d.contains("/")) "em $d" else d
        return "$dayText às %02d:%02d".format(at.hour, at.minute)
    }

    companion object {
        const val HELP = "Hum, essa eu ainda não sei. Posso agendar (\"agendar reunião sexta às 14h\"), lembrar (\"me lembra de ligar pro João às 15h\"), " +
            "dizer sua agenda (\"o que tenho amanhã?\") ou aprender com você (\"lembre que...\"). Conectada ao ChatGPT eu fico ainda mais esperta!"
    }
}
