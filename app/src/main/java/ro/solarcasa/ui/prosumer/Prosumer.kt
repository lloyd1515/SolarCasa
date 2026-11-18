package ro.solarcasa.ui.prosumer

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import ro.solarcasa.R
import ro.solarcasa.data.DemoData
import ro.solarcasa.data.EnergyMath
import ro.solarcasa.data.EnergyMath.Reading
import ro.solarcasa.data.EnergyMath.ReportPeriod
import ro.solarcasa.data.EnergyMath.SupplierReport
import ro.solarcasa.data.HouseRepository
import ro.solarcasa.navigation.ProsumerReport
import ro.solarcasa.ui.PdfExport
import ro.solarcasa.ui.components.BarChart
import ro.solarcasa.ui.components.InfoCard
import ro.solarcasa.ui.components.KeyValue
import ro.solarcasa.ui.components.PrimaryButton
import ro.solarcasa.ui.components.ScreenScaffold
import ro.solarcasa.ui.components.Series
import ro.solarcasa.ui.components.Tag
import ro.solarcasa.ui.grouped
import ro.solarcasa.ui.kwh
import ro.solarcasa.ui.screenViewModel
import kotlin.math.abs

/** Sarcina 7 (Etapa 5, Scenariul A): the prosumer summary and the 3-step report for the supplier. */
class ProsumerViewModel(private val repository: HouseRepository) : ViewModel() {
    data class Form(
        val step: Int = 0, // 0 = period, 1 = contract, 2 = verification, 3 = ready
        val period: ReportPeriod = ReportPeriod.Semester,
        val supplier: String = DemoData.SUPPLIER,
        val pod: String = DemoData.POD,
        val serial: String = DemoData.METER_SERIAL,
        val reading: String = "",
    )

    data class UiState(
        val form: Form,
        val supplierMissing: Boolean,
        val podMissing: Boolean,
        val podInvalid: Boolean,
        val serialMismatch: Boolean,
        val readingNotNumber: Boolean,
        val report: SupplierReport,
        val lastReport: String?,
    ) {
        val contractValid = !supplierMissing && !podMissing && !podInvalid && !serialMismatch
        val readingValid = !readingNotNumber && (report.reading == Reading.Match || report.reading == Reading.Manual)
        val manual = report.reading == Reading.Manual
    }

    private val form = MutableStateFlow(Form())

