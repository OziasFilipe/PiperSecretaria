package com.piper.secretaria.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRulesTest {
    @Test fun learnsFact() {
        assertEquals(Learning.Fact("Eu prefiro reuniões à tarde"), MemoryRules.learn("lembre que eu prefiro reuniões à tarde"))
        assertEquals(Learning.Fact("Prefiro café sem açúcar"), MemoryRules.learn("prefiro café sem açúcar"))
    }

    @Test fun learnsTaughtReply() {
        assertEquals(
            Learning.Reply("bom dia piper", "Bom dia, chefe! Bora vencer o dia!"),
            MemoryRules.learn("quando eu disser bom dia piper, responda Bom dia, chefe! Bora vencer o dia!"),
        )
    }

    @Test fun learnsName() {
        assertEquals(Learning.Name("Ozias"), MemoryRules.learn("pode me chamar de ozias"))
    }

    @Test fun refusesSecrets() {
        assertEquals(Learning.RefusedSecret, MemoryRules.learn("lembre que minha senha do banco é 1234"))
        assertTrue(MemoryRules.isSecret("a chave de API é sk-abc"))
    }

    @Test fun reminderIsNotMemory() {
        assertNull(MemoryRules.learn("me lembra de ligar pro João amanhã às 15h"))
        assertNull(MemoryRules.learn("lembra de pagar a conta amanhã"))
    }

    @Test fun faqAnswersAboutApp() {
        assertTrue(MemoryRules.faq("como eu adio um lembrete?")!!.contains("Adiar 15 min"))
        assertTrue(MemoryRules.faq("como conecto o outlook")!!.contains("Outlook"))
    }

    @Test fun similarityMatchesCue() {
        assertTrue(MemoryRules.similarity("bom dia, Piper!", "bom dia piper") >= 0.9)
    }
}
