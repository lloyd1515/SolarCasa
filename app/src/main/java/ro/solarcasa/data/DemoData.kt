package ro.solarcasa.data

/**
 * Demo house: Cluj-Napoca, 6 kWp (PVGIS 7,269 kWh/year), 10 kWh battery, gas boiler for heating,
 * electric water heater, split heat pump in the living room, 5,900 kWh/year consumption.
 * The numbers follow the Etapa 5 scenarios.
 */
object DemoData {
    val CONSUMPTION_2026 = listOf(560.0, 500.0, 490.0, 460.0, 450.0, 470.0, 500.0, 490.0, 450.0, 470.0, 500.0, 560.0)
    val MONTHS = listOf("ian", "feb", "mar", "apr", "mai", "iun", "iul", "aug", "sep", "oct", "nov", "dec")

    fun house(scenario: Scenario): House {
        val base = House(
            scenario = scenario,
            solarKw = 4.8,
            baseLoadKw = 1.2,
            batteryPercent = 76,
            surplusUntil = "15:30",
            appliances = appliances,
            circuits = circuits,
            rules = rules,
            alerts = listOf(dustAlert),
        )
        return when (scenario) {
            Scenario.SunnyNoon -> base
            Scenario.CloudyMorning -> base.copy(solarKw = 1.1, baseLoadKw = 1.9, batteryPercent = 48, surplusUntil = null)
            Scenario.ProductionFault -> base.copy(
                solarKw = 2.4, baseLoadKw = 0.9, batteryPercent = 82, alerts = listOf(stringAlert, dustAlert),
            )
            Scenario.GridOutage -> base.copy(solarKw = 0.0, batteryPercent = 64, surplusUntil = null, gridDown = true)
        }
    }

    private val appliances = listOf(
        Appliance("washer", "Mașina de spălat", ApplianceKind.Washer, kw = 2.0, runHours = 1.5, smart = true),
        Appliance("dryer", "Uscătorul", ApplianceKind.Dryer, kw = 2.5, runHours = 1.5, smart = true),
        Appliance("dishwasher", "Mașina de spălat vase", ApplianceKind.Dishwasher, kw = 1.8, runHours = 2.0, smart = true),
        Appliance("oven", "Cuptorul electric", ApplianceKind.Oven, kw = 3.8, runHours = 1.0, smart = false),
    )

    private val circuits = listOf(
        Circuit("fridge", "Frigider", "Bucătărie", 0.12, essential = true, strictlyNeeded = true, vital = true),
        Circuit("heating", "Centrală / pompă circulație", "Camera tehnică", 0.15, essential = true, strictlyNeeded = true),
        Circuit("alarm", "Alarmă și camere", "Hol", 0.04, essential = true),
        Circuit("router", "Router internet", "Birou", 0.02, essential = true),
        Circuit("hall", "Lumini hol", "Hol", 0.05, essential = true),
        Circuit("kids", "Lumini dormitor copii", "Dormitor copii", 0.07, essential = false),
        Circuit("living", "Lumini living", "Living", 0.10, essential = false),
        Circuit("tv", "Televizor și console", "Living", 0.12, essential = false),
        Circuit("oven", "Cuptor", "Bucătărie", 0.70, essential = false, responds = false),
        Circuit("boiler", "Boiler electric", "Baie", 0.60, essential = false),
        Circuit("garage", "Priză garaj", "Garaj", 0.15, essential = false),
    )

    private val rules = listOf(
        Rule("comfort", "Confort", "Cineva este acasă", "Pompa de căldură (split living) la 22 °C", "06:00 – 23:00"),
        Rule("boiler", "Boiler din surplus", "Surplus solar peste 2 kW", "Boiler electric pornit", "10:00 – 16:00"),
    )

    val stringAlert = Alert(
        "string2", "Producție cu 45% sub așteptări",
        "Producția solară este cu 45% sub cea așteptată pentru o zi senină.", Severity.High,
    )
    val dustAlert = Alert(
        "dust", "Producție în scădere lentă",
        "−12% în ultimele 3 săptămâni, după normalizarea cu vremea.", Severity.Medium,
    )

    // ---- Sarcina 5: today's production, hourly (kW), 06:00 – 20:00 ----------------------------
    val alertHours = (6..20).map { "%02d".format(it) }
    val expectedToday = listOf(0.1, 0.6, 1.5, 2.6, 3.6, 4.3, 4.6, 4.5, 4.1, 3.4, 2.5, 1.5, 0.7, 0.2, 0.0)
    val realToday = expectedToday.mapIndexed { i, v -> if (i >= 4) v * 0.52 else v }

