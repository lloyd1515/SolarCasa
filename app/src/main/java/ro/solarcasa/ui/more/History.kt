package ro.solarcasa.ui.more

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ro.solarcasa.R
import ro.solarcasa.data.ActionRecord
import ro.solarcasa.data.HouseRepository
import ro.solarcasa.ui.components.InfoCard
import ro.solarcasa.ui.components.ScreenScaffold
import ro.solarcasa.ui.screenViewModel

/** "Istoric acțiuni" (Etapa 4 and 5): every action of the session, with "Anulează" where the action can be undone. */
class HistoryViewModel(private val repository: HouseRepository) : ViewModel() {
    val history = repository.uiState(viewModelScope) { it.history }

    fun undo(id: Long) = repository.undo(id)
}

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val vm = screenViewModel { HistoryViewModel(it) }
    val history by vm.history.collectAsStateWithLifecycle()

    ScreenScaffold("Istoric acțiuni", onBack = onBack) {
        if (history.isEmpty()) {
            InfoCard(title = "Nicio acțiune încă", icon = R.drawable.ic_history) {
                Text("Aici apar acțiunile tale: aparate pornite, reguli salvate, programări. De aici le poți anula.")
            }
            return@ScreenScaffold
        }
        Text("Cele mai noi acțiuni sunt primele. „Anulează” readuce casa la starea de dinainte.", style = MaterialTheme.typography.bodyMedium)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            history.forEach { record -> HistoryRow(record) { vm.undo(record.id) } }
        }
    }
}

@Composable
private fun HistoryRow(record: ActionRecord, onUndo: () -> Unit) {
    val canUndo = record.undo != null && !record.undone
    ListItem(
        headlineContent = {
            Text(record.label, textDecoration = if (record.undone) TextDecoration.LineThrough else null)
        },
        overlineContent = { Text(record.time) },
        supportingContent = when {
            record.undone -> { { Text("Anulată") } }
            record.undo == null -> { { Text("Acțiunea nu se poate anula") } }
            else -> null
        },
        trailingContent = if (canUndo) {
            {
                // Several rows have "Anulează": TalkBack also reads which action it undoes.
                TextButton(onUndo, Modifier.semantics { contentDescription = "Anulează: ${record.label}" }) { Text("Anulează") }
            }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
