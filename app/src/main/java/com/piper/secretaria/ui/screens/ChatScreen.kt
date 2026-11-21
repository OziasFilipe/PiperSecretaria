package com.piper.secretaria.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piper.secretaria.ui.AppActions
import com.piper.secretaria.ui.ChatMessage
import com.piper.secretaria.ui.MainViewModel
import com.piper.secretaria.ui.components.FluteMascot
import com.piper.secretaria.ui.components.PiperTopBar
import com.piper.secretaria.ui.components.SoundWave
import com.piper.secretaria.ui.theme.PiperColors
import java.time.LocalDate

@Composable
fun ChatScreen(vm: MainViewModel, actions: AppActions, autoListen: Boolean) {
    val context = LocalContext.current
    val chat by vm.chat.collectAsStateWithLifecycle()
    val speaking by vm.speaking.collectAsStateWithLifecycle()
    var listening by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    var autoListenDone by rememberSaveable { mutableStateOf(false) }

    val recognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        listening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) vm.send(text)
        }
    }
    val listen: () -> Unit = {
        vm.stopSpeaking()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale com a Piper…")
        try {
            listening = true
            recognizer.launch(intent)
        } catch (_: ActivityNotFoundException) {
            listening = false
            Toast.makeText(context, "Reconhecimento de voz indisponível. Instale o app Google.", Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(autoListen) {
        if (autoListen && !autoListenDone) { autoListenDone = true; listen() }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(chat.size) { if (chat.isNotEmpty()) listState.animateScrollToItem(chat.lastIndex) }

    Scaffold(
        topBar = {
            PiperTopBar("Conversar com a Piper", Icons.AutoMirrored.Filled.Chat, onBack = actions.back, actions = {
                if (speaking) IconButton(onClick = vm::stopSpeaking) { Icon(Icons.Default.VolumeOff, "Silenciar") }
            })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            val compact = chat.size > 2
            Column(
                Modifier.fillMaxWidth().animateContentSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FluteMascot(Modifier.size(if (compact) 96.dp else 170.dp), talking = speaking, listening = listening)
                SoundWave(active = listening || speaking, barCount = if (compact) 16 else 24)
                Text(
                    when {
                        listening -> "Pode falar, estou ouvindo…"
                        speaking -> "Falando…"
                        else -> "Toque no microfone ou digite"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                items(chat, key = { it.id }) { Bubble(it) }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Suggestion("Agendar uma reunião", Icons.Default.CalendarMonth) { actions.newEvent(LocalDate.now()) }
                Suggestion("Criar uma tarefa", Icons.Default.Check) { actions.newTask() }
                Suggestion("Me lembrar mais tarde", Icons.Default.NotificationsActive) { input = "Me lembra de " }
                Suggestion("Resumo do dia", Icons.Default.BarChart) { vm.send("o que tenho hoje") }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    input, { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ex.: me lembra de ligar pro João às 15h") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { vm.send(input); input = "" }),
                    trailingIcon = {
                        if (input.isNotBlank()) IconButton(onClick = { vm.send(input); input = "" }) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                )
                Spacer(Modifier.size(10.dp))
                Box(
                    Modifier
                        .size(56.dp)
                        .shadow(8.dp, CircleShape, spotColor = PiperColors.Purple)
                        .clip(CircleShape)
                        .background(PiperColors.buttonGradient)
                        .clickable(onClick = listen),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Mic, "Falar", tint = Color.White, modifier = Modifier.size(28.dp)) }
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            m.text,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    if (m.fromUser) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
                    else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                )
                .background(if (m.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            color = if (m.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Suggestion(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}
