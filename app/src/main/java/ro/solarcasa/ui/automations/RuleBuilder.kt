package ro.solarcasa.ui.automations

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import ro.solarcasa.data.ProfileId
import ro.solarcasa.data.RuleDraft
import ro.solarcasa.navigation.Tab
import ro.solarcasa.ui.components.ScreenScaffold

@Composable
fun RuleBuilderScreen(draft: RuleDraft?, onBack: () -> Unit) {
    ScreenScaffold("Regulă nouă", onBack = onBack) { Text("În lucru") }
}
