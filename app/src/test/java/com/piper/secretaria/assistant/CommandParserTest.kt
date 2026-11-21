package com.piper.secretaria.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CommandParserTest {
    // Sexta-feira, 21/11/2026, 10:00
    private val now = LocalDateTime.of(2026, 11, 21, 10, 0)

    @Test fun reminderTomorrowAt15() {
        val c = CommandParser.parse("Me lembra de ligar pro João amanhã às 15h", now) as Command.CreateReminder
        assertEquals("Ligar pro João", c.title)
        assertEquals(LocalDateTime.of(2026, 11, 22, 15, 0), c.at)
    }

    @Test fun reminderInMinutes() {
        val c = CommandParser.parse("me avisa em 30 minutos de tirar o bolo", now) as Command.CreateReminder
        assertEquals(LocalDateTime.of(2026, 11, 21, 10, 30), c.at)
        assertEquals("Tirar o bolo", c.title)
    }

    @Test fun eventOnWeekday() {
        val c = CommandParser.parse("Agendar reunião com NC Group segunda às 14:30", now) as Command.CreateEvent
        assertEquals("Reunião com NC Group", c.title)
        assertEquals(LocalDateTime.of(2026, 11, 23, 14, 30), c.at)
    }

    @Test fun taskWithoutTime() {
        val c = CommandParser.parse("Criar tarefa ajustar view no Oracle", now) as Command.CreateTask
        assertEquals("Ajustar view no Oracle", c.title)
        assertEquals(null, c.remindAt)
    }

    @Test fun afternoonHour() {
        val c = CommandParser.parse("nova tarefa enviar relatório às 3 da tarde", now) as Command.CreateTask
        assertEquals(LocalDateTime.of(2026, 11, 21, 15, 0), c.remindAt)
        assertEquals("Enviar relatório", c.title)
    }

    @Test fun summaryTomorrow() {
        val c = CommandParser.parse("O que tenho amanhã?", now)
        assertEquals(Command.Summary(LocalDate.of(2026, 11, 22)), c)
    }

    @Test fun note() {
        val c = CommandParser.parse("Anota ideia: app de recibos", now) as Command.AddNote
        assertEquals("App de recibos", c.text)
    }

    @Test fun pastTimeGoesToTomorrow() {
        val c = CommandParser.parse("lembrete tomar remédio 08:00", now) as Command.CreateReminder
        assertEquals(LocalDateTime.of(2026, 11, 22, 8, 0), c.at)
    }

    @Test fun unknownIsHelp() {
        assertTrue(CommandParser.parse("qual a capital da França", now) is Command.Help)
    }
}
