package ro.solarcasa.ui.planning

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import ro.solarcasa.data.ProfileId
import ro.solarcasa.data.RuleDraft
import ro.solarcasa.navigation.Tab
import ro.solarcasa.ui.components.ScreenScaffold

@Composable
fun PlanningScreen(onNavigate: (Any) -> Unit, onBack: (() -> Unit)?) {
    ScreenScaffold("Planificare", onBack = onBack) { Text("În lucru") }
}

@Composable
fun SimulationsScreen(onBack: () -> Unit) {
    ScreenScaffold("Simulări", onBack = onBack) { Text("În lucru") }
}
