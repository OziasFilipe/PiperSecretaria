package com.piper.secretaria.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piper.secretaria.ui.AppActions
import com.piper.secretaria.ui.MainViewModel
import com.piper.secretaria.ui.components.GradientButton
import com.piper.secretaria.ui.components.PiperTopBar
import com.piper.secretaria.ui.components.SoftCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, actions: AppActions) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++; vm.rescheduleAll() }

    val canNotify = remember(refresh) { vm.canNotify() }
    val canExact = remember(refresh) { vm.canScheduleExact() }
    val batteryOk = remember(refresh) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    var editName by remember { mutableStateOf(false) }
    var pickSummaryTime by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { PiperTopBar("Configurações", Icons.Default.Settings, onBack = actions.back) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            SoftCard {
                Column {
                    SettingRow(Icons.Default.Person, "Perfil", settings.userName.ifBlank { "Toque para dizer seu nome" }) { editName = true }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow(Icons.Default.GraphicEq, "Voz da Piper", if (settings.voiceReplies) "Respostas faladas ligadas" else "Só texto") {
                        vm.updateSettings { it.copy(voiceReplies = !it.voiceReplies) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Avisos confiáveis", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            SoftCard {
                Column {
                    SettingRow(
                        Icons.Default.Notifications, "Notificações",
                        if (canNotify) "Permitidas ✓" else "Bloqueadas — toque para permitir", warn = !canNotify,
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !canNotify) {
                            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow(
                        Icons.Default.Alarm, "Alarmes no horário exato",
                        if (canExact) "Permitidos ✓" else "Desligados — os avisos podem atrasar", warn = !canExact,
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canExact) {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow(
                        Icons.Default.BatteryChargingFull, "Economia de bateria",
                        if (batteryOk) "Sem restrição ✓" else "Toque e escolha \"Não otimizar\" para a Piper", warn = !batteryOk,
                    ) {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Como ser avisado", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            SoftCard {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Aviso padrão dos compromissos", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Na hora", 5 to "5 min", 15 to "15 min", 30 to "30 min", 60 to "1 hora").forEach { (m, label) ->
                            FilterChip(
                                selected = settings.defaultReminderMinutes == m,
                                onClick = { vm.updateSettings { it.copy(defaultReminderMinutes = m) } },
                                label = { Text(label) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WbSunny, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f).clickable { pickSummaryTime = true }) {
                            Text("Resumo do dia", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Todo dia às %02d:%02d (toque para mudar)".format(settings.dailySummaryHour, settings.dailySummaryMinute),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(settings.dailySummaryEnabled, { on -> vm.updateSettings { it.copy(dailySummaryEnabled = on) } })
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            GradientButton("Testar notificação", icon = Icons.Default.NotificationsActive, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                vm.testNotification()
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (editName) {
        var name by remember { mutableStateOf(settings.userName) }
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text("Como devo te chamar?") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { vm.updateSettings { it.copy(userName = name.trim()) }; editName = false }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("Cancelar") } },
        )
    }
    if (pickSummaryTime) {
        val state = rememberTimePickerState(settings.dailySummaryHour, settings.dailySummaryMinute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickSummaryTime = false },
            title = { Text("Horário do resumo") },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateSettings { it.copy(dailySummaryHour = state.hour, dailySummaryMinute = state.minute, dailySummaryEnabled = true) }
                    pickSummaryTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickSummaryTime = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, warn: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle, style = MaterialTheme.typography.bodyMedium,
                color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
