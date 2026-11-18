package ro.solarcasa.ui.more

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ro.solarcasa.BuildConfig
import ro.solarcasa.R
import ro.solarcasa.data.EnergyMath
import ro.solarcasa.data.HouseRepository
import ro.solarcasa.data.RunState
import ro.solarcasa.navigation.Appliances
import ro.solarcasa.navigation.BatterySettings
import ro.solarcasa.navigation.DemoScenarios
import ro.solarcasa.navigation.Garage
import ro.solarcasa.navigation.History
import ro.solarcasa.navigation.Tab
import ro.solarcasa.ui.components.InfoCard
import ro.solarcasa.ui.components.ScreenScaffold
import ro.solarcasa.ui.components.SectionTitle
import ro.solarcasa.ui.fmt
import ro.solarcasa.ui.screenViewModel

/** "Mai mult" (Etapa 4 and 5): the sections that do not fit in the bottom bar. Each row shows its current state. */
class MoreViewModel(repository: HouseRepository) : ViewModel() {
    data class UiState(
        val car: String,
        val running: Int,
        val reservePercent: Int,
        val prosumerReport: String?,
        val actions: Int,
        val scenario: String,
    )

    val uiState = repository.uiState(viewModelScope) { h ->
        UiState(
            car = if (h.car.connected) "Mașina: ${h.car.percent}% · ${h.car.mode.label}" else "Mașina nu este conectată",
            running = h.appliances.count { it.state == RunState.RunningSolar || it.state == RunState.RunningGrid },
            reservePercent = h.reservePercent,
            prosumerReport = h.prosumerReport,
            actions = h.history.size,
            scenario = h.scenario.title,
        )
    }
}

/** On phones, Garaj, Planificare, Prosumator, Istoric acțiuni and Scenarii demo open only from here. */
@Composable
fun MoreScreen(onNavigate: (Any) -> Unit, onSelectTab: (Tab) -> Unit) {
    val vm = screenViewModel { MoreViewModel(it) }
    val ui by vm.uiState.collectAsStateWithLifecycle()
    var about by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold("Mai mult") {
        Section("Dispozitive") {
            MoreItem(R.drawable.ic_garage_home, "Garaj", ui.car) { onNavigate(Garage) }
            MoreItem(
                R.drawable.ic_devices, "Electrocasnice",
                if (ui.running > 0) "Aparate pornite: ${ui.running}" else "Ce pot porni acum?",
            ) { onNavigate(Appliances) }
        }
        Section("Planificare și prosumator") {
            MoreItem(Tab.Planning.icon, Tab.Planning.label, "Dimensionarea sistemului și simulări") { onSelectTab(Tab.Planning) }
            MoreItem(
                Tab.Prosumer.icon, Tab.Prosumer.label,
                ui.prosumerReport?.let { "Ultimul raport: $it" } ?: "Raport pentru furnizor",
            ) { onSelectTab(Tab.Prosumer) }
        }
        // Etapa 6: users can look for the battery reserve in the general settings, so the link is also here.
        Section("Setări") {
            MoreItem(R.drawable.ic_battery_5_bar, "Setări baterie", "Rezervă minimă: ${ui.reservePercent}%") { onNavigate(BatterySettings) }
        }
        Section("Aplicație") {
            MoreItem(
                R.drawable.ic_history, "Istoric acțiuni",
                if (ui.actions == 0) "Nicio acțiune încă" else "Acțiuni în această sesiune: ${ui.actions}",
            ) { onNavigate(History) }
            MoreItem(R.drawable.ic_play_arrow, "Scenarii demo", "Acum: ${ui.scenario}") { onNavigate(DemoScenarios) }
            MoreItem(R.drawable.ic_info, "Despre aplicație", "Versiunea ${BuildConfig.VERSION_NAME}", opens = false) { about = !about }
        }
        if (about) About()
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionTitle(title)
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        content = content,
    )
}

/** One row: 72 dp high (two lines), the whole row is the touch target. [opens] shows the arrow of a new screen. */
@Composable
private fun MoreItem(@DrawableRes icon: Int, title: String, subtitle: String, opens: Boolean = true, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        trailingContent = if (opens) {
            { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun About() {
    InfoCard(title = "SolarCasa ${BuildConfig.VERSION_NAME}", icon = R.drawable.ic_solar_power) {
        Text("Prototip de nivel înalt (Etapa 7) pentru proiectul PIU 2026, Echipa 6.")
        Text("Casa demo: Cluj-Napoca, panouri de 6 kWp, baterie de 10 kWh.")
        Text(
            "Surse: producția din PVGIS 5.3 (Comisia Europeană), prețurile din ofertele pentru 2026, " +
                "cursul BNR de ${EnergyMath.EUR_RON.fmt(2)} lei/€.",
        )
        Text("Asistentul Solar rulează local, fără internet. Nicio acțiune nu pornește fără confirmarea ta.")
    }
}

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    ScreenScaffold("Istoric acțiuni", onBack = onBack) { Text("În lucru") }
}

@Composable
fun DemoScreen(onBack: () -> Unit) {
    ScreenScaffold("Scenarii demo", onBack = onBack) { Text("În lucru") }
}
