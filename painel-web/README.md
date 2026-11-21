# Piper · Painel web

Painel da Piper para publicar na **Vercel**. Nele você:
- entra com usuário e senha;
- conversa com a Piper usando o **ChatGPT** (API da OpenAI);
- vê e apaga a **memória** da Piper (o que ela aprendeu);
- vê e cria **usuários** (perfil administrador).

O app Android faz login neste painel, guarda a sessão e passa a usar o ChatGPT para conversar,
agendar, criar tarefas e tirar dúvidas. O que você combinar no painel é aplicado no celular quando o app abrir.

## Publicar na Vercel (uma vez)
1. Em vercel.com: **Add New › Project › Import** o repositório `OziasFilipe/PiperSecretaria`.
   Em **Root Directory** escolha `painel-web`. Framework: Next.js (detectado sozinho).
2. **Storage › Create Database › Upstash (Redis)**, plano gratuito, e conecte ao projeto.
   Isso cria sozinho `KV_REST_API_URL` e `KV_REST_API_TOKEN`.
3. **Settings › Environment Variables**, adicione:
   - `OPENAI_API_KEY`: sua chave em https://platform.openai.com/api-keys (é o que "conecta o ChatGPT"; a API é paga por uso).
   - `OPENAI_MODEL` (opcional): padrão `gpt-4o-mini`.
   - `ADMIN_USER`: seu usuário (ex.: `ozias`).
   - `ADMIN_PASSWORD`: sua senha. Ela vira hash no primeiro acesso; nunca fica no código.
4. **Deploy**. Abra o endereço (ex.: `https://piper-painel.vercel.app`), entre e pronto.
5. No celular, na tela de login da Piper, use esse mesmo endereço, seu usuário e senha. A sessão fica salva.

## Segurança
- Senhas são guardadas só como hash (scrypt). Sessões duram 180 dias e podem ser encerradas com "Sair".
- Login bloqueia por 15 minutos depois de 8 tentativas erradas.
- A chave da OpenAI fica só na Vercel; nunca vai para o app nem para o GitHub.
- A memória nunca guarda senhas, chaves ou documentos.

## Rodar localmente
```
npm install
cp .env.example .env.local   # preencha os valores
npm run dev
```
