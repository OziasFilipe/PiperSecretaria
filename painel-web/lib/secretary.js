// Cérebro da Piper quando o ChatGPT está conectado.
// O app envia a agenda, as tarefas e a memória; a IA responde e devolve "ações" que o app executa.

const APP_GUIDE = `
Sobre o app Piper (para responder dúvidas do usuário):
- Início: saudação, atalhos (Falar comigo, Agenda, Tarefas, Lembretes), compromissos de hoje e próximos lembretes.
- Agenda: navegação por semana, criar/editar/excluir eventos, tipo (reunião online, ligação, trabalho, pessoal), duração e aviso (na hora, 5/15/30 min, 1 h ou 1 dia antes).
- Tarefas: abas Hoje / Pendentes / Concluídas, estrela de importante, lembrete com data e hora.
- Notificações: compromissos têm "Ver pauta" e "Adiar 15 min"; tarefas têm "Concluir" e "Adiar 15 min". Resumo do dia todo dia no horário escolhido (padrão 8h).
- Conversar com a Piper: voz em português; botão do microfone no centro da barra inferior.
- Anotações: ideias rápidas ("anota ideia: ...").
- Configurações: nome, voz, permissões de aviso (notificações, alarmes exatos, bateria), aviso padrão, resumo do dia, integrações (Outlook, Google e outras agendas do celular), atualização do app, conexão com o painel/ChatGPT e "Memória da Piper" (ver e apagar o que ela aprendeu).
- Atualização: o app verifica nova versão ao abrir e pergunta "Atualizar agora" ou "Depois".
- Outlook: instalar o app Microsoft Outlook no celular e ativar "Sincronizar calendários"; depois ligar a integração em Configurações.
`;

const TOOLS = [
  {
    type: "function",
    function: {
      name: "create_event",
      description: "Cria um compromisso na agenda do usuário.",
      parameters: {
        type: "object",
        properties: {
          title: { type: "string" },
          start: { type: "string", description: "Data e hora locais no formato YYYY-MM-DDTHH:mm" },
          duration_minutes: { type: "integer", default: 60 },
          type: { type: "string", enum: ["MEETING", "CALL", "TASK", "PERSONAL"] },
          reminder_minutes: { type: "integer", description: "Minutos antes para avisar; -1 sem aviso" },
          description: { type: "string" },
        },
        required: ["title", "start"],
      },
    },
  },
  {
    type: "function",
    function: {
      name: "update_event",
      description: "Altera um compromisso existente (use o id da agenda enviada).",
      parameters: {
        type: "object",
        properties: {
          event_id: { type: "integer" },
          title: { type: "string" },
          start: { type: "string", description: "YYYY-MM-DDTHH:mm" },
          duration_minutes: { type: "integer" },
          reminder_minutes: { type: "integer" },
          description: { type: "string" },
        },
        required: ["event_id"],
      },
    },
  },
  {
    type: "function",
    function: {
      name: "delete_event",
      description: "Exclui um compromisso. Só use se o usuário pedir claramente.",
      parameters: { type: "object", properties: { event_id: { type: "integer" } }, required: ["event_id"] },
    },
  },
  {
    type: "function",
    function: {
      name: "create_task",
      description: "Cria uma tarefa, opcionalmente com lembrete.",
      parameters: {
        type: "object",
        properties: {
          title: { type: "string" },
          remind_at: { type: "string", description: "YYYY-MM-DDTHH:mm (opcional)" },
          notes: { type: "string" },
          starred: { type: "boolean" },
        },
        required: ["title"],
      },
    },
  },
  {
    type: "function",
    function: {
      name: "complete_task",
      description: "Marca uma tarefa como concluída.",
      parameters: { type: "object", properties: { task_id: { type: "integer" } }, required: ["task_id"] },
    },
  },
  {
    type: "function",
    function: {
      name: "add_note",
      description: "Guarda uma anotação/ideia do usuário.",
      parameters: { type: "object", properties: { text: { type: "string" } }, required: ["text"] },
    },
  },
  {
    type: "function",
    function: {
      name: "remember",
      description:
        "Guarda na memória da Piper um fato duradouro sobre o usuário (preferência, rotina, nome de pessoas, jeito de falar). Nunca guarde senhas, chaves, tokens, números de cartão ou documentos.",
      parameters: { type: "object", properties: { fact: { type: "string" } }, required: ["fact"] },
    },
  },
];

