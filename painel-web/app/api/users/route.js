import { deleteUser, getUser, listUsers, normalizeUser, publicUser, saveUser } from "../../../lib/auth";
import { json, requireUser, safe } from "../../../lib/http";

export const dynamic = "force-dynamic";

/** Lista os usuários (somente admin). */
export const GET = safe(async (req) => {
  const { error } = await requireUser(req, { admin: true });
  if (error) return error;
  return json({ users: (await listUsers()).map(publicUser) });
});

/** Cria usuário ou troca senha (somente admin). */
export const POST = safe(async (req) => {
  const { error } = await requireUser(req, { admin: true });
  if (error) return error;
  const body = await req.json().catch(() => ({}));
  const username = normalizeUser(body.username);
  const password = String(body.password || "");
  if (!/^[a-z0-9._-]{3,32}$/.test(username)) return json({ error: "Usuário inválido (3 a 32 letras, números, . _ -)." }, 400);
  const existing = await getUser(username);
  if (!existing && password.length < 6) return json({ error: "A senha precisa ter pelo menos 6 caracteres." }, 400);
  if (existing && password && password.length < 6) return json({ error: "A senha precisa ter pelo menos 6 caracteres." }, 400);
  await saveUser({
    username,
    name: String(body.name || existing?.name || username).slice(0, 60),
    role: body.role === "admin" ? "admin" : body.role === "user" ? "user" : existing?.role || "user",
    password: password || null,
  });
  return json({ ok: true });
});

/** Remove usuário (somente admin; não remove a si mesmo). */
export const DELETE = safe(async (req) => {
  const { user, error } = await requireUser(req, { admin: true });
  if (error) return error;
  const username = normalizeUser(new URL(req.url).searchParams.get("username"));
  if (!username || username === user.username) return json({ error: "Não é possível remover este usuário." }, 400);
  await deleteUser(username);
  return json({ ok: true });
});
