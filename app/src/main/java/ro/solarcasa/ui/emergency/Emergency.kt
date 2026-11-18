package ro.solarcasa.ui.emergency

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import ro.solarcasa.data.ProfileId
import ro.solarcasa.data.RuleDraft
import ro.solarcasa.navigation.Tab
import ro.solarcasa.ui.components.ScreenScaffold

@Composable
fun EmergencyScreen() {
    ScreenScaffold("Mod urgență", onBack = null) { Text("În lucru") }
}
