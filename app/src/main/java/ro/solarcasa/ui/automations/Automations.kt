package ro.solarcasa.ui.automations

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import ro.solarcasa.data.ProfileId
import ro.solarcasa.data.RuleDraft
import ro.solarcasa.navigation.Tab
import ro.solarcasa.ui.components.ScreenScaffold

@Composable
fun AutomationsScreen(onNavigate: (Any) -> Unit) {
    ScreenScaffold("Automatizări", onBack = null) { Text("În lucru") }
}

@Composable
fun ProfileDetailScreen(id: ProfileId, onBack: () -> Unit) {
    ScreenScaffold("Profil", onBack = onBack) { Text("În lucru") }
}
