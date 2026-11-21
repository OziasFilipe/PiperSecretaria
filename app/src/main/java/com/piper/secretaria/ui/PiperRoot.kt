package com.piper.secretaria.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.piper.secretaria.data.EventEntity
import com.piper.secretaria.data.TaskEntity
import androidx.compose.material3.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.piper.secretaria.update.UpdateState
import com.piper.secretaria.ui.components.DownloadProgress
import com.piper.secretaria.ui.components.UpdateDialog
import com.piper.secretaria.ui.components.EventEditorSheet
import com.piper.secretaria.ui.components.FluteMascot
import com.piper.secretaria.ui.components.IconBadge
import com.piper.secretaria.ui.components.NoteDialog
import com.piper.secretaria.ui.components.TaskEditorSheet
import com.piper.secretaria.ui.screens.AgendaScreen
import com.piper.secretaria.ui.screens.ChatScreen
import com.piper.secretaria.ui.screens.HomeScreen
import com.piper.secretaria.ui.screens.NotesScreen
import com.piper.secretaria.ui.screens.NotificationsScreen
import com.piper.secretaria.ui.screens.SettingsScreen
import com.piper.secretaria.ui.screens.TasksScreen
import com.piper.secretaria.ui.theme.PiperColors
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Pedido vindo de uma notificação (ex.: "Ver pauta" abre o evento). */
data class DeepLink(val route: String, val itemId: Long, val nonce: Long)

object Routes {
    const val HOME = "home"
    const val AGENDA = "agenda"
    const val TASKS = "tasks"
    const val CHAT = "chat"
    const val NOTIFICATIONS = "notifications"
    const val NOTES = "notes"
    const val SETTINGS = "settings"
    const val MEMORY = "memory"
    const val LOGIN = "login"
}

