package ro.solarcasa.ui.reports

import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ro.solarcasa.R
import ro.solarcasa.data.DemoData
import ro.solarcasa.data.EnergyMath
import ro.solarcasa.data.SolarAssistant
import ro.solarcasa.ui.PdfExport
import ro.solarcasa.ui.components.AiCard
import ro.solarcasa.ui.components.BarChart
import ro.solarcasa.ui.components.InfoCard
import ro.solarcasa.ui.components.PrimaryButton
import ro.solarcasa.ui.components.ScreenScaffold
import ro.solarcasa.ui.components.SegmentedChoice
import ro.solarcasa.ui.components.Series
import ro.solarcasa.ui.components.Tag
import ro.solarcasa.ui.kwh
import ro.solarcasa.ui.lei
import ro.solarcasa.ui.signedPercent
import kotlin.math.roundToInt

/** Sarcina 4: energy bought from the grid in November 2026, compared with another month (static demo data). */
private enum class Compare(val label: String, val weeks: List<Double>) {
    PreviousMonth("Octombrie 2026", DemoData.weeksOct2026),
    LastYear("Noiembrie 2025", DemoData.weeksNov2025),
}

@Composable
fun ReportsScreen() {
    val context = LocalContext.current
    var compare by rememberSaveable { mutableStateOf(Compare.PreviousMonth) }
    var explain by rememberSaveable { mutableStateOf(false) }
    val now = DemoData.weeksNov2026.sum()
    val before = compare.weeks.sum()
    val change = ((now / before - 1) * 100).roundToInt()
    val diffLei = ((now - before) * EnergyMath.PRICE_LEI_PER_KWH).roundToInt()
    val summary = "${change.signedPercent()} energie cumpărată față de ${compare.label.lowercase()} (${if (diffLei > 0) "+" else ""}${diffLei.lei()})"

    ScreenScaffold("Rapoarte") {
        Text("Financiar · noiembrie 2026", style = MaterialTheme.typography.titleMedium)
        InfoCard(title = "Noiembrie 2026", icon = R.drawable.ic_bar_chart) {
            Text(
                "${now.kwh()} cumpărați din rețea (≈ ${(now * EnergyMath.PRICE_LEI_PER_KWH).toInt().lei()}), ${DemoData.SOLD_NOV_2026.kwh()} vânduți",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text("Compară cu", style = MaterialTheme.typography.titleSmall)
        SegmentedChoice(Compare.entries, compare, { it.label }, { compare = it; explain = false })
        Text(summary, style = MaterialTheme.typography.titleLarge)
        if (change < 0) Tag("Investiția în baterie se vede", MaterialTheme.colorScheme.primary)
        BarChart(
            listOf("Săpt. 1", "Săpt. 2", "Săpt. 3", "Săpt. 4"),
            listOf(
                Series(compare.label, compare.weeks, MaterialTheme.colorScheme.outline),
                Series("Noiembrie 2026", DemoData.weeksNov2026, MaterialTheme.colorScheme.primary),
            ),
            description = "Energia cumpărată pe săptămâni: $summary.",
            hatched = setOf(0),
        )
        // Sarcina 4, E1: missing inverter data is marked, not hidden.
        Text("Hașurat: date incomplete pentru 3 – 5 noiembrie; valorile pot fi cu până la 6% mai mari.", style = MaterialTheme.typography.bodySmall)
        if (compare == Compare.PreviousMonth) {
            if (explain) AiCard(SolarAssistant.REPORT_EXPLANATION, title = "De ce a crescut?", confidence = 88)
            else FilledTonalButton({ explain = true }) { Text("De ce a crescut?") }
        }
        PrimaryButton("Exportă PDF", {
            PdfExport.share(
                context, "SolarCasa_raport_noiembrie_2026.pdf", "SolarCasa – energie din rețea, noiembrie 2026",
                listOf(
                    "Cumpărat din rețea: ${now.kwh()} (≈ ${(now * EnergyMath.PRICE_LEI_PER_KWH).toInt().lei()})",
                    "Vândut în rețea: ${DemoData.SOLD_NOV_2026.kwh()}",
                    "Comparație: $summary",
                    "",
                    "Pe săptămâni (kWh): noiembrie 2026 / ${compare.label.lowercase()}",
                ) + DemoData.weeksNov2026.indices.map { "  Săptămâna ${it + 1}: ${DemoData.weeksNov2026[it].toInt()} / ${compare.weeks[it].toInt()}" } +
                    listOf("", "Notă: date incomplete pentru 3 – 5 noiembrie.", "", SolarAssistant.REPORT_EXPLANATION.take(95), SolarAssistant.REPORT_EXPLANATION.drop(95)),
            )
        })
    }
}
