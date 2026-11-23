import { runSecretary } from "../../../lib/secretary";
import { json, requireUser } from "../../../lib/http";
import { getMemories, getSnapshot, queueActions } from "../../../lib/data";

export const dynamic = "force-dynamic";
export const maxDuration = 30;

const local = (ms) =>
  ms ? new Date(ms).toLocaleString("sv-SE", { timeZone: "America/Sao_Paulo" }).slice(0, 16).replace(" ", "T") : null;

export async function POST(req) {
  const { user, error } = await requireUser(req);
  if (error) return error;
  if (!process.env.OPENAI_API_KEY) {
    return json({ error: "ChatGPT não conectado: falta OPENAI_API_KEY na Vercel." }, 503);
  }
  try {
    const body = await req.json();
    body.context = { ...(body.context || {}), userName: body.context?.userName || user.name };

    // Conversa feita no painel: usa a agenda e as tarefas que o celular sincronizou no Supabase.
    if (body.source === "panel") {
      const [snap, mem] = await Promise.all([getSnapshot(user.username), getMemories(user.username)]);
      const now = Date.now();
      body.context.events = snap.events
        .filter((e) => e.startAt >= now - 86400_000)
        .slice(0, 80)
        .map((e) => ({ id: e.id, start: local(e.startAt), title: e.title, duration: e.durationMinutes, type: e.type, source: e.source || "Piper" }));
      body.context.tasks = snap.tasks
        .filter((t) => !t.done || (t.completedAt && t.completedAt > now - 3 * 86400_000))
        .slice(0, 80)
        .map((t) => ({ id: t.id, title: t.title, remind: local(t.remindAt), done: t.done }));
      body.context.memories = mem.map((m) => (m.cue ? `Quando eu disser "${m.cue}", responda: ${m.text}` : m.text));
    }

    const result = await runSecretary(body);
    // As ações combinadas no painel vão para a caixa de entrada e o celular aplica na próxima sincronização.
    if (body.source === "panel" && result.actions.length) {
      await queueActions(user.username, result.actions, "chat");
      result.queued = true;
    }
    return json(result);
  } catch (e) {
    console.error(e);
    return json({ error: "Falha ao falar com o ChatGPT." }, 502);
  }
}
