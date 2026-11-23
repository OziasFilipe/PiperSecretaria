import { bearer, deleteSession } from "../../../../lib/auth";
import { storeConfigured } from "../../../../lib/store";

export const dynamic = "force-dynamic";

export async function POST(req) {
  if (storeConfigured()) await deleteSession(bearer(req)).catch(() => {});
  return Response.json({ ok: true });
}