/** Ações compartilhadas entre as telas (abrir editores, navegar). */
class AppActions(
    val navigate: (String) -> Unit,
    val back: () -> Unit,
    val openDrawer: () -> Unit,
    val newTask: () -> Unit,
    val editTask: (TaskEntity) -> Unit,
    val newEvent: (LocalDate) -> Unit,
    val editEvent: (EventEntity) -> Unit,
    val newNote: () -> Unit,
    val listen: () -> Unit,
    val quickActions: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PiperRoot(vm: MainViewModel, deepLink: DeepLink?, onDeepLinkHandled: () -> Unit) {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()

    var taskEditor by remember { mutableStateOf<TaskEntity?>(null) }
    var taskEditorOpen by remember { mutableStateOf(false) }
    var eventEditor by remember { mutableStateOf<EventEntity?>(null) }
    var eventEditorDay by remember { mutableStateOf(LocalDate.now()) }
    var eventEditorOpen by remember { mutableStateOf(false) }
    var noteOpen by remember { mutableStateOf(false) }
    var quickOpen by remember { mutableStateOf(false) }

    // Pede permissão de notificação logo de cara (Android 13+): sem ela a Piper não consegue avisar.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.rescheduleAll() }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !vm.canNotify()) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val actions = remember(nav) {
        AppActions(
            navigate = { route -> nav.navigateTop(route) },
            back = { if (!nav.popBackStack()) nav.navigateTop(Routes.HOME) },
            openDrawer = { scope.launch { drawer.open() } },
            newTask = { taskEditor = null; taskEditorOpen = true },
            editTask = { taskEditor = it; taskEditorOpen = true },
            newEvent = { day -> eventEditor = null; eventEditorDay = day; eventEditorOpen = true },
            editEvent = { eventEditor = it; eventEditorOpen = true },
            newNote = { noteOpen = true },
            listen = { nav.navigate("${Routes.CHAT}?listen=true") { launchSingleTop = true } },
            quickActions = { quickOpen = true },
        )
    }

    // Abre o que veio da notificação
    LaunchedEffect(deepLink) {
        val link = deepLink ?: return@LaunchedEffect
        when (link.route) {
            Routes.AGENDA -> {
                nav.navigateTop(Routes.AGENDA)
                vm.findEvent(link.itemId)?.let { actions.editEvent(it) }
            }
            Routes.TASKS -> {
                nav.navigateTop(Routes.TASKS)
                vm.findTask(link.itemId)?.let { actions.editTask(it) }
            }
            else -> nav.navigateTop(Routes.HOME)
        }
        onDeepLinkHandled()
    }

    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route?.substringBefore("?") ?: Routes.HOME

    // Primeira vez: tela de login (ou "usar sem conta"). Depois a sessão fica salva.
    if (!settings.loggedIn && !settings.offlineMode) {
        com.piper.secretaria.ui.screens.LoginScreen(vm, onBack = null)
        return
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            PiperDrawer(current) { route ->
                scope.launch { drawer.close() }
                nav.navigateTop(route)
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { PiperBottomBar(current, actions) },
        ) { padding ->
            NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
                composable(Routes.HOME) { HomeScreen(vm, actions) }
                composable(Routes.AGENDA) { AgendaScreen(vm, actions) }
                composable(Routes.TASKS) { TasksScreen(vm, actions) }
                composable(
                    "${Routes.CHAT}?listen={listen}",
                    arguments = listOf(navArgument("listen") { type = NavType.BoolType; defaultValue = false }),
                ) { entry -> ChatScreen(vm, actions, autoListen = entry.arguments?.getBoolean("listen") == true) }
                composable(Routes.NOTIFICATIONS) { NotificationsScreen(vm, actions) }
                composable(Routes.NOTES) { NotesScreen(vm, actions) }
                composable(Routes.SETTINGS) { SettingsScreen(vm, actions) }
                composable(Routes.MEMORY) { com.piper.secretaria.ui.screens.MemoryScreen(vm, actions) }
                composable(Routes.LOGIN) { com.piper.secretaria.ui.screens.LoginScreen(vm, onBack = actions.back) }
            }
        }
    }

    // Atualização: verifica toda vez que o app volta para a frente; o pop-up reaparece até atualizar.
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        vm.checkUpdateOnOpen()
        vm.syncCloud()
        if (settings.deviceCalendarsEnabled) vm.syncCalendars(silent = true)
    }
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val updatePopup by vm.updatePopup.collectAsStateWithLifecycle()
    val pendingInfo = when (val s = updateState) {
        is UpdateState.Available -> s.info
        is UpdateState.ReadyToInstall -> s.info
        else -> null
    }
    if (updatePopup && pendingInfo != null) {
        UpdateDialog(pendingInfo, vm.updater.currentVersion, onUpdate = { vm.startUpdate() }, onLater = { vm.postponeUpdate() })
    }
    (updateState as? UpdateState.Downloading)?.let { d ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Atualizando para ${d.info.versionName}") },
            text = { DownloadProgress(d.progress) },
            confirmButton = {},
        )
    }

    if (taskEditorOpen) TaskEditorSheet(
        initial = taskEditor,
        onDismiss = { taskEditorOpen = false },
        onSave = { vm.saveTask(it); taskEditorOpen = false },
        onDelete = { vm.deleteTask(it); taskEditorOpen = false },
    )
    if (eventEditorOpen) EventEditorSheet(
        initial = eventEditor,
        defaultDay = eventEditorDay,
        defaultReminder = settings.defaultReminderMinutes,
        onDismiss = { eventEditorOpen = false },
        onSave = { vm.saveEvent(it); eventEditorOpen = false },
        onDelete = { vm.deleteEvent(it); eventEditorOpen = false },
    )
    if (noteOpen) NoteDialog(onDismiss = { noteOpen = false }) { vm.addNote(it); noteOpen = false }
    if (quickOpen) QuickActionsSheet(
        onDismiss = { quickOpen = false },
        onAction = { a ->
            quickOpen = false
            when (a) {
                QuickAction.EVENT -> actions.newEvent(LocalDate.now())
                QuickAction.TASK -> actions.newTask()
                QuickAction.REMINDER -> actions.newTask()
                QuickAction.NOTE -> actions.newNote()
                QuickAction.SUMMARY -> {
                    nav.navigateTop(Routes.CHAT)
                    vm.send("o que tenho hoje")
                }
                QuickAction.WEEK -> nav.navigateTop(Routes.AGENDA)
            }
        },
    )
}

