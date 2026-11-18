package ro.solarcasa.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import ro.solarcasa.MainActivity
import ro.solarcasa.R
import ro.solarcasa.data.AlertStatus
import ro.solarcasa.data.HouseRepository
import ro.solarcasa.data.UserMessage
import ro.solarcasa.navigation.AlertDetail
import ro.solarcasa.navigation.Alerts
import ro.solarcasa.navigation.Appliances
import ro.solarcasa.navigation.BatterySettings
import ro.solarcasa.navigation.ConsumerDetail
import ro.solarcasa.navigation.DemoScenarios
import ro.solarcasa.navigation.Garage
import ro.solarcasa.navigation.History
import ro.solarcasa.navigation.Navigator
import ro.solarcasa.navigation.ProfileDetail
import ro.solarcasa.navigation.ProsumerReport
import ro.solarcasa.navigation.RuleBuilder
import ro.solarcasa.navigation.ServiceRequest
import ro.solarcasa.navigation.Simulations
import ro.solarcasa.navigation.Tab
import ro.solarcasa.ui.alerts.AlertDetailScreen
import ro.solarcasa.ui.alerts.AlertsScreen
import ro.solarcasa.ui.alerts.ServiceRequestScreen
import ro.solarcasa.ui.assistant.AssistantSheet
import ro.solarcasa.ui.automations.AutomationsScreen
import ro.solarcasa.ui.automations.ProfileDetailScreen
import ro.solarcasa.ui.automations.RuleBuilderScreen
import ro.solarcasa.ui.components.LocalTopBarActions
import ro.solarcasa.ui.emergency.EmergencyScreen
import ro.solarcasa.ui.energy.BatterySettingsScreen
import ro.solarcasa.ui.energy.ConsumerDetailScreen
import ro.solarcasa.ui.energy.EnergyScreen
import ro.solarcasa.ui.garage.GarageScreen
import ro.solarcasa.ui.home.AppliancesScreen
import ro.solarcasa.ui.home.HomeScreen
import ro.solarcasa.ui.more.DemoScreen
import ro.solarcasa.ui.more.HistoryScreen
import ro.solarcasa.ui.more.MoreScreen
import ro.solarcasa.ui.planning.PlanningScreen
import ro.solarcasa.ui.planning.SimulationsScreen
import ro.solarcasa.ui.prosumer.ProsumerReportScreen
import ro.solarcasa.ui.prosumer.ProsumerScreen
import ro.solarcasa.ui.reports.ReportsScreen
import ro.solarcasa.ui.theme.EmergencyColors

class AppViewModel(private val repository: HouseRepository) : ViewModel() {
    data class UiState(val newAlerts: Int, val message: UserMessage?, val emergency: Boolean)

    val uiState = repository.uiState(viewModelScope) { h ->
        UiState(h.alerts.count { it.status == AlertStatus.New }, h.message, h.gridDown && !h.emergencyMinimized)
    }

    fun messageShown(id: Long) = repository.messageShown(id)
    fun undo(actionId: Long) = repository.undo(actionId)
    fun openEmergency() = repository.set { it.copy(emergencyMinimized = false) }
}

@Composable
fun SolarCasaApp(openRequest: String?, onOpenHandled: () -> Unit) {
    val navigator: Navigator = viewModel()
    val app = screenViewModel { AppViewModel(it) }
    val ui by app.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var assistantOpen by rememberSaveable { mutableStateOf(false) }
    val wide = currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)

    // User messages are state: show, then report them as shown (02_architecture_recommendations).
    LaunchedEffect(ui.message) {
        val message = ui.message ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message.text,
            actionLabel = if (message.undoId != null) "Anulează" else null,
            duration = if (message.undoId != null) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) message.undoId?.let(app::undo)
        app.messageShown(message.id)
    }

    LaunchedEffect(openRequest) {
        when {
            openRequest == null -> return@LaunchedEffect
            openRequest == MainActivity.OPEN_EMERGENCY -> app.openEmergency()
            openRequest.startsWith(MainActivity.OPEN_ALERT) -> {
                navigator.selectTab(Tab.Home)
                navigator.navigate(AlertDetail(openRequest.removePrefix(MainActivity.OPEN_ALERT)))
            }
        }
        onOpenHandled()
    }

    if (ui.emergency) {
        // Emergency mode replaces the whole app: no navigation bar, so no accidental exit (Etapa 6).
        MaterialTheme(colorScheme = EmergencyColors) { EmergencyScreen() }
        return
    }

    CompositionLocalProvider(LocalTopBarActions provides { AlertsBell(ui.newAlerts) { navigator.navigate(Alerts) } }) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                Tab.entries.filter { wide || !it.wideOnly }.forEach { tab ->
                    item(
                        selected = tab == navigator.currentTab || (!wide && tab == Tab.More && navigator.currentTab.wideOnly),
                        onClick = { navigator.selectTab(tab) },
                        icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                NavDisplay(
                    backStack = navigator.backStack,
                    onBack = { navigator.back() },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = { key -> NavEntry(key) { Destination(it, navigator, wide) } },
                )
                ExtendedFloatingActionButton(
                    onClick = { assistantOpen = true },
                    icon = { Icon(painterResource(R.drawable.ic_auto_awesome), contentDescription = null) },
                    text = { Text("Întreabă asistentul") },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
            }
        }
    }

    if (assistantOpen) {
        AssistantSheet(
            onDismiss = { assistantOpen = false },
            onEditRule = { draft ->
                assistantOpen = false
                navigator.selectTab(Tab.Automations)
                navigator.navigate(RuleBuilder(draft))
            },
        )
    }
}

@Composable
private fun AlertsBell(count: Int, onClick: () -> Unit) {
    IconButton(onClick) {
        BadgedBox(badge = { if (count > 0) Badge { Text("$count") } }) {
            Icon(painterResource(R.drawable.ic_notifications), contentDescription = if (count > 0) "Alerte, $count noi" else "Alerte")
        }
    }
}

/** Maps a back stack key to its screen. */
@Composable
private fun Destination(key: Any, nav: Navigator, wide: Boolean) {
    val back = nav::back
    val open: (Any) -> Unit = nav::navigate
    val backIfHidden = if (wide) null else back // Planning and Prosumer are in "Mai mult" on phones
    when (key) {
        Tab.Home -> HomeScreen(open)
        Tab.Energy -> EnergyScreen(open)
        Tab.Automations -> AutomationsScreen(open)
        Tab.Reports -> ReportsScreen()
        Tab.Planning -> PlanningScreen(open, backIfHidden)
        Tab.Prosumer -> ProsumerScreen(open, backIfHidden)
        Tab.More -> MoreScreen(open, nav::selectTab)
        Appliances -> AppliancesScreen(back)
        BatterySettings -> BatterySettingsScreen(back)
        is ConsumerDetail -> ConsumerDetailScreen(key.id, back)
        is RuleBuilder -> RuleBuilderScreen(key.draft, back)
        is ProfileDetail -> ProfileDetailScreen(key.id, back)
        Alerts -> AlertsScreen(open, back)
        is AlertDetail -> AlertDetailScreen(key.id, open, back)
        is ServiceRequest -> ServiceRequestScreen(key.alertId, back)
        Garage -> GarageScreen(back)
        Simulations -> SimulationsScreen(back)
        ProsumerReport -> ProsumerReportScreen(back)
        History -> HistoryScreen(back)
        DemoScenarios -> DemoScreen(back)
        else -> error("Ecran necunoscut: $key")
    }
}
