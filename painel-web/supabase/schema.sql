-- Piper · Painel — banco de dados no Supabase
-- Como usar: Supabase › SQL Editor › New query › cole este arquivo inteiro › Run.
-- Pode rodar de novo sem problema (só cria o que ainda não existe).
--
-- Segurança: todas as tabelas ficam com RLS ligado e SEM políticas públicas.
-- Só o servidor do painel (Vercel), usando a chave service_role, lê e grava.
-- O app Android nunca fala direto com o Supabase: ele passa sempre pelas APIs do painel.

-- ---------- Usuários e sessões ----------
create table if not exists piper_users (
  username      text primary key,
  name          text not null default '',
  role          text not null default 'user' check (role in ('admin', 'user')),
  password_hash text not null,
  created_at    timestamptz not null default now(),
  last_login    timestamptz
);

create table if not exists piper_sessions (
  token_hash  text primary key,               -- sha256 do token; o token em si nunca é guardado
  username    text not null references piper_users(username) on delete cascade on update cascade,
  device      text not null default '',
  created_at  timestamptz not null default now(),
  last_seen   timestamptz not null default now(),
  expires_at  timestamptz not null
);
create index if not exists piper_sessions_user on piper_sessions(username);

-- Limite de tentativas de login (8 erros a cada 15 minutos por usuário e por IP)
create table if not exists piper_login_failures (
  key       text primary key,
  count     int not null default 0,
  reset_at  timestamptz not null
);

-- ---------- Memória da Piper ----------
create table if not exists piper_memories (
  id          bigserial primary key,
  username    text not null references piper_users(username) on delete cascade on update cascade,
  text        text not null,
  kind        text not null default 'FACT' check (kind in ('FACT', 'REPLY', 'HABIT')),
  cue         text,
  created_at  timestamptz not null default now()
);
create index if not exists piper_memories_user on piper_memories(username);

-- ---------- Caixa de entrada: ações combinadas no painel e aplicadas no celular ----------
create table if not exists piper_inbox (
  id            bigserial primary key,
  username      text not null references piper_users(username) on delete cascade on update cascade,
  action        jsonb not null,               -- { "action": "create_event", "title": ..., ... }
  source        text not null default 'panel',-- 'panel' (botões) ou 'chat' (ChatGPT no painel)
  created_at    timestamptz not null default now(),
  delivered_at  timestamptz,                  -- quando o celular buscou
  device        text
);
create index if not exists piper_inbox_pending on piper_inbox(username) where delivered_at is null;

-- ---------- Espelho do celular (agenda, tarefas, anotações) ----------
-- O celular é a fonte da verdade (funciona offline). A cada mudança ele envia uma "foto" completa
-- e o painel mostra essa foto. local_id é o id do item no celular (usado pelas ações de editar/concluir).
create table if not exists piper_events (
  username          text not null references piper_users(username) on delete cascade on update cascade,
  local_id          bigint not null,
  title             text not null,
  description       text not null default '',
  start_at          timestamptz not null,
  duration_minutes  int not null default 60,
  type              text not null default 'MEETING',
  reminder_minutes  int not null default 30,
  source            text,                     -- nulo = criado na Piper; senão "Outlook · ...", "Google · ..."
  primary key (username, local_id)
);
create index if not exists piper_events_start on piper_events(username, start_at);

create table if not exists piper_tasks (
  username      text not null references piper_users(username) on delete cascade on update cascade,
  local_id      bigint not null,
  title         text not null,
  notes         text not null default '',
  remind_at     timestamptz,
  starred       boolean not null default false,
  done          boolean not null default false,
  created_at    timestamptz not null default now(),
  completed_at  timestamptz,
  primary key (username, local_id)
);

create table if not exists piper_notes (
  username    text not null references piper_users(username) on delete cascade on update cascade,
  local_id    bigint not null,
  text        text not null,
  created_at  timestamptz not null default now(),
  primary key (username, local_id)
);

-- Aparelhos conectados e última sincronização (aba "Visão geral")
create table if not exists piper_devices (
  username     text not null references piper_users(username) on delete cascade on update cascade,
  device       text not null,
  app_version  text,
  last_sync    timestamptz not null default now(),
  primary key (username, device)
);

-- ---------- RLS: ninguém de fora acessa ----------
alter table piper_users          enable row level security;
alter table piper_sessions       enable row level security;
alter table piper_login_failures enable row level security;
alter table piper_memories       enable row level security;
alter table piper_inbox          enable row level security;
alter table piper_events         enable row level security;
alter table piper_tasks          enable row level security;
alter table piper_notes          enable row level security;
alter table piper_devices        enable row level security;

-- ---------- Funções (chamadas pelo painel via /rest/v1/rpc/...) ----------

