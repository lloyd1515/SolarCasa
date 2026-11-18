package ro.solarcasa.ui.assistant

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import ro.solarcasa.data.RuleDraft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantSheet(onDismiss: () -> Unit, onEditRule: (RuleDraft) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) { Text("În lucru") }
}
