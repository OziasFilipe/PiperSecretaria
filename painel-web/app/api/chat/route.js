import { runSecretary } from "../../../lib/secretary";
import { json, requireUser } from "../../../lib/http";
import { redis } from "../../../lib/store";

export const dynamic = "force-dynamic";
export const maxDuration = 30;

export async function POST(req) {
  const { user, error } = await requireUser(req);
  if (error) return error;
  if (!process.env.OPENAI_API_KEY) {
    return json({ error: "ChatGPT não conectado: falta OPENAI_API_KEY na Vercel." }, 503);
  }
  try {
    const body = await req.json();
    body.context = { ...(body.context || {}), userName: body.context?.userName || user.name };
    const result = await runSecretary(body);
    // Conversa feita no painel: as ações vão para a "caixa de entrada" e o app aplica ao abrir.
    if (body.source === "panel" && result.actions.length) {
      await redis("RPUSH", `inbox:${user.username}`, ...result.actions.map((a) => JSON.stringify(a)));
      result.queued = true;
    }
    return json(result);
  } catch (e) {
    console.error(e);
    return json({ error: "Falha ao falar com o ChatGPT." }, 502);
  }
}
