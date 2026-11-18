package ro.solarcasa.ui.planning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ro.solarcasa.R
import ro.solarcasa.data.DemoData
import ro.solarcasa.data.EnergyMath
import ro.solarcasa.data.EnergyMath.Orientation
import ro.solarcasa.data.EnergyMath.Plan
import ro.solarcasa.data.HouseRepository
import ro.solarcasa.navigation.Simulations
import ro.solarcasa.ui.PdfExport
import ro.solarcasa.ui.components.AiCard
import ro.solarcasa.ui.components.BarChart
import ro.solarcasa.ui.components.InfoCard
import ro.solarcasa.ui.components.KeyValue
import ro.solarcasa.ui.components.PrimaryButton
import ro.solarcasa.ui.components.ScreenScaffold
import ro.solarcasa.ui.components.SectionTitle
import ro.solarcasa.ui.components.Series
import ro.solarcasa.ui.fmt
import ro.solarcasa.ui.eur
import ro.solarcasa.ui.grouped
import ro.solarcasa.ui.kwh
import ro.solarcasa.ui.lei
import ro.solarcasa.ui.screenViewModel

class PlanningViewModel(private val repository: HouseRepository) : ViewModel() {
    data class Form(val orientation: Orientation = Orientation.South, val roofM2: Double = 40.0, val budgetEur: Int = 6000)

    data class UiState(
        val form: Form,
        val calculating: Boolean,
        val calculated: Boolean,
        val recommended: Plan,
        val budget: Plan,
        val roofLimited: Boolean,
        val saved: List<String>,
    )

    private val form = MutableStateFlow(Form())
    private val phase = MutableStateFlow(0) // 0 = not calculated, 1 = calculating, 2 = done

    val uiState = combine(form, phase, repository.house) { f, p, h -> toUi(f, p, h.savedPlans) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), toUi(form.value, 0, emptyList()))

    private fun toUi(f: Form, phase: Int, saved: List<String>): UiState {
        val consumption = DemoData.plannedConsumption
        val ideal = EnergyMath.recommendedPanels(consumption, f.orientation, roofM2 = 1000.0)
        val panels = EnergyMath.recommendedPanels(consumption, f.orientation, f.roofM2)
        val budgetPanels = minOf(EnergyMath.panelsForBudget(f.budgetEur), EnergyMath.maxPanels(f.roofM2))
        return UiState(
            form = f,
            calculating = phase == 1,
            calculated = phase == 2,
            recommended = EnergyMath.plan(consumption, panels, f.orientation),
            budget = EnergyMath.plan(consumption, budgetPanels, f.orientation),
            roofLimited = panels < ideal,
            saved = saved,
        )
    }

    fun edit(change: (Form) -> Form) = form.update(change)

    fun calculate() = viewModelScope.launch {
        phase.value = 1
        delay(2_000) // the assistant "thinks"; the user sees what it analyses
        phase.value = 2
    }

    fun save(plan: Plan) {
        val line = "${plan.kwp.fmt()} kWp – ${plan.panels} panouri, ${plan.costEur.eur()}, acoperire ${plan.coveragePercent}%"
        repository.perform("Varianta ${plan.kwp.fmt()} kWp a fost salvată", undo = { it.copy(savedPlans = it.savedPlans - line) }) {
            it.copy(savedPlans = it.savedPlans + line)
        }
    }

    fun pdfLines(plan: Plan, f: Form) = listOf(
        "Casă nouă, Florești (jud. Cluj): 140 m², 4 persoane, pompă de căldură",
        "Acoperiș: ${f.orientation.label}, înclinare 35°, suprafață utilă ${f.roofM2.fmt(0)} m²",
        "Consum anual estimat: ${DemoData.plannedConsumption.sum().kwh()}",
        "",
        "Sistem: ${plan.kwp.fmt()} kWp – ${plan.panels} panouri de 450 W, ≈ ${plan.areaM2.fmt(0)} m²",
        "Producție anuală estimată (PVGIS 5.3): ${plan.annualProduction.kwh()}",
        "Acoperire estimată (bilanț lunar): ${plan.coveragePercent}%",
        "Cost estimat: ${costRange(plan)}",
        "Economie estimată: ${plan.savingsLeiPerYear.lei()} pe an; amortizare ≈ ${plan.paybackYears.fmt()} ani",
        "",
        "Producție lunară estimată (kWh):",
    ) + DemoData.MONTHS.zip(plan.monthlyProduction) { m, v -> "  $m: ${v.toInt().grouped()}" } + listOf(
        "",
        "Ipoteze: preț energie ${EnergyMath.PRICE_LEI_PER_KWH.fmt(2)} lei/kWh, curs ${EnergyMath.EUR_RON.fmt(2)} lei/€,",
        "cost ${EnergyMath.COST_EUR_PER_KWP.toInt()} €/kWp, pierderi de sistem 14%.",
    )
}

