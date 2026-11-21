package com.piper.secretaria.assistant

import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** O que a Piper entendeu de uma frase em português. */
sealed interface Command {
    data class CreateTask(val title: String, val remindAt: LocalDateTime?) : Command
    data class CreateReminder(val title: String, val at: LocalDateTime?) : Command
    data class CreateEvent(val title: String, val at: LocalDateTime?) : Command
    data class AddNote(val text: String) : Command
    data class Summary(val day: LocalDate) : Command
    data object ListTasks : Command
    data object Greeting : Command
    data object Help : Command
}

/**
 * Interpretador local (sem internet) de comandos de voz/texto em pt-BR.
 * Exemplos: "me lembra de ligar pro João amanhã às 15h", "agendar reunião com NC Group sexta 14:00",
 * "criar tarefa ajustar view no Oracle", "o que tenho hoje?", "anota ideia: app de recibos".
 */
object CommandParser {

    fun parse(input: String, now: LocalDateTime = LocalDateTime.now()): Command {
        val original = input.trim()
        val n = normalize(original)
        if (n.isBlank()) return Command.Help

        // Consultas
        if (Regex("""\b(o que tenho|minha agenda|meus compromissos|resumo|pauta|agenda de (hoje|amanha))\b""").containsMatchIn(n)) {
            val day = if (n.contains("amanha")) now.toLocalDate().plusDays(1) else now.toLocalDate()
            return Command.Summary(day)
        }
        if (Regex("""\b(tarefas pendentes|minhas tarefas|o que falta|quais tarefas|listar tarefas)\b""").containsMatchIn(n)) {
            return Command.ListTasks
        }

        // Anotação
        Regex("""^(anot[ae]r?|anote|nota|registr[ae]r?)\b( (uma|a) )?( ?(ideia|nota))?:?""").find(n)?.let { m ->
            val text = cleanTitle(original, listOf(m.range))
            return if (text.isBlank()) Command.Help else Command.AddNote(text)
        }

        val time = extractDateTime(n, now)
        val removes = time.ranges.toMutableList()

        val eventVerb = Regex("""\b(agend[ae]r?|marc[ae]r?|nov[oa] (evento|compromisso|reuniao))\b( (um|uma))?( (compromisso|evento))?""").find(n)
        val reminderVerb = Regex("""\b(me )?(lembr[ae]r?|lembrete|avis[ae]r?)( me)?( (de|que|para|pra))?\b""").find(n)
        val taskVerb = Regex("""\b(cri[ae]r?|adicion[ae]r?|nova|novo|anot[ae]r?)?\s*(uma )?(tarefa|to-?do)( (de|para|pra))?:?""").find(n)

        return when {
            eventVerb != null -> {
                removes += eventVerb.range
                Command.CreateEvent(cleanTitle(original, removes).ifBlank { "Compromisso" }, time.value)
            }
            reminderVerb != null -> {
                removes += reminderVerb.range
                Command.CreateReminder(cleanTitle(original, removes).ifBlank { "Lembrete" }, time.value)
            }
            taskVerb != null && taskVerb.value.isNotBlank() -> {
                removes += taskVerb.range
                val title = cleanTitle(original, removes)
                if (title.isBlank()) Command.Help else Command.CreateTask(title, time.value)
            }
            Regex("""^(oi|ola|bom dia|boa tarde|boa noite|e ai|hey)\b""").containsMatchIn(n) -> Command.Greeting
            Regex("""\breuniao\b""").containsMatchIn(n) && time.value != null ->
                Command.CreateEvent(cleanTitle(original, removes), time.value)
            Regex("""^(preciso|tenho que|devo|lembrar)\b""").containsMatchIn(n) -> {
                val m = Regex("""^(preciso|tenho que|devo|lembrar)( de)?""").find(n)!!
                removes += m.range
                Command.CreateTask(cleanTitle(original, removes), time.value)
            }
            else -> Command.Help
        }
    }

    data class TimeMatch(val value: LocalDateTime?, val ranges: List<IntRange>)