-- Troca toda a "foto" do celular de uma vez (atômico: ou grava tudo ou nada).
create or replace function piper_replace_snapshot(
  p_username text, p_device text, p_app_version text,
  p_events jsonb, p_tasks jsonb, p_notes jsonb
) returns void
language plpgsql security definer set search_path = public as $$
begin
  delete from piper_events where username = p_username;
  delete from piper_tasks  where username = p_username;
  delete from piper_notes  where username = p_username;

  insert into piper_events (username, local_id, title, description, start_at, duration_minutes, type, reminder_minutes, source)
  select p_username, (e->>'id')::bigint, left(coalesce(e->>'title', ''), 300), left(coalesce(e->>'description', ''), 4000),
         to_timestamp((e->>'startAt')::double precision / 1000),
         coalesce((e->>'durationMinutes')::int, 60), coalesce(e->>'type', 'MEETING'),
         coalesce((e->>'reminderMinutes')::int, 30), nullif(e->>'source', '')
  from jsonb_array_elements(coalesce(p_events, '[]'::jsonb)) e
  where e->>'id' is not null and e->>'startAt' is not null
  on conflict do nothing;

  insert into piper_tasks (username, local_id, title, notes, remind_at, starred, done, created_at, completed_at)
  select p_username, (t->>'id')::bigint, left(coalesce(t->>'title', ''), 300), left(coalesce(t->>'notes', ''), 4000),
         case when t->>'remindAt' is null then null else to_timestamp((t->>'remindAt')::double precision / 1000) end,
         coalesce((t->>'starred')::boolean, false), coalesce((t->>'done')::boolean, false),
         coalesce(to_timestamp((t->>'createdAt')::double precision / 1000), now()),
         case when t->>'completedAt' is null then null else to_timestamp((t->>'completedAt')::double precision / 1000) end
  from jsonb_array_elements(coalesce(p_tasks, '[]'::jsonb)) t
  where t->>'id' is not null
  on conflict do nothing;

  insert into piper_notes (username, local_id, text, created_at)
  select p_username, (n->>'id')::bigint, left(coalesce(n->>'text', ''), 4000),
         coalesce(to_timestamp((n->>'createdAt')::double precision / 1000), now())
  from jsonb_array_elements(coalesce(p_notes, '[]'::jsonb)) n
  where n->>'id' is not null
  on conflict do nothing;

  insert into piper_devices (username, device, app_version, last_sync)
  values (p_username, coalesce(nullif(p_device, ''), 'celular'), p_app_version, now())
  on conflict (username, device) do update set app_version = excluded.app_version, last_sync = now();
end $$;

-- O celular busca as ações pendentes e já marca como entregues (sem entregar duas vezes).
create or replace function piper_pull_inbox(p_username text, p_device text)
returns setof piper_inbox
language sql security definer set search_path = public as $$
  update piper_inbox set delivered_at = now(), device = p_device
  where id in (
    select id from piper_inbox
    where username = p_username and delivered_at is null
    order by id limit 200
    for update skip locked
  )
  returning *;
$$;

-- Troca a memória inteira de um usuário (o app e o painel enviam a lista completa).
create or replace function piper_replace_memories(p_username text, p_memories jsonb)
returns int
language plpgsql security definer set search_path = public as $$
declare n int;
begin
  delete from piper_memories where username = p_username;
  insert into piper_memories (username, text, kind, cue, created_at)
  select p_username, m->>'text', coalesce(m->>'kind', 'FACT'), nullif(m->>'cue', ''),
         coalesce(to_timestamp((m->>'createdAt')::double precision / 1000), now())
  from jsonb_array_elements(coalesce(p_memories, '[]'::jsonb)) m
  where coalesce(m->>'text', '') <> '';
  get diagnostics n = row_count;
  return n;
end $$;

-- Conta uma tentativa de login errada; zera depois de 15 minutos.
create or replace function piper_register_failure(p_key text)
returns int
language sql security definer set search_path = public as $$
  insert into piper_login_failures (key, count, reset_at) values (p_key, 1, now() + interval '15 minutes')
  on conflict (key) do update set
    count    = case when piper_login_failures.reset_at < now() then 1 else piper_login_failures.count + 1 end,
    reset_at = case when piper_login_failures.reset_at < now() then now() + interval '15 minutes' else piper_login_failures.reset_at end
  returning count;
$$;

-- As funções também só podem ser chamadas pelo servidor.
revoke all on function piper_replace_snapshot(text, text, text, jsonb, jsonb, jsonb) from public, anon, authenticated;
revoke all on function piper_pull_inbox(text, text)                                 from public, anon, authenticated;
revoke all on function piper_replace_memories(text, jsonb)                          from public, anon, authenticated;
revoke all on function piper_register_failure(text)                                 from public, anon, authenticated;
grant execute on function piper_replace_snapshot(text, text, text, jsonb, jsonb, jsonb) to service_role;
grant execute on function piper_pull_inbox(text, text)                                 to service_role;
grant execute on function piper_replace_memories(text, jsonb)                          to service_role;
grant execute on function piper_register_failure(text)                                 to service_role;
