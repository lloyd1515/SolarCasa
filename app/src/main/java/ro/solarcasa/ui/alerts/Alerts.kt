package ro.solarcasa.ui.alerts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ro.solarcasa.R
import ro.solarcasa.data.Alert
import ro.solarcasa.data.AlertStatus
import ro.solarcasa.data.DemoData
import ro.solarcasa.data.House
import ro.solarcasa.data.HouseRepository
import ro.solarcasa.data.Severity
import ro.solarcasa.data.SolarAssistant
import ro.solarcasa.navigation.AlertDetail
import ro.solarcasa.navigation.ServiceRequest
import ro.solarcasa.ui.components.AiCard
import ro.solarcasa.ui.components.BarChart
import ro.solarcasa.ui.components.InfoCard
import ro.solarcasa.ui.components.LineChart
import ro.solarcasa.ui.components.PrimaryButton
import ro.solarcasa.ui.components.ScreenScaffold
import ro.solarcasa.ui.components.Series
import ro.solarcasa.ui.components.Tag
import ro.solarcasa.ui.screenViewModel

class AlertsViewModel(private val repository: HouseRepository) : ViewModel() {
    val alerts = repository.uiState(viewModelScope) { it.alerts }

    private fun setStatus(id: String, status: AlertStatus, note: String?, message: String) {
        val old = repository.house.value.alerts.first { it.id == id }
        repository.perform(message, undo = { h -> h.withAlert(old) }) { h -> h.withAlert(old.copy(status = status, statusNote = note)) }
    }

    fun requestService(id: String, slot: String) =
        setStatus(id, AlertStatus.InProgress, "Service programat: $slot", "Cererea a fost trimisă. Service programat: $slot.")

    fun checkedFuse(id: String) =
        setStatus(id, AlertStatus.Monitored, "Siguranța a fost verificată. Urmărim producția.", "Mulțumim. Verificăm din nou producția în 15 minute.")

    fun waitForRain(id: String) =
        setStatus(id, AlertStatus.Monitored, "Așteptăm ploaia. Te anunțăm dacă producția nu revine.", "Alerta este monitorizată. Te anunțăm dacă producția nu revine.")

    fun scheduleCleaning(id: String) =
        setStatus(id, AlertStatus.InProgress, "Curățare programată (≈ 250 lei)", "Cererea de curățare a fost trimisă.")

    private fun House.withAlert(a: Alert) = copy(alerts = alerts.map { if (it.id == a.id) a else it })
}

