// Dados compartilhados entre o app Android e o painel: memória, caixa de entrada e espelho do celular.
import { eq, insert, ms, rpc, sb } from "./store";

export const SECRET = /(senha|password|token|api[ _-]?key|chave (de )?api|sk-[a-z0-9]|cart[aã]o de cr[eé]dito|\bcvv\b|\bcpf\b)/i;

// ---------- Memória ----------
export async function getMemories(username) {
  const rows = (await sb(`piper_memories?username=${eq(username)}&order=created_at.desc&limit=300`)) || [];
  return rows.map((r) => ({ text: r.text, kind: r.kind, cue: r.cue, createdAt: ms(r.created_at) }));
}

export function cleanMemories(list) {
  return (Array.isArray(list) ? list : [])
    .filter((m) => m && typeof m.text === "string" && m.text.trim() && !SECRET.test(m.text))
    .slice(0, 300)
    .map((m) => ({
      text: m.text.slice(0, 500),
      kind: ["FACT", "REPLY", "HABIT"].includes(m.kind) ? m.kind : "FACT",
      cue: typeof m.cue === "string" && m.cue !== "null" ? m.cue.slice(0, 200) : null,
      createdAt: Number(m.createdAt) || Date.now(),
    }));
}

export const replaceMemories = (username, memories) => rpc("piper_replace_memories", { p_username: username, p_memories: memories });

// ---------- Caixa de entrada (painel -> celular) ----------
export const ACTIONS = ["create_event", "update_event", "delete_event", "create_task", "complete_task", "add_note", "remember"];

export async function queueActions(username, actions, source = "panel") {
  const rows = actions
    .filter((a) => a && ACTIONS.includes(a.action))
    .filter((a) => !(a.action === "remember" && SECRET.test(a.fact || "")))
    .map((a) => ({ username, action: a, source }));
  if (rows.length) await insert("piper_inbox", rows);
  return rows.length;
}

/** Chamado pelo celular: devolve as ações pendentes e marca como entregues. */
export async function pullInbox(username, device) {
  const rows = (await rpc("piper_pull_inbox", { p_username: username, p_device: device || "celular" })) || [];
  return rows.sort((a, b) => a.id - b.id).map((r) => r.action);
}

/** Para o painel: pendentes + últimas entregues. */
export async function listInbox(username) {
  const rows = (await sb(`piper_inbox?username=${eq(username)}&order=id.desc&limit=50`)) || [];
  return rows.map((r) => ({ id: r.id, action: r.action, source: r.source, createdAt: ms(r.created_at), deliveredAt: ms(r.delivered_at), device: r.device }));
}

export const cancelInboxItem = (username, id) =>
  sb(`piper_inbox?username=${eq(username)}&id=${eq(Number(id))}&delivered_at=is.null`, { method: "DELETE", prefer: "return=minimal" });

// ---------- Espelho do celular ----------
const num = (v) => (v === null || v === undefined || v === "" ? null : Number(v));

export async function replaceSnapshot(username, body) {
  const events = (Array.isArray(body.events) ? body.events : []).slice(0, 2000).map((e) => ({
    id: num(e.id), title: String(e.title || ""), description: String(e.description || ""), startAt: num(e.startAt),
    durationMinutes: num(e.durationMinutes) ?? 60, type: String(e.type || "MEETING"), reminderMinutes: num(e.reminderMinutes) ?? 30,
    source: e.source ? String(e.source) : null,
  }));
  const tasks = (Array.isArray(body.tasks) ? body.tasks : []).slice(0, 2000).map((t) => ({
    id: num(t.id), title: String(t.title || ""), notes: String(t.notes || ""), remindAt: num(t.remindAt),
    starred: Boolean(t.starred), done: Boolean(t.done), createdAt: num(t.createdAt), completedAt: num(t.completedAt),
  }));
  const notes = (Array.isArray(body.notes) ? body.notes : []).slice(0, 2000).map((n) => ({
    id: num(n.id), text: String(n.text || ""), createdAt: num(n.createdAt),
  }));
  await rpc("piper_replace_snapshot", {
    p_username: username,
    p_device: String(body.device || "celular").slice(0, 80),
    p_app_version: String(body.appVersion || "").slice(0, 20),
    p_events: events, p_tasks: tasks, p_notes: notes,
  });
  return { events: events.length, tasks: tasks.length, notes: notes.length };
}

export async function getSnapshot(username) {
  const u = eq(username);
  const since = encodeURIComponent(new Date(Date.now() - 30 * 86400_000).toISOString());
  const [events, tasks, notes, devices] = await Promise.all([
    sb(`piper_events?username=${u}&start_at=gte.${since}&order=start_at.asc&limit=1000`),
    sb(`piper_tasks?username=${u}&order=done.asc,remind_at.asc.nullslast,created_at.desc&limit=1000`),
    sb(`piper_notes?username=${u}&order=created_at.desc&limit=500`),
    sb(`piper_devices?username=${u}&order=last_sync.desc`),
  ]);
  return {
    events: (events || []).map((e) => ({
      id: e.local_id, title: e.title, description: e.description, startAt: ms(e.start_at), durationMinutes: e.duration_minutes,
      type: e.type, reminderMinutes: e.reminder_minutes, source: e.source,
    })),
    tasks: (tasks || []).map((t) => ({
      id: t.local_id, title: t.title, notes: t.notes, remindAt: ms(t.remind_at), starred: t.starred, done: t.done,
      createdAt: ms(t.created_at), completedAt: ms(t.completed_at),
    })),
    notes: (notes || []).map((n) => ({ id: n.local_id, text: n.text, createdAt: ms(n.created_at) })),
    devices: (devices || []).map((d) => ({ device: d.device, appVersion: d.app_version, lastSync: ms(d.last_sync) })),
  };
}