const SECRET = /(senha|password|token|api[ _-]?key|chave (de )?api|sk-[a-z0-9]|cart[aã]o de cr[eé]dito|\bcvv\b|\bcpf\b)/i;

function systemPrompt(ctx) {
  const name = ctx.userName ? `O usuário se chama ${ctx.userName}.` : "";
  return `Você é a Piper, secretária pessoal do usuário, uma flautinha simpática que mora num app Android.
Fale sempre em português do Brasil, de forma calorosa, natural e BREVE (suas respostas são lidas em voz alta: 1 a 3 frases, sem markdown, sem listas longas).
${name}
Agora são ${ctx.now || "(desconhecido)"} (fuso ${ctx.timezone || "America/Sao_Paulo"}).

Seu trabalho: agendar, lembrar, organizar tarefas, conversar sobre a agenda e tirar dúvidas sobre o app.
- Para criar/alterar/excluir algo, CHAME A FERRAMENTA correspondente; não diga que fez sem chamar.
- Datas relativas ("amanhã", "sexta", "dia 25") devem ser convertidas para YYYY-MM-DDTHH:mm a partir de agora. Se faltar horário essencial, pergunte.
- Use somente a agenda e as tarefas abaixo; nunca invente compromissos.
- Quando o usuário contar algo duradouro sobre si (preferências, rotina, pessoas), use "remember".
- Use a memória abaixo para personalizar as respostas.

${APP_GUIDE}

Memória (o que você já aprendeu sobre o usuário):
${(ctx.memories || []).map((m) => "- " + m).join("\n") || "- (vazia)"}

Agenda (id, início, título, duração, tipo, origem):
${JSON.stringify(ctx.events || [])}

Tarefas pendentes e recentes (id, título, lembrete, concluída):
${JSON.stringify(ctx.tasks || [])}`;
}

async function openai(messages) {
  const res = await fetch("https://api.openai.com/v1/chat/completions", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${process.env.OPENAI_API_KEY}`,
    },
    body: JSON.stringify({
      model: process.env.OPENAI_MODEL || "gpt-4o-mini",
      messages,
      tools: TOOLS,
      temperature: 0.5,
    }),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`OpenAI ${res.status}: ${text.slice(0, 300)}`);
  }
  return res.json();
}

/**
 * @param {{messages: {role: 'user'|'assistant', content: string}[], context: object}} body
 * @returns {Promise<{reply: string, actions: object[]}>}
 */
export async function runSecretary(body) {
  const ctx = body.context || {};
  const history = (body.messages || [])
    .filter((m) => m && (m.role === "user" || m.role === "assistant") && typeof m.content === "string")
    .slice(-16)
    .map((m) => ({ role: m.role, content: m.content.slice(0, 4000) }));

  const messages = [{ role: "system", content: systemPrompt(ctx) }, ...history];
  const actions = [];

  for (let round = 0; round < 3; round++) {
    const data = await openai(messages);
    const msg = data.choices?.[0]?.message;
    if (!msg) break;
    if (!msg.tool_calls?.length) {
      return { reply: (msg.content || "").trim() || "Certo!", actions };
    }
    messages.push(msg);
    for (const call of msg.tool_calls) {
      let args = {};
      try {
        args = JSON.parse(call.function.arguments || "{}");
      } catch {
        args = {};
      }
      let result = "ok, será executado no app";
      if (call.function.name === "remember" && SECRET.test(args.fact || "")) {
        result = "recusado: não guardo senhas, chaves ou documentos";
      } else {
        actions.push({ ...args, action: call.function.name });
      }
      messages.push({ role: "tool", tool_call_id: call.id, content: result });
    }
  }
  return { reply: "Pronto, já cuidei disso.", actions };
}
