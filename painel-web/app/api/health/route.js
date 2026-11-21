import { storeConfigured } from "../../../lib/store";

export const dynamic = "force-dynamic";

export async function GET() {
  return Response.json({
    ok: true,
    app: "piper-painel",
    version: "1.2",
    chatgpt: Boolean(process.env.OPENAI_API_KEY),
    storage: storeConfigured(),
  });
}
