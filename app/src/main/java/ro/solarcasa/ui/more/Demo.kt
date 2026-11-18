package ro.solarcasa.ui.more

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ro.solarcasa.MainActivity
import ro.solarcasa.R
import ro.solarcasa.data.DemoData
import ro.solarcasa.data.EnergyMath
import ro.solarcasa.data.HouseRepository
import ro.solarcasa.data.Scenario
import ro.solarcasa.data.SolarAssistant
import ro.solarcasa.ui.Notifier
import ro.solarcasa.ui.components.InfoCard
import ro.solarcasa.ui.components.ScreenScaffold
import ro.solarcasa.ui.components.SectionTitle
import ro.solarcasa.ui.screenViewModel

/** "Scenarii demo": the presenter loads the moments of the Etapa 5 scenarios and their error cases. */
class DemoViewModel(private val repository: HouseRepository) : ViewModel() {
    data class UiState(val scenario: Scenario, val staleData: Boolean, val carConnected: Boolean)

    data class Push(val id: Int, val title: String, val text: String, val open: String)

    val uiState = repository.uiState(viewModelScope) { UiState(it.scenario, it.dataAgeMin > 5, it.car.connected) }

    /** Loads the moment and returns its push notification, if the Etapa 5 scenario starts from one. */
    fun start(scenario: Scenario): Push? {
        repository.loadScenario(scenario)
        val h = repository.house.value
        val alert = DemoData.stringAlert
        return when (scenario) {
            Scenario.ProductionFault -> Push(1, alert.title, alert.summary, MainActivity.OPEN_ALERT + alert.id)
            Scenario.GridOutage -> Push(2, "Pană de curent", "Casa funcționează pe baterie (${h.batteryPercent}%).", MainActivity.OPEN_EMERGENCY)
            Scenario.LongOutage -> {
                val left = SolarAssistant.hours(EnergyMath.autonomyHours(h.batteryPercent, h.reservePercent, EnergyMath.houseLoad(h)))
                Push(
                    2, "Bateria: ${h.batteryPercent}% – $left rămase",
                    "Casa este în pană de curent de 6 ore. Funcționează doar consumatorii esențiali.", MainActivity.OPEN_EMERGENCY,
                )
            }
            Scenario.SunnyNoon, Scenario.CloudyMorning -> null
        }
    }

    /** Sarcina 2, E3: inverter data older than 5 minutes. */
    fun setStaleData(on: Boolean) = repository.set { it.copy(dataAgeMin = if (on) 7 else 0) }

    /** Sarcina 8, E1: the car is not plugged in. */
    fun setCarConnected(on: Boolean) = repository.set { it.copy(car = it.car.copy(connected = on)) }
}

private fun Scenario.demoNote() = when (this) {
    Scenario.SunnyNoon -> "Sarcina 2: surplus solar și „Ce pot porni?”."
    Scenario.CloudyMorning -> "Sarcina 2, E1: deficit; asistentul propune o oră mai bună."
    Scenario.ProductionFault -> "Sarcina 5: notificarea „Producție cu 45% sub așteptări” deschide alerta."
    Scenario.GridOutage -> "Sarcina 6: notificarea de pană de curent și modul urgență."
    Scenario.LongOutage -> "Sarcina 6, scenariul B: bateria la 18%, doar esențialele pornite, notificare critică."
}

@Composable
fun DemoScreen(onBack: () -> Unit) {
    val vm = screenViewModel { DemoViewModel(it) }
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(Notifier.allowed(context)) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        denied = !granted
    }
    // The user can allow notifications in the system settings and come back.
    LifecycleResumeEffect(Unit) {
        allowed = Notifier.allowed(context)
        onPauseOrDispose {}
    }

    ScreenScaffold("Scenarii demo", onBack = onBack) {
        Text(
            "Pentru prezentare: alege un moment. Datele casei se schimbă imediat. Setările și istoricul rămân.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!allowed) {
            // 28_notification_permission: ask in context, after a user action, and say what the notifications are for.
            InfoCard(title = "Notificările sunt oprite", icon = R.drawable.ic_notifications, container = MaterialTheme.colorScheme.secondaryContainer) {
                Text("Alerta de producție și pana de curent încep cu o notificare. Fără permisiune, scenariile pornesc fără ea.")
                FilledTonalButton({
                    if (denied || Build.VERSION.SDK_INT < 33) openNotificationSettings(context)
                    else askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text(if (denied) "Deschide setările notificărilor" else "Permite notificările") }
            }
        }

        SectionTitle("Momentul zilei")
        Scenario.entries.forEach { s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .selectable(ui.scenario == s, role = Role.RadioButton) {
                        vm.start(s)?.let { push -> Notifier.post(context, push.id, push.title, push.text, push.open) }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(ui.scenario == s, onClick = null)
                Column(Modifier.padding(start = 8.dp)) {
                    Text(s.title, style = MaterialTheme.typography.titleSmall)
                    Text(s.demoNote(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        SectionTitle("Cazuri de eroare")
        SwitchRow("Date întârziate de la invertor", "Sarcina 2, E3: „Acasă” arată „Actualizat acum 7 min”.", ui.staleData, vm::setStaleData)
        SwitchRow(
            "Mașina nu este conectată", "Sarcina 8, E1: „Garaj” păstrează setarea pentru următoarea conectare.",
            !ui.carConnected,
        ) { vm.setCarConnected(!it) }
    }
}

@Composable
private fun SwitchRow(title: String, note: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(note, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

private fun openNotificationSettings(context: Context) = context.startActivity(
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
)