private fun NavHostController.navigateTop(route: String) = navigate(route) {
    popUpTo(Routes.HOME) { saveState = route != Routes.HOME }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun PiperBottomBar(current: String, actions: AppActions) {
    Box {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            BottomItem("Início", Icons.Filled.Home, Icons.Outlined.Home, current == Routes.HOME) { actions.navigate(Routes.HOME) }
            BottomItem("Agenda", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, current == Routes.AGENDA) { actions.navigate(Routes.AGENDA) }
            NavigationBarItem(selected = false, onClick = actions.listen, icon = { Spacer(Modifier.size(48.dp)) }, enabled = false)
            BottomItem("Piper", Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Filled.Chat, current == Routes.CHAT) { actions.navigate(Routes.CHAT) }
            BottomItem("Mais", Icons.Filled.Apps, Icons.Filled.Apps, false, actions.quickActions)
        }
        // botão central de microfone
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-18).dp)
                .size(64.dp)
                .shadow(10.dp, CircleShape, spotColor = PiperColors.Purple)
                .clip(CircleShape)
                .background(PiperColors.buttonGradient)
                .clickable(onClick = actions.listen),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Mic, "Falar com a Piper", tint = Color.White, modifier = Modifier.size(30.dp)) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BottomItem(
    label: String, selectedIcon: ImageVector, icon: ImageVector, selected: Boolean, onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(if (selected) selectedIcon else icon, label) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    )
}

@Composable
private fun PiperDrawer(current: String, onSelect: (String) -> Unit) {
    ModalDrawerSheet(drawerContainerColor = PiperColors.Night, drawerContentColor = Color.White) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            FluteMascot(Modifier.size(64.dp), avatar = true)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Piper", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text("Secretária Pessoal", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC4B5FD))
            }
        }
        val items = listOf(
            Triple(Routes.HOME, "Início", Icons.Outlined.Home),
            Triple(Routes.AGENDA, "Agenda", Icons.Outlined.CalendarMonth),
            Triple(Routes.TASKS, "Tarefas", Icons.Outlined.CheckCircle),
            Triple(Routes.NOTIFICATIONS, "Lembretes", Icons.Outlined.Notifications),
            Triple(Routes.NOTES, "Anotações", Icons.Outlined.EditNote),
            Triple(Routes.CHAT, "Conversar com a Piper", Icons.AutoMirrored.Filled.Chat),
            Triple(Routes.SETTINGS, "Configurações", Icons.Outlined.Settings),
        )
        items.forEach { (route, label, icon) ->
            NavigationDrawerItem(
                label = { Text(label) },
                icon = { Icon(icon, null) },
                selected = current == route,
                onClick = { onSelect(route) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = PiperColors.Purple,
                    unselectedContainerColor = Color.Transparent,
                    selectedTextColor = Color.White,
                    unselectedTextColor = Color(0xFFE9E3FF),
                    selectedIconColor = Color.White,
                    unselectedIconColor = Color(0xFFC4B5FD),
                ),
            )
        }
    }
}

enum class QuickAction { EVENT, TASK, REMINDER, NOTE, SUMMARY, WEEK }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuickActionsSheet(onDismiss: () -> Unit, onAction: (QuickAction) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("Ações Rápidas", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(16.dp))
            val items = listOf(
                Triple(QuickAction.EVENT, "Agendar compromisso", Icons.Default.CalendarMonth to PiperColors.Purple),
                Triple(QuickAction.TASK, "Criar tarefa", Icons.Default.CheckCircle to PiperColors.Green),
                Triple(QuickAction.REMINDER, "Definir lembrete", Icons.Default.NotificationsActive to PiperColors.Orange),
                Triple(QuickAction.NOTE, "Anotar ideia", Icons.Default.EditNote to PiperColors.Blue),
                Triple(QuickAction.SUMMARY, "Resumo do dia", Icons.Default.BarChart to PiperColors.Purple),
                Triple(QuickAction.WEEK, "Planejar semana", Icons.AutoMirrored.Filled.EventNote to PiperColors.Pink),
            )
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 2,
            ) {
                items.forEach { (action, label, iconColor) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onAction(action) }
                            .padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        IconBadge(iconColor.first, iconColor.second)
                        Spacer(Modifier.height(8.dp))
                        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
