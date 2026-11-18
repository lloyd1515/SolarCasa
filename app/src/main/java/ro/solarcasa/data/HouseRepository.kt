package ro.solarcasa.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * Data layer: the single source of truth for the demo house.
 * In a real app this class would read the inverter, the battery and the smart relays.
 */
class HouseRepository(initial: House = DemoData.house(Scenario.SunnyNoon)) {
    private val state = MutableStateFlow(initial)
    private val ids = AtomicLong(1)

    val house: StateFlow<House> = state.asStateFlow()

    /** Changes the house without a history entry (form edits, navigation-only state). */
    fun set(change: (House) -> House) = state.update(change)

    /**
     * Runs a user action. The action goes to "Istoric acțiuni"; with [undo] it can be undone
     * from the message ("Anulează") or from the history.
     */
    fun perform(label: String, message: String? = label, undo: ((House) -> House)? = null, change: (House) -> House) {
        val id = ids.getAndIncrement()
        state.update { h ->
            change(h).copy(
                history = listOf(ActionRecord(id, label, h.scenario.clock, undo)) + h.history,
                message = message?.let { UserMessage(id, it, undoId = id.takeIf { undo != null }) },
            )
        }
    }

    fun undo(actionId: Long) = state.update { h ->
        val record = h.history.find { it.id == actionId && !it.undone } ?: return@update h
        val undo = record.undo ?: return@update h
        undo(h).copy(
            history = h.history.map { if (it.id == actionId) it.copy(undone = true) else it },
            message = UserMessage(ids.getAndIncrement(), "Anulat: ${record.label}"),
        )
    }

    fun show(text: String) = state.update { it.copy(message = UserMessage(ids.getAndIncrement(), text)) }

    fun messageShown(id: Long) = state.update { if (it.message?.id == id) it.copy(message = null) else it }

    /** Loads a demo moment and keeps the user's own settings and history. */
    fun loadScenario(scenario: Scenario) = state.update { old ->
        DemoData.house(scenario).copy(
            rules = old.rules,
            reservePercent = old.reservePercent,
            seasonalReserve = old.seasonalReserve,
            car = old.car,
            savedPlans = old.savedPlans,
            savedSimulations = old.savedSimulations,
            history = old.history,
        )
    }

    /** Maps the house to the UI state of one screen (see 02_architecture_recommendations). */
    fun <T> uiState(scope: CoroutineScope, map: (House) -> T): StateFlow<T> =
        house.map(map).stateIn(scope, SharingStarted.WhileSubscribed(5_000), map(house.value))
}
