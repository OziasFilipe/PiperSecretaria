package com.piper.secretaria.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piper.secretaria.data.MemoryKind
import com.piper.secretaria.ui.AppActions
import com.piper.secretaria.ui.MainViewModel
import com.piper.secretaria.ui.components.EmptyState
import com.piper.secretaria.ui.components.IconBadge
import com.piper.secretaria.ui.components.PiperTopBar
import com.piper.secretaria.ui.components.SoftCard
import com.piper.secretaria.ui.theme.PiperColors

/** "Memória da Piper": tudo o que ela aprendeu, com opção de apagar. */
@Composable
fun MemoryScreen(vm: MainViewModel, actions: AppActions) {
    val memories by vm.memories.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            PiperTopBar("Memória da Piper", Icons.Default.Psychology, onBack = actions.back, actions = {
                if (memories.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Default.DeleteSweep, "Apagar tudo") }
            })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Eu aprendo com o que você me conta (\"lembre que…\", \"quando eu disser X, responda Y\") e com a sua rotina. " +
                    "Nunca guardo senhas, chaves ou documentos.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.weight(1f)) {
                if (memories.isEmpty()) item { EmptyState("Ainda estou te conhecendo.\nDiga: \"lembre que eu prefiro reuniões à tarde\".") }
                items(memories, key = { it.id }) { m ->
                    val (icon, color, label) = when (m.kind) {
                        MemoryKind.REPLY.name -> Triple(Icons.Default.Reply, PiperColors.Blue, "Resposta ensinada")
                        MemoryKind.HABIT.name -> Triple(Icons.Default.Repeat, PiperColors.Orange, "Hábito percebido")
                        else -> Triple(Icons.Default.AutoAwesome, PiperColors.Purple, "Sobre você")
                    }
                    SoftCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(icon, color)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.labelLarge, color = color)
                                Text(if (m.cue != null) "\"${m.cue}\" → ${m.text}" else m.text, style = MaterialTheme.typography.bodyLarge)
                            }
                            IconButton(onClick = { vm.deleteMemory(m) }) { Icon(Icons.Default.Delete, "Apagar") }
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Apagar toda a memória?") },
        text = { Text("A Piper vai esquecer tudo o que aprendeu (aqui e no painel).") },
        confirmButton = { TextButton(onClick = { vm.clearMemories(); confirmClear = false }) { Text("Apagar tudo") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
    )
}
