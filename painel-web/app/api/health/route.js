import { sb, storeConfigured } from "../../../lib/store";

export const dynamic = "force-dynamic";

export async function GET() {
  let database = false;
  if (storeConfigured()) database = await sb("piper_users?select=username&limit=1").then(() => true).catch(() => false);
  return Response.json({
    ok: true,
    app: "piper-painel",
    version: "1.3",
    chatgpt: Boolean(process.env.OPENAI_API_KEY),
    storage: storeConfigured(),
    database, // true quando o Supabase respondeu e as tabelas existem
  });
}
