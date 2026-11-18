package ro.solarcasa.ui.home

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import ro.solarcasa.data.ProfileId
import ro.solarcasa.data.RuleDraft
import ro.solarcasa.navigation.Tab
import ro.solarcasa.ui.components.ScreenScaffold

@Composable
fun HomeScreen(onNavigate: (Any) -> Unit) {
    ScreenScaffold("Acasă", onBack = null) { Text("În lucru") }
}

@Composable
fun AppliancesScreen(onBack: () -> Unit) {
    ScreenScaffold("Ce pot porni?", onBack = onBack) { Text("În lucru") }
}
