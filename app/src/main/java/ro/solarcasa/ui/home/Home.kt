package ro.solarcasa.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ro.solarcasa.R
import ro.solarcasa.data.Appliance
import ro.solarcasa.data.ApplianceKind
import ro.solarcasa.data.Balance
import ro.solarcasa.data.EnergyMath
import ro.solarcasa.data.House
import ro.solarcasa.data.HouseRepository
import ro.solarcasa.data.ProfileId
import ro.solarcasa.data.RunState
import ro.solarcasa.data.SolarAssistant
import ro.solarcasa.navigation.Appliances
import ro.solarcasa.navigation.BatterySettings
import ro.solarcasa.navigation.ProfileDetail
import ro.solarcasa.ui.components.AiCard
import ro.solarcasa.ui.components.InfoCard
import ro.solarcasa.ui.components.ScreenScaffold
import ro.solarcasa.ui.components.SectionTitle
import ro.solarcasa.ui.components.Tag
import ro.solarcasa.ui.fmt
import ro.solarcasa.ui.kw
import ro.solarcasa.ui.screenViewModel
import ro.solarcasa.ui.theme.statusColors

class HomeViewModel(private val repository: HouseRepository) : ViewModel() {
    data class UiState(
        val house: House,
        val flows: EnergyMath.Flows,
        val surplus: Double,
        val tip: SolarAssistant.Tip,
        val autonomy: String,
    )

    val uiState = repository.uiState(viewModelScope) { h ->
        val flows = EnergyMath.flows(h)
        UiState(
            house = h,
            flows = flows,
            surplus = EnergyMath.surplusKw(h),
            tip = SolarAssistant.homeTip(h),
            autonomy = SolarAssistant.hours(EnergyMath.autonomyHours(h.batteryPercent, h.reservePercent, flows.house)),
        )
    }

    /** Starts a smart appliance; a manual appliance only gets a reminder (Sarcina 2, E2). */
    fun start(a: Appliance) {
        if (!a.smart) {
            repository.show("${a.name} nu este conectat. Pornește-l tu acum – te anunțăm când surplusul se termină.")
            return
        }
        val solar = EnergyMath.surplusKw(repository.house.value) >= a.kw
        val text = if (solar) "${a.name} a pornit – 100% energie solară" else "${a.name} a pornit – parțial din rețea"
        repository.perform(text, undo = { h -> h.withState(a.id, RunState.Off) }) { h ->
            h.withState(a.id, if (solar) RunState.RunningSolar else RunState.RunningGrid)
        }
    }

    /** Delegates the start to the system, with a latest time set by the user (Sarcina 2, scenario B). */
    fun startWhenFree(a: Appliance) = repository.perform(
        "${a.name} va porni automat când este surplus, cel târziu la 15:00. Te anunțăm.",
        undo = { h -> h.withState(a.id, RunState.Off) },
    ) { h -> h.withState(a.id, RunState.Scheduled, "cel târziu 15:00") }

    fun stop(a: Appliance) = repository.perform("${a.name} a fost oprit", undo = { h -> h.withState(a.id, a.state, a.scheduledAt) }) { h ->
        h.withState(a.id, RunState.Off)
    }

    fun stopVacation() = repository.perform("Profilul Normal este activ", undo = { it.copy(profile = ProfileId.Vacation) }) {
        it.copy(profile = ProfileId.Normal, vacationUntil = null)
    }

    fun openEmergency() = repository.set { it.copy(emergencyMinimized = false) }

    private fun House.withState(id: String, state: RunState, at: String? = null) =
        copy(appliances = appliances.map { if (it.id == id) it.copy(state = state, scheduledAt = at) else it })
}

