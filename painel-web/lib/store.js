// Armazenamento no Upstash Redis via REST (o banco "KV" da Vercel). Sem dependências extras.
const URL_ = () => process.env.KV_REST_API_URL || process.env.UPSTASH_REDIS_REST_URL;
const TOKEN = () => process.env.KV_REST_API_TOKEN || process.env.UPSTASH_REDIS_REST_TOKEN;

export const storeConfigured = () => Boolean(URL_() && TOKEN());

export async function redis(...command) {
  if (!storeConfigured()) throw new Error("STORE_NOT_CONFIGURED");
  const res = await fetch(URL_(), {
    method: "POST",
    headers: { Authorization: `Bearer ${TOKEN()}`, "Content-Type": "application/json" },
    body: JSON.stringify(command.map(String)),
    cache: "no-store",
  });
  const data = await res.json();
  if (data.error) throw new Error(data.error);
  return data.result;
}

export async function getJson(key) {
  const v = await redis("GET", key);
  return v ? JSON.parse(v) : null;
}

export async function setJson(key, value, ttlSeconds) {
  return ttlSeconds
    ? redis("SET", key, JSON.stringify(value), "EX", ttlSeconds)
    : redis("SET", key, JSON.stringify(value));
}
