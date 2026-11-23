import { createHash, randomBytes, scryptSync, timingSafeEqual } from "crypto";
import { del, eq, insert, ms, one, patch, rpc, sb } from "./store";

const SESSION_DAYS = 180;
const MAX_FAILURES = 8;

export function hashPassword(password) {
  const salt = randomBytes(16).toString("hex");
  const hash = scryptSync(password, salt, 64).toString("hex");
  return `scrypt$${salt}$${hash}`;
}

export function verifyPassword(password, stored) {
  const [, salt, hash] = String(stored || "").split("$");
  if (!salt || !hash) return false;
  const a = Buffer.from(hash, "hex");
  const b = scryptSync(password, salt, 64);
  return a.length === b.length && timingSafeEqual(a, b);
}

const sha256 = (s) => createHash("sha256").update(s).digest("hex");
const nowIso = () => encodeURIComponent(new Date().toISOString());

export const normalizeUser = (u) => String(u || "").trim().toLowerCase();

/** Converte a linha do banco no formato usado pelo painel e pelo app. */
function fromRow(r) {
  if (!r) return null;
  return {
    username: r.username,
    name: r.name,
    role: r.role,
    passwordHash: r.password_hash,
    createdAt: ms(r.created_at),
    lastLogin: ms(r.last_login),
  };
}

let seeded = false;
/** Cria o administrador a partir de ADMIN_USER / ADMIN_PASSWORD, se ainda não existir. */
export async function ensureSeed() {
  if (seeded) return;
  const username = normalizeUser(process.env.ADMIN_USER);
  const password = process.env.ADMIN_PASSWORD;
  if (!username || !password) return;
  if (!(await one(`piper_users?select=username&username=${eq(username)}`))) {
    await insert("piper_users", {
      username,
      name: username.charAt(0).toUpperCase() + username.slice(1),
      role: "admin",
      password_hash: hashPassword(password),
    });
  }
  seeded = true;
}

export async function getUser(username) {
  return fromRow(await one(`piper_users?username=${eq(normalizeUser(username))}`));
}

export async function listUsers() {
  return ((await sb("piper_users?order=username.asc")) || []).map(fromRow);
}

/** Cria o usuário ou atualiza nome/perfil/senha. */
export async function saveUser({ username, name, role, password }) {
  const row = { username, name, role };
  if (password) row.password_hash = hashPassword(password);
  if (await getUser(username)) await patch("piper_users", `username=${eq(username)}`, row);
  else await insert("piper_users", row);
}

/** Remove o usuário; sessões, memória, agenda e tarefas dele saem juntas (on delete cascade). */
export const deleteUser = (username) => del("piper_users", `username=${eq(username)}`);

export const touchLogin = (username) => patch("piper_users", `username=${eq(username)}`, { last_login: new Date().toISOString() });

export function publicUser(u) {
  if (!u) return null;
  const { passwordHash, device, ...rest } = u;
  return rest;
}

const cleanKey = (k) => String(k).replace(/["(),]/g, "");

/** Limite de tentativas: 8 erros a cada 15 minutos por usuário e por IP. */
export async function tooManyAttempts(keys) {
  const list = keys.map((k) => `"${cleanKey(k)}"`).join(",");
  const rows = (await sb(`piper_login_failures?key=in.(${encodeURIComponent(list)})&reset_at=gt.${nowIso()}`)) || [];
  return rows.some((r) => r.count >= MAX_FAILURES);
}

export async function registerFailure(keys) {
  for (const k of keys) await rpc("piper_register_failure", { p_key: cleanKey(k) });
}

export async function createSession(username, device) {
  const token = randomBytes(32).toString("base64url");
  await insert("piper_sessions", {
    token_hash: sha256(token),
    username,
    device: device || "",
    expires_at: new Date(Date.now() + SESSION_DAYS * 86400_000).toISOString(),
  });
  return token;
}

export async function deleteSession(token) {
  if (token) await del("piper_sessions", `token_hash=${eq(sha256(token))}`);
}

export function bearer(req) {
  const auth = req.headers.get("authorization") || "";
  return auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
}

/** Retorna o usuário da sessão (ou null), junto com o aparelho da sessão. */
export async function currentUser(req) {
  const token = bearer(req);
  if (!token || token.length < 20) return null;
  const hash = sha256(token);
  const session = await one(`piper_sessions?token_hash=${eq(hash)}&expires_at=gt.${nowIso()}&select=username,device,last_seen`);
  if (!session) return null;
  // Atualiza "visto por último" no máximo a cada 10 minutos
  if (Date.now() - ms(session.last_seen) > 600_000) {
    await patch("piper_sessions", `token_hash=${eq(hash)}`, { last_seen: new Date().toISOString() }).catch(() => {});
  }
  const user = await getUser(session.username);
  return user ? { ...user, device: session.device } : null;
}

export function clientIp(req) {
  return (req.headers.get("x-forwarded-for") || "").split(",")[0].trim() || "desconhecido";
}
