import { ACTIONS, cancelInboxItem, queueActions } from "../../../lib/data";
import { json, requireUser, safe } from "../../../lib/http";

export const dynamic = "force-dynamic";

const DT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/;

/**
 * Painel: cria/edita/conclui/exclui itens. Vai para a caixa de entrada e o celular aplica
 * na próxima sincronização (ao abrir o app e a cada minuto com ele aberto).
 */
export const POST = safe(async (req) => {
  const { user, error } = await requireUser(req);
  if (error) return error;
  const a = await req.json().catch(() => ({}));
  if (!ACTIONS.includes(a.action)) return json({ error: "Ação inválida." }, 400);
  if (["create_event", "create_task", "add_note"].includes(a.action) && !String(a.title || a.text || "").trim()) {
    return json({ error: "Preencha o título." }, 400);
  }
  if (a.action === "create_event" && !DT.test(a.start || "")) return json({ error: "Informe data e hora." }, 400);
  if (a.remind_at && !DT.test(a.remind_at)) return json({ error: "Lembrete inválido." }, 400);
  const n = await queueActions(user.username, [a], "panel");
  if (!n) return json({ error: "Ação recusada." }, 400);
  return json({ ok: true });
});

/** Cancela uma ação que ainda não chegou ao celular. */
export const DELETE = safe(async (req) => {
  const { user, error } = await requireUser(req);
  if (error) return error;
  const id = new URL(req.url).searchParams.get("id");
  if (!id) return json({ error: "Informe o id." }, 400);
  await cancelInboxItem(user.username, id);
  return json({ ok: true });
});