@Composable
fun HomeScreen(onNavigate: (Any) -> Unit) {
    val vm = screenViewModel { HomeViewModel(it) }
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val h = ui.house
    ScreenScaffold("Acasă") {
        if (h.gridDown) {
            InfoCard(title = "Pană de curent", icon = R.drawable.ic_emergency_home, container = MaterialTheme.colorScheme.errorContainer) {
                Text("Casa funcționează pe baterie (${h.batteryPercent}%).")
                Button(vm::openEmergency) { Text("Deschide modul urgență") }
            }
        }
        if (h.profile == ProfileId.Vacation) {
            InfoCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_flight_takeoff), contentDescription = null)
                    Text("Mod Vacanță activ până pe ${h.vacationUntil}", Modifier.weight(1f).padding8())
                    TextButton(vm::stopVacation) { Text("Oprește") }
                }
            }
        }
        BalanceCard(ui)
        AiCard(
            title = ui.tip.title,
            text = ui.tip.text,
            why = ui.tip.why,
            confidence = if (ui.flows.balance == Balance.Surplus) 86 else 71,
        ) {
            val washer = h.appliances.first { it.id == "washer" }
            if (ui.surplus >= washer.kw && washer.state == RunState.Off) {
                Button({ vm.start(washer) }) { Text("Pornește mașina de spălat") }
            }
            FilledTonalButton({ onNavigate(Appliances) }) { Text("Ce pot porni?") }
        }
        h.appliances.filter { it.state != RunState.Off }.forEach { a ->
            InfoCard(title = a.name, icon = a.kind.icon()) {
                Text(stateText(a), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton({ vm.stop(a) }) { Text(if (a.state == RunState.Scheduled) "Anulează programarea" else "Oprește") }
            }
        }
        InfoCard(title = "Bateria", icon = R.drawable.ic_battery_5_bar) {
            Text("${h.batteryPercent}% · la consumul actual ajunge ${ui.autonomy}", style = MaterialTheme.typography.bodyLarge)
            Text("Rezervă minimă: ${h.reservePercent}%", style = MaterialTheme.typography.bodyMedium)
            TextButton({ onNavigate(BatterySettings) }) { Text("Setări baterie") }
        }
        SectionTitle("Profiluri rapide")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileId.entries.forEach { p ->
                AssistChip(
                    onClick = { onNavigate(ProfileDetail(p)) },
                    label = { Text(if (p == h.profile) "${p.title} (activ)" else p.title) },
                    leadingIcon = { Icon(painterResource(p.icon()), contentDescription = null, Modifier.size(18.dp)) },
                )
            }
        }
    }
}

