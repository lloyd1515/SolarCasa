package ro.solarcasa.ui.automations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ro.solarcasa.R
import ro.solarcasa.data.HouseRepository
import ro.solarcasa.data.ProfileId
import ro.solarcasa.data.Rule
import ro.solarcasa.data.RuleDraft
import ro.solarcasa.data.SolarAssistant
import ro.solarcasa.navigation.ProfileDetail
import ro.solarcasa.navigation.RuleBuilder
import ro.solarcasa.ui.components.InfoCard
import ro.solarcasa.ui.components.PrimaryButton
import ro.solarcasa.ui.components.ScreenScaffold
import ro.solarcasa.ui.components.Tag
import ro.solarcasa.ui.home.icon
import ro.solarcasa.ui.screenViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Saves a rule from the builder or from the assistant; "Anulează" removes it again. */
fun HouseRepository.saveRule(draft: RuleDraft, priority: String) {
    val rule = Rule(
        id = "rule-${System.nanoTime()}",
        name = ruleName(draft.condition),
        condition = draft.condition.orEmpty(),
        action = actionText(draft) + " · prioritate: $priority",
        window = "${draft.from} – ${draft.to}",
        byAssistant = draft.byAssistant,
    )
    perform(
        "Regula „${rule.name}” a fost salvată. Este verificată în fiecare seară la 21:00, pe baza prognozei.",
        undo = { h -> h.copy(rules = h.rules - rule) },
    ) { h -> h.copy(rules = h.rules + rule) }
}

fun actionText(d: RuleDraft) = when (d.device) {
    SolarAssistant.HEAT_PUMP -> "${d.device} → mod economic (${d.temperature} °C)"
    null -> ""
    else -> d.device
}

private fun ruleName(condition: String?) = when (condition) {
    SolarAssistant.CLOUDY_CONDITION -> "Economic în zile înnorate"
    SolarAssistant.RAIN_CONDITION -> "Economic în zile cu ploaie"
    SolarAssistant.COLD_CONDITION -> "Protecție la îngheț"
    else -> "Regulă nouă"
}

class AutomationsViewModel(private val repository: HouseRepository) : ViewModel() {
    val uiState = repository.uiState(viewModelScope) { it }

    fun toggle(rule: Rule, on: Boolean) = repository.perform(
        if (on) "Regula „${rule.name}” este activă" else "Regula „${rule.name}” este oprită",
        undo = { h -> h.copy(rules = h.rules.map { if (it.id == rule.id) rule else it }) },
    ) { h -> h.copy(rules = h.rules.map { if (it.id == rule.id) it.copy(enabled = on) else it }) }

    fun activate(profile: ProfileId, until: String?) {
        val old = repository.house.value
        repository.perform(
            if (until != null) "Mod ${profile.title} activ până pe $until" else "Profilul ${profile.title} este activ",
            undo = { it.copy(profile = old.profile, vacationUntil = old.vacationUntil) },
        ) { it.copy(profile = profile, vacationUntil = until) }
    }
}

