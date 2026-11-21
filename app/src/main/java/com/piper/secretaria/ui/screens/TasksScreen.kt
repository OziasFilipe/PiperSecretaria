package com.piper.secretaria.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piper.secretaria.data.TaskEntity
import com.piper.secretaria.ui.AppActions
import com.piper.secretaria.ui.MainViewModel
import com.piper.secretaria.ui.components.EmptyState
import com.piper.secretaria.ui.components.GradientButton
import com.piper.secretaria.ui.components.PillTabs
import com.piper.secretaria.ui.components.PiperTopBar
import com.piper.secretaria.util.Dates
import java.time.LocalDate

@Composable
fun TasksScreen(vm: MainViewModel, actions: AppActions) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val today = LocalDate.now()
    val tomorrowStart = Dates.startOfDay(today.plusDays(1))
    val shown = when (tab) {
        0 -> tasks.filter { !it.done && (it.remindAt == null || it.remindAt < tomorrowStart) }
        1 -> tasks.filter { !it.done }
        else -> tasks.filter { it.done }.sortedByDescending { it.completedAt ?: 0 }
    }.sortedByDescending { it.starred && !it.done }

    Scaffold(
        topBar = { PiperTopBar("Tarefas", Icons.Outlined.CheckCircle, onBack = actions.back, onAdd = actions.newTask) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val pending = tasks.count { !it.done }
            PillTabs(
                listOf("Hoje", "Pendentes ($pending)", "Concluídas"), tab, { tab = it },
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyColumn(Modifier.weight(1f)) {
                if (shown.isEmpty()) item {
                    EmptyState(if (tab == 2) "Nada concluído ainda." else "Tudo em dia! Crie uma tarefa ou fale comigo.")
                }
                items(shown, key = { it.id }) { t ->
                    TaskRow(t, onToggle = { vm.toggleTask(t) }, onStar = { vm.saveTask(t.copy(starred = !t.starred)) }) { actions.editTask(t) }
                    HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            GradientButton("Nova Tarefa", modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp), onClick = actions.newTask)
        }
    }
}

@Composable
private fun TaskRow(t: TaskEntity, onToggle: () -> Unit, onStar: () -> Unit, onClick: () -> Unit) {
    val overdue = !t.done && t.remindAt != null && t.remindAt < System.currentTimeMillis()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = t.done, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(
                t.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textDecoration = if (t.done) TextDecoration.LineThrough else null,
                color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val sub = when {
                t.done -> t.completedAt?.let { "Concluída ${Dates.relative(it)}" }
                t.remindAt != null -> (if (overdue) "Atrasada · " else "Lembrete · ") + Dates.relative(t.remindAt)
                else -> t.notes.takeIf { it.isNotBlank() }
            }
            if (sub != null) Text(
                sub, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onStar) {
            Icon(
                if (t.starred) Icons.Default.Star else Icons.Default.StarBorder, "Importante",
                tint = if (t.starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
