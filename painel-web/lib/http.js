import { storeConfigured } from "./store";
import { currentUser, ensureSeed } from "./auth";

export const json = (data, status = 200) => Response.json(data, { status });

export const NOT_CONFIGURED = "Banco de dados não configurado: faltam SUPABASE_URL e SUPABASE_SERVICE_ROLE_KEY na Vercel.";

/** Garante banco configurado + usuário logado (e admin, se pedido). */
export async function requireUser(req, { admin = false } = {}) {
  if (!storeConfigured()) return { error: json({ error: NOT_CONFIGURED }, 503) };
  try {
    await ensureSeed();
    const user = await currentUser(req);
    if (!user) return { error: json({ error: "Sessão expirada. Entre de novo." }, 401) };
    if (admin && user.role !== "admin") return { error: json({ error: "Apenas administradores." }, 403) };
    return { user };
  } catch (e) {
    console.error(e);
    return { error: json({ error: "Falha no banco de dados (Supabase). Confira se o schema.sql foi executado." }, 500) };
  }
}

/** Envolve o handler para que erros do banco virem uma resposta legível. */
export function safe(handler) {
  return async (req) => {
    try {
      return await handler(req);
    } catch (e) {
      console.error(e);
      return json({ error: "Erro no servidor do painel." }, 500);
    }
  };
}
