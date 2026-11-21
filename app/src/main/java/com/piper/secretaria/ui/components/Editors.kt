package com.piper.secretaria.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.piper.secretaria.data.EventEntity
import com.piper.secretaria.data.EventType
import com.piper.secretaria.data.TaskEntity
import com.piper.secretaria.util.Dates
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorSheet(
    initial: TaskEntity?,
    onDismiss: () -> Unit,
    onSave: (TaskEntity) -> Unit,
    onDelete: ((TaskEntity) -> Unit)? = null,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var starred by remember { mutableStateOf(initial?.starred ?: false) }
    var remindAt by remember { mutableStateOf(initial?.remindAt?.let(Dates::toDateTime)) }
    var picking by remember { mutableStateOf(false) }
    val now = remember { LocalDateTime.now() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (initial == null) "Nova tarefa" else "Editar tarefa", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { starred = !starred }) {
                    Icon(if (starred) Icons.Default.Star else Icons.Default.StarBorder, "Importante", tint = MaterialTheme.colorScheme.primary)
                }
                if (initial != null && onDelete != null) IconButton(onClick = { onDelete(initial) }) { Icon(Icons.Default.Delete, "Excluir") }
            }
            OutlinedTextField(
                title, { title = it }, label = { Text("O que precisa ser feito?") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            OutlinedTextField(
                notes, { notes = it }, label = { Text("Detalhes (opcional)") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Text("Lembrar", style = MaterialTheme.typography.titleMedium)
            val presets = listOf(
                "Sem lembrete" to null,
                "Em 1 hora" to now.plusHours(1).withSecond(0).withNano(0),
                "Hoje 18:00" to LocalDateTime.of(LocalDate.now(), LocalTime.of(18, 0)).takeIf { it.isAfter(now) },
                "Amanhã 09:00" to LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(9, 0)),
            ).filter { it.first == "Sem lembrete" || it.second != null }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { (label, value) ->
                    FilterChip(selected = remindAt == value, onClick = { remindAt = value }, label = { Text(label) })
                }
                AssistChip(
                    onClick = { picking = true },
                    label = { Text(remindAt?.let { Dates.relative(Dates.toMillis(it)) } ?: "Escolher…") },
                    leadingIcon = { Icon(Icons.Default.Schedule, null) },
                )
            }
            Spacer(Modifier.height(4.dp))
            GradientButton("Salvar", icon = null, modifier = Modifier.fillMaxWidth()) {
                if (title.isNotBlank()) {
                    val base = initial ?: TaskEntity(title = "")
                    onSave(base.copy(title = title.trim(), notes = notes.trim(), starred = starred, remindAt = remindAt?.let(Dates::toMillis)))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (picking) DateTimePickerDialog(remindAt ?: now.plusHours(1).withMinute(0), onDismiss = { picking = false }) {
        remindAt = it; picking = false
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventEditorSheet(
    initial: EventEntity?,
    defaultDay: LocalDate,
    defaultReminder: Int,
    onDismiss: () -> Unit,
    onSave: (EventEntity) -> Unit,
    onDelete: ((EventEntity) -> Unit)? = null,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: EventType.MEETING) }
    var reminder by remember { mutableStateOf(initial?.reminderMinutes ?: defaultReminder) }
    var duration by remember { mutableStateOf(initial?.durationMinutes ?: 60) }
    var start by remember {
        mutableStateOf(
            initial?.startAt?.let(Dates::toDateTime) ?: run {
                val nextHour = LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
                LocalDateTime.of(defaultDay, if (defaultDay == LocalDate.now()) nextHour else LocalTime.of(9, 0))
            },
        )
    }
    var picking by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (initial == null) "Novo evento" else "Editar evento", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (initial != null && onDelete != null) IconButton(onClick = { onDelete(initial) }) { Icon(Icons.Default.Delete, "Excluir") }
            }
            OutlinedTextField(
                title, { title = it }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            OutlinedTextField(
                description, { description = it }, label = { Text("Pauta / detalhes") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            AssistChip(
                onClick = { picking = true },
                label = { Text("${Dates.longDay(start.toLocalDate())} às %02d:%02d".format(start.hour, start.minute)) },
                leadingIcon = { Icon(Icons.Default.CalendarMonth, null) },
            )
            Text("Tipo", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EventType.entries.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) }) }
            }
            Text("Duração", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60, 90, 120).forEach { m ->
                    FilterChip(selected = duration == m, onClick = { duration = m }, label = { Text(if (m < 60) "$m min" else "${m / 60}h${if (m % 60 > 0) "${m % 60}" else ""}") })
                }
            }
            Text("Me avisar", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-1 to "Não avisar", 0 to "Na hora", 5 to "5 min antes", 15 to "15 min antes", 30 to "30 min antes", 60 to "1 h antes", 1440 to "1 dia antes")
                    .forEach { (m, label) -> FilterChip(selected = reminder == m, onClick = { reminder = m }, label = { Text(label) }) }
            }
            Spacer(Modifier.height(4.dp))
            GradientButton("Salvar", icon = null, modifier = Modifier.fillMaxWidth()) {
                if (title.isNotBlank()) {
                    val base = initial ?: EventEntity(title = "", startAt = 0)
                    onSave(
                        base.copy(
                            title = title.trim(), description = description.trim(), type = type,
                            startAt = Dates.toMillis(start), durationMinutes = duration, reminderMinutes = reminder,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (picking) DateTimePickerDialog(start, onDismiss = { picking = false }) { start = it; picking = false }
}

/** Escolha de data e depois de hora, em dois passos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(initial: LocalDateTime, onDismiss: () -> Unit, onPicked: (LocalDateTime) -> Unit) {
    var date by remember { mutableStateOf<LocalDate?>(null) }
    if (date == null) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    date = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: initial.toLocalDate()
                }) { Text("Próximo") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        ) { DatePicker(state) }
    } else {
        val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Horário") },
            text = { TimePicker(state) },
            confirmButton = { TextButton(onClick = { onPicked(LocalDateTime.of(date!!, LocalTime.of(state.hour, state.minute))) }) { Text("OK") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        )
    }
}

@Composable
fun NoteDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anotar ideia") },
        text = {
            OutlinedTextField(
                text, { text = it }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
