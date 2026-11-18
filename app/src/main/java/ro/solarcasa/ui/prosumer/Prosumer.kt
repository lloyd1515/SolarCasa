package ro.solarcasa.ui.prosumer

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import ro.solarcasa.data.ProfileId
import ro.solarcasa.data.RuleDraft
import ro.solarcasa.navigation.Tab
import ro.solarcasa.ui.components.ScreenScaffold

@Composable
fun ProsumerScreen(onNavigate: (Any) -> Unit, onBack: (() -> Unit)?) {
    ScreenScaffold("Prosumator", onBack = onBack) { Text("În lucru") }
}

@Composable
fun ProsumerReportScreen(onBack: () -> Unit) {
    ScreenScaffold("Raport pentru furnizor", onBack = onBack) { Text("În lucru") }
}