    val uiState = combine(form, repository.house) { f, h -> toUi(f, h.prosumerReport) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), toUi(form.value, repository.house.value.prosumerReport))

    private fun toUi(f: Form, lastReport: String?): UiState {
        val digits = f.reading.filterNot { it == '.' || it.isWhitespace() } // "8.439" is a Romanian thousands separator
        val reading = digits.toIntOrNull()
        return UiState(
            form = f,
            supplierMissing = f.supplier.isBlank(),
            podMissing = f.pod.isBlank(),
            podInvalid = f.pod.isNotBlank() && !EnergyMath.isValidPod(f.pod),
            serialMismatch = !EnergyMath.sameSerial(f.serial, DemoData.METER_SERIAL),
            readingNotNumber = digits.isNotEmpty() && reading == null,
            report = EnergyMath.supplierReport(f.period, reading),
            lastReport = lastReport,
        )
    }

    fun edit(change: (Form) -> Form) = form.update(change)
    fun next() = form.update { it.copy(step = it.step + 1) }
    fun back() = form.update { it.copy(step = it.step - 1) }

    /** The report is ready. The action goes to "Istoric acțiuni"; a sent document cannot be undone. */
    fun generate() {
        val ui = toUi(form.value, null)
        val line = "${ui.form.period.dates}: ${ui.report.injected.kwh()} injectați" + if (ui.manual) ", index citit manual" else ""
        repository.perform("Raport pentru furnizor: ${ui.form.period.label.lowercase()}", message = "Raportul este gata") {
            it.copy(prosumerReport = line)
        }
        next()
    }

    fun fileName() = "SolarCasa_raport_furnizor_${form.value.period.name.lowercase()}_2026.pdf"

    fun pdfLines(): List<String> {
        val ui = toUi(form.value, null)
        val f = ui.form
        val r = ui.report
        return listOf(
            "Perioada: ${f.period.dates}",
            "Furnizor: ${f.supplier.trim()}",
            "Cod loc de consum (POD): ${f.pod.filterNot(Char::isWhitespace).uppercase()}",
            "Seria contorului: ${f.serial.trim()}",
            "Instalație: 6 kWp, baterie 10 kWh, Cluj-Napoca",
            "",
            "Luna\tInjectat în rețea (kWh)\tConsumat din rețea (kWh)",
        ) + f.period.months.map { "${DemoData.MONTH_NAMES[it]}\t${DemoData.injected[it].grouped()}\t${DemoData.fromGrid[it].grouped()}" } +
            (if (ui.manual) listOf("Corecție după indexul citit\t${"%+d".format(r.difference)}") else emptyList()) +
            listOf(
                "Total\t${r.injected.grouped()}\t${r.fromGrid.grouped()}",
                "",
                "Index energie injectată (registrul 2.8.0)",
                "Începutul perioadei\t${r.startIndex.kwh()}",
                "31.12.2026\t${r.endIndex.kwh()}\t${if (ui.manual) "citit manual de pe contor" else "confirmat de titular"}",
                "",
                "Datele lunare provin de la invertor. Raport generat cu aplicația SolarCasa.",
            )
    }

    fun emailBody(): String {
        val ui = toUi(form.value, null)
        return "Bună ziua,\n\nVă trimit raportul de producție pentru locul de consum ${ui.form.pod.trim()}, " +
            "perioada ${ui.form.period.dates}.\nEnergie injectată: ${ui.report.injected.kwh()}. " +
            "Index la 31.12.2026: ${ui.report.endIndex.kwh()}.\n\nCu stimă,\nRadu"
    }
}

private const val REPORT_TITLE = "SolarCasa – raport de producție pentru furnizor"

@Composable
fun ProsumerScreen(onNavigate: (Any) -> Unit, onBack: (() -> Unit)?) {
    val vm = screenViewModel { ProsumerViewModel(it) }
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val year = EnergyMath.supplierReport(ReportPeriod.Year)
    val pending = EnergyMath.supplierReport(ReportPeriod.Semester)
    val balance = pending.injected - pending.fromGrid
    val regularized = (0..5).sumOf { DemoData.injected[it] - DemoData.fromGrid[it] }

    ScreenScaffold("Prosumator", onBack = onBack) {
        InfoCard(title = "Anul 2026", icon = R.drawable.ic_swap_horiz) {
            KeyValue("Energie injectată în rețea", year.injected.kwh())
            KeyValue("Energie consumată din rețea", year.fromGrid.kwh())
            KeyValue("Sold estimat din 01.07.2026", if (balance >= 0) "credit ${balance.kwh()}" else "datorie ${(-balance).kwh()}")
            KeyValue("Ultima regularizare", "${DemoData.LAST_REGULARIZATION} (credit ${regularized.kwh()})")
            Text("Soldul este o estimare din datele invertorului. Furnizorul face regularizarea finală.", style = MaterialTheme.typography.bodySmall)
        }
        BarChart(
            DemoData.MONTHS.map { it.take(1).uppercase() },
            listOf(
                Series("Injectat", DemoData.injected.map(Int::toDouble), MaterialTheme.colorScheme.secondary),
                Series("Din rețea", DemoData.fromGrid.map(Int::toDouble), MaterialTheme.colorScheme.primary),
            ),
            description = "Din martie până în septembrie casa injectează mai mult decât consumă din rețea. Iarna consumă mai mult din rețea.",
        )
        InfoCard(title = "Contract de prosumator", icon = R.drawable.ic_description) {
            KeyValue("Furnizor", DemoData.SUPPLIER)
            KeyValue("Cod loc de consum", DemoData.POD)
            KeyValue("Seria contorului", DemoData.METER_SERIAL)
        }
        ui.lastReport?.let { Text("Ultimul raport generat: $it", style = MaterialTheme.typography.bodyMedium) }
        PrimaryButton("Generează raport pentru furnizor", { onNavigate(ProsumerReport) })
    }
}

