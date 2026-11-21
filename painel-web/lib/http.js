import { storeConfigured } from "./store";
import { currentUser, ensureSeed } from "./auth";

export const json = (data, status = 200) => Response.json(data, { status });

/** Garante banco configurado + usuário logado (e admin, se pedido). */
export async function requireUser(req, { admin = false } = {}) {
  if (!storeConfigured()) return { error: json({ error: "Banco de dados não configurado na Vercel (Upstash Redis)." }, 503) };
  await ensureSeed();
  const user = await currentUser(req);
  if (!user) return { error: json({ error: "Sessão expirada. Entre de novo." }, 401) };
  if (admin && user.role !== "admin") return { error: json({ error: "Apenas administradores." }, 403) };
  return { user };
}
