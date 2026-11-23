import { cleanMemories, getMemories, replaceMemories } from "../../../lib/data";
import { json, requireUser, safe } from "../../../lib/http";

export const dynamic = "force-dynamic";

/** Memória da Piper (o que ela aprendeu), sincronizada entre app e painel. */
export const GET = safe(async (req) => {
  const { user, error } = await requireUser(req);
  if (error) return error;
  return json({ memories: await getMemories(user.username) });
});

export const PUT = safe(async (req) => {
  const { user, error } = await requireUser(req);
  if (error) return error;
  const body = await req.json().catch(() => ({}));
  const memories = cleanMemories(body.memories);
  await replaceMemories(user.username, memories);
  return json({ ok: true, count: memories.length });
});
