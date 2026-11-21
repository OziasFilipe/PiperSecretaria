package com.piper.secretaria.assistant

/** O que a frase ensina para a Piper (modo offline). */
sealed interface Learning {
    data class Fact(val text: String) : Learning
    data class Reply(val cue: String, val answer: String) : Learning
    data class Name(val name: String) : Learning
    data class Forget(val about: String) : Learning
    data object WhatDoYouKnow : Learning
    data object RefusedSecret : Learning
}

/**
 * Regras locais de aprendizado e de "conversa" da Piper quando o ChatGPT não está conectado.
 * Tudo roda no aparelho. Nada que pareça senha, chave ou documento é guardado.
 */
object MemoryRules {
    private val SECRET = Regex(
        """(senha|password|token|api ?key|chave (de )?api|chave pix|sk-[a-z0-9]|cartao|cvv|\bcpf\b|\brg\b|pin do)""",
    )

    fun isSecret(text: String) = SECRET.containsMatchIn(CommandParser.normalize(text))

    fun learn(input: String): Learning? {
        val raw = input.trim()
        val original = raw.trimEnd('.', '!')
        val n = CommandParser.normalize(original)

        if (Regex("""^(o que (voce )?(sabe|aprendeu|lembra) (sobre mim|de mim)|o que voce sabe de mim|minha memoria)""").containsMatchIn(n)) {
            return Learning.WhatDoYouKnow
        }
        Regex("""^(esquece|esqueca|apaga|apague) (que |sobre |o que (eu )?(te )?disse sobre )?(.+)""").find(n)?.let {
            return Learning.Forget(original.substring(it.groups[6]!!.range.first).trim())
        }
        Regex("""^(quando eu (disser|falar|perguntar)) ["“]?(.+?)["”]?,? (voce )?(responde|responda|diga|fala|fale)( que)? ["“]?(.+?)["”]?$""").find(n)?.let { m ->
            val cue = original.substring(m.groups[3]!!.range.first, m.groups[3]!!.range.last + 1)
            val answer = raw.substring(m.groups[7]!!.range.first)
            if (isSecret(cue) || isSecret(answer)) return Learning.RefusedSecret
            return Learning.Reply(cue.trim(' ', '"', '“', '”'), answer.trim(' ', '"', '“', '”'))
        }
        Regex("""^(meu nome e|me chama de|me chame de|pode me chamar de|eu me chamo) (.+)""").find(n)?.let { m ->
            val name = original.substring(m.groups[2]!!.range.first).trim().split(" ").take(3).joinToString(" ")
            return Learning.Name(name.replaceFirstChar { it.uppercase() })
        }
        Regex("""^(lembre|lembra|guarde|guarda|aprenda|aprende|saiba|anote na memoria)( que| isso:?)? (.+)""").find(n)?.let { m ->
            // "lembra de X amanhã" é lembrete com hora, não memória
            if (n.startsWith("lembra de") || n.startsWith("lembre de") || n.startsWith("lembre-me") || n.startsWith("lembra-me")) return null
            val fact = original.substring(m.groups[3]!!.range.first).trim()
            if (isSecret(fact)) return Learning.RefusedSecret
            return Learning.Fact(fact.replaceFirstChar { it.uppercase() })
        }
        Regex("""^(eu )?(gosto|prefiro|nao gosto|costumo|sempre|trabalho|moro|minha esposa|meu marido|meu filho|minha filha|meu chefe|minha empresa|meu time|meu horario)\b""").find(n)?.let {
            if (isSecret(original)) return Learning.RefusedSecret
            if (original.length in 8..300) return Learning.Fact(original.replaceFirstChar { it.uppercase() })
        }
        return null
    }

    /** Semelhança simples por palavras (0..1), ignorando acentos e palavras curtas. */
    fun similarity(a: String, b: String): Double {
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val inter = ta.intersect(tb).size.toDouble()
        return inter / minOf(ta.size, tb.size).coerceAtLeast(1)
    }

    fun tokens(s: String): Set<String> =
        CommandParser.normalize(s).split(Regex("""[^a-z0-9]+""")).filter { it.length > 2 && it !in STOP }.toSet()

    private val STOP = setOf(
        "que", "para", "pra", "com", "uma", "um", "por", "dos", "das", "nos", "nas", "meu", "minha", "voce", "sobre",
        "qual", "quais", "como", "isso", "esse", "essa", "tem", "ter", "sou", "est", "esta", "mais", "muito", "the",
    )

    /** Perguntas frequentes sobre o próprio app, respondidas sem internet. */
    val FAQ: List<Pair<Set<String>, String>> = listOf(
        setOf("adiar", "soneca") to "Quando chegar o aviso, toque em \"Adiar 15 min\" na própria notificação. Eu te aviso de novo em 15 minutos.",
        setOf("atualizar", "atualizacao", "versao") to "Toda vez que você abre o app eu verifico se há versão nova e pergunto se quer atualizar. Também dá para tocar em Configurações › Verificar atualização.",
        setOf("outlook", "google", "integracao", "integrar", "sincronizar") to "Em Configurações › Integrações, ligue \"Outlook, Google e outras agendas\". Para o Outlook, instale o app Outlook no celular e ative Sincronizar calendários.",
        setOf("notificacao", "notificacoes", "aviso", "avisar", "alarme", "bateria") to "Para eu nunca falhar nos avisos, veja em Configurações se notificações, alarmes exatos e bateria sem restrição estão com ✓. Lá tem também o botão Testar notificação.",
        setOf("resumo", "manha", "diario") to "Todo dia, no horário que você escolher em Configurações (padrão 8h), eu mando um resumo com seus compromissos e tarefas.",
        setOf("tarefa", "tarefas", "concluir", "concluida") to "Na aba Tarefas você vê Hoje, Pendentes e Concluídas. Toque no quadradinho para concluir ou na estrela para marcar como importante.",
        setOf("memoria", "aprender", "aprendeu", "esquecer") to "Eu aprendo com o que você me conta, como \"lembre que eu prefiro reuniões à tarde\". Você vê e apaga tudo em Configurações › Memória da Piper.",
        setOf("chatgpt", "painel", "login", "conta", "inteligente") to "Entrando com sua conta do painel, eu passo a usar o ChatGPT para conversar de forma mais inteligente. Sem conexão, continuo funcionando com o que aprendi.",
        setOf("voz", "falar", "microfone") to "Toque no microfone roxo no centro da barra de baixo e fale. Se preferir só texto, desligue as respostas faladas em Configurações › Voz da Piper.",
    )

    fun faq(question: String): String? {
        val t = tokens(question)
        fun hits(keys: Set<String>) = keys.count { k -> t.any { w -> close(w, k) } }
        return FAQ.maxByOrNull { (keys, _) -> hits(keys) }?.takeIf { (keys, _) -> hits(keys) > 0 }?.second
    }

    /** Palavras parecidas: "adio" ~ "adiar", "notificacoes" ~ "notificacao". */
    private fun close(a: String, b: String): Boolean {
        if (a == b) return true
        val p = a.commonPrefixWith(b).length
        return p >= 3 && p >= (minOf(a.length, b.length) * 0.7)
    }
}
