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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piper.secretaria.data.EventEntity
import com.piper.secretaria.ui.AppActions
import com.piper.secretaria.ui.MainViewModel
import com.piper.secretaria.ui.components.EmptyState
import com.piper.secretaria.ui.components.GradientButton
import com.piper.secretaria.ui.components.PiperTopBar
import com.piper.secretaria.ui.components.SoftCard
import com.piper.secretaria.util.Dates
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Composable
fun AgendaScreen(vm: MainViewModel, actions: AppActions) {
    val events by vm.events.collectAsStateWithLifecycle()
    var selectedEpochDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedEpochDay)
    val weekStart = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val daysWithEvents = events.map { Dates.toDate(it.startAt) }.toSet()
    val dayEvents = events.filter { Dates.toDate(it.startAt) == selected }

    Scaffold(
        topBar = { PiperTopBar("Agenda", Icons.Default.CalendarMonth, onBack = actions.back, onAdd = { actions.newEvent(selected) }) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedEpochDay = selected.minusWeeks(1).toEpochDay() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Semana anterior")
                }
                Text(
                    Dates.monthTitle(selected), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { selectedEpochDay = selected.plusWeeks(1).toEpochDay() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próxima semana")
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                (0L until 7L).map { weekStart.plusDays(it) }.forEach { day ->
                    DayCell(day, day == selected, day == LocalDate.now(), day in daysWithEvents) { selectedEpochDay = day.toEpochDay() }
                }
            }
            if (selected != LocalDate.now()) {
                TextButton(onClick = { selectedEpochDay = LocalDate.now().toEpochDay() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Voltar para hoje")
                }
            }
            Text(
                Dates.longDay(selected), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (dayEvents.isEmpty()) item { EmptyState("Nenhum compromisso neste dia.\nToque em \"Novo Evento\" ou fale comigo.") }
                items(dayEvents, key = { it.id }) { e -> EventCard(e) { actions.editEvent(e) } }
            }
            GradientButton("Novo Evento", modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp)) {
                actions.newEvent(selected)
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, selected: Boolean, today: Boolean, hasEvents: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(Dates.weekdayLetter(day), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    when {
                        selected -> MaterialTheme.colorScheme.primary
                        today -> MaterialTheme.colorScheme.primaryContainer
                        else -> androidx.compose.ui.graphics.Color.Transparent
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                fontWeight = if (selected || today) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.size(5.dp).clip(CircleShape)
                .background(if (hasEvents) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}

@Composable
private fun EventCard(e: EventEntity, onClick: () -> Unit) {
    SoftCard(Modifier.padding(horizontal = 16.dp), onClick = onClick) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(e.type.color()))
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Dates.hhmm(e.startAt), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(56.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        e.description.ifBlank { "${e.type.label} · ${e.durationMinutes} min" },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    if (e.source != null) Text(
                        e.source, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary,
                    )
                    if (e.reminderMinutes >= 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NotificationsActive, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (e.reminderMinutes == 0) "Aviso na hora" else "Aviso ${e.reminderMinutes} min antes",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Icon(e.type.icon(), null, tint = e.type.color())
            }
        }
    }
}
