import { redis } from "../../../lib/store";
import { json, requireUser } from "../../../lib/http";

export const dynamic = "force-dynamic";

/** O app busca (e esvazia) as ações combinadas pelo painel: agendar, criar tarefa etc. */
export async function POST(req) {
  const { user, error } = await requireUser(req);
  if (error) return error;
  const key = `inbox:${user.username}`;
  const items = (await redis("LRANGE", key, 0, 199)) || [];
  if (items.length) await redis("DEL", key);
  return json({ actions: items.map((s) => { try { return JSON.parse(s); } catch { return null; } }).filter(Boolean) });
}
