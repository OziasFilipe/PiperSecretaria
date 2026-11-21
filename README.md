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

## Estrutura
```
app/src/main/java/com/piper/secretaria/
  data/        Room (tarefas, eventos, notas, histórico) + preferências
  reminders/   AlarmManager, receptores (disparo, adiar, concluir, boot) e notificações
  assistant/   interpretador de comandos em pt-BR (offline) e respostas
  ui/          tema roxo, mascote (FluteMascot), telas e navegação
```

## Próximos passos sugeridos
- Integrar uma IA de verdade (ex.: API do Claude) no chat para conversas livres e resumo de e-mails.
- Integrações com Google Agenda / Outlook / WhatsApp.
- Widget na tela inicial e atalho de voz pelo Assistente.
