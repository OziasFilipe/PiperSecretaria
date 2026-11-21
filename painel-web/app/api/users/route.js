import { hashPassword, normalizeUser, publicUser, getUser } from "../../../lib/auth";
import { redis, setJson } from "../../../lib/store";
import { json, requireUser } from "../../../lib/http";

export const dynamic = "force-dynamic";

/** Lista os usuários (somente admin). */
export async function GET(req) {
  const { error } = await requireUser(req, { admin: true });
  if (error) return error;
  const names = (await redis("SMEMBERS", "users")) || [];
  const users = [];
  for (const n of names.sort()) {
    const u = await getUser(n);
    if (u) users.push(publicUser(u));
  }
  return json({ users });
}

/** Cria usuário ou troca senha (somente admin). */
export async function POST(req) {
  const { error } = await requireUser(req, { admin: true });
  if (error) return error;
  const body = await req.json().catch(() => ({}));
  const username = normalizeUser(body.username);
  const password = String(body.password || "");
  if (!/^[a-z0-9._-]{3,32}$/.test(username)) return json({ error: "Usuário inválido (3 a 32 letras, números, . _ -)." }, 400);
  if (password.length < 6) return json({ error: "A senha precisa ter pelo menos 6 caracteres." }, 400);
  const existing = await getUser(username);
  await setJson(`user:${username}`, {
    username,
    name: String(body.name || existing?.name || username).slice(0, 60),
    role: body.role === "admin" ? "admin" : existing?.role || "user",
    passwordHash: hashPassword(password),
    createdAt: existing?.createdAt || Date.now(),
    lastLogin: existing?.lastLogin || null,
  });
  await redis("SADD", "users", username);
  return json({ ok: true });
}

/** Remove usuário (somente admin; não remove a si mesmo). */
export async function DELETE(req) {
  const { user, error } = await requireUser(req, { admin: true });
  if (error) return error;
  const username = normalizeUser(new URL(req.url).searchParams.get("username"));
  if (!username || username === user.username) return json({ error: "Não é possível remover este usuário." }, 400);
  await redis("DEL", `user:${username}`, `memory:${username}`);
  await redis("SREM", "users", username);
  return json({ ok: true });
}
