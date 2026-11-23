import { getSnapshot, replaceSnapshot } from "../../../lib/data";
import { json, requireUser, safe } from "../../../lib/http";

export const dynamic = "force-dynamic";

/** Celular: envia a "foto" completa da agenda, tarefas e anotações. */
export const PUT = safe(async (req) => {
  const { user, error } = await requireUser(req);
  if (error) return error;
  const body = await req.json().catch(() => ({}));
  const counts = await replaceSnapshot(user.username, { ...body, device: body.device || user.device });
  return json({ ok: true, ...counts, syncedAt: Date.now() });
});

/** Painel: lê a agenda, as tarefas e as anotações do celular. */
export const GET = safe(async (req) => {
  const { user, error } = await requireUser(req);
  if (error) return error;
  return json(await getSnapshot(user.username));
});