    // ---- Sarcina 4: energy bought from the grid, per week (kWh) ---------------------------------
    val weeksNov2026 = listOf(62.0, 74.0, 84.0, 92.0)
    val weeksOct2026 = listOf(48.0, 52.0, 58.0, 68.0)
    val weeksNov2025 = listOf(86.0, 96.0, 104.0, 114.0)
    const val SOLD_NOV_2026 = 96

    // ---- Sarcina 7: prosumer, second half of 2026 (kWh) ----------------------------------------
    val prosumerMonths = listOf("iulie", "august", "septembrie", "octombrie", "noiembrie", "decembrie")
    val injected = listOf(600, 580, 498, 370, 96, 40)
    val fromGrid = listOf(90, 95, 118, 226, 312, 204)
    const val INJECTED_2026 = 3984
    const val FROM_GRID_2026 = 2615
    const val METER_INDEX_INJECTED = 8412
    const val SUPPLIER = "Electrica Furnizare"
    const val POD = "RO005E512345678"
    const val METER_SERIAL = "ZMD 36 210 455"

    // ---- Sarcina 12: consumers in November 2026 -------------------------------------------------
    data class Consumer(val id: String, val name: String, val kwh: Int, val changePercent: Int, val measured: Boolean = true)

    val consumers = listOf(
        Consumer("boiler", "Boiler electric", 172, 64),
        Consumer("heatpump", "Pompă de căldură (split living)", 96, 18),
        Consumer("lights", "Iluminat", 41, 12),
        Consumer("fridge", "Frigider", 38, 0),
        Consumer("it", "Televizor, router, calculatoare", 35, 3),
        Consumer("oven", "Cuptor electric", 30, -6),
        Consumer("washer", "Mașina de spălat", 22, -4),
        Consumer("dryer", "Uscătorul", 18, 5, measured = false),
    )

    /** Boiler consumption (kWh) for the last 14 nights × 24 hours: night heating started on 28.10. */
    val boilerHeatmap: List<List<Double>> = List(14) { day ->
        List(24) { hour ->
            when {
                day >= 2 && hour in 1..4 -> 1.1 + (day % 3) * 0.1
                hour in 18..20 -> 0.9
                hour in 6..7 -> 0.5
                else -> 0.05
            }
        }
    }

    // ---- Sarcina 1: profile of the planned house in Florești -----------------------------------
    val plannedConsumption = listOf(900.0, 780.0, 640.0, 450.0, 380.0, 360.0, 380.0, 380.0, 400.0, 520.0, 720.0, 890.0)

    // ---- Sarcina 10: catalog of large consumers, extra kWh per month ----------------------------
    data class CatalogItem(val name: String, val typicalKw: ClosedFloatingPointRange<Double>, val defaultKw: Double, val annualKwh: Double, val shares: List<Double>, val note: String)

    val catalog = listOf(
        CatalogItem(
            "Pompă de căldură aer–apă", 5.0..12.0, 8.0, 3500.0,
            listOf(.19, .16, .12, .06, .02, 0.0, 0.0, 0.0, .02, .08, .15, .20),
            "Înlocuiește centrala pe gaz: 14.000 kWh căldură pe an, SCOP 4.",
        ),
        CatalogItem(
            "Mașină electrică", 3.7..11.0, 7.4, 2300.0, List(12) { 1.0 / 12 },
            "40 km pe zi, 15,7 kWh / 100 km.",
        ),
        CatalogItem(
            "Aer condiționat", 2.5..7.0, 3.5, 600.0,
            listOf(0.0, 0.0, 0.0, 0.0, .1, .3, .35, .25, 0.0, 0.0, 0.0, 0.0),
            "Răcire în lunile calde.",
        ),
        CatalogItem(
            "Piscină (pompă și încălzire)", 1.0..5.0, 2.0, 1500.0,
            listOf(0.0, 0.0, 0.0, 0.0, .2, .3, .3, .2, 0.0, 0.0, 0.0, 0.0),
            "Sezonul mai – august.",
        ),
    )

    /** Heat no longer produced with gas when a heat pump is added (kWh, boiler efficiency 90%). */
    const val GAS_REPLACED_KWH = 14000 / 0.9
}
