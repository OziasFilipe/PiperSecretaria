// Banco de dados no Supabase, via API REST (PostgREST). Sem dependências extras.
// Só roda no servidor (Vercel): usa a chave service_role, que nunca vai para o navegador nem para o app.
const URL_ = () => (process.env.SUPABASE_URL || process.env.NEXT_PUBLIC_SUPABASE_URL || "").replace(/\/+$/, "");
const KEY = () => process.env.SUPABASE_SERVICE_ROLE_KEY || process.env.SUPABASE_SECRET_KEY || "";

export const storeConfigured = () => Boolean(URL_() && KEY());

/**
 * Chamada genérica ao Supabase.
 * @param {string} path ex.: "piper_users?username=eq.ozias" ou "rpc/piper_pull_inbox"
 */
export async function sb(path, { method = "GET", body, prefer } = {}) {
  if (!storeConfigured()) throw new Error("STORE_NOT_CONFIGURED");
  const res = await fetch(`${URL_()}/rest/v1/${path}`, {
    method,
    headers: {
      apikey: KEY(),
      Authorization: `Bearer ${KEY()}`,
      "Content-Type": "application/json",
      ...(prefer ? { Prefer: prefer } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
    cache: "no-store",
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`Supabase ${res.status}: ${text.slice(0, 300)}`);
  return text ? JSON.parse(text) : null;
}

export const eq = (v) => `eq.${encodeURIComponent(String(v))}`;

/** Primeira linha de uma consulta (ou null). */
export async function one(path) {
  const rows = await sb(path);
  return Array.isArray(rows) && rows.length ? rows[0] : null;
}

export const rpc = (fn, args) => sb(`rpc/${fn}`, { method: "POST", body: args });

export const insert = (table, rows, { upsert = false } = {}) =>
  sb(table, { method: "POST", body: rows, prefer: upsert ? "resolution=merge-duplicates,return=minimal" : "return=minimal" });

export const patch = (table, filter, values) => sb(`${table}?${filter}`, { method: "PATCH", body: values, prefer: "return=minimal" });

export const del = (table, filter) => sb(`${table}?${filter}`, { method: "DELETE", prefer: "return=minimal" });

export const ms = (iso) => (iso ? new Date(iso).getTime() : null);