@Composable
fun AlertsScreen(onNavigate: (Any) -> Unit, onBack: () -> Unit) {
    val vm = screenViewModel { AlertsViewModel(it) }
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    ScreenScaffold("Alerte", onBack = onBack) {
        if (alerts.isEmpty()) Text("Nu există alerte. Sistemul funcționează normal.")
        alerts.forEach { a ->
            InfoCard(Modifier.clickable { onNavigate(AlertDetail(a.id)) }, title = a.title, icon = a.severity.icon()) {
                Text(a.summary, style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeverityTag(a.severity)
                    Tag(a.status.label, MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
fun AlertDetailScreen(id: String, onNavigate: (Any) -> Unit, onBack: () -> Unit) {
    val vm = screenViewModel { AlertsViewModel(it) }
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val alert = alerts.firstOrNull { it.id == id } ?: return ScreenScaffold("Alertă", onBack = onBack) { Text("Alerta a fost închisă.") }
    val diagnosis = SolarAssistant.diagnosis(id)
    var showSteps by rememberSaveable { mutableStateOf(false) }
    var explainFuse by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(alert.title, onBack = onBack) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SeverityTag(alert.severity)
            Tag(alert.status.label, MaterialTheme.colorScheme.outline)
        }
        alert.statusNote?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        if (id == "string2") {
            // The conclusion in words comes before the chart (Etapa 6, sarcina 5, problema 1).
            Text("Azi ai produs cu 45% mai puțin decât era normal pentru o zi senină.", style = MaterialTheme.typography.titleLarge)
            LineChart(
                DemoData.alertHours,
                listOf(
                    Series("Așteptat (senin)", DemoData.expectedToday, MaterialTheme.colorScheme.outline),
                    Series("Real azi", DemoData.realToday, MaterialTheme.colorScheme.error),
                ),
                description = "Producția reală este aproape la jumătate din cea așteptată începând cu ora 10.",
            )
            RoofSketch()
        } else {
            Text("Producția scade încet de 3 săptămâni, pe ambele șiruri de panouri.", style = MaterialTheme.typography.titleLarge)
            BarChart(
                listOf("S1", "S2", "S3", "S4", "S5", "S6"),
                listOf(Series("Producție față de normal (%)", listOf(100.0, 99.0, 96.0, 93.0, 90.0, 88.0), MaterialTheme.colorScheme.secondary)),
                description = "Producția normalizată după vreme scade de la 100% la 88% în 6 săptămâni.",
                height = 120.dp,
            )
        }
        AiCard(
            title = "Cauza probabilă",
            text = diagnosis.cause,
            confidence = diagnosis.confidence,
            why = "Comparăm producția fiecărui șir cu prognoza meteo și cu ultimele 30 de zile. " +
                "Sub 50% încredere îți arătăm mai multe cauze posibile.",
        ) {
            if (id == "string2") TextButton({ explainFuse = !explainFuse }) { Text("Ce este o siguranță?") }
        }
        if (explainFuse) {
            Text(
                "Siguranța este un întrerupător mic din tabloul invertorului. Ea oprește curentul când apare o problemă. " +
                    "O siguranță „declanșată” are maneta în jos.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!showSteps && alert.status == AlertStatus.New) {
            PrimaryButton("Ce pot face?", { showSteps = true })
        }
        if (showSteps && alert.status == AlertStatus.New) {
            InfoCard(title = "Pași recomandați", icon = R.drawable.ic_checklist) {
                diagnosis.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodyLarge) }
            }
            if (id == "string2") {
                PrimaryButton("Contactează service-ul", { onNavigate(ServiceRequest(id)) })
                OutlinedButton({ vm.checkedFuse(id) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Am verificat siguranța") }
            } else {
                PrimaryButton("Așteaptă ploaia și amintește-mi dacă nu revine", { vm.waitForRain(id) })
                OutlinedButton({ vm.scheduleCleaning(id) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Programează curățarea (≈ 250 lei)")
                }
            }
        }
    }
}

@Composable
fun ServiceRequestScreen(alertId: String, onBack: () -> Unit) {
    val vm = screenViewModel { AlertsViewModel(it) }
    val slots = listOf("joi, 9:00 – 12:00", "joi, 13:00 – 16:00", "vineri, 9:00 – 12:00")
    var slot by rememberSaveable { mutableStateOf(slots.first()) }
    var note by rememberSaveable { mutableStateOf("") }
    ScreenScaffold("Cerere service", onBack = onBack) {
        InfoCard(title = "Date completate automat", icon = R.drawable.ic_build) {
            Text("Sistem: 6 kWp, 13 panouri pe 2 șiruri, baterie 10 kWh")
            Text("Problema: șirul 2 produce 0 W de la ora 10:12")
            Text("Cod invertor: PV2 – tensiune de intrare zero")
            Text("Atașat: graficul producției de azi")
            Text("Adresa: Cluj-Napoca (din profilul casei)")
        }
        Text("Alege intervalul vizitei", style = MaterialTheme.typography.titleMedium)
        slots.forEach { s ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(s == slot, role = Role.RadioButton) { slot = s },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = s == slot, onClick = null)
                Text(s, Modifier.padding8())
            }
        }
        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Observații (opțional)") })
        PrimaryButton("Trimite", { vm.requestService(alertId, slot); onBack() })
    }
}

/** Roof sketch: string 1 works, string 2 is marked (red, with text, not only color). */
@Composable
private fun RoofSketch() {
    val ok = MaterialTheme.colorScheme.primary
    val bad = MaterialTheme.colorScheme.error
    val line = MaterialTheme.colorScheme.onSurface
    InfoCard(title = "Unde este problema", icon = R.drawable.ic_solar_power) {
        Canvas(
            Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = "Schița acoperișului: șirul 2 este marcat ca defect." },
        ) {
            val roof = Path().apply {
                moveTo(size.width * 0.1f, size.height); lineTo(size.width * 0.25f, 0f)
                lineTo(size.width * 0.75f, 0f); lineTo(size.width * 0.9f, size.height); close()
            }
            drawPath(roof, line, style = Stroke(4f))
            for (row in 0..1) for (col in 0..5) {
                val w = size.width * 0.5f / 6
                val left = size.width * 0.25f + col * w
                val top = 12f + row * (size.height / 2)
                drawRect(if (row == 0) ok else bad, Offset(left + 3f, top), Size(w - 6f, size.height / 2 - 24f))
            }
        }
        Text("Rândul de sus: șirul 1 – normal. Rândul de jos: șirul 2 – 0 W de la 10:12.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SeverityTag(s: Severity) = when (s) {
    Severity.High -> Tag("Prioritate ridicată", MaterialTheme.colorScheme.error)
    Severity.Medium -> Tag("Prioritate medie", Color(0xFF8A5A00))
}

private fun Severity.icon() = if (this == Severity.High) R.drawable.ic_error else R.drawable.ic_warning

private fun Modifier.padding8() = this.then(Modifier.padding(start = 8.dp))
