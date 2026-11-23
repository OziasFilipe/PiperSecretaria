"use client";
import { useCallback, useEffect, useRef, useState } from "react";

const TOKEN_KEY = "piper_panel_token";
const TZ = "America/Sao_Paulo";

async function api(path, { token, method = "GET", body } = {}) {
  const res = await fetch(path, {
    method,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw Object.assign(new Error(data.error || `Erro ${res.status}`), { status: res.status });
  return data;
}

// ---------- Datas (sempre no fuso de São Paulo) ----------
const fmtTime = (ms) => new Date(ms).toLocaleTimeString("pt-BR", { timeZone: TZ, hour: "2-digit", minute: "2-digit" });
const fmtDateTime = (ms) => (ms ? new Date(ms).toLocaleString("pt-BR", { timeZone: TZ, day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" }) : "—");
const dayKey = (ms) => new Date(ms).toLocaleDateString("sv-SE", { timeZone: TZ });
const dayLabel = (ms) => {
  const k = dayKey(ms);
  if (k === dayKey(Date.now())) return "Hoje";
  if (k === dayKey(Date.now() + 86400_000)) return "Amanhã";
  return new Date(ms).toLocaleDateString("pt-BR", { timeZone: TZ, weekday: "long", day: "2-digit", month: "long" });
};
const localNow = (offsetMin = 0) =>
  new Date(Date.now() + offsetMin * 60_000).toLocaleString("sv-SE", { timeZone: TZ }).slice(0, 16).replace(" ", "T");
const ago = (ms) => {
  if (!ms) return "nunca";
  const m = Math.round((Date.now() - ms) / 60_000);
  if (m < 1) return "agora há pouco";
  if (m < 60) return `há ${m} min`;
  if (m < 1440) return `há ${Math.round(m / 60)} h`;
  return `há ${Math.round(m / 1440)} dia(s)`;
};

const TYPES = { MEETING: "Reunião online", CALL: "Ligação", TASK: "Trabalho", PERSONAL: "Pessoal" };
const REMINDERS = [[0, "Na hora"], [5, "5 min antes"], [15, "15 min antes"], [30, "30 min antes"], [60, "1 h antes"], [1440, "1 dia antes"], [-1, "Sem aviso"]];
const ACTION_LABEL = {
  create_event: "Novo compromisso", update_event: "Alterar compromisso", delete_event: "Excluir compromisso",
  create_task: "Nova tarefa", complete_task: "Concluir tarefa", add_note: "Nova anotação", remember: "Lembrar",
};
const describe = (a) => `${ACTION_LABEL[a.action] || a.action}${a.title ? `: ${a.title}` : a.text ? `: ${a.text}` : a.fact ? `: ${a.fact}` : ""}${a.start ? ` (${a.start.replace("T", " ")})` : ""}`;

function Flute({ size = 56 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 108 108" aria-hidden="true">
      <g transform="rotate(-35 54 54)">
        <rect x="12" y="44" width="84" height="20" rx="10" fill="#EDE9FE" />
        <circle cx="40" cy="51" r="4" fill="#2E1065" /><circle cx="54" cy="51" r="4" fill="#2E1065" />
        <path d="M42 57 Q47 63 52 57 Z" fill="#7C3AED" />
        <circle cx="68" cy="55" r="2.2" fill="#7C3AED" /><circle cx="76" cy="55" r="2.2" fill="#7C3AED" /><circle cx="84" cy="55" r="2.2" fill="#7C3AED" />
      </g>
    </svg>
  );
}

export default function Page() {
  const [token, setToken] = useState(null);
  const [me, setMe] = useState(null);
  const [health, setHealth] = useState(null);
  const [tab, setTab] = useState("overview");
  const [snap, setSnap] = useState(null);
  const [inbox, setInbox] = useState([]);
  const [loadErr, setLoadErr] = useState("");

  useEffect(() => {
    api("/api/health").then(setHealth).catch(() => {});
    let t = null;
    try { t = localStorage.getItem(TOKEN_KEY); } catch {}
    if (t) {
      api("/api/me", { token: t })
        .then((d) => { setToken(t); setMe(d); })
        .catch(() => { try { localStorage.removeItem(TOKEN_KEY); } catch {} });
    }
  }, []);

  const reload = useCallback(async () => {
    if (!token) return;
    try {
      const [s, i] = await Promise.all([api("/api/sync", { token }), api("/api/inbox", { token })]);
      setSnap(s); setInbox(i.items); setLoadErr("");
    } catch (e) {
      if (e.status === 401) { setMe(null); setToken(null); }
      setLoadErr(e.message);
    }
  }, [token]);

  // Atualiza sozinho a cada 30 s enquanto o painel está aberto
  useEffect(() => {
    if (!me) return;
    reload();
    const id = setInterval(reload, 30_000);
    return () => clearInterval(id);
  }, [me, reload]);

  const act = useCallback(async (action) => {
    await api("/api/actions", { token, method: "POST", body: action });
    await reload();
  }, [token, reload]);

  const logout = async () => {
    await api("/api/auth/logout", { token, method: "POST" }).catch(() => {});
    try { localStorage.removeItem(TOKEN_KEY); } catch {}
    setToken(null); setMe(null); setSnap(null);
  };

  const pending = inbox.filter((i) => !i.deliveredAt);
  const tabs = [
    ["overview", "Visão geral"], ["agenda", "Agenda"], ["tasks", "Tarefas"], ["notes", "Anotações"],
    ["chat", "Conversar"], ["memory", "Memória"],
    ...(me?.user.role === "admin" ? [["users", "Usuários"]] : []), ["status", "Conexão"],
  ];
  const props = { token, me, snap, inbox, pending, act, reload };

  return (
    <>
      <div className="header">
        <div className="wrap">
          <Flute />
          <div className="grow">
            <h1>Piper · Painel</h1>
            <div className="sub">Sua secretária pessoal {me ? `· olá, ${me.user.name}` : ""}</div>
          </div>
          {me && <button className="btn ghost" style={{ color: "#fff", borderColor: "#ffffff55" }} onClick={logout}>Sair</button>}
        </div>
      </div>
      <div className="wrap">
        {!me ? (
          <Login health={health} onLogin={(t, d) => { try { localStorage.setItem(TOKEN_KEY, t); } catch {} setToken(t); setMe(d); }} />
        ) : (
          <>
            <div className="tabs">
              {tabs.map(([k, l]) => (
                <button key={k} className={`tab ${tab === k ? "on" : ""}`} onClick={() => setTab(k)}>{l}</button>
              ))}
            </div>
            {loadErr && <p className="err">{loadErr}</p>}
            {tab === "overview" && <Overview {...props} go={setTab} />}
            {tab === "agenda" && <Agenda {...props} />}
            {tab === "tasks" && <Tasks {...props} />}
            {tab === "notes" && <Notes {...props} />}
            {tab === "chat" && <Chat {...props} />}
            {tab === "memory" && <Memory token={token} />}
            {tab === "users" && <Users token={token} me={me} />}
            {tab === "status" && <Status me={me} health={health} snap={snap} />}
          </>
        )}
      </div>
    </>
  );
}

function Login({ onLogin, health }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const submit = async (e) => {
    e.preventDefault(); setBusy(true); setError("");
    try {
      const d = await api("/api/auth/login", { method: "POST", body: { username, password, device: "painel-web" } });
      onLogin(d.token, { user: d.user, chatgpt: d.chatgpt });
    } catch (err) { setError(err.message); } finally { setBusy(false); }
  };
  return (
    <form className="card login" onSubmit={submit}>
      <h2>Entrar</h2>
      <label>Usuário</label>
      <input value={username} onChange={(e) => setUsername(e.target.value)} autoCapitalize="none" autoComplete="username" />
      <label>Senha</label>
      <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
      <div style={{ marginTop: 16 }}><button className="btn" disabled={busy || !username || !password}>{busy ? "Entrando…" : "Entrar"}</button></div>
      {error && <div className="err">{error}</div>}
      {health && !health.storage && <p className="err">O Supabase ainda não foi configurado na Vercel (SUPABASE_URL e SUPABASE_SERVICE_ROLE_KEY).</p>}
      {health && health.storage && !health.database && <p className="err">O Supabase respondeu, mas as tabelas não existem. Rode o arquivo supabase/schema.sql no SQL Editor.</p>}
    </form>
  );
}

/** Aviso de itens que ainda não chegaram ao celular. */
function PendingNote({ pending, filter }) {
  const list = filter ? pending.filter((p) => filter.includes(p.action.action)) : pending;
  if (!list.length) return null;
  return (
    <div className="card" style={{ borderColor: "#f59e0b" }}>
      <b>Aguardando o celular ({list.length})</b>
      <p className="muted" style={{ marginTop: 4 }}>Aplicado quando o app abrir (ou em até 1 minuto, se ele já estiver aberto).</p>
      {list.map((p) => <div key={p.id} className="muted">• {describe(p.action)}</div>)}
    </div>
  );
}

function Overview({ snap, pending, go }) {
  if (!snap) return <div className="card">Carregando…</div>;
  const now = Date.now();
  const today = dayKey(now);
  const todayEvents = snap.events.filter((e) => dayKey(e.startAt) === today);
  const nextEvents = snap.events.filter((e) => e.startAt + e.durationMinutes * 60_000 >= now).slice(0, 6);
  const open = snap.tasks.filter((t) => !t.done);
  const late = open.filter((t) => t.remindAt && t.remindAt < now);
  const doneWeek = snap.tasks.filter((t) => t.done && t.completedAt && t.completedAt > now - 7 * 86400_000);
  const lastSync = snap.devices[0]?.lastSync;
  return (
    <>
      <div className="stats">
        <div className="stat"><b>{todayEvents.length}</b><span>compromissos hoje</span></div>
        <div className="stat"><b>{open.length}</b><span>tarefas pendentes</span></div>
        <div className="stat"><b style={{ color: late.length ? "var(--danger)" : undefined }}>{late.length}</b><span>tarefas atrasadas</span></div>
        <div className="stat"><b>{doneWeek.length}</b><span>concluídas em 7 dias</span></div>
      </div>
      {!snap.devices.length && (
        <div className="card"><b>O celular ainda não sincronizou.</b><p className="muted">Entre com sua conta no app Piper (versão 1.3 ou mais nova). A agenda e as tarefas aparecem aqui sozinhas.</p></div>
      )}
      {snap.devices.length > 0 && <p className="muted">Última sincronização do celular: {ago(lastSync)} ({fmtDateTime(lastSync)}).</p>}
      <PendingNote pending={pending} />
      <div className="card">
        <div className="row"><h2 className="grow">Próximos compromissos</h2><button className="btn ghost" onClick={() => go("agenda")}>Ver agenda</button></div>
        {nextEvents.length === 0 && <p className="muted">Nada agendado.</p>}
        {nextEvents.map((e) => (
          <div key={e.id} className="item">
            <div className="when">{dayLabel(e.startAt)}<br />{fmtTime(e.startAt)}</div>
            <div className="grow"><b>{e.title}</b><div className="muted">{TYPES[e.type] || e.type} · {e.durationMinutes} min{e.source ? ` · ${e.source}` : ""}</div></div>
          </div>
        ))}
      </div>
      <div className="card">
        <div className="row"><h2 className="grow">Tarefas pendentes</h2><button className="btn ghost" onClick={() => go("tasks")}>Ver tarefas</button></div>
        {open.length === 0 && <p className="muted">Tudo em dia 🎉</p>}
        {open.slice(0, 8).map((t) => (
          <div key={t.id} className="item">
            <div className="when">{t.remindAt ? fmtDateTime(t.remindAt) : "sem data"}</div>
            <div className="grow">{t.starred ? "★ " : ""}{t.title}{t.remindAt && t.remindAt < now && <span className="pill wait" style={{ marginLeft: 8 }}>atrasada</span>}</div>
          </div>
        ))}
      </div>
    </>
  );
}

function Agenda({ snap, pending, act }) {
  const blank = { title: "", start: localNow(60).slice(0, 14) + "00", duration_minutes: 60, type: "MEETING", reminder_minutes: 30, description: "" };
  const [form, setForm] = useState(blank);
  const [editing, setEditing] = useState(null);
  const [showPast, setShowPast] = useState(false);
  const [msg, setMsg] = useState("");
  if (!snap) return <div className="card">Carregando…</div>;

  const now = Date.now();
  const events = snap.events.filter((e) => showPast || e.startAt + e.durationMinutes * 60_000 >= now - 3600_000);
  const groups = [];
  for (const e of events) {
    const k = dayKey(e.startAt);
    if (!groups.length || groups[groups.length - 1].k !== k) groups.push({ k, label: dayLabel(e.startAt), items: [] });
    groups[groups.length - 1].items.push(e);
  }

  const startEdit = (e) => {
    setEditing(e.id);
    setForm({
      title: e.title, start: new Date(e.startAt).toLocaleString("sv-SE", { timeZone: TZ }).slice(0, 16).replace(" ", "T"),
      duration_minutes: e.durationMinutes, type: e.type, reminder_minutes: e.reminderMinutes, description: e.description || "",
    });
    window.scrollTo({ top: document.body.scrollHeight, behavior: "smooth" });
  };

  const submit = async (ev) => {
    ev.preventDefault(); setMsg("");
    try {
      const payload = { ...form, duration_minutes: Number(form.duration_minutes), reminder_minutes: Number(form.reminder_minutes) };
      if (editing) await act({ action: "update_event", event_id: editing, ...payload });
      else await act({ action: "create_event", ...payload });
      setMsg(editing ? "Alteração enviada ao celular ✓" : "Compromisso enviado ao celular ✓");
      setForm(blank); setEditing(null);
    } catch (e) { setMsg(e.message); }
  };

  const remove = async (e) => {
    if (!confirm(`Excluir "${e.title}"?`)) return;
    try { await act({ action: "delete_event", event_id: e.id, title: e.title }); setMsg("Exclusão enviada ao celular ✓"); } catch (err) { setMsg(err.message); }
  };

  return (
    <>
      <PendingNote pending={pending} filter={["create_event", "update_event", "delete_event"]} />
      <div className="card">
        <div className="row">
          <h2 className="grow">Agenda</h2>
          <label className="row" style={{ margin: 0, gap: 6 }}><input type="checkbox" style={{ width: "auto" }} checked={showPast} onChange={(e) => setShowPast(e.target.checked)} /> mostrar passados (30 dias)</label>
        </div>
        {groups.length === 0 && <p className="muted">Nenhum compromisso.</p>}
        {groups.map((g) => (
          <div key={g.k}>
            <div className="day">{g.label}</div>
            {g.items.map((e) => (
              <div key={e.id} className="item">
                <div className="when">{fmtTime(e.startAt)}<br />{e.durationMinutes} min</div>
                <div className="grow">
                  <b>{e.title}</b>
                  <div className="muted">{TYPES[e.type] || e.type}{e.source ? ` · ${e.source}` : ""}{e.description ? ` · ${e.description}` : ""}</div>
                </div>
                {!e.source && (
                  <div className="row" style={{ gap: 6 }}>
                    <button className="btn ghost" style={{ padding: "6px 12px" }} onClick={() => startEdit(e)}>Editar</button>
                    <button className="btn danger" onClick={() => remove(e)}>Excluir</button>
                  </div>
                )}
              </div>
            ))}
          </div>
        ))}
        <p className="muted" style={{ marginTop: 12 }}>Compromissos do Outlook/Google aparecem aqui, mas são editados no app de origem.</p>
      </div>
      <form className="card" onSubmit={submit}>
        <h2>{editing ? "Editar compromisso" : "Novo compromisso"}</h2>
        <label>Título</label>
        <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="Ex.: Reunião com o time" />
        <div className="grid2">
          <div><label>Data e hora</label><input type="datetime-local" value={form.start} onChange={(e) => setForm({ ...form, start: e.target.value })} /></div>
          <div><label>Duração (min)</label><input type="number" min="5" step="5" value={form.duration_minutes} onChange={(e) => setForm({ ...form, duration_minutes: e.target.value })} /></div>
          <div><label>Tipo</label>
            <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>{Object.entries(TYPES).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
          </div>
          <div><label>Aviso</label>
            <select value={form.reminder_minutes} onChange={(e) => setForm({ ...form, reminder_minutes: e.target.value })}>{REMINDERS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select>
          </div>
        </div>
        <label>Pauta / descrição</label>
        <textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        <div className="row" style={{ marginTop: 14 }}>
          <button className="btn" disabled={!form.title.trim() || !form.start}>{editing ? "Salvar alteração" : "Agendar"}</button>
          {editing && <button type="button" className="btn ghost" onClick={() => { setEditing(null); setForm(blank); }}>Cancelar</button>}
        </div>
        {msg && <p className={msg.includes("✓") ? "ok" : "err"}>{msg}</p>}
      </form>
    </>
  );
}

function Tasks({ snap, pending, act }) {
  const [filter, setFilter] = useState("open");
  const [form, setForm] = useState({ title: "", remind_at: "", notes: "", starred: false });
  const [msg, setMsg] = useState("");
  if (!snap) return <div className="card">Carregando…</div>;

  const now = Date.now();
  const today = dayKey(now);
  const completing = new Set(pending.filter((p) => p.action.action === "complete_task").map((p) => p.action.task_id));
  const list = snap.tasks.filter((t) =>
    filter === "open" ? !t.done : filter === "today" ? !t.done && t.remindAt && dayKey(t.remindAt) <= today : t.done,
  );

  const submit = async (e) => {
    e.preventDefault(); setMsg("");
    try {
      const a = { action: "create_task", title: form.title.trim(), notes: form.notes, starred: form.starred };
      if (form.remind_at) a.remind_at = form.remind_at;
      await act(a);
      setForm({ title: "", remind_at: "", notes: "", starred: false });
      setMsg("Tarefa enviada ao celular ✓");
    } catch (err) { setMsg(err.message); }
  };

  return (
    <>
      <PendingNote pending={pending} filter={["create_task", "complete_task"]} />
      <div className="card">
        <div className="row" style={{ marginBottom: 8 }}>
          <h2 className="grow" style={{ margin: 0 }}>Tarefas</h2>
          {[["today", "Hoje"], ["open", "Pendentes"], ["done", "Concluídas"]].map(([k, l]) => (
            <button key={k} className={`tab ${filter === k ? "on" : ""}`} onClick={() => setFilter(k)}>{l}</button>
          ))}
        </div>
        {list.length === 0 && <p className="muted">Nada aqui.</p>}
        {list.map((t) => (
          <div key={t.id} className={`item ${t.done ? "done" : ""}`}>
            <button
              className={`check ${t.done || completing.has(t.id) ? "on" : ""}`}
              title={t.done ? "Concluída" : "Concluir"}
              disabled={t.done || completing.has(t.id)}
              onClick={() => act({ action: "complete_task", task_id: t.id, title: t.title }).catch((e) => setMsg(e.message))}
            >{t.done || completing.has(t.id) ? "✓" : ""}</button>
            <div className="grow">
              {t.starred && <span className="star on">★</span>}{t.title}
              {completing.has(t.id) && <span className="pill wait" style={{ marginLeft: 8 }}>aguardando celular</span>}
              <div className="muted">
                {t.done ? `Concluída ${fmtDateTime(t.completedAt)}` : t.remindAt ? `Lembrete ${fmtDateTime(t.remindAt)}${t.remindAt < now ? " · atrasada" : ""}` : "Sem lembrete"}
                {t.notes ? ` · ${t.notes}` : ""}
              </div>
            </div>
          </div>
        ))}
      </div>
      <form className="card" onSubmit={submit}>
        <h2>Nova tarefa</h2>
        <label>Título</label>
        <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="Ex.: Ligar para o João" />
        <div className="grid2">
          <div><label>Lembrete (opcional)</label><input type="datetime-local" value={form.remind_at} onChange={(e) => setForm({ ...form, remind_at: e.target.value })} /></div>
          <div><label>&nbsp;</label><label className="row" style={{ margin: 0, gap: 6, color: "var(--text)" }}><input type="checkbox" style={{ width: "auto" }} checked={form.starred} onChange={(e) => setForm({ ...form, starred: e.target.checked })} /> Importante ★</label></div>
        </div>
        <label>Observações</label>
        <textarea value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
        <div style={{ marginTop: 14 }}><button className="btn" disabled={!form.title.trim()}>Criar tarefa</button></div>
        {msg && <p className={msg.includes("✓") ? "ok" : "err"}>{msg}</p>}
      </form>
    </>
  );
}

function Notes({ snap, pending, act }) {
  const [text, setText] = useState("");
  const [msg, setMsg] = useState("");
  if (!snap) return <div className="card">Carregando…</div>;
  const submit = async (e) => {
    e.preventDefault(); setMsg("");
    try { await act({ action: "add_note", text: text.trim() }); setText(""); setMsg("Anotação enviada ao celular ✓"); } catch (err) { setMsg(err.message); }
  };
  return (
    <>
      <PendingNote pending={pending} filter={["add_note"]} />
      <form className="card" onSubmit={submit}>
        <h2>Nova anotação</h2>
        <textarea value={text} onChange={(e) => setText(e.target.value)} placeholder="Uma ideia rápida…" />
        <div style={{ marginTop: 12 }}><button className="btn" disabled={!text.trim()}>Anotar</button></div>
        {msg && <p className={msg.includes("✓") ? "ok" : "err"}>{msg}</p>}
      </form>
      <div className="card">
        <h2>Anotações ({snap.notes.length})</h2>
        {snap.notes.length === 0 && <p className="muted">Nenhuma anotação.</p>}
        {snap.notes.map((n) => (
          <div key={n.id} className="item"><div className="when">{fmtDateTime(n.createdAt)}</div><div className="grow" style={{ whiteSpace: "pre-wrap" }}>{n.text}</div></div>
        ))}
      </div>
    </>
  );
}

function Chat({ token, me, reload }) {
  const [messages, setMessages] = useState([{ role: "assistant", content: `Oi, ${me.user.name}! Eu vejo sua agenda e suas tarefas. Posso agendar, criar tarefas e responder dúvidas sobre o app.` }]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const box = useRef(null);
  useEffect(() => { box.current?.scrollTo(0, box.current.scrollHeight); }, [messages]);

  const send = async (e) => {
    e.preventDefault();
    const text = input.trim(); if (!text) return;
    const next = [...messages, { role: "user", content: text }];
    setMessages(next); setInput(""); setBusy(true);
    try {
      const d = await api("/api/chat", {
        token, method: "POST",
        body: { source: "panel", messages: next, context: { now: localNow(), timezone: TZ } },
      });
      const extra = d.queued ? "\n\n(Enviei para o seu celular. Ele aplica assim que o app abrir.)" : "";
      setMessages([...next, { role: "assistant", content: d.reply + extra }]);
      if (d.queued) reload();
    } catch (err) {
      setMessages([...next, { role: "assistant", content: `⚠️ ${err.message}` }]);
    } finally { setBusy(false); }
  };

  return (
    <div className="card">
      {!me.chatgpt && <p className="err">ChatGPT ainda não conectado: configure OPENAI_API_KEY na Vercel.</p>}
      <div className="chat" ref={box}>
        {messages.map((m, i) => <div key={i} className={`bubble ${m.role === "user" ? "me" : "her"}`}>{m.content}</div>)}
        {busy && <div className="bubble her">…</div>}
      </div>
      <form className="row" style={{ marginTop: 12 }} onSubmit={send}>
        <input className="grow" value={input} onChange={(e) => setInput(e.target.value)} placeholder="Ex.: agende reunião com o time sexta às 10h" />
        <button className="btn" disabled={busy}>Enviar</button>
      </form>
    </div>
  );
}

function Memory({ token }) {
  const [items, setItems] = useState(null);
  const [err, setErr] = useState("");
  useEffect(() => { api("/api/memory", { token }).then((d) => setItems(d.memories)).catch(() => setItems([])); }, [token]);
  const save = async (list) => {
    try { await api("/api/memory", { token, method: "PUT", body: { memories: list } }); setItems(list); setErr(""); } catch (e) { setErr(e.message); }
  };
  if (!items) return <div className="card">Carregando…</div>;
  return (
    <div className="card">
      <h2>O que a Piper aprendeu</h2>
      <p className="muted">Usado para personalizar as conversas, inclusive sem internet no celular. Nunca guardamos senhas ou chaves.</p>
      {items.length === 0 && <p>Nada ainda. Converse com ela e conte suas preferências.</p>}
      <div className="scroll"><table><tbody>
        {items.map((m, i) => (
          <tr key={i}>
            <td><span className="pill">{m.kind === "REPLY" ? "Resposta" : m.kind === "HABIT" ? "Hábito" : "Fato"}</span></td>
            <td className="grow">{m.cue ? <><b>{m.cue}</b> → </> : null}{m.text}</td>
            <td><button className="btn danger" onClick={() => save(items.filter((_, j) => j !== i))}>Apagar</button></td>
          </tr>
        ))}
      </tbody></table></div>
      {items.length > 0 && <div style={{ marginTop: 12 }}><button className="btn danger" onClick={() => confirm("Apagar toda a memória?") && save([])}>Apagar tudo</button></div>}
      {err && <p className="err">{err}</p>}
    </div>
  );
}

function Users({ token, me }) {
  const [users, setUsers] = useState([]);
  const [form, setForm] = useState({ username: "", name: "", password: "", role: "user" });
  const [msg, setMsg] = useState("");
  const load = useCallback(() => api("/api/users", { token }).then((d) => setUsers(d.users)).catch((e) => setMsg(e.message)), [token]);
  useEffect(() => { load(); }, [load]);
  const create = async (e) => {
    e.preventDefault(); setMsg("");
    try { await api("/api/users", { token, method: "POST", body: form }); setForm({ username: "", name: "", password: "", role: "user" }); setMsg("Usuário salvo ✓"); load(); }
    catch (err) { setMsg(err.message); }
  };
  const remove = async (u) => {
    if (!confirm(`Remover ${u}? A agenda, tarefas e memória dele no painel também serão apagadas.`)) return;
    try { await api(`/api/users?username=${encodeURIComponent(u)}`, { token, method: "DELETE" }); load(); } catch (err) { setMsg(err.message); }
  };
  return (
    <>
      <div className="card">
        <h2>Usuários ({users.length})</h2>
        <div className="scroll"><table>
          <thead><tr><th>Usuário</th><th>Nome</th><th>Perfil</th><th>Criado</th><th>Último acesso</th><th></th></tr></thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.username}>
                <td>{u.username}</td><td>{u.name}</td><td><span className="pill">{u.role}</span></td>
                <td>{fmtDateTime(u.createdAt)}</td><td>{fmtDateTime(u.lastLogin)}</td>
                <td>{u.username !== me.user.username && <button className="btn danger" onClick={() => remove(u.username)}>Remover</button>}</td>
              </tr>
            ))}
          </tbody>
        </table></div>
      </div>
      <form className="card" onSubmit={create}>
        <h2>Novo usuário / alterar</h2>
        <div className="grid2">
          <div><label>Usuário</label><input value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} autoCapitalize="none" /></div>
          <div><label>Nome</label><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></div>
          <div><label>Senha (deixe vazio para manter, se já existe)</label><input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} autoComplete="new-password" /></div>
          <div><label>Perfil</label>
            <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}><option value="user">Usuário</option><option value="admin">Administrador</option></select>
          </div>
        </div>
        <div style={{ marginTop: 14 }}><button className="btn">Salvar</button></div>
        {msg && <p className={msg.includes("✓") ? "ok" : "err"}>{msg}</p>}
      </form>
    </>
  );
}

function Status({ me, health, snap }) {
  const origin = typeof window !== "undefined" ? window.location.origin : "";
  return (
    <div className="card">
      <h2>Conexão</h2>
      <p>ChatGPT: {me.chatgpt ? <span className="ok">conectado ✓ ({me.model})</span> : <span className="err">não conectado</span>}</p>
      <p>Banco de dados (Supabase): {health?.database ? <span className="ok">ok ✓</span> : <span className="err">{health?.storage ? "tabelas não encontradas (rode o schema.sql)" : "não configurado"}</span>}</p>
      <h2 style={{ marginTop: 20 }}>Aparelhos</h2>
      {!snap?.devices.length && <p className="muted">Nenhum celular sincronizou ainda.</p>}
      {snap?.devices.map((d) => <p key={d.device}>{d.device} {d.appVersion ? `· app ${d.appVersion}` : ""} · {ago(d.lastSync)}</p>)}
      <h2 style={{ marginTop: 20 }}>Conectar o app</h2>
      <p>No celular, abra a Piper › tela de login e use este endereço do painel:</p>
      <p><code>{origin}</code></p>
      <p className="muted">Depois de entrar uma vez, o app guarda a sessão. Ele envia a agenda e as tarefas para cá sempre que algo muda e busca o que você combinou aqui ao abrir e a cada minuto.</p>
    </div>
  );
}
