package ro.solarcasa.ui.assistant

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ro.solarcasa.R
import ro.solarcasa.data.HouseRepository
import ro.solarcasa.data.RuleDraft
import ro.solarcasa.data.SolarAssistant
import ro.solarcasa.data.SolarAssistant.Reply
import ro.solarcasa.ui.automations.saveRule
import ro.solarcasa.ui.components.AiCard
import ro.solarcasa.ui.components.InfoCard
import ro.solarcasa.ui.screenViewModel

class AssistantViewModel(private val repository: HouseRepository) : ViewModel() {
    data class Turn(val question: String, val reply: Reply, val closed: Boolean = false)

    private val state = MutableStateFlow<List<Turn>>(emptyList())
    val turns = state.asStateFlow()

    fun ask(text: String) {
        if (text.isBlank()) return
        state.update { it + Turn(text, SolarAssistant.interpret(text, repository.house.value)) }
    }

    fun choose(option: String) = state.update { closeLast(it) + Turn(option, SolarAssistant.clarified(option, repository.house.value)) }

    /** The interpreted rule is saved only after the user reads it and presses "Salvează" (HAX G9, G10). */
    fun save(draft: RuleDraft) {
        repository.saveRule(draft, "Întreabă-mă de fiecare dată")
        state.update(::closeLast)
    }

    fun cancel() = state.update(::closeLast)

    private fun closeLast(turns: List<Turn>) = turns.dropLast(1) + turns.last().copy(closed = true)
}

private val suggestions = listOf(
    "Când mâine e înnorat, pune încălzirea pe economic",
    "Ce pot porni acum?",
    "Cât mai ține bateria?",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantSheet(onDismiss: () -> Unit, onEditRule: (RuleDraft) -> Unit) {
    val vm = screenViewModel { AssistantViewModel(it) }
    val turns by vm.turns.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    var voiceError by rememberSaveable { mutableStateOf(false) }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && spoken != null) vm.ask(spoken)
    }
    val send = { vm.ask(text); text = "" }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Asistentul Solar", style = MaterialTheme.typography.titleLarge)
            Text(
                "Scrie sau spune o comandă. Îți arăt ce am înțeles înainte de orice acțiune.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (turns.isEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { s -> SuggestionChip({ vm.ask(s) }, label = { Text(s) }) }
                }
            }
            turns.forEach { turn -> TurnView(turn, vm, onEditRule) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    text, { text = it }, Modifier.weight(1f),
                    label = { Text("Comanda ta") },
                    singleLine = true,
                )
                IconButton(send) { Icon(painterResource(R.drawable.ic_send), contentDescription = "Trimite") }
                IconButton({
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ro-RO")
                    try { voice.launch(intent) } catch (_: ActivityNotFoundException) { voiceError = true }
                }) { Icon(painterResource(R.drawable.ic_mic), contentDescription = "Comandă vocală") }
            }
            if (voiceError) Text("Recunoașterea vocală nu este disponibilă pe acest telefon. Scrie comanda.")
            TextButton(onDismiss, Modifier.padding(bottom = 16.dp)) { Text("Închide") }
        }
    }
}

@Composable
private fun TurnView(turn: AssistantViewModel.Turn, vm: AssistantViewModel, onEditRule: (RuleDraft) -> Unit) {
    // The transcript is shown so the user can check what was understood (Etapa 5, sarcina 3, scenariul B).
    InfoCard { Text("Tu: ${turn.question}", style = MaterialTheme.typography.bodyLarge) }
    when (val r = turn.reply) {
        is Reply.Answer -> AiCard(r.text)
        is Reply.Clarify -> AiCard(r.question) {
            if (!turn.closed) r.options.forEach { o -> OutlinedButton({ vm.choose(o) }) { Text(o) } }
        }
        is Reply.Draft -> AiCard(
            title = "Am înțeles această regulă",
            text = r.summary,
            why = "Am recunoscut condiția meteo și acțiunea pentru încălzire. Temperatura implicită este 20 °C, " +
                "dacă nu spui alta. Nimic nu se salvează fără acordul tău.",
        ) {
            if (!turn.closed) {
                Button({ vm.save(r.draft) }) { Text("Salvează") }
                OutlinedButton({ onEditRule(r.draft) }) { Text("Modifică") }
                TextButton(vm::cancel) { Text("Anulează") }
            }
        }
    }
}
