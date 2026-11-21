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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piper.secretaria.data.NotificationCategory
import com.piper.secretaria.data.NotificationEntity
import com.piper.secretaria.ui.AppActions
import com.piper.secretaria.ui.MainViewModel
import com.piper.secretaria.ui.components.EmptyState
import com.piper.secretaria.ui.components.IconBadge
import com.piper.secretaria.ui.components.PillTabs
import com.piper.secretaria.ui.components.PiperTopBar
import com.piper.secretaria.ui.components.SoftCard
import com.piper.secretaria.ui.theme.PiperColors
import com.piper.secretaria.util.Dates

@Composable
fun NotificationsScreen(vm: MainViewModel, actions: AppActions) {
    val all by vm.notifications.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { vm.markNotificationsRead() }

    val filtered = when (tab) {
        1 -> all.filter { it.category == NotificationCategory.AGENDA }
        2 -> all.filter { it.category == NotificationCategory.TAREFAS }
        3 -> all.filter { it.category == NotificationCategory.SISTEMA }
        else -> all
    }
    val groups = filtered.groupBy { Dates.relativeDay(Dates.toDate(it.createdAt)) }

    Scaffold(
        topBar = {
            PiperTopBar("Notificações", Icons.Default.Notifications, onBack = actions.back, actions = {
                if (all.isNotEmpty()) IconButton(onClick = { vm.clearNotifications() }) { Icon(Icons.Default.DeleteSweep, "Limpar") }
            })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PillTabs(listOf("Todas", "Agenda", "Tarefas", "Sistema"), tab, { tab = it }, Modifier.padding(horizontal = 16.dp))
            LazyColumn(Modifier.weight(1f)) {
                if (filtered.isEmpty()) item {
                    EmptyState("Nenhum aviso por aqui ainda.\nQuando eu te lembrar de algo, aparece nesta lista.")
                }
                groups.forEach { (day, list) ->
                    item(key = "h-$day") {
                        Text(day, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp))
                    }
                    items(list, key = { it.id }) { NotificationRow(it) }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: NotificationEntity) {
    val (icon, color) = when (n.category) {
        NotificationCategory.AGENDA -> Icons.Default.CalendarMonth to PiperColors.Purple
        NotificationCategory.TAREFAS -> Icons.Default.CheckCircle to PiperColors.Green
        NotificationCategory.SISTEMA -> Icons.Default.AutoAwesome to PiperColors.Pink
    }
    SoftCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(n.title, fontWeight = if (n.read) FontWeight.Medium else FontWeight.Bold)
                Text(n.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(Dates.hhmm(n.createdAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
