# Piper · Painel web

Painel da Piper para publicar na **Vercel**, com os dados guardados no **Supabase**. Pelo navegador você:
- vê a **visão geral**: compromissos de hoje, tarefas pendentes e atrasadas, concluídas na semana e a última sincronização do celular;
- acompanha a **agenda** (inclusive o que veio do Outlook/Google) e cria, edita ou exclui compromissos;
- acompanha as **tarefas**, cria novas e conclui;
- vê e cria **anotações**;
- **conversa** com a Piper usando o ChatGPT (ela enxerga sua agenda e tarefas reais);
- vê e apaga a **memória** da Piper;
- gerencia **usuários** (perfil administrador).

## Como o painel e o celular conversam
```
 Celular (app Piper)                      Painel (Vercel)                    Supabase
 ───────────────────                      ───────────────                    ────────
 muda agenda/tarefa/anotação ──PUT /api/sync──▶ grava a "foto" ───────────▶ piper_events / piper_tasks / piper_notes
 abre o app e a cada 1 min  ──POST /api/inbox─▶ entrega pendentes ◀──────── piper_inbox
                                          você cria/conclui algo ─────────▶ piper_inbox (aguardando o celular)
 memória ◀──────────────── GET/PUT /api/memory ──────────────────────────▶ piper_memories
```
- O celular continua sendo a fonte da verdade (funciona sem internet e é ele que toca os avisos).
- O que você faz no painel entra na **caixa de entrada** e aparece como "Aguardando o celular" até o app aplicar
  (ao abrir o app, ou em até 1 minuto se ele já estiver aberto). Depois disso a agenda do painel é atualizada sozinha.
- O app nunca fala direto com o Supabase: tudo passa pelo painel, com a sessão do usuário.

## Publicar (uma vez)

### 1. Supabase
1. Em https://supabase.com crie uma conta e um **New project** (plano gratuito serve). Anote a senha do banco.
2. No projeto, abra **SQL Editor › New query**, cole todo o arquivo [`supabase/schema.sql`](supabase/schema.sql) e clique em **Run**.
   Pode rodar de novo quando atualizar o painel; ele só cria o que falta.
3. Em **Project Settings › API** (ou **API Keys**) copie:
   - **Project URL** (ex.: `https://abcdefgh.supabase.co`);
   - a chave **service_role** (secreta). Não use a chave `anon`.

### 2. Vercel
1. Em vercel.com: **Add New › Project › Import** o repositório.
   Em **Root Directory** escolha a pasta do painel (`painel-web`, ou `PiperSecretaria/painel-web` se o repositório for o `epiperEcommerce`).
   Framework: Next.js (detectado sozinho).
2. Em **Settings › Environment Variables** adicione:
   - `SUPABASE_URL`: o Project URL.
   - `SUPABASE_SERVICE_ROLE_KEY`: a chave service_role.
   - `ADMIN_USER`: seu usuário (ex.: `ozias`).
   - `ADMIN_PASSWORD`: sua senha (vira hash no primeiro acesso; nunca fica no código).
   - `OPENAI_API_KEY` (opcional): para conversar com o ChatGPT. `OPENAI_MODEL` (opcional), padrão `gpt-4o-mini`.
3. **Deploy**. Abra o endereço (ex.: `https://piper-painel.vercel.app`) e entre.
   Na aba **Conexão** aparece "Banco de dados (Supabase): ok ✓" quando está tudo certo.
4. No celular, na tela de login da Piper, use esse mesmo endereço, seu usuário e senha.
   Em alguns segundos a agenda e as tarefas aparecem no painel.

Se você já usava a versão com Upstash Redis: os usuários precisam ser criados de novo (o administrador é recriado
sozinho pelo `ADMIN_USER`/`ADMIN_PASSWORD`), e a memória volta do celular no próximo login. Pode remover o Upstash da Vercel.

## Segurança
- Todas as tabelas têm RLS ligado e nenhuma política pública: só o servidor do painel, com a chave service_role, lê e grava.
- Senhas são guardadas só como hash (scrypt). Os tokens de sessão também (sha256). Sessões duram 180 dias e podem ser encerradas com "Sair".
- Login bloqueia por 15 minutos depois de 8 tentativas erradas (por usuário e por IP).
- As chaves do Supabase e da OpenAI ficam só na Vercel; nunca vão para o app nem para o GitHub.
- A memória nunca guarda senhas, chaves ou documentos.

## APIs
| Rota | Quem usa | O que faz |
|---|---|---|
| `POST /api/auth/login` · `POST /api/auth/logout` | app e painel | entrar / sair |
| `GET /api/me` | app e painel | dados do usuário logado |
| `PUT /api/sync` | app | envia agenda, tarefas e anotações |
| `GET /api/sync` | painel | lê agenda, tarefas, anotações e aparelhos |
| `POST /api/inbox` | app | busca e marca como entregues as ações do painel |
| `GET /api/inbox` | painel | lista ações pendentes e entregues |
| `POST /api/actions` · `DELETE /api/actions?id=` | painel | cria uma ação (agendar, concluir…) / cancela uma pendente |
| `GET/PUT /api/memory` | app e painel | memória da Piper |
| `POST /api/chat` | app e painel | conversa com o ChatGPT |
| `GET/POST/DELETE /api/users` | painel (admin) | usuários |
| `GET /api/health` | qualquer um | status (ChatGPT, Supabase) |

## Rodar localmente
```
npm install
cp .env.example .env.local   # preencha os valores
npm run dev
```
