"use client";
import { useEffect, useRef, useState } from "react";

const TOKEN_KEY = "piper_panel_token";

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
  const [tab, setTab] = useState("chat");

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

  const logout = async () => {
    await api("/api/auth/logout", { token, method: "POST" }).catch(() => {});
    try { localStorage.removeItem(TOKEN_KEY); } catch {}
    setToken(null); setMe(null);
  };

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
              {[["chat", "Conversar"], ["memory", "Memória da Piper"], ...(me.user.role === "admin" ? [["users", "Usuários"]] : []), ["status", "Conexão"]].map(([k, l]) => (
                <button key={k} className={`tab ${tab === k ? "on" : ""}`} onClick={() => setTab(k)}>{l}</button>
              ))}
            </div>
            {tab === "chat" && <Chat token={token} me={me} />}
            {tab === "memory" && <Memory token={token} />}
            {tab === "users" && <Users token={token} me={me} />}
            {tab === "status" && <Status me={me} health={health} />}
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
      {health && !health.storage && <p className="err">O banco de dados ainda não foi configurado na Vercel (Upstash Redis).</p>}
    </form>
  );
}

function Chat({ token, me }) {
  const [messages, setMessages] = useState([{ role: "assistant", content: `Oi, ${me.user.name}! Posso agendar, criar tarefas e responder dúvidas sobre o app.` }]);
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
      const mem = await api("/api/memory", { token }).catch(() => ({ memories: [] }));
      const now = new Date();
      const d = await api("/api/chat", {
        token, method: "POST",
        body: {
          source: "panel",
          messages: next,
          context: {
            now: now.toLocaleString("sv-SE", { timeZone: "America/Sao_Paulo" }).slice(0, 16).replace(" ", "T"),
            timezone: "America/Sao_Paulo",
            memories: mem.memories.map((m) => m.text),
            events: [], tasks: [],
            note: "Conversa pelo painel web: a agenda completa está no celular.",
          },
        },
      });
      const extra = d.queued ? "\n\n(Vou aplicar isso no seu celular na próxima vez que você abrir o app.)" : "";
      setMessages([...next, { role: "assistant", content: d.reply + extra }]);
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
      <p className="muted">A agenda completa fica no celular. O que você combinar aqui é aplicado no app quando ele abrir.</p>
    </div>
  );
}

function Memory({ token }) {
  const [items, setItems] = useState(null);
  const load = () => api("/api/memory", { token }).then((d) => setItems(d.memories)).catch(() => setItems([]));
  useEffect(() => { load(); }, []);
  const save = async (list) => { await api("/api/memory", { token, method: "PUT", body: { memories: list } }); setItems(list); };
  if (!items) return <div className="card">Carregando…</div>;
  return (
    <div className="card">
      <h2>O que a Piper aprendeu</h2>
      <p className="muted">Usado para personalizar as conversas, inclusive sem internet no celular. Nunca guardamos senhas ou chaves.</p>
      {items.length === 0 && <p>Nada ainda. Converse com ela e conte suas preferências.</p>}
      <table><tbody>
        {items.map((m, i) => (
          <tr key={i}>
            <td><span className="pill">{m.kind === "REPLY" ? "Resposta" : m.kind === "HABIT" ? "Hábito" : "Fato"}</span></td>
            <td className="grow">{m.cue ? <><b>{m.cue}</b> → </> : null}{m.text}</td>
            <td><button className="btn danger" onClick={() => save(items.filter((_, j) => j !== i))}>Apagar</button></td>
          </tr>
        ))}
      </tbody></table>
      {items.length > 0 && <div style={{ marginTop: 12 }}><button className="btn danger" onClick={() => save([])}>Apagar tudo</button></div>}
    </div>
  );
}

function Users({ token, me }) {
  const [users, setUsers] = useState([]);
  const [form, setForm] = useState({ username: "", name: "", password: "", role: "user" });
  const [msg, setMsg] = useState("");
  const load = () => api("/api/users", { token }).then((d) => setUsers(d.users)).catch((e) => setMsg(e.message));
  useEffect(() => { load(); }, []);
  const create = async (e) => {
    e.preventDefault(); setMsg("");
    try { await api("/api/users", { token, method: "POST", body: form }); setForm({ username: "", name: "", password: "", role: "user" }); setMsg("Usuário salvo ✓"); load(); }
    catch (err) { setMsg(err.message); }
  };
  const remove = async (u) => {
    if (!confirm(`Remover ${u}?`)) return;
    try { await api(`/api/users?username=${encodeURIComponent(u)}`, { token, method: "DELETE" }); load(); } catch (err) { setMsg(err.message); }
  };
  const fmt = (t) => (t ? new Date(t).toLocaleString("pt-BR") : "—");
  return (
    <>
      <div className="card">
        <h2>Usuários ({users.length})</h2>
        <table>
          <thead><tr><th>Usuário</th><th>Nome</th><th>Perfil</th><th>Criado</th><th>Último acesso</th><th></th></tr></thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.username}>
                <td>{u.username}</td><td>{u.name}</td><td><span className="pill">{u.role}</span></td>
                <td>{fmt(u.createdAt)}</td><td>{fmt(u.lastLogin)}</td>
                <td>{u.username !== me.user.username && <button className="btn danger" onClick={() => remove(u.username)}>Remover</button>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <form className="card" onSubmit={create}>
        <h2>Novo usuário / trocar senha</h2>
        <div className="row">
          <div className="grow"><label>Usuário</label><input value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} autoCapitalize="none" /></div>
          <div className="grow"><label>Nome</label><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></div>
        </div>
        <div className="row">
          <div className="grow"><label>Senha</label><input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} autoComplete="new-password" /></div>
          <div className="grow"><label>Perfil</label>
            <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}><option value="user">Usuário</option><option value="admin">Administrador</option></select>
          </div>
        </div>
        <div style={{ marginTop: 14 }}><button className="btn">Salvar</button></div>
        {msg && <p className={msg.includes("✓") ? "ok" : "err"}>{msg}</p>}
      </form>
    </>
  );
}

function Status({ me, health }) {
  const origin = typeof window !== "undefined" ? window.location.origin : "";
  return (
    <div className="card">
      <h2>Conexão</h2>
      <p>ChatGPT: {me.chatgpt ? <span className="ok">conectado ✓ ({me.model})</span> : <span className="err">não conectado</span>}</p>
      <p>Banco de dados: {health?.storage ? <span className="ok">ok ✓</span> : <span className="err">não configurado</span>}</p>
      <h2 style={{ marginTop: 20 }}>Conectar o app</h2>
      <p>No celular, abra a Piper › tela de login e use este endereço do painel:</p>
      <p><code>{origin}</code></p>
      <p className="muted">Depois de entrar uma vez, o app guarda a sessão e não pede de novo.</p>
    </div>
  );
}
