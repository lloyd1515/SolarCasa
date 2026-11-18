package ro.solarcasa.navigation

import androidx.annotation.DrawableRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import ro.solarcasa.R
import ro.solarcasa.data.ProfileId
import ro.solarcasa.data.RuleDraft

/** Top-level destinations. [wideOnly] tabs appear in the rail on tablets; on phones they are in "Mai mult". */
enum class Tab(val label: String, @param:DrawableRes val icon: Int, val wideOnly: Boolean = false) {
    Home("Acasă", R.drawable.ic_home),
    Energy("Energie", R.drawable.ic_bolt),
    Automations("Automatizări", R.drawable.ic_auto_mode),
    Reports("Rapoarte", R.drawable.ic_bar_chart),
    Planning("Planificare", R.drawable.ic_architecture, wideOnly = true),
    Prosumer("Prosumator", R.drawable.ic_swap_horiz, wideOnly = true),
    More("Mai mult", R.drawable.ic_more_horiz),
}

// Sub-screens. Data objects and data classes give stable keys for the saved state of each entry.
data object Appliances
data object BatterySettings
data class ConsumerDetail(val id: String)
data class RuleBuilder(val draft: RuleDraft? = null)
data class ProfileDetail(val id: ProfileId)
data object Alerts
data class AlertDetail(val id: String)
data class ServiceRequest(val alertId: String)
data object Garage
data object Simulations
data object ProsumerReport
data object History
data object DemoScenarios

/**
 * One back stack per tab, flattened for NavDisplay (Navigation 3 "Common UI" recipe).
 * It is a ViewModel, so the stacks survive a screen rotation.
 */
class Navigator : ViewModel() {
    private val stacks = linkedMapOf<Tab, MutableList<Any>>(Tab.Home to mutableListOf(Tab.Home))

    var currentTab by mutableStateOf(Tab.Home)
        private set

    val backStack = mutableStateListOf<Any>(Tab.Home)

    /** Opens a tab with its saved history. A second tap on the current tab returns to its first screen. */
    fun selectTab(tab: Tab) {
        stacks[tab] = if (tab == currentTab) mutableListOf(tab) else stacks.remove(tab) ?: mutableListOf(tab)
        currentTab = tab
        sync()
    }

    /** Opens a sub-screen in the current tab. A key can be only once in NavDisplay, so older copies go away. */
    fun navigate(route: Any) {
        stacks.values.forEach { it.remove(route) }
        stacks.entries.removeAll { it.value.isEmpty() }
        stacks.getValue(currentTab).add(route)
        sync()
    }

    fun back() {
        if (backStack.size <= 1) return
        val stack = stacks.getValue(currentTab)
        stack.removeAt(stack.lastIndex)
        if (stack.isEmpty()) stacks.remove(currentTab)
        currentTab = stacks.keys.last()
        sync()
    }

    private fun sync() {
        backStack.clear()
        backStack.addAll(stacks.values.flatten())
    }
}
