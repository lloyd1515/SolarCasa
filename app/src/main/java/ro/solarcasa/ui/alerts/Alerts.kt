package ro.solarcasa.ui.alerts

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import ro.solarcasa.data.ProfileId
import ro.solarcasa.data.RuleDraft
import ro.solarcasa.navigation.Tab
import ro.solarcasa.ui.components.ScreenScaffold

@Composable
fun AlertsScreen(onNavigate: (Any) -> Unit, onBack: () -> Unit) {
    ScreenScaffold("Alerte", onBack = onBack) { Text("În lucru") }
}

@Composable
fun AlertDetailScreen(id: String, onNavigate: (Any) -> Unit, onBack: () -> Unit) {
    ScreenScaffold("Alertă", onBack = onBack) { Text("În lucru") }
}

@Composable
fun ServiceRequestScreen(alertId: String, onBack: () -> Unit) {
    ScreenScaffold("Cerere service", onBack = onBack) { Text("În lucru") }
}