fun costRange(p: Plan) = "${(p.costEur * 0.91 / 100).toInt() * 100} – ${(p.costEur * 1.07 / 100).toInt() * 100} €"

@Composable
fun PlanningScreen(onNavigate: (Any) -> Unit, onBack: (() -> Unit)?) {
    val vm = screenViewModel { PlanningViewModel(it) }
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editProfile by rememberSaveable { mutableStateOf(false) }
    var compare by rememberSaveable { mutableStateOf(false) }
    val f = ui.form

    ScreenScaffold("Planificare", onBack = onBack) {
        InfoCard(title = "Profilul casei", icon = R.drawable.ic_house) {
            Text("Casă nouă în Florești · 140 m² · 4 persoane · încălzire cu pompă de căldură")
            Text("Acoperiș: ${f.orientation.label}, 35° · suprafață utilă ${f.roofM2.fmt(0)} m²")
            TextButton({ editProfile = !editProfile }) { Text(if (editProfile) "Gata" else "Modifică profilul") }
            if (editProfile) {
                Text("Orientarea acoperișului", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Orientation.entries.forEach { o -> FilterChip(f.orientation == o, { vm.edit { it.copy(orientation = o) } }, label = { Text(o.label) }) }
                }
                Text("Suprafață utilă a acoperișului", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(20.0, 26.0, 40.0).forEach { m -> FilterChip(f.roofM2 == m, { vm.edit { it.copy(roofM2 = m) } }, label = { Text("${m.toInt()} m²") }) }
                }
            }
        }
        FilledTonalButton({ onNavigate(Simulations) }) { Text("Simulări: adaugă un consumator mare") }

        if (!ui.calculated) {
            PrimaryButton("Calculează sistemul necesar", vm::calculate, enabled = !ui.calculating)
            if (ui.calculating) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Asistentul Solar analizează consumul estimat și datele meteo pentru Florești…")
            }
            ui.saved.forEach { Text("Salvat: $it") }
            return@ScreenScaffold
        }

        val p = ui.recommended
        if (f.orientation == Orientation.Unknown) {
            // Sarcina 1, E1
            InfoCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                Text("Rezultat aproximativ – lipsește orientarea acoperișului.")
                TextButton({ editProfile = true }) { Text("Completează acum") }
            }
        }
        if (ui.roofLimited) {
            // Sarcina 1, E2
            InfoCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                Text("Acoperișul permite maximum ${p.panels} panouri (${p.kwp.fmt(2)} kWp). Îți propunem varianta maximă și o baterie mai mare.")
            }
        }
        InfoCard(title = "Sistemul recomandat", icon = R.drawable.ic_solar_power) {
            Text("Consum anual estimat: ${DemoData.plannedConsumption.sum().kwh()}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "${p.kwp.fmt()} kWp – ${p.panels} panouri de 450 W, ≈ ${p.areaM2.fmt(0)} m² de acoperiș. Acoperire estimată: ${p.coveragePercent}% din consum.",
                style = MaterialTheme.typography.titleMedium,
            )
            KeyValue("Cost estimat", costRange(p))
            KeyValue("Economie pe an", p.savingsLeiPerYear.lei())
            KeyValue("Amortizare", "≈ ${p.paybackYears.fmt()} ani")
            MonthlyChart(p)
        }
        AiCard(
            "Calculul folosește consumul estimat al casei și producția medie din zonă.",
            title = "De unde vin cifrele",
            why = "• Pompa de căldură consumă ≈ 45% din total, mai ales iarna.\n" +
                "• Producția medie în Florești: 1.199 kWh pe kWp pe an (PVGIS 5.3, Comisia Europeană).\n" +
                "• Pierderi estimate: 14% (cabluri, invertor, temperatură).\n" +
                "• Lunile noiembrie – februarie rămân cu deficit; acolo ajută rețeaua sau o baterie.",
        )

        SectionTitle("Buget maxim: ${f.budgetEur.eur()}")
        Slider(
            f.budgetEur.toFloat(), { v -> vm.edit { it.copy(budgetEur = (v / 500).toInt() * 500) } },
            valueRange = 2000f..8000f, steps = 11,
            modifier = Modifier.semantics { stateDescription = "${f.budgetEur} euro" },
        )
        Text(
            "Cu acest buget: ${ui.budget.kwp.fmt()} kWp – ${ui.budget.panels} panouri, acoperire ${ui.budget.coveragePercent}%.",
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedButton({ compare = !compare }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Compară variantele") }
        if (compare) CompareTable(p, ui.budget)

        PrimaryButton("Salvează și exportă PDF", {
            vm.save(p)
            PdfExport.share(context, "SolarCasa_plan_${p.panels}_panouri.pdf", "SolarCasa – plan sistem fotovoltaic", vm.pdfLines(p, f))
        })
        ui.saved.forEach { Text("Salvat: $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun MonthlyChart(p: Plan) {
    val deficit = DemoData.plannedConsumption.indices.filter { DemoData.plannedConsumption[it] > p.monthlyProduction[it] }.toSet()
    BarChart(
        DemoData.MONTHS.map { it.take(1).uppercase() },
        listOf(
            Series("Producție", p.monthlyProduction, MaterialTheme.colorScheme.secondary),
            Series("Consum", DemoData.plannedConsumption, MaterialTheme.colorScheme.primary),
        ),
        description = "Producția depășește consumul din martie până în octombrie. Lunile cu deficit sunt evidențiate.",
        highlight = deficit,
    )
    Text("Fundalul roșu marchează lunile cu deficit.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun CompareTable(a: Plan, b: Plan) {
    InfoCard(title = "Comparație") {
        Row { Text("", Modifier.weight(1.2f)); Text("${a.kwp.fmt()} kWp", Modifier.weight(1f)); Text("${b.kwp.fmt()} kWp", Modifier.weight(1f)) }
        listOf(
            "Panouri" to { p: Plan -> "${p.panels}" },
            "Cost" to { p: Plan -> p.costEur.eur() },
            "Acoperire" to { p: Plan -> "${p.coveragePercent}%" },
            "Economie/an" to { p: Plan -> p.savingsLeiPerYear.lei() },
            "Amortizare" to { p: Plan -> "${p.paybackYears.fmt()} ani" },
        ).forEach { (label, value) ->
            Row(Modifier.semantics(mergeDescendants = true) {}) {
                Text(label, Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium)
                Text(value(a), Modifier.weight(1f))
                Text(value(b), Modifier.weight(1f))
            }
        }
    }
}

// ---- Sarcina 10: simulations --------------------------------------------------------------------

class SimulationsViewModel(private val repository: HouseRepository) : ViewModel() {
    val saved = repository.uiState(viewModelScope) { it.savedSimulations }

    fun simulate(item: DemoData.CatalogItem) =
        EnergyMath.simulate(DemoData.CONSUMPTION_2026, item.shares.map { it * item.annualKwh })

    fun save(item: DemoData.CatalogItem, kw: Double, sim: EnergyMath.Simulation) {
        val line = "${item.name} ${kw.fmt()} kW: ${sim.consumptionAfter.kwh()}/an, acoperire ${sim.coverageAfter}%"
        repository.perform("Simularea a fost salvată", undo = { it.copy(savedSimulations = it.savedSimulations - line) }) {
            it.copy(savedSimulations = it.savedSimulations + line)
        }
    }
}

@Composable
fun SimulationsScreen(onBack: () -> Unit) {
    val vm = screenViewModel { SimulationsViewModel(it) }
    val saved by vm.saved.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var power by rememberSaveable { mutableStateOf("") }
    var ran by rememberSaveable { mutableStateOf(false) }
    var extraKwp by rememberSaveable { mutableStateOf(3) }
    val item = DemoData.catalog.firstOrNull { it.name == selected }

    ScreenScaffold("Simulări", onBack = onBack) {
        Text("Ce consumator mare vrei să adaugi?", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DemoData.catalog.forEach { c ->
                FilterChip(selected == c.name, { selected = c.name; power = c.defaultKw.fmt(); ran = false }, label = { Text(c.name) })
            }
        }
        if (item == null) {
            saved.forEach { Text("Salvat: $it") }
            return@ScreenScaffold
        }
        Text(item.note, style = MaterialTheme.typography.bodyMedium)
        val kw = power.replace(',', '.').toDoubleOrNull()
        val unusual = kw == null || kw !in item.typicalKw
        OutlinedTextField(
            power, { power = it; ran = false }, Modifier.fillMaxWidth(),
            label = { Text("Putere (kW)") },
            isError = unusual,
            supportingText = {
                // Sarcina 10, E2
                if (unusual) Text("Valoare neobișnuită pentru o locuință (tipic ${item.typicalKw.start.fmt(0)}–${item.typicalKw.endInclusive.fmt(0)} kW). Verifică.")
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
        )
        PrimaryButton("Rulează simularea", { ran = true }, enabled = !unusual)
        if (!ran || kw == null) return@ScreenScaffold

        val sim = vm.simulate(item)
        Text("Bazat pe istoricul real al casei (2 ani) și pe datele meteo.", style = MaterialTheme.typography.bodySmall)
        InfoCard(title = "Rezultat", icon = R.drawable.ic_science) {
            val increase = ((sim.consumptionAfter / sim.consumptionBefore - 1) * 100).toInt()
            KeyValue("Consum anual", "${sim.consumptionBefore.kwh()} → ${sim.consumptionAfter.kwh()} (+$increase%)")
            KeyValue("Acoperire solară", "${sim.coverageBefore}% → ${sim.coverageAfter}%")
            KeyValue("Cost suplimentar din rețea", "≈ ${sim.extraGridLeiPerYear.lei()}/an")
            if (item == DemoData.catalog.first()) {
                KeyValue("Economie la gaz", "≈ ${(DemoData.GAS_REPLACED_KWH * EnergyMath.GAS_LEI_PER_KWH).toInt().lei()}/an")
            }
            BarChart(
                DemoData.MONTHS.map { it.take(1).uppercase() },
                listOf(
                    Series("Producție", EnergyMath.PVGIS_6KWP_CLUJ, MaterialTheme.colorScheme.secondary),
                    Series("Consum după", sim.monthlyAfter, MaterialTheme.colorScheme.primary),
                ),
                description = "După adăugare, consumul depășește mult producția în lunile de iarnă.",
                highlight = sim.deficitMonths,
            )
            Text("Fundalul roșu marchează lunile cu deficit mare.", style = MaterialTheme.typography.bodySmall)
        }
        val coverage = EnergyMath.coverageWithExtension(sim.monthlyAfter, extraKwp.toDouble())
        val cost = extraKwp * EnergyMath.COST_EUR_PER_KWP
        AiCard(
            title = "Ce extindere ar ajuta?",
            text = "+$extraKwp kWp (${(extraKwp / EnergyMath.PANEL_KW).toInt()} panouri) ridică acoperirea la $coverage%; " +
                "cost ≈ ${cost.toInt().eur()}.",
            why = "Panourile noi produc mai ales vara; iarna deficitul rămâne. De aceea acoperirea crește mai puțin decât puterea.",
        ) {
            listOf(2, 3, 4).forEach { k -> FilterChip(extraKwp == k, { extraKwp = k }, label = { Text("+$k kWp") }) }
        }
        PrimaryButton("Salvează simularea", { vm.save(item, kw, sim) })
        saved.forEach { Text("Salvat: $it", style = MaterialTheme.typography.bodyMedium) }
    }
}