@Composable
fun AutomationsScreen(onNavigate: (Any) -> Unit) {
    val vm = screenViewModel { AutomationsViewModel(it) }
    val h by vm.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    ScreenScaffold("Automatizări") {
        PrimaryTabRow(selectedTabIndex = tab) {
            listOf("Reguli", "Profiluri").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
        }
        if (tab == 0) {
            h.rules.forEach { rule ->
                InfoCard(title = rule.name, icon = R.drawable.ic_auto_mode) {
                    Text("DACĂ ${rule.condition}", style = MaterialTheme.typography.bodyLarge)
                    Text("ATUNCI ${rule.action}", style = MaterialTheme.typography.bodyLarge)
                    Text("Interval: ${rule.window}", style = MaterialTheme.typography.bodyMedium)
                    if (rule.byAssistant) Tag("Creată de asistent", MaterialTheme.colorScheme.tertiary)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(rule.enabled, role = Role.Switch) { vm.toggle(rule, it) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (rule.enabled) "Activă" else "Oprită", Modifier.weight(1f))
                        Switch(rule.enabled, onCheckedChange = null)
                    }
                }
            }
            PrimaryButton("+ Regulă nouă", { onNavigate(RuleBuilder()) })
        } else {
            ProfileId.entries.forEach { p ->
                InfoCard(Modifier.clickable { onNavigate(ProfileDetail(p)) }, title = p.title, icon = p.icon()) {
                    Text(profileInfo(p).summary, style = MaterialTheme.typography.bodyMedium)
                    if (p == h.profile) Tag("Activ", MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private data class ProfileInfo(val summary: String, val changes: List<String>, val stays: List<String>, val leiPerDay: Int)

private fun profileInfo(p: ProfileId) = when (p) {
    ProfileId.Normal -> ProfileInfo(
        "Confortul obișnuit al casei.", listOf("încălzire 22 °C", "boiler din surplus solar"), listOf("toți consumatorii"), 0,
    )
    ProfileId.Night -> ProfileInfo(
        "Consum redus pe timpul nopții.", listOf("încălzire 19 °C", "lumini exterioare oprite"),
        listOf("alarmă și camere", "frigider", "router"), 2,
    )
    ProfileId.Vacation -> ProfileInfo(
        "Consum minim când lipsiți de acasă. Surplusul se vinde în rețea.",
        listOf("încălzire 16 °C", "boiler oprit", "prize neesențiale oprite"),
        listOf("alarmă și camere", "frigider", "ventilație minimă"), 21,
    )
    ProfileId.Eco -> ProfileInfo(
        "Aparatele mari pornesc doar din surplus; bateria păstrează 30% seara.",
        listOf("încălzire 20 °C", "aparate mari doar din surplus"), listOf("toți consumatorii"), 4,
    )
}

@Composable
fun ProfileDetailScreen(id: ProfileId, onBack: () -> Unit) {
    val vm = screenViewModel { AutomationsViewModel(it) }
    val h by vm.uiState.collectAsStateWithLifecycle()
    val info = profileInfo(id)
    var days by rememberSaveable { mutableIntStateOf(10) }
    var hour by rememberSaveable { mutableStateOf("18:00") }
    var prepare by rememberSaveable { mutableStateOf(true) }
    var windowWarning by rememberSaveable { mutableStateOf(false) }
    val until = LocalDate.now().plusDays(days.toLong()).format(DateTimeFormatter.ofPattern("dd.MM")) + ", $hour"
    val activate = {
        vm.activate(id, if (id == ProfileId.Vacation) until else null)
        onBack()
    }

    ScreenScaffold("Profil: ${id.title}", onBack = onBack) {
        Text(info.summary, style = MaterialTheme.typography.bodyLarge)
        InfoCard(title = "Ce se schimbă", icon = R.drawable.ic_tune) { info.changes.forEach { Text("• $it") } }
        InfoCard(title = "Ce rămâne pornit", icon = R.drawable.ic_check_circle) { info.stays.forEach { Text("• $it") } }
        if (id == ProfileId.Vacation) {
            Text("Până când?", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 7, 10, 14).forEach { d -> FilterChip(days == d, { days = d }, label = { Text("$d zile") }) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("08:00", "12:00", "18:00", "22:00").forEach { t -> FilterChip(hour == t, { hour = t }, label = { Text(t) }) }
            }
            Text("Întoarcere: $until", style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(prepare, role = Role.Checkbox) { prepare = it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(prepare, onCheckedChange = null)
                Text("Pregătește casa pentru întoarcere: încălzire 21 °C și boiler pornit cu 3 ore înainte", Modifier.padding(start = 8.dp))
            }
            Text("Economie estimată: ≈ ${info.leiPerDay * days} lei", style = MaterialTheme.typography.titleMedium)
        }
        if (h.profile == id) {
            Tag("Profilul este activ", MaterialTheme.colorScheme.primary)
        } else {
            // Sarcina 9, E1: an open window is reported before the house is left alone.
            PrimaryButton("Activează profilul", { if (id == ProfileId.Vacation) windowWarning = true else activate() })
        }
    }

    if (windowWarning) {
        AlertDialog(
            onDismissRequest = { windowWarning = false },
            title = { Text("Fereastră deschisă") },
            text = { Text("Fereastra din bucătărie este deschisă. Activezi oricum profilul Vacanță?") },
            confirmButton = { TextButton({ windowWarning = false; activate() }) { Text("Activează oricum") } },
            dismissButton = { TextButton({ windowWarning = false }) { Text("Închid fereastra") } },
        )
    }
}
