package ro.solarcasa.ui.energy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ro.solarcasa.R
import ro.solarcasa.data.DemoData
import ro.solarcasa.data.EnergyMath
import ro.solarcasa.data.HouseRepository
import ro.solarcasa.data.SolarAssistant
import ro.solarcasa.navigation.BatterySettings
import ro.solarcasa.navigation.ConsumerDetail
import ro.solarcasa.ui.components.AiCard
import ro.solarcasa.ui.components.BarChart
import ro.solarcasa.ui.components.Heatmap
import ro.solarcasa.ui.components.InfoCard
import ro.solarcasa.ui.components.PrimaryButton
import ro.solarcasa.ui.components.ScreenScaffold
import ro.solarcasa.ui.components.Series
import ro.solarcasa.ui.components.Tag
import ro.solarcasa.ui.fmt
import ro.solarcasa.ui.kw
import ro.solarcasa.ui.kwh
import ro.solarcasa.ui.screenViewModel
import ro.solarcasa.ui.signedPercent

class EnergyViewModel(private val repository: HouseRepository) : ViewModel() {
    val uiState = repository.uiState(viewModelScope) { it }

    /** Hours the essential consumers can run on the reserve alone (shown while the user moves the slider). */
    fun essentialHoursAt(reserve: Int): String {
        val essentials = repository.house.value.circuits.filter { it.essential }.sumOf { it.kw }
        return SolarAssistant.hours(EnergyMath.autonomyHours(reserve, 0, essentials))
    }

    fun saveReserve(percent: Int, seasonal: Boolean) {
        val old = repository.house.value
        val text = if (seasonal) "Rezerva minimă: $percent% (iarnă). Revine la 15% pe 1 aprilie." else "Rezerva minimă: $percent%."
        repository.perform(text, undo = { it.copy(reservePercent = old.reservePercent, seasonalReserve = old.seasonalReserve) }) {
            it.copy(reservePercent = percent, seasonalReserve = seasonal)
        }
    }

    fun applyBoilerSchedule() = repository.perform(
        "Boilerul încălzește apa doar între 11:00 – 15:00 și 18:00 – 19:00",
        undo = { it.copy(boilerScheduled = false) },
    ) { it.copy(boilerScheduled = true) }

    /** "Nu e corect": the assistant learns from the feedback and stops this proposal (HAX G15). */
    fun markWrong() = repository.perform(
        "Mulțumim. Asistentul nu mai propune această explicație.",
        undo = { it.copy(boilerFeedbackWrong = false) },
    ) { it.copy(boilerFeedbackWrong = true) }
}

