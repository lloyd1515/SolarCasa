package ro.solarcasa.ui.garage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ro.solarcasa.R
import ro.solarcasa.data.Car
import ro.solarcasa.data.ChargeMode
import ro.solarcasa.data.EnergyMath
import ro.solarcasa.data.HouseRepository
import ro.solarcasa.ui.components.AiCard
import ro.solarcasa.ui.components.InfoCard
import ro.solarcasa.ui.components.PrimaryButton
import ro.solarcasa.ui.components.ScreenScaffold
import ro.solarcasa.ui.components.Tag
import ro.solarcasa.ui.dec
import ro.solarcasa.ui.screenViewModel

class GarageViewModel(private val repository: HouseRepository) : ViewModel() {
    /** Tomorrow's forecast: sun from 10:00, ≈ 22 kWh of surplus until 15:00 (demo data). */
    private val tomorrowSurplusKwh = 22.0

    private val state = MutableStateFlow(repository.house.value.car)
    val car = state.asStateFlow()

    fun edit(change: (Car) -> Car) = state.update(change)

    fun plan(c: Car) = EnergyMath.carPlan(c, tomorrowSurplusKwh, sunStartsAfterMinTime = c.minTime < "10:00")

    fun save() {
        val c = state.value
        val p = plan(c)
        val text = when {
            c.mode == ChargeMode.Normal -> "Mașina se încarcă acum, din orice sursă."
            c.mode == ChargeMode.Smart || c.acceptGrid ->
                "Până la ${c.minTime} mașina ajunge la ${c.minPercent}% (≈ ${p.gridCostLei.dec(0)} lei din rețea), apoi se încarcă din soare până la ${p.finalPercent}%."
            else -> "Mașina se va încărca mâine între ~10:00 și ~15:00, estimat până la ${p.finalPercent}%. Te anunțăm când pornește încărcarea."
        }
        val old = repository.house.value.car
        repository.perform(text, undo = { it.copy(car = old) }) { it.copy(car = c.copy(plan = text)) }
    }
}

@Composable
fun GarageScreen(onBack: () -> Unit) {
    val vm = screenViewModel { GarageViewModel(it) }
    val car by vm.car.collectAsStateWithLifecycle()
    val plan = vm.plan(car)
    ScreenScaffold("Garaj", onBack = onBack) {
        InfoCard(title = "Stația de încărcare", icon = R.drawable.ic_ev_station) {
            if (car.connected) {
                Text("Mașina este conectată · baterie ${car.percent}% (${car.percent * car.capacityKwh / 100} din ${car.capacityKwh} kWh)")
            } else {
                // Sarcina 8, E1
                Text("Mașina nu este conectată. Setarea se salvează pentru următoarea conectare.")
            }
            car.plan?.let { Tag("Program activ", MaterialTheme.colorScheme.primary); Text(it) }
        }

        Text("Mod de încărcare", style = MaterialTheme.typography.titleMedium)
        // Every mode shows its explanation, so the modes cannot be confused (Etapa 6, sarcina 8).
        ChargeMode.entries.forEach { mode ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(car.mode == mode, role = Role.RadioButton) { vm.edit { it.copy(mode = mode) } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(car.mode == mode, onClick = null)
                Column(Modifier.padding(start = 8.dp)) {
                    Text(mode.label, style = MaterialTheme.typography.titleSmall)
                    Text(mode.explanation, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (car.mode != ChargeMode.Normal) {
            Text("Nivel minim garantat: ${car.minPercent}% până la ${car.minTime}", style = MaterialTheme.typography.titleMedium)
            Slider(
                car.minPercent.toFloat(), { v -> vm.edit { it.copy(minPercent = v.toInt()) } },
                valueRange = 0f..80f, steps = 7,
                modifier = Modifier.semantics { stateDescription = "${car.minPercent} la sută" },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("06:30", "07:30", "08:30", "12:00").forEach { t ->
                    FilterChip(car.minTime == t, { vm.edit { it.copy(minTime = t) } }, label = { Text("până la $t") })
                }
            }
        }

        if (car.mode == ChargeMode.SolarOnly && plan.gridKwhForMinimum > 0) {
            // The system does the analysis (sub-task 4); the user only decides (sub-task 5).
            AiCard(
                title = "Atenție la nivelul minim",
                text = "Mâine este prognozat soare de la 10:00. Până la ${car.minTime} nu va fi surplus. " +
                    "Pentru ${car.minPercent}% ar trebui folosiți ${plan.gridKwhForMinimum.dec(0)} kWh din rețea (≈ ${plan.gridCostLei.dec(0)} lei).",
                why = "Prognoza meteo pentru Cluj-Napoca și consumul obișnuit al casei de dimineață.",
            ) {
                if (car.acceptGrid) {
                    Button({ vm.edit { it.copy(acceptGrid = true) } }) { Text("✓ Accept rețeaua pentru minim") }
                    OutlinedButton({ vm.edit { it.copy(acceptGrid = false) } }) { Text("Nu, doar soare") }
                } else {
                    OutlinedButton({ vm.edit { it.copy(acceptGrid = true) } }) { Text("Accept rețeaua pentru minim") }
                    Button({ vm.edit { it.copy(acceptGrid = false) } }) { Text("✓ Nu, doar soare") }
                }
            }
            Text(
                if (car.acceptGrid) "Rezultat: ${car.minPercent}% la ${car.minTime}, apoi soare până la ${plan.finalPercent}%."
                else "Rezultat: mașina NU va avea ${car.minPercent}% dimineață; mâine seară va avea ≈ ${plan.finalPercent}%.",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (car.mode == ChargeMode.SolarOnly) {
            // Sarcina 8, E2
            Text(
                "Stația are nevoie de cel puțin 1,4 kW surplus. În zilele cu surplus mic, modul Inteligent (AI) este mai potrivit.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        PrimaryButton("Salvează", vm::save)
    }
}