@Composable
fun ProsumerReportScreen(onBack: () -> Unit) {
    val vm = screenViewModel { ProsumerViewModel(it) }
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val share = { email: Boolean ->
        PdfExport.share(
            context, vm.fileName(), REPORT_TITLE, vm.pdfLines(),
            emailTo = DemoData.SUPPLIER_EMAIL.takeIf { email }, body = vm.emailBody().takeIf { email },
        )
    }

    ScreenScaffold("Raport pentru furnizor", onBack = onBack) {
        StepIndicator(ui.form.step)
        when (ui.form.step) {
            0 -> PeriodStep(ui, vm::edit, vm::next)
            1 -> ContractStep(ui, vm::edit, vm::next, vm::back)
            2 -> VerifyStep(ui, vm::edit, vm::generate, vm::back)
            else -> ReadyStep(ui, { share(true) }, { share(false) }, vm::back)
        }
    }
}

private val STEPS = listOf("Perioadă", "Date contract", "Verificare")

@Composable
private fun StepIndicator(step: Int) {
    val status = if (step < STEPS.size) "Pasul ${step + 1} din ${STEPS.size}: ${STEPS[step]}" else "Toți pașii sunt gata"
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = status }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            STEPS.forEachIndexed { i, name ->
                Text(
                    if (i < step) "✓ $name" else "${i + 1} $name",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (i == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (i == step) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
        LinearProgressIndicator(
            progress = { (step + 1f).coerceAtMost(3f) / STEPS.size },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun PeriodStep(ui: ProsumerViewModel.UiState, onEdit: ((ProsumerViewModel.Form) -> ProsumerViewModel.Form) -> Unit, onNext: () -> Unit) {
    Text("Pentru ce perioadă faci raportul?", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReportPeriod.entries.forEach { p ->
            FilterChip(ui.form.period == p, { onEdit { it.copy(period = p) } }, label = { Text(p.label) })
        }
    }
    Text(ui.form.period.dates, style = MaterialTheme.typography.bodyLarge)
    Totals(ui.report)
    PrimaryButton("Continuă", onNext)
}

@Composable
private fun ContractStep(
    ui: ProsumerViewModel.UiState,
    onEdit: ((ProsumerViewModel.Form) -> ProsumerViewModel.Form) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val f = ui.form
    Text("Verifică datele din contract cu factura de energie.", style = MaterialTheme.typography.bodyLarge)
    OutlinedTextField(
        f.supplier, { v -> onEdit { it.copy(supplier = v) } }, Modifier.fillMaxWidth(),
        label = { Text("Furnizor") },
        isError = ui.supplierMissing,
        supportingText = { if (ui.supplierMissing) Text("Completează numele furnizorului.") },
        singleLine = true,
    )
    OutlinedTextField(
        f.pod, { v -> onEdit { it.copy(pod = v) } }, Modifier.fillMaxWidth(),
        label = { Text("Cod loc de consum (POD)") },
        isError = ui.podMissing || ui.podInvalid,
        supportingText = {
            when {
                ui.podMissing -> Text("Lipsește codul locului de consum. Îl găsești pe factură, la „Date loc de consum”.")
                ui.podInvalid -> Text("Codul are 15 caractere: RO, 3 cifre, litera E și 9 cifre.")
            }
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        singleLine = true,
    )
    if (ui.podMissing || ui.podInvalid) InvoiceHint() // Sarcina 7, E2
    OutlinedTextField(
        f.serial, { v -> onEdit { it.copy(serial = v) } }, Modifier.fillMaxWidth(),
        label = { Text("Seria contorului") },
        isError = ui.serialMismatch,
        supportingText = {
            Text(
                if (ui.serialMismatch) "Seria diferă de cea din contract (${DemoData.METER_SERIAL}). Verifică eticheta contorului."
                else "Corespunde cu seria din contract.",
            )
        },
        singleLine = true,
    )
    PrimaryButton("Continuă", onNext, enabled = ui.contractValid)
    BackButton(onBack)
}

@Composable
private fun VerifyStep(
    ui: ProsumerViewModel.UiState,
    onEdit: ((ProsumerViewModel.Form) -> ProsumerViewModel.Form) -> Unit,
    onGenerate: () -> Unit,
    onBack: () -> Unit,
) {
    val r = ui.report
    KeyValue("Index în aplicație la 31.12.2026", DemoData.METER_INDEX_INJECTED.kwh())
    Text(
        "Citește pe contor registrul 2.8.0 (energie injectată) și compară-l cu indexul din aplicație.",
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
        ui.form.reading, { v -> onEdit { it.copy(reading = v) } }, Modifier.fillMaxWidth(),
        label = { Text("Index citit de pe contor (kWh)") },
        isError = !ui.readingValid,
        supportingText = {
            Text(
                when {
                    ui.readingNotNumber -> "Introdu numai cifre."
                    r.reading == Reading.BelowStart -> "Indexul nu poate fi mai mic decât cel de la începutul perioadei (${r.startIndex.kwh()})."
                    r.reading == Reading.TooHigh -> "Valoare prea mare pentru producția panourilor. Verifică cifrele de pe contor."
                    ui.manual -> "Diferență de ${abs(r.difference).kwh()} față de aplicație."
                    ui.form.reading.isNotBlank() ->
                        "Diferență de ${abs(r.difference).kwh()}, în toleranța de ${EnergyMath.INDEX_TOLERANCE_KWH} kWh. Indexurile corespund."
                    else -> "Completează numai dacă indexul de pe contor este altul."
                },
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
    if (ui.manual) {
        // Sarcina 7, E1
        InfoCard(container = MaterialTheme.colorScheme.secondaryContainer) {
            Text("Diferență de ${abs(r.difference).kwh()}. Raportul folosește indexul citit de pe contor, marcat „citit manual”.")
        }
    }
    Totals(r)
    PrimaryButton(if (ui.manual) "Generează raportul cu indexul citit" else "Indexurile corespund", onGenerate, enabled = ui.readingValid)
    BackButton(onBack)
}

@Composable
private fun ReadyStep(ui: ProsumerViewModel.UiState, onEmail: () -> Unit, onSave: () -> Unit, onChange: () -> Unit) {
    val r = ui.report
    InfoCard(title = "Raportul este gata", icon = R.drawable.ic_check_circle) {
        KeyValue("Perioada", ui.form.period.dates)
        KeyValue("Energie injectată", r.injected.kwh())
        KeyValue("Energie consumată din rețea", r.fromGrid.kwh())
        KeyValue("Index la 31.12.2026", r.endIndex.kwh())
        if (ui.manual) Tag("Index citit manual", MaterialTheme.colorScheme.tertiary)
        Text("PDF-ul conține antetul, tabelul lunar, totalurile și indexurile.", style = MaterialTheme.typography.bodySmall)
    }
    PrimaryButton("Trimite pe e-mail către furnizor", onEmail)
    OutlinedButton(onSave, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Descarcă PDF") }
    TextButton(onChange) { Text("Modifică datele") }
}

@Composable
private fun Totals(r: SupplierReport) {
    Text("Injectat: ${r.injected.kwh()} · Consumat din rețea: ${r.fromGrid.kwh()}", style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    OutlinedButton(onBack, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Înapoi") }
}

/** Sarcina 7, E2: a simplified invoice that shows where the POD code is. */
@Composable
private fun InvoiceHint() {
    val mark = MaterialTheme.colorScheme.error
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Exemplu de factură: codul POD este în secțiunea Date loc de consum, sub adresă."
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Factură energie electrică – pagina 1", style = MaterialTheme.typography.labelLarge)
        Text("Date loc de consum", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text("Adresa: str. Exemplu nr. 1, Cluj-Napoca", style = MaterialTheme.typography.bodySmall)
        Text(
            "Cod loc de consum (POD): RO005E…",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.border(2.dp, mark, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Text("Seria contorului: …", style = MaterialTheme.typography.bodySmall)
    }
}
