# Piper — Secretária Pessoal (Android)

App nativo Android (Kotlin + Jetpack Compose) para organizar tarefas, agenda e lembretes,
com a **Piper**, uma flautinha com olhos e boca que conversa com você por voz.

## Como abrir e rodar
1. Abra esta pasta no **Android Studio** (Ladybug 2024.2 ou mais novo).
2. Espere o Gradle sincronizar (ele baixa as bibliotecas na primeira vez).
3. Conecte o celular (depuração USB) ou use um emulador e clique em **Run ▶**.

Pela linha de comando: `gradlew.bat assembleDebug` (o APK sai em `app/build/outputs/apk/debug/`).
Testes do interpretador de comandos: `gradlew.bat testDebugUnitTest`.

## O que já tem
- **Início**: saudação da Piper, atalhos (Falar comigo, Agenda, Tarefas, Lembretes), compromissos de hoje e próximos lembretes.
- **Agenda**: semana com navegação, eventos do dia, criar/editar/excluir, tipo (reunião, ligação, trabalho, pessoal), duração e aviso (na hora, 5/15/30 min, 1 h, 1 dia antes).
- **Tarefas**: abas Hoje / Pendentes / Concluídas, estrela de importante, lembrete com atalhos (em 1 hora, hoje 18:00, amanhã 09:00 ou data/hora livre).
- **Conversar com a Piper**: reconhecimento de voz em português + resposta falada. Entende frases como:
  - "me lembra de ligar pro João amanhã às 15h"
  - "agendar reunião com NC Group segunda às 14:30"
  - "criar tarefa ajustar view no Oracle"
  - "o que tenho hoje?" / "o que tenho amanhã?" / "minhas tarefas"
  - "anota ideia: app de recibos"
  - "me avisa em 30 minutos de tirar o bolo"
- **Notificações** (histórico com abas Todas / Agenda / Tarefas / Sistema), **Anotações**, **Ações rápidas**, **menu lateral** e **Configurações**.

## Avisos confiáveis
- Alarmes exatos (`setExactAndAllowWhileIdle`) que tocam mesmo com o celular em repouso.
- Notificação de compromisso com **Ver pauta** e **Adiar 15 min**; de tarefa com **Concluir** e **Adiar 15 min**.
- Reagenda tudo depois de reiniciar o celular, trocar o fuso/horário ou atualizar o app.
- **Resumo do dia** todo dia no horário escolhido (padrão 08:00).
- Em Configurações há o status das permissões (notificações, alarmes exatos, economia de bateria) e um botão **Testar notificação**.
  Em celulares Xiaomi/Samsung/Motorola, deixe a Piper como "Sem restrição" de bateria para não perder avisos.

## Integrações (Outlook, Google e outras agendas)
Em **Configurações › Integrações**, ligue "Outlook, Google e outras agendas". A Piper lê (só leitura) as agendas
das contas do celular, importa os compromissos dos próximos 60 dias e passa a avisar deles. Sincroniza ao abrir o app.
- **Outlook / Microsoft 365**: instale o app Microsoft Outlook, entre na conta e ative *Sincronizar calendários*.
- **Google Agenda**: a conta Google do celular já aparece.
Login direto na Microsoft/Google (OAuth, inclusive tarefas do To Do) fica para uma próxima versão e exige
registrar o app no Azure e no Google Cloud.

## Atualização dentro do app (desde a 1.1)
Toda vez que o app é aberto, a Piper lê o arquivo `update.json` em
`https://github.com/OziasFilipe/PiperSecretaria/releases/latest/download/update.json`
(endereço em `app/build.gradle.kts`, campo `UPDATE_URL`). Se o `versionCode` de lá for maior que o instalado,
aparece o pop-up **Atualizar agora / Depois**; escolhendo "Depois", ele volta a aparecer na próxima abertura.
Também há o botão **Verificar atualização** em Configurações.

### Como publicar uma nova versão
1. Em `app/build.gradle.kts`, aumente `versionCode` (ex.: 2 → 3) e `versionName` (ex.: "1.2").
2. Gere o APK (`gradlew.bat assembleDebug`) **no mesmo PC** (o Android só aceita a atualização se o APK
   for assinado com a mesma chave; a chave de debug fica em `%USERPROFILE%\.android\debug.keystore`, guarde uma cópia).
3. No GitHub, no repositório público `PiperSecretaria`, crie uma *Release* (ex.: tag `v1.2`) e anexe dois arquivos:
   - o APK renomeado para **`Piper.apk`**;
   - o **`update.json`** com o novo `versionCode`, `versionName` e as notas (modelo em `APK/update.json`).
4. Pronto: na próxima vez que o app abrir no celular, ele oferece a atualização.

## Estrutura
```
app/src/main/java/com/piper/secretaria/
  data/        Room (tarefas, eventos, notas, histórico) + preferências
  reminders/   AlarmManager, receptores (disparo, adiar, concluir, boot) e notificações
  assistant/   interpretador de comandos em pt-BR (offline) e respostas
  update/      verificação, download e instalação de novas versões
  ui/          tema roxo, mascote (FluteMascot), telas e navegação
```

## Próximos passos sugeridos
- Integrar uma IA de verdade (ex.: API do Claude) no chat para conversas livres e resumo de e-mails.
- Integrações com Google Agenda / Outlook / WhatsApp.
- Widget na tela inicial e atalho de voz pelo Assistente.
