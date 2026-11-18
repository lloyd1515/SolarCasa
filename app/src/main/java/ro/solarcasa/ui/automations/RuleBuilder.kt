package ro.solarcasa.ui.automations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ro.solarcasa.R
import ro.solarcasa.data.HouseRepository
import ro.solarcasa.data.RuleDraft
import ro.solarcasa.data.SolarAssistant
import ro.solarcasa.ui.components.InfoCard
import ro.solarcasa.ui.components.PrimaryButton
import ro.solarcasa.ui.components.ScreenScaffold
import ro.solarcasa.ui.components.Tag
import ro.solarcasa.ui.screenViewModel

/** Holds the rule while the user builds it; the house changes only on save. */
class RuleBuilderViewModel(private val repository: HouseRepository, initial: RuleDraft?) : ViewModel() {
    private val state = MutableStateFlow(initial ?: RuleDraft())
    val draft = state.asStateFlow()

    fun edit(change: (RuleDraft) -> RuleDraft) = state.update(change)

    fun conflictsWithComfort() = state.value.device == SolarAssistant.HEAT_PUMP &&
        repository.house.value.rules.any { it.id == "comfort" && it.enabled }

    fun save(priority: String) = repository.saveRule(state.value, priority)
}

private data class Condition(val template: String, val text: String, val timesIn30Days: Int)

private val conditions = listOf(
    Condition("Vreme", SolarAssistant.CLOUDY_CONDITION, 9),
    Condition("Vreme", SolarAssistant.RAIN_CONDITION, 6),
    Condition("Vreme", SolarAssistant.COLD_CONDITION, 4),
    Condition("Nivel baterie", "Bateria scade sub 30%", 11),
    Condition("Oră", "În fiecare zi la 22:00", 30),
    Condition("Preț energie", "Prețul din rețea depășește 1,30 lei/kWh", 0),
)

private val devices = listOf(SolarAssistant.HEAT_PUMP, "Boiler electric oprit", "Încărcarea mașinii doar din surplus")

private val windows = listOf("06:00" to "22:00", "00:00" to "24:00", "22:00" to "06:00")

private val priorities = listOf(
    "Întreabă-mă de fiecare dată" to "Primești o notificare și alegi tu, în acel moment.",
    "Regula nouă" to "În zilele înnorate casa are 20 °C, chiar dacă cineva este acasă.",
    "Confort" to "Când cineva este acasă, rămân 22 °C; regula nouă se aplică doar când casa e goală.",
)

@Composable
fun RuleBuilderScreen(draft: RuleDraft?, onBack: () -> Unit) {
    val vm = screenViewModel { RuleBuilderViewModel(it, draft) }
    val d by vm.draft.collectAsStateWithLifecycle()
    var template by rememberSaveable { mutableStateOf<String?>(if (draft?.condition != null) "Vreme" else null) }
    var askPriority by rememberSaveable { mutableStateOf(false) }
    var priority by rememberSaveable { mutableStateOf(priorities.first().first) }
    val save = { vm.save(priority); onBack() }

    ScreenScaffold("Regulă nouă", onBack = onBack) {
        if (d.byAssistant) Tag("Propusă de asistent – verifică și corectează", MaterialTheme.colorScheme.tertiary)

        Block("DACĂ") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                conditions.map { it.template }.distinct().forEach { t ->
                    FilterChip(template == t, { template = t }, label = { Text(t) })
                }
            }
            conditions.filter { it.template == template }.forEach { c ->
                Choice(c.text, d.condition == c.text) { vm.edit { it.copy(condition = c.text) } }
            }
            conditions.firstOrNull { it.text == d.condition }?.let {
                Text(
                    "În ultimele 30 de zile, condiția ar fi fost îndeplinită de ${it.timesIn30Days} ori.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Block("ATUNCI", empty = d.device == null) {
            if (d.device == null) Text("Alege ce să facă sistemul", style = MaterialTheme.typography.bodyLarge)
            devices.forEach { dev -> Choice(dev, d.device == dev) { vm.edit { it.copy(device = dev) } } }
            if (d.device == SolarAssistant.HEAT_PUMP) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mod economic: ${d.temperature} °C", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton({ vm.edit { it.copy(temperature = (it.temperature - 1).coerceAtLeast(16)) } }) {
                        Icon(painterResource(R.drawable.ic_remove), contentDescription = "Scade temperatura")
                    }
                    IconButton({ vm.edit { it.copy(temperature = (it.temperature + 1).coerceAtMost(24)) } }) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = "Crește temperatura")
                    }
                }
                Text(
                    "Economie: ≈ ${22 - d.temperature + 2} kWh pe zi înnorată; bateria rezistă cu ≈ 5 ore mai mult.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Block("CÂND SE APLICĂ") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                windows.forEach { (from, to) ->
                    FilterChip(d.from == from && d.to == to, { vm.edit { it.copy(from = from, to = to) } }, label = { Text("$from – $to") })
                }
            }
        }

        val complete = d.condition != null && d.device != null
        if (complete) {
            // Preview before saving, shown automatically (Etapa 6, sarcina 3, problema 2).
            InfoCard(title = "Previzualizare: următoarele 3 zile", icon = R.drawable.ic_calendar_month) {
                Text("Joi – înnorat: regula se aplică")
                Text("Vineri – senin: regula nu se aplică")
                Text("Sâmbătă – înnorat: regula se aplică")
                Text("Bateria seara, în zilele înnorate: 38% → 52%", style = MaterialTheme.typography.titleSmall)
            }
        }
        // Sarcina 3, E2: no save without an action.
        PrimaryButton("Salvează regula", { if (vm.conflictsWithComfort()) askPriority = true else save() }, enabled = complete)
    }

    if (askPriority) {
        AlertDialog(
            onDismissRequest = { askPriority = false },
            title = { Text("Conflict cu regula „Confort”") },
            text = {
                Column {
                    Text("Ambele reguli schimbă temperatura. Care are prioritate?")
                    priorities.forEach { (name, explanation) ->
                        Choice(name, priority == name, explanation) { priority = name }
                    }
                }
            },
            confirmButton = { TextButton({ askPriority = false; save() }) { Text("Salvează") } },
            dismissButton = { TextButton({ askPriority = false }) { Text("Înapoi") } },
        )
    }
}

@Composable
private fun Block(title: String, empty: Boolean = false, content: @Composable () -> Unit) {
    OutlinedCard(
        Modifier.fillMaxWidth(),
        border = BorderStroke(if (empty) 2.dp else 1.dp, if (empty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun Choice(text: String, selected: Boolean, detail: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}
