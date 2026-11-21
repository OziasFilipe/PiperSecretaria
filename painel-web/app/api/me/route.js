import { publicUser } from "../../../lib/auth";
import { json, requireUser } from "../../../lib/http";

export const dynamic = "force-dynamic";

export async function GET(req) {
  const { user, error } = await requireUser(req);
  if (error) return error;
  return json({ user: publicUser(user), chatgpt: Boolean(process.env.OPENAI_API_KEY), model: process.env.OPENAI_MODEL || "gpt-4o-mini" });
}
