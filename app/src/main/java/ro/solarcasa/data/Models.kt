package ro.solarcasa.data

/** Demo moments from the Etapa 5 scenarios. The presenter switches them in "Mai mult > Scenarii demo". */
enum class Scenario(val title: String, val clock: String) {
    SunnyNoon("Sâmbătă, 12:30 – cer senin", "12:30"),
    CloudyMorning("Marți, 09:30 – cer înnorat", "09:30"),
    ProductionFault("Miercuri, 14:00 – producție scăzută", "14:00"),
    GridOutage("Seara, 20:15 – pană de curent", "20:15"),
}

enum class Balance { Surplus, Even, Deficit }

enum class ApplianceKind { Washer, Dryer, Dishwasher, Oven }

enum class RunState { Off, RunningSolar, RunningGrid, Scheduled }

data class Appliance(
    val id: String,
    val name: String,
    val kind: ApplianceKind,
    val kw: Double,
    val runHours: Double,
    val smart: Boolean,
    val state: RunState = RunState.Off,
    val scheduledAt: String? = null,
)

/** A relay-controlled circuit used in emergency mode (Sarcina 6). */
data class Circuit(
    val id: String,
    val name: String,
    val room: String,
    val kw: Double,
    val essential: Boolean,
    val strictlyNeeded: Boolean = false,
    val vital: Boolean = false,
    val responds: Boolean = true,
    val on: Boolean = true,
)

data class Rule(
    val id: String,
    val name: String,
    val condition: String,
    val action: String,
    val window: String,
    val enabled: Boolean = true,
    val byAssistant: Boolean = false,
)

/** Editable rule in the "DACĂ … ATUNCI …" builder (Sarcina 3). */
data class RuleDraft(
    val condition: String? = null,
    val device: String? = null,
    val temperature: Int = 20,
    val from: String = "06:00",
    val to: String = "22:00",
    val byAssistant: Boolean = false,
)

enum class ProfileId(val title: String) { Normal("Normal"), Night("Noapte"), Vacation("Vacanță"), Eco("Economic") }

enum class Severity { High, Medium }

enum class AlertStatus(val label: String) {
    New("Nouă"), InProgress("În curs"), Monitored("Monitorizată"), Resolved("Rezolvată")
}

data class Alert(
    val id: String,
    val title: String,
    val summary: String,
    val severity: Severity,
    val status: AlertStatus = AlertStatus.New,
    val statusNote: String? = null,
)

enum class ChargeMode(val label: String, val explanation: String) {
    Normal("Normal", "Mașina se încarcă imediat, din orice sursă: soare, baterie sau rețea."),
    SolarOnly(
        "Doar surplus solar",
        "Mașina se încarcă doar când panourile produc mai mult decât consumă casa. Rețeaua nu este folosită.",
    ),
    Smart(
        "Inteligent (AI)",
        "Asistentul folosește soarele când există și rețeaua doar cât trebuie pentru nivelul minim.",
    ),
}

data class Car(
    val connected: Boolean = true,
    val percent: Int = 35,
    val capacityKwh: Int = 60,
    val mode: ChargeMode = ChargeMode.Normal,
    val minPercent: Int = 50,
    val minTime: String = "07:30",
    val acceptGrid: Boolean = false,
    val plan: String? = null,
)

data class ActionRecord(
    val id: Long,
    val label: String,
    val time: String,
    val undo: ((House) -> House)? = null,
    val undone: Boolean = false,
)

/** A message for the user. It is UI state: the UI shows it, then calls `messageShown`. */
data class UserMessage(val id: Long, val text: String, val undoId: Long? = null)

/** The whole state of the demo house: the single source of truth of the app. */
data class House(
    val scenario: Scenario,
    val solarKw: Double,
    val baseLoadKw: Double,
    val batteryPercent: Int,
    val surplusUntil: String?,
    val dataAgeMin: Int = 0,
    val gridDown: Boolean = false,
    val appliances: List<Appliance>,
    val circuits: List<Circuit>,
    val emergencyApplied: Boolean = false,
    val emergencyMinimized: Boolean = false,
    val reservePercent: Int = 10,
    val seasonalReserve: Boolean = false,
    val rules: List<Rule>,
    val profile: ProfileId = ProfileId.Normal,
    val vacationUntil: String? = null,
    val alerts: List<Alert>,
    val car: Car = Car(),
    val boilerScheduled: Boolean = false,
    val boilerFeedbackWrong: Boolean = false,
    val savedPlans: List<String> = emptyList(),
    val savedSimulations: List<String> = emptyList(),
    val prosumerReport: String? = null,
    val history: List<ActionRecord> = emptyList(),
    val message: UserMessage? = null,
)