    fun extractDateTime(n: String, now: LocalDateTime): TimeMatch {
        val ranges = mutableListOf<IntRange>()

        // "em 30 minutos", "daqui a 2 horas", "daqui meia hora"
        Regex("""\b(em|daqui a|daqui)\s+(\d+|uma|um|meia|duas|dois)\s*(minutos?|min|horas?|hrs?|h)\b""").find(n)?.let { m ->
            val qty = when (val q = m.groupValues[2]) {
                "um", "uma" -> 1L; "dois", "duas" -> 2L; "meia" -> -30L
                else -> q.toLong()
            }
            val unit = m.groupValues[3]
            val minutes = when {
                qty == -30L -> 30L
                unit.startsWith("h") -> qty * 60
                else -> qty
            }
            ranges += m.range
            return TimeMatch(now.plusMinutes(minutes).withSecond(0).withNano(0), ranges)
        }

        val today = now.toLocalDate()
        var date: LocalDate? = null
        fun take(m: MatchResult) { ranges += m.range }

        Regex("""\bdepois de amanha\b""").find(n)?.let { take(it); date = today.plusDays(2) }
        if (date == null) Regex("""\bamanha\b""").find(n)?.let { take(it); date = today.plusDays(1) }
        if (date == null) Regex("""\bhoje\b""").find(n)?.let { take(it); date = today }
        if (date == null) {
            Regex("""\b(n[ao] )?(proxim[ao] )?(segunda|terca|quarta|quinta|sexta|sabado|domingo)(-feira| feira)?\b""").find(n)?.let { m ->
                take(m)
                val dow = when (m.groupValues[3]) {
                    "segunda" -> DayOfWeek.MONDAY; "terca" -> DayOfWeek.TUESDAY; "quarta" -> DayOfWeek.WEDNESDAY
                    "quinta" -> DayOfWeek.THURSDAY; "sexta" -> DayOfWeek.FRIDAY; "sabado" -> DayOfWeek.SATURDAY
                    else -> DayOfWeek.SUNDAY
                }
                date = today.with(TemporalAdjusters.next(dow))
            }
        }
        if (date == null) {
            Regex("""\b(no )?dia (\d{1,2})(/(\d{1,2}))?\b|\b(\d{1,2})/(\d{1,2})\b""").find(n)?.let { m ->
                val day = (m.groupValues[2].ifEmpty { m.groupValues[5] }).toInt()
                val month = (m.groupValues[4].ifEmpty { m.groupValues[6] }).toIntOrNull() ?: today.monthValue
                runCatching {
                    var d = LocalDate.of(today.year, month, day)
                    if (d.isBefore(today)) d = if (m.groupValues[4].isEmpty() && m.groupValues[6].isEmpty()) d.plusMonths(1) else d.plusYears(1)
                    date = d
                    take(m)
                }
            }
        }

        var time: LocalTime? = null
        Regex("""\b((a|as|para as|pras|ao) )?(meio[- ]dia|meia[- ]noite)\b""").find(n)?.let { m ->
            take(m); time = if (m.groupValues[3].startsWith("meio")) LocalTime.NOON else LocalTime.MIDNIGHT
        }
        if (time == null) {
            Regex("""\b((a|as|para as|pras|ate as) )?(\d{1,2})(?:(?::|h)(\d{2})|h|\s?horas)\b""").find(n)?.let { m ->
                val h = m.groupValues[3].toInt(); val min = m.groupValues[4].toIntOrNull() ?: 0
                if (h in 0..23 && min in 0..59) { take(m); time = LocalTime.of(h, min) }
            }
        }
        if (time == null) {
            Regex("""\b(as|pras|para as) (\d{1,2})\b""").find(n)?.let { m ->
                val h = m.groupValues[2].toInt()
                if (h in 0..23) { take(m); time = LocalTime.of(h, 0) }
            }
        }
        Regex("""\b(da|de) (manha|tarde|noite)\b""").find(n)?.let { m ->
            take(m)
            val period = m.groupValues[2]
            val t = time
            time = when {
                t == null -> when (period) { "manha" -> LocalTime.of(9, 0); "tarde" -> LocalTime.of(14, 0); else -> LocalTime.of(19, 0) }
                period != "manha" && t.hour < 12 -> t.plusHours(12)
                else -> t
            }
        }

        val d = date
        val t = time
        val value = when {
            d == null && t == null -> null
            d == null -> {
                var dt = LocalDateTime.of(today, t)
                if (!dt.isAfter(now)) {
                    // "às 3" pela tarde: tenta 15h antes de ir para amanhã
                    dt = if (t!!.hour in 1..7 && LocalDateTime.of(today, t.plusHours(12)).isAfter(now)) LocalDateTime.of(today, t.plusHours(12))
                    else dt.plusDays(1)
                }
                dt
            }
            else -> LocalDateTime.of(d, t ?: LocalTime.of(9, 0))
        }
        return TimeMatch(value, ranges)
    }

    /** Remove acentos sem mudar o tamanho da string, para que os índices batam com o original. */
    fun normalize(s: String): String = buildString(s.length) {
        for (c in s) {
            val base = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).first()
            append(base.lowercaseChar())
        }
    }

    private fun cleanTitle(original: String, remove: List<IntRange>): String {
        val sb = StringBuilder()
        original.forEachIndexed { i, c -> if (remove.none { i in it }) sb.append(c) else sb.append(' ') }
        var t = sb.toString().replace(Regex("""\s+"""), " ").trim()
        t = t.trim(' ', ',', '.', ':', ';', '?', '!', '-')
        // conectores que sobram no início/fim
        val lead = Regex("""^(de|que|para|pra|o|a|um|uma|com a|e|as|às|no|na)\s+""", RegexOption.IGNORE_CASE)
        repeat(3) { t = t.replace(lead, "") }
        t = t.replace(Regex("""\s+(de|para|pra|no|na|as|às|em|e|a)$""", RegexOption.IGNORE_CASE), "")
        return t.trim(' ', ',', '.', ':').replaceFirstChar { it.uppercase() }
    }
}
