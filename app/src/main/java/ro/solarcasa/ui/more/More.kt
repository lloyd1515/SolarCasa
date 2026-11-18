package ro.solarcasa.ui.more

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import ro.solarcasa.data.ProfileId
import ro.solarcasa.data.RuleDraft
import ro.solarcasa.navigation.Tab
import ro.solarcasa.ui.components.ScreenScaffold

@Composable
fun MoreScreen(onNavigate: (Any) -> Unit, onSelectTab: (Tab) -> Unit) {
    ScreenScaffold("Mai mult", onBack = null) { Text("În lucru") }
}

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    ScreenScaffold("Istoric acțiuni", onBack = onBack) { Text("În lucru") }
}

@Composable
fun DemoScreen(onBack: () -> Unit) {
    ScreenScaffold("Scenarii demo", onBack = onBack) { Text("În lucru") }
}
