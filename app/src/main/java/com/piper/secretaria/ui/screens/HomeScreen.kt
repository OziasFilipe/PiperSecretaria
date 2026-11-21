package com.piper.secretaria.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piper.secretaria.data.EventEntity
import com.piper.secretaria.data.TaskEntity
import com.piper.secretaria.ui.AppActions
import com.piper.secretaria.ui.MainViewModel
import com.piper.secretaria.ui.Routes
import com.piper.secretaria.ui.components.Dot
import com.piper.secretaria.ui.components.FluteMascot
import com.piper.secretaria.ui.components.SoftCard
import com.piper.secretaria.ui.theme.PiperColors
import com.piper.secretaria.util.Dates
import java.time.LocalDate

@Composable
fun HomeScreen(vm: MainViewModel, actions: AppActions) {
    val events by vm.events.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val speaking by vm.speaking.collectAsStateWithLifecycle()
    val celebrations by vm.celebrations.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val todayEvents = events.filter { Dates.toDate(it.startAt) == today }
    val upcoming = tasks.filter { !it.done }.sortedBy { it.remindAt ?: Long.MAX_VALUE }.take(5)
    val greeting = if (settings.userName.isBlank()) "Oi! 👋" else "Oi, ${settings.userName}! 👋"

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(PiperColors.headerGradient)
                    .statusBarsPadding()
                    .padding(bottom = 56.dp),
            ) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = actions.openDrawer) { Icon(Icons.Default.Menu, "Menu", tint = Color.White) }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Piper", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                            Text("Secretária Pessoal", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFDDD6FE))
                        }
                        IconButton(onClick = { actions.navigate(Routes.SETTINGS) }) { Icon(Icons.Default.Settings, "Configurações", tint = Color.White) }
                    }
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        FluteMascot(
                            Modifier.size(170.dp),
                            talking = speaking,
                            celebrate = celebrations,
                            onTap = { vm.say("$greeting Como posso te ajudar hoje?".replace("👋", "")) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp))
                                .background(Color.White.copy(alpha = 0.14f))
                                .padding(16.dp),
                        ) {
                            Text(greeting, color = Color.White, style = MaterialTheme.typography.titleLarge)
                            Text(
                                if (speaking) "…" else "Como posso te ajudar hoje?",
                                color = Color(0xFFEDE9FE), style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
        item {
            // Atalhos sobrepostos ao cabeçalho
            Row(
                Modifier.fillMaxWidth().offset(y = (-44).dp).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Shortcut("Falar comigo", Icons.Default.Mic, primary = true, Modifier.weight(1f), actions.listen)
                Shortcut("Agenda", Icons.Default.CalendarMonth, false, Modifier.weight(1f)) { actions.navigate(Routes.AGENDA) }
                Shortcut("Tarefas", Icons.Default.CheckCircle, false, Modifier.weight(1f)) { actions.navigate(Routes.TASKS) }
                Shortcut("Lembretes", Icons.Default.Notifications, false, Modifier.weight(1f)) { actions.navigate(Routes.NOTIFICATIONS) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().offset(y = (-28).dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Hoje", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Text(Dates.longDay(today), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SoftCard(Modifier.offset(y = (-20).dp).padding(horizontal = 16.dp)) {
                if (todayEvents.isEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().clickable { actions.newEvent(today) }.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Agenda livre hoje. Toque para agendar algo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    todayEvents.forEachIndexed { i, e ->
                        TimelineRow(e, last = i == todayEvents.lastIndex) { actions.editEvent(e) }
                        if (i < todayEvents.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsNone, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Próximos lembretes", style = MaterialTheme.typography.titleLarge)
            }
        }
        if (upcoming.isEmpty()) {
            item {
                Text(
                    "Nenhuma tarefa pendente. Diga \"me lembra de…\" no microfone.",
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(upcoming, key = { it.id }) { t ->
            ReminderRow(t, onToggle = { vm.toggleTask(t) }, onClick = { actions.editTask(t) })
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Shortcut(label: String, icon: ImageVector, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (primary) PiperColors.buttonGradient else null
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .then(if (bg != null) Modifier.background(bg) else Modifier.background(MaterialTheme.colorScheme.surface))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (primary) Color.White else MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (primary) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun TimelineRow(e: EventEntity, last: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(Dates.hhmm(e.startAt), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(56.dp))
        Box(Modifier.fillMaxHeight().width(16.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
            Dot(e.type.color())
        }
        Spacer(Modifier.width(12.dp))
        Text(e.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Icon(e.type.icon(), null, tint = e.type.color())
    }
}

@Composable
fun ReminderRow(t: TaskEntity, onToggle: () -> Unit, onClick: () -> Unit) {
    val overdue = t.remindAt != null && t.remindAt < System.currentTimeMillis()
    SoftCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), onClick = onClick) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                t.remindAt?.let { Dates.relative(it) } ?: "Sem hora",
                style = MaterialTheme.typography.labelLarge,
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(96.dp),
            )
            Text(t.title, Modifier.weight(1f), fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Checkbox(checked = t.done, onCheckedChange = { onToggle() })
        }
    }
}