/** "Balanța acum": the answer in words first (largest text), the kW values smaller below (Etapa 6, problem 1). */
@Composable
private fun BalanceCard(ui: HomeViewModel.UiState) {
    val f = ui.flows
    val colors = statusColors(f.balance)
    val (icon, headline) = when (f.balance) {
        Balance.Surplus -> R.drawable.ic_trending_up to "Produci mai mult decât consumi"
        Balance.Even -> R.drawable.ic_check_circle to "Producția acoperă consumul"
        Balance.Deficit -> R.drawable.ic_trending_down to "Consumi mai mult decât produci"
    }
    val stale = ui.house.dataAgeMin > 5
    InfoCard(Modifier.alpha(if (stale) 0.6f else 1f), container = colors.container) {
        Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.content, modifier = Modifier.size(40.dp))
            Text(headline, style = MaterialTheme.typography.headlineSmall, color = colors.content, modifier = Modifier.padding8())
        }
        if (stale) Tag("Actualizat acum ${ui.house.dataAgeMin} min", MaterialTheme.colorScheme.outline)
        Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FlowLine(R.drawable.ic_solar_power, "Panouri", f.solar.kw())
            FlowLine(R.drawable.ic_house, "Casa consumă", f.house.kw())
            FlowLine(
                R.drawable.ic_battery_charging_full, "Baterie ${ui.house.batteryPercent}%",
                when {
                    f.battery > 0.05 -> "se încarcă, ${f.battery.kw()}"
                    f.battery < -0.05 -> "se descarcă, ${(-f.battery).kw()}"
                    else -> "în repaus"
                },
            )
            FlowLine(
                R.drawable.ic_power, "Rețea",
                when {
                    ui.house.gridDown -> "indisponibilă"
                    f.grid > 0.05 -> "vinzi ${f.grid.kw()}"
                    f.grid < -0.05 -> "cumperi ${(-f.grid).kw()}"
                    else -> "0 kW"
                },
            )
        }
        Text("Economisit azi: ${4.9.fmt(2)} lei", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FlowLine(@DrawableRes icon: Int, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---- "Ce pot porni?" ----------------------------------------------------------------------------

@Composable
fun AppliancesScreen(onBack: () -> Unit) {
    val vm = screenViewModel { HomeViewModel(it) }
    val ui by vm.uiState.collectAsStateWithLifecycle()
    ScreenScaffold("Ce pot porni?", onBack = onBack) {
        val surplus = ui.surplus
        Text(
            if (surplus > 0.3) "Surplus acum: ${surplus.kw()}, până la ≈ ${ui.house.surplusUntil ?: "–"} (prognoză)."
            else "Acum nu ai surplus. Cel mai bun moment azi: 12:30 – 14:30 (gratuit).",
            style = MaterialTheme.typography.bodyLarge,
        )
        ui.house.appliances.forEach { a -> ApplianceCard(a, surplus, vm) }
    }
}

@Composable
private fun ApplianceCard(a: Appliance, surplus: Double, vm: HomeViewModel) {
    val fits = surplus >= a.kw
    InfoCard(title = a.name, icon = a.kind.icon()) {
        Text("≈ ${a.kw.kw()}, ${SolarAssistant.hours(a.runHours).removePrefix("≈ ")}", style = MaterialTheme.typography.bodyMedium)
        if (a.state != RunState.Off) {
            Text(stateText(a), style = MaterialTheme.typography.bodyLarge)
            OutlinedButton({ vm.stop(a) }) { Text(if (a.state == RunState.Scheduled) "Anulează programarea" else "Oprește") }
        } else {
            FitDetails(a, surplus, fits, vm)
        }
    }
}

@Composable
private fun FitDetails(a: Appliance, surplus: Double, fits: Boolean, vm: HomeViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                painterResource(if (fits) R.drawable.ic_check_circle else R.drawable.ic_warning),
                contentDescription = null,
                tint = statusColors(if (fits) Balance.Surplus else Balance.Even).content,
            )
            Text(SolarAssistant.fit(a, surplus), style = MaterialTheme.typography.bodyLarge)
        }
        if (!a.smart) Tag("Aparat manual – nu poate fi pornit din aplicație", MaterialTheme.colorScheme.outline)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                !a.smart -> FilledTonalButton({ vm.start(a) }) { Text("Amintește-mi") }
                fits -> Button({ vm.start(a) }) { Text("Pornește acum") }
                else -> {
                    Button({ vm.startWhenFree(a) }) { Text("Pornește la momentul potrivit") }
                    OutlinedButton({ vm.start(a) }) { Text("Pornește acum") }
                }
            }
        }
    }
}

private fun stateText(a: Appliance) = when (a.state) {
    RunState.RunningSolar -> "În funcțiune – energie solară. Economie estimată: ${(a.kw * a.runHours * EnergyMath.PRICE_LEI_PER_KWH).fmt(2)} lei"
    RunState.RunningGrid -> "În funcțiune – parțial din rețea"
    RunState.Scheduled -> "Programat: pornește automat la surplus, ${a.scheduledAt}"
    RunState.Off -> "Oprit"
}

@DrawableRes
fun ApplianceKind.icon() = when (this) {
    ApplianceKind.Washer -> R.drawable.ic_local_laundry_service
    ApplianceKind.Dryer -> R.drawable.ic_dry
    ApplianceKind.Dishwasher -> R.drawable.ic_dishwasher_gen
    ApplianceKind.Oven -> R.drawable.ic_oven_gen
}

@DrawableRes
fun ProfileId.icon() = when (this) {
    ProfileId.Normal -> R.drawable.ic_house
    ProfileId.Night -> R.drawable.ic_bedtime
    ProfileId.Vacation -> R.drawable.ic_flight_takeoff
    ProfileId.Eco -> R.drawable.ic_eco
}

private fun Modifier.padding8() = this.then(Modifier.padding(horizontal = 8.dp))
