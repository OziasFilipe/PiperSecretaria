import { storeConfigured } from "../../../../lib/store";
import {
  clientIp, createSession, ensureSeed, getUser, normalizeUser, publicUser,
  registerFailure, tooManyAttempts, verifyPassword,
} from "../../../../lib/auth";
import { setJson } from "../../../../lib/store";
import { json } from "../../../../lib/http";

export const dynamic = "force-dynamic";

export async function POST(req) {
  if (!storeConfigured()) return json({ error: "Banco de dados não configurado na Vercel (Upstash Redis)." }, 503);
  await ensureSeed();
  const body = await req.json().catch(() => ({}));
  const username = normalizeUser(body.username);
  const password = String(body.password || "");
  const keys = [`u:${username}`, `ip:${clientIp(req)}`];

  if (await tooManyAttempts(keys)) {
    return json({ error: "Muitas tentativas. Espere 15 minutos e tente de novo." }, 429);
  }
  const user = username ? await getUser(username) : null;
  if (!user || !verifyPassword(password, user.passwordHash)) {
    await registerFailure(keys);
    return json({ error: "Usuário ou senha incorretos." }, 401);
  }
  const token = await createSession(user.username, String(body.device || "").slice(0, 80));
  await setJson(`user:${user.username}`, { ...user, lastLogin: Date.now() });
  return json({ token, user: publicUser({ ...user, lastLogin: Date.now() }), chatgpt: Boolean(process.env.OPENAI_API_KEY) });
}