@Composable
fun EnergyScreen(onNavigate: (Any) -> Unit) {
    val vm = screenViewModel { EnergyViewModel(it) }
    val h by vm.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    ScreenScaffold("Energie") {
        PrimaryTabRow(selectedTabIndex = tab) {
            listOf("Bateria", "Consumatori").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
        }
        if (tab == 0) {
            val flows = EnergyMath.flows(h)
            InfoCard(title = "Bateria", icon = R.drawable.ic_battery_5_bar) {
                BatteryGauge(h.batteryPercent, h.reservePercent)
                Text(
                    when {
                        flows.battery > 0.05 -> "Se încarcă cu ${flows.battery.kw()}"
                        flows.battery < -0.05 -> "Se descarcă cu ${(-flows.battery).kw()}"
                        else -> "În repaus"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text("Rezervă minimă: ${h.reservePercent}%" + if (h.seasonalReserve) " (se schimbă automat pe sezon)" else "")
                Button({ onNavigate(BatterySettings) }) { Text("Setări baterie") }
            }
            InfoCard(title = "Producție și consum în 2026", icon = R.drawable.ic_bar_chart) {
                BarChart(
                    DemoData.MONTHS.map { it.take(1).uppercase() },
                    listOf(
                        Series("Producție (PVGIS)", EnergyMath.PVGIS_6KWP_CLUJ, MaterialTheme.colorScheme.secondary),
                        Series("Consum", DemoData.CONSUMPTION_2026, MaterialTheme.colorScheme.primary),
                    ),
                    description = "Producția depășește consumul din martie până în octombrie. Iarna consumul este mai mare.",
                )
                Text("Producție anuală: ${EnergyMath.PVGIS_6KWP_CLUJ.sum().kwh()} · Consum: ${DemoData.CONSUMPTION_2026.sum().kwh()}")
            }
        } else {
            Text("Noiembrie 2026, față de octombrie", style = MaterialTheme.typography.titleMedium)
            val max = DemoData.consumers.maxOf { it.kwh }.toFloat()
            DemoData.consumers.forEach { c ->
                InfoCard(Modifier.clickable { onNavigate(ConsumerDetail(c.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Text(c.kwh.kwh(), style = MaterialTheme.typography.titleSmall)
                    }
                    LinearProgressIndicator(progress = { c.kwh / max }, Modifier.fillMaxWidth(), drawStopIndicator = {})
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${c.changePercent.signedPercent()} față de luna trecută", style = MaterialTheme.typography.bodyMedium)
                        if (c.changePercent >= 30) Tag("! Neobișnuit", MaterialTheme.colorScheme.error)
                        if (!c.measured) Tag("Estimat", MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
fun ConsumerDetailScreen(id: String, onBack: () -> Unit) {
    val vm = screenViewModel { EnergyViewModel(it) }
    val c = DemoData.consumers.first { it.id == id }
    ScreenScaffold(c.name, onBack = onBack) {
        Text(
            "${c.kwh.kwh()} în noiembrie (≈ ${(c.kwh * EnergyMath.PRICE_LEI_PER_KWH).fmt(0)} lei), ${c.changePercent.signedPercent()} față de octombrie",
            style = MaterialTheme.typography.titleMedium,
        )
        if (!c.measured) {
            Tag("Estimat – aparatul nu are senzor separat", MaterialTheme.colorScheme.outline)
            Text("Asistentul estimează consumul din tiparul de consum al casei.")
        }
        if (id == "boiler") BoilerFinding(vm)
    }
}

@Composable
private fun BoilerFinding(vm: EnergyViewModel) {
    val h by vm.uiState.collectAsStateWithLifecycle()
    var showProposal by rememberSaveable { mutableStateOf(false) }
    var window by rememberSaveable { mutableStateOf(SolarAssistant.BOILER_PROPOSAL) }
    var editing by rememberSaveable { mutableStateOf(false) }

    // A sentence with the conclusion comes before the heat map (Etapa 6, sarcina 12).
    Text("Boilerul consumă în fiecare noapte între 01:00 și 05:00, din 28 octombrie.", style = MaterialTheme.typography.titleLarge)
    Heatmap(
        DemoData.boilerHeatmap,
        List(14) { "${5 + it}.11" },
        description = "Harta consumului pe zile și ore: în ultimele 12 nopți boilerul consumă între orele 1 și 5.",
    )
    when {
        h.boilerScheduled -> Tag("Program aplicat: $window", MaterialTheme.colorScheme.primary)
        h.boilerFeedbackWrong -> Text("Ai marcat explicația ca greșită. Asistentul nu o mai propune.")
        else -> AiCard(
            SolarAssistant.BOILER_FINDING,
            confidence = 79,
            why = "Consumul de noapte a început după pana de curent din 28.10. Apa caldă se folosește dimineața și seara.",
        ) {
            FilledTonalButton({ showProposal = true }) { Text("Programează boilerul") }
            TextButton(vm::markWrong) { Text("Nu e corect") }
        }
    }
    if (showProposal && !h.boilerScheduled && !h.boilerFeedbackWrong) {
        AiCard(title = "Propunere", text = window) {
            Button(vm::applyBoilerSchedule) { Text("Aplică") }
            OutlinedButton({ editing = !editing }) { Text("Modifică") }
            TextButton({ showProposal = false }) { Text("Ignoră") }
        }
        if (editing) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(SolarAssistant.BOILER_PROPOSAL, "Încălzire doar între 10:00 și 16:00 (din soare).", "Încălzire doar între 12:00 și 14:00.")
                    .forEach { w -> FilterChip(window == w, { window = w }, label = { Text(w) }) }
            }
        }
    }
}

@Composable
fun BatterySettingsScreen(onBack: () -> Unit) {
    val vm = screenViewModel { EnergyViewModel(it) }
    val h by vm.uiState.collectAsStateWithLifecycle()
    var reserve by rememberSaveable { mutableIntStateOf(h.reservePercent) }
    var seasonal by rememberSaveable { mutableStateOf(h.seasonalReserve) }
    var suggestionHandled by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold("Setări baterie", onBack = onBack) {
        Text("Rezerva curentă: ${h.reservePercent}%", style = MaterialTheme.typography.titleMedium)
        BatteryGauge(h.batteryPercent, reserve)
        Text("Rezervă minimă: $reserve%", style = MaterialTheme.typography.headlineSmall)
        Slider(
            value = reserve.toFloat(),
            onValueChange = { reserve = it.toInt() },
            valueRange = EnergyMath.MIN_RESERVE_PERCENT.toFloat()..50f,
            steps = 8,
            modifier = Modifier.semantics { stateDescription = "$reserve la sută" },
        )
        if (reserve == EnergyMath.MIN_RESERVE_PERCENT) Text("Minimul permis de producător", style = MaterialTheme.typography.bodyMedium)
        // The consequence of each value, in hours and kWh (Etapa 5, sarcina 11).
        Text(
            "Bateria nu va coborî sub $reserve%. În caz de pană de curent ai mereu ${vm.essentialHoursAt(reserve)} pentru consumatorii esențiali. " +
                "Din rețea vei cumpăra în plus ≈ ${EnergyMath.extraWinterGridKwhPerDay(reserve).fmt()} kWh pe zi, iarna.",
            style = MaterialTheme.typography.bodyLarge,
        )
        if (h.batteryPercent < reserve) {
            Text("Bateria are acum ${h.batteryPercent}%. Se va încărca până la $reserve% din soare sau, dacă nu e soare, din rețea în orele cu tarif redus.")
        }
        if (!suggestionHandled) {
            // The suggestion states the new value and lets the user keep the old one (Etapa 6, sarcina 11).
            AiCard(
                title = "Sugestie sezonieră",
                text = "Iarna recomandăm 25%, vara 15%. Vrei să schimb automat valoarea pe 1 aprilie și pe 1 noiembrie?",
                why = "Iarna penele de curent sunt mai dese și producția este mică. Vara bateria se încarcă zilnic din soare.",
            ) {
                Button({ reserve = 25; seasonal = true; suggestionHandled = true }) { Text("Aplică: 25% acum, 15% din aprilie") }
                OutlinedButton({ suggestionHandled = true }) { Text("Păstrez $reserve%") }
            }
        }
        if (seasonal) Tag("Schimbare automată: 25% iarna, 15% vara", MaterialTheme.colorScheme.primary)
        PrimaryButton("Salvează", { vm.saveReserve(reserve, seasonal); onBack() })
    }
}

/** Battery drawing: filled part = charge, hatched part = reserve that is never used. */
@Composable
fun BatteryGauge(percent: Int, reservePercent: Int, modifier: Modifier = Modifier) {
    val fill = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.onSurface
    val reserveColor = MaterialTheme.colorScheme.error
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            Modifier.weight(1f).height(56.dp).semantics {
                contentDescription = "Baterie încărcată $percent%, rezervă $reservePercent%"
            },
        ) {
            val body = Size(size.width - 12f, size.height)
            drawRoundRect(fill.copy(alpha = 0.85f), size = Size(body.width * percent / 100, body.height), cornerRadius = CornerRadius(12f))
            val reserveWidth = body.width * reservePercent / 100
            clipRect(0f, 0f, reserveWidth, body.height) {
                var x = -body.height
                while (x < reserveWidth) {
                    drawLine(reserveColor, Offset(x, body.height), Offset(x + body.height, 0f), 4f)
                    x += 14f
                }
            }
            drawRoundRect(outline, size = body, cornerRadius = CornerRadius(12f), style = Stroke(4f))
            drawRect(outline, Offset(body.width, body.height * 0.3f), Size(12f, body.height * 0.4f))
        }
        Icon(painterResource(R.drawable.ic_battery_charging_full), contentDescription = null)
        Text(" $percent%", style = MaterialTheme.typography.titleLarge)
    }
}
