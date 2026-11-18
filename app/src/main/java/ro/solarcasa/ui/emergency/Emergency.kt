package ro.solarcasa.ui.emergency

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import ro.solarcasa.data.Circuit
import ro.solarcasa.data.EnergyMath
import ro.solarcasa.data.House
import ro.solarcasa.data.HouseRepository
import ro.solarcasa.data.SolarAssistant
import ro.solarcasa.ui.components.SlideToConfirm
import ro.solarcasa.ui.kw
import ro.solarcasa.ui.screenViewModel

class EmergencyViewModel(private val repository: HouseRepository) : ViewModel() {
    data class UiState(
        val battery: Int,
        val currentLoad: Double,
        val currentHours: String,
        val circuits: List<Circuit>,
        val selected: Map<String, Boolean>,
        val selectionHours: String,
        val essentialsHours: String,
        val strictHours: String,
        val applied: Boolean,
    )

    /** Selection before confirmation; nothing changes in the house until the user slides to apply. */
    private val selection = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    val uiState = combine(repository.house, selection, ::toUi)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), toUi(repository.house.value, emptyMap()))

    private fun toUi(h: House, sel: Map<String, Boolean>): UiState {
        fun hoursFor(on: (Circuit) -> Boolean) =
            SolarAssistant.hours(EnergyMath.autonomyHours(h.batteryPercent, h.reservePercent, h.circuits.filter(on).sumOf { it.kw }))
        val selected = h.circuits.associate { it.id to (sel[it.id] ?: it.on) }
        return UiState(
            battery = h.batteryPercent,
            currentLoad = EnergyMath.houseLoad(h),
            currentHours = hoursFor { it.on },
            circuits = h.circuits,
            selected = selected,
            selectionHours = hoursFor { selected.getValue(it.id) },
            essentialsHours = hoursFor { it.essential },
            strictHours = hoursFor { it.strictlyNeeded },
            applied = h.emergencyApplied,
        )
    }

    fun selectEssentials() = selection.update { repository.house.value.circuits.associate { it.id to it.essential } }
    fun selectStrictlyNeeded() = selection.update { repository.house.value.circuits.associate { it.id to it.strictlyNeeded } }
    fun toggle(id: String, on: Boolean) = selection.update { uiState.value.selected + (id to on) }

    fun apply() {
        val sel = uiState.value.selected
        val active = sel.count { it.value }
        repository.perform(
            "Aplicat. Funcționează doar $active consumatori.",
            undo = { h -> h.copy(circuits = h.circuits.map { it.copy(on = true) }, emergencyApplied = false) },
        ) { h ->
            // A relay that does not answer keeps its circuit powered (Sarcina 6, E1).
            h.copy(circuits = h.circuits.map { it.copy(on = sel.getValue(it.id) || !it.responds) }, emergencyApplied = true)
        }
    }

    fun edit() = repository.set { it.copy(emergencyApplied = false) }
    fun minimize() = repository.set { it.copy(emergencyMinimized = true) }

    fun gridBack() = repository.perform("Rețeaua a revenit. Alimentarea normală este restabilită.") { h ->
        h.copy(gridDown = false, baseLoadKw = 2.1, circuits = h.circuits.map { it.copy(on = true) }, emergencyApplied = false)
    }
}

@Composable
fun EmergencyScreen() {
    val vm = screenViewModel { EmergencyViewModel(it) }
    val ui by vm.uiState.collectAsStateWithLifecycle()
    var confirmOff by rememberSaveable { mutableStateOf<String?>(null) }
    var askRestore by rememberSaveable { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 600.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.ic_emergency_home), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Mod urgență", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                }
                Text("Pană de curent. Casa funcționează pe baterie.", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Bateria: ${ui.battery}% – la consumul actual (${ui.currentLoad.kw()}) mai ai ${ui.currentHours}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (ui.applied) Result(ui, vm) else Selection(ui, vm, onAskTurnOff = { confirmOff = it })
                OutlinedButton(vm::minimize, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Închide modul urgență") }
                TextButton({ askRestore = true }) { Text("Demo: rețeaua a revenit") }
            }
        }
    }

    confirmOff?.let { id ->
        // Sarcina 6, E2: the fridge keeps the food; the user still keeps control.
        AlertDialog(
            onDismissRequest = { confirmOff = null },
            title = { Text("Oprești frigiderul?") },
            text = { Text("Frigiderul este esențial pentru alimente. Sigur îl oprești?") },
            confirmButton = { TextButton({ vm.toggle(id, false); confirmOff = null }) { Text("Da, oprește") } },
            dismissButton = { TextButton({ confirmOff = null }) { Text("Nu") } },
        )
    }
    if (askRestore) {
        AlertDialog(
            onDismissRequest = { askRestore = false },
            title = { Text("Rețeaua a revenit") },
            text = { Text("Restabilesc alimentarea normală pentru toți consumatorii?") },
            confirmButton = { TextButton({ vm.gridBack(); askRestore = false }) { Text("Da") } },
            dismissButton = { TextButton({ askRestore = false }) { Text("Mai târziu") } },
        )
    }
}

@Composable
private fun Selection(ui: EmergencyViewModel.UiState, vm: EmergencyViewModel, onAskTurnOff: (String) -> Unit) {
    Button(vm::selectEssentials, Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Text("Prelungește bateria (doar esențiale) · ${ui.essentialsHours}", style = MaterialTheme.typography.titleMedium)
    }
    OutlinedButton(vm::selectStrictlyNeeded, Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text("Doar strictul necesar · ${ui.strictHours}")
    }
    Text("Cu selecția de mai jos: ${ui.selectionHours}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    ui.circuits.groupBy { it.room }.forEach { (room, circuits) ->
        Text(room, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        circuits.forEach { c ->
            val on = ui.selected.getValue(c.id)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(on, role = Role.Switch) { if (!it && c.vital) onAskTurnOff(c.id) else vm.toggle(c.id, it) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.bodyLarge)
                    if (c.id == "router" && !on) {
                        Text("Fără router nu vei mai putea vedea casa din aplicație.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Switch(checked = on, onCheckedChange = null)
            }
        }
    }
    SlideToConfirm("Glisează pentru a aplica", vm::apply, Modifier.fillMaxWidth())
}

@Composable
private fun Result(ui: EmergencyViewModel.UiState, vm: EmergencyViewModel) {
    val active = ui.circuits.filter { it.on }
    Text("Aplicat. Funcționează ${active.size} consumatori. Autonomie: ${ui.currentHours}.", style = MaterialTheme.typography.titleLarge)
    Text(active.joinToString(" · ") { it.name }, style = MaterialTheme.typography.bodyLarge)
    ui.circuits.filter { it.on && !it.responds && !it.essential }.forEach {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_warning), contentDescription = "Atenție", tint = MaterialTheme.colorScheme.error)
            Text("${it.name}: releul nu a răspuns. Oprește-l manual de la tablou.", color = MaterialTheme.colorScheme.error)
        }
    }
    Text(
        "Te anunțăm la 30% și la 15% și când revine rețeaua. Membrii familiei au primit notificarea: " +
            "„Casa funcționează doar cu consumatorii esențiali pentru a păstra bateria.”",
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedButton(vm::edit, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Modifică selecția") }
}
