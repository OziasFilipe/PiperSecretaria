package com.piper.secretaria.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object Dates {
    val ptBR: Locale = Locale.forLanguageTag("pt-BR")
    private val time = DateTimeFormatter.ofPattern("HH:mm")
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", ptBR)

    fun zone(): ZoneId = ZoneId.systemDefault()
    fun toDateTime(millis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone())
    fun toDate(millis: Long): LocalDate = toDateTime(millis).toLocalDate()
    fun toMillis(dt: LocalDateTime): Long = dt.atZone(zone()).toInstant().toEpochMilli()
    fun startOfDay(d: LocalDate): Long = d.atStartOfDay(zone()).toInstant().toEpochMilli()

    fun hhmm(millis: Long): String = toDateTime(millis).format(time)

    /** "Sexta, 21 de nov" */
    fun longDay(d: LocalDate): String {
        val wd = d.dayOfWeek.getDisplayName(TextStyle.FULL, ptBR).substringBefore("-").replaceFirstChar { it.uppercase() }
        val m = d.month.getDisplayName(TextStyle.SHORT, ptBR).trimEnd('.')
        return "$wd, ${d.dayOfMonth} de $m"
    }

    fun monthTitle(d: LocalDate): String = d.format(monthYear).replaceFirstChar { it.uppercase() }

    /** "Hoje", "Amanhã", "Ontem" ou "21/11". */
    fun relativeDay(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
        today -> "Hoje"
        today.plusDays(1) -> "Amanhã"
        today.minusDays(1) -> "Ontem"
        else -> "%02d/%02d".format(d.dayOfMonth, d.monthValue)
    }

    /** "Hoje 17:30", "Amanhã 09:00" */
    fun relative(millis: Long): String = "${relativeDay(toDate(millis))} ${hhmm(millis)}"

    fun weekdayLetter(d: LocalDate): String =
        d.dayOfWeek.getDisplayName(TextStyle.NARROW, ptBR).uppercase()
}
