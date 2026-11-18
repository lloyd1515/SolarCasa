package ro.solarcasa.ui.energy

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import ro.solarcasa.data.ProfileId
import ro.solarcasa.data.RuleDraft
import ro.solarcasa.navigation.Tab
import ro.solarcasa.ui.components.ScreenScaffold

@Composable
fun EnergyScreen(onNavigate: (Any) -> Unit) {
    ScreenScaffold("Energie", onBack = null) { Text("În lucru") }
}

@Composable
fun BatterySettingsScreen(onBack: () -> Unit) {
    ScreenScaffold("Setări baterie", onBack = onBack) { Text("În lucru") }
}

@Composable
fun ConsumerDetailScreen(id: String, onBack: () -> Unit) {
    ScreenScaffold("Consumator", onBack = onBack) { Text("În lucru") }
}
