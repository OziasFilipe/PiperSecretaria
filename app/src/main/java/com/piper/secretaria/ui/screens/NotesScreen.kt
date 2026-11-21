package com.piper.secretaria.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piper.secretaria.ui.AppActions
import com.piper.secretaria.ui.MainViewModel
import com.piper.secretaria.ui.components.EmptyState
import com.piper.secretaria.ui.components.GradientButton
import com.piper.secretaria.ui.components.PiperTopBar
import com.piper.secretaria.ui.components.SoftCard
import com.piper.secretaria.util.Dates

@Composable
fun NotesScreen(vm: MainViewModel, actions: AppActions) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { PiperTopBar("Anotações", Icons.Default.EditNote, onBack = actions.back, onAdd = actions.newNote) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.weight(1f)) {
                if (notes.isEmpty()) item { EmptyState("Guarde ideias rápidas aqui.\nDiga: \"anota ideia: …\"") }
                items(notes, key = { it.id }) { n ->
                    SoftCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(n.text, style = MaterialTheme.typography.bodyLarge)
                                Text(Dates.relative(n.createdAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.deleteNote(n) }) { Icon(Icons.Default.Delete, "Excluir") }
                        }
                    }
                }
            }
            GradientButton("Anotar ideia", modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp), onClick = actions.newNote)
        }
    }
}
