import { getJson, setJson } from "../../../lib/store";
import { json, requireUser } from "../../../lib/http";

export const dynamic = "force-dynamic";

const SECRET = /(senha|password|token|api[ _-]?key|chave (de )?api|sk-[a-z0-9]|cart[aã]o de cr[eé]dito|\bcvv\b|\bcpf\b)/i;

/** Memória da Piper (o que ela aprendeu), sincronizada entre app e painel. */
export async function GET(req) {
  const { user, error } = await requireUser(req);
  if (error) return error;
  return json({ memories: (await getJson(`memory:${user.username}`)) || [] });
}

export async function PUT(req) {
  const { user, error } = await requireUser(req);
  if (error) return error;
  const body = await req.json().catch(() => ({}));
  const memories = (Array.isArray(body.memories) ? body.memories : [])
    .filter((m) => m && typeof m.text === "string" && m.text.trim() && !SECRET.test(m.text))
    .slice(0, 300)
    .map((m) => ({
      text: m.text.slice(0, 500),
      kind: ["FACT", "REPLY", "HABIT"].includes(m.kind) ? m.kind : "FACT",
      cue: typeof m.cue === "string" ? m.cue.slice(0, 200) : null,
      createdAt: Number(m.createdAt) || Date.now(),
    }));
  await setJson(`memory:${user.username}`, memories);
  return json({ ok: true, count: memories.length });
}
