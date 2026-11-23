import { listInbox, pullInbox } from "../../../lib/data";
import { json, requireUser, safe } from "../../../lib/http";

export const dynamic = "force-dynamic";

/** Celular: busca (e marca como entregues) as ações combinadas no painel. */
export const POST = safe(async (req) => {
  const { user, error } = await requireUser(req);
  if (error) return error;
  return json({ actions: await pullInbox(user.username, user.device) });
});

/** Painel: lista o que está aguardando o celular e o que já foi entregue. */
export const GET = safe(async (req) => {
  const { user, error } = await requireUser(req);
  if (error) return error;
  return json({ items: await listInbox(user.username) });
});
