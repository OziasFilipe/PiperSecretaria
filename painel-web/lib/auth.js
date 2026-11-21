import { randomBytes, scryptSync, timingSafeEqual } from "crypto";
import { getJson, redis, setJson } from "./store";

const SESSION_DAYS = 180;

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

export const normalizeUser = (u) => String(u || "").trim().toLowerCase();

/** Cria o administrador a partir de ADMIN_USER / ADMIN_PASSWORD, se ainda não existir. */
export async function ensureSeed() {
  const username = normalizeUser(process.env.ADMIN_USER);
  const password = process.env.ADMIN_PASSWORD;
  if (!username || !password) return;
  if (await redis("SISMEMBER", "users", username)) return;
  await setJson(`user:${username}`, {
    username,
    name: username.charAt(0).toUpperCase() + username.slice(1),
    role: "admin",
    passwordHash: hashPassword(password),
    createdAt: Date.now(),
    lastLogin: null,
  });
  await redis("SADD", "users", username);
}

export async function getUser(username) {
  return getJson(`user:${normalizeUser(username)}`);
}

export function publicUser(u) {
  if (!u) return null;
  const { passwordHash, ...rest } = u;
  return rest;
}

/** Limite de tentativas: 8 erros a cada 15 minutos por usuário e por IP. */
export async function tooManyAttempts(keys) {
  for (const k of keys) {
    const n = Number(await redis("GET", `rl:${k}`)) || 0;
    if (n >= 8) return true;
  }
  return false;
}

export async function registerFailure(keys) {
  for (const k of keys) {
    const n = await redis("INCR", `rl:${k}`);
    if (Number(n) === 1) await redis("EXPIRE", `rl:${k}`, 900);
  }
}

export async function createSession(username, device) {
  const token = randomBytes(32).toString("base64url");
  await setJson(`session:${token}`, { username, device: device || "", createdAt: Date.now() }, SESSION_DAYS * 86400);
  return token;
}

export async function deleteSession(token) {
  if (token) await redis("DEL", `session:${token}`);
}

export function bearer(req) {
  const auth = req.headers.get("authorization") || "";
  return auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
}

/** Retorna o usuário da sessão (ou null). */
export async function currentUser(req) {
  const token = bearer(req);
  if (!token || token.length < 20) return null;
  const session = await getJson(`session:${token}`);
  if (!session) return null;
  return getUser(session.username);
}

export function clientIp(req) {
  return (req.headers.get("x-forwarded-for") || "").split(",")[0].trim() || "desconhecido";
}
