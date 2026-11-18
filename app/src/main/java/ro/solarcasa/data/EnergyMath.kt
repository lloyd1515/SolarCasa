package ro.solarcasa.data

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Energy calculations. Pure functions, covered by unit tests.
 *
 * Sources of the constants:
 * - production: PVGIS 5.3 (JRC, European Commission), Cluj-Napoca and Florești, 35° tilt, 14% losses;
 * - electricity price: 2026 household offers in Romania (1.20 – 1.42 lei/kWh), demo value 1.30 lei/kWh;
 * - gas price: household cap of 0.31 lei/kWh (01.04.2026 – 31.03.2027);
 * - exchange rate: BNR, 11.11.2026, 5.3460 lei/€.
 */
object EnergyMath {
    const val BATTERY_KWH = 10.0
    const val MAX_CHARGE_KW = 2.1
    const val MAX_DISCHARGE_KW = 5.0
    const val MIN_RESERVE_PERCENT = 5
    const val PRICE_LEI_PER_KWH = 1.30
    const val GAS_LEI_PER_KWH = 0.31
    const val EUR_RON = 5.35
    const val PANEL_KW = 0.45
    const val PANEL_M2 = 2.16
    const val COST_EUR_PER_KWP = 1000.0
    private const val EVEN_THRESHOLD_KW = 0.3

    /** PVGIS 5.3: monthly production (kWh) of the 6 kWp demo system in Cluj-Napoca, south, 35°. */
    val PVGIS_6KWP_CLUJ = listOf(315.0, 416.7, 644.3, 710.6, 769.3, 796.9, 843.6, 827.8, 703.2, 600.4, 382.9, 258.9)

    /** PVGIS 5.3, Florești: annual yield per kWp by roof orientation, relative to south (1199 kWh/kWp). */
    enum class Orientation(val label: String, val factor: Double) {
        South("Sud", 1.0),
        SouthEastWest("Sud-est / Sud-vest", 0.921),
        EastWest("Est / Vest", 0.762),
        Unknown("Necunoscută", 0.9),
    }

    data class Flows(val solar: Double, val house: Double, val battery: Double, val grid: Double, val balance: Balance)

    fun houseLoad(h: House): Double =
        if (h.gridDown) h.circuits.filter { it.on }.sumOf { it.kw }
        else h.baseLoadKw + h.appliances.filter { it.state == RunState.RunningSolar || it.state == RunState.RunningGrid }.sumOf { it.kw }

    /** Splits the power between battery and grid. Positive battery = charging, positive grid = export. */
    fun flows(h: House): Flows {
        val house = houseLoad(h)
        val surplus = h.solarKw - house
        val battery = if (surplus >= 0) {
            min(surplus, if (h.batteryPercent < 100) MAX_CHARGE_KW else 0.0)
        } else {
            -min(-surplus, if (h.batteryPercent > h.reservePercent) MAX_DISCHARGE_KW else 0.0)
        }
        val grid = if (h.gridDown) 0.0 else surplus - battery
        val balance = when {
            abs(surplus) <= EVEN_THRESHOLD_KW -> Balance.Even
            surplus > 0 -> Balance.Surplus
            else -> Balance.Deficit
        }
        return Flows(h.solarKw, house, battery, grid, balance)
    }

    fun surplusKw(h: House): Double = h.solarKw - houseLoad(h)

    /** Hours until the battery reaches the reserve at a constant load. */
    fun autonomyHours(batteryPercent: Int, reservePercent: Int, loadKw: Double): Double {
        if (loadKw <= 0) return Double.POSITIVE_INFINITY
        val usableKwh = BATTERY_KWH * (batteryPercent - reservePercent).coerceAtLeast(0) / 100
        return usableKwh / loadKw
    }

    /** Extra energy bought from the grid each winter day when the reserve is above the 10% default. */
    fun extraWinterGridKwhPerDay(reservePercent: Int): Double = BATTERY_KWH * (reservePercent - 10).coerceAtLeast(0) / 100

    // ---- Sarcina 1: sizing the PV system --------------------------------------------------------

    data class Plan(
        val panels: Int,
        val kwp: Double,
        val areaM2: Double,
        val monthlyProduction: List<Double>,
        val annualProduction: Double,
        val coveragePercent: Int,
        val costEur: Int,
        val savingsLeiPerYear: Int,
        val paybackYears: Double,
    )

    fun monthlyPerKwp(orientation: Orientation): List<Double> =
        PVGIS_6KWP_CLUJ.map { it / 6 * (1199.4 / 1211.6) * orientation.factor }

    fun maxPanels(roofM2: Double): Int = floor(roofM2 / PANEL_M2).toInt()

    fun recommendedPanels(consumption: List<Double>, orientation: Orientation, roofM2: Double): Int {
        val perPanel = monthlyPerKwp(orientation).sum() * PANEL_KW
        return min(floor(consumption.sum() / perPanel).toInt(), maxPanels(roofM2)).coerceAtLeast(1)
    }

    fun panelsForBudget(budgetEur: Int): Int = floor(budgetEur / (PANEL_KW * COST_EUR_PER_KWP)).toInt().coerceAtLeast(1)

    fun plan(consumption: List<Double>, panels: Int, orientation: Orientation): Plan {
        val kwp = panels * PANEL_KW
        val production = monthlyPerKwp(orientation).map { it * kwp }
        val covered = coveredKwh(production, consumption)
        val cost = kwp * COST_EUR_PER_KWP
        val savings = covered * PRICE_LEI_PER_KWH
        return Plan(
            panels = panels,
            kwp = kwp,
            areaM2 = panels * PANEL_M2,
            monthlyProduction = production,
            annualProduction = production.sum(),
            coveragePercent = (100 * covered / consumption.sum()).toInt(),
            costEur = cost.toInt(),
            savingsLeiPerYear = savings.toInt(),
            paybackYears = cost * EUR_RON / savings,
        )
    }

    /** Monthly balance (quantitative compensation for prosumers): own production covers consumption month by month. */
    fun coveredKwh(production: List<Double>, consumption: List<Double>): Double =
        production.zip(consumption).sumOf { (p, c) -> min(p, c) }

    // ---- Sarcina 10: impact of a new large consumer ---------------------------------------------

    data class Simulation(
        val consumptionBefore: Double,
        val consumptionAfter: Double,
        val coverageBefore: Int,
        val coverageAfter: Int,
        val extraGridLeiPerYear: Int,
        val monthlyAfter: List<Double>,
        val deficitMonths: Set<Int>,
    )

    fun simulate(consumption: List<Double>, added: List<Double>, production: List<Double> = PVGIS_6KWP_CLUJ): Simulation {
        val after = consumption.zip(added) { c, a -> c + a }
        val gridBefore = consumption.sum() - coveredKwh(production, consumption)
        val gridAfter = after.sum() - coveredKwh(production, after)
        return Simulation(
            consumptionBefore = consumption.sum(),
            consumptionAfter = after.sum(),
            coverageBefore = (100 * coveredKwh(production, consumption) / consumption.sum()).toInt(),
            coverageAfter = (100 * coveredKwh(production, after) / after.sum()).toInt(),
            extraGridLeiPerYear = ((gridAfter - gridBefore) * PRICE_LEI_PER_KWH).toInt(),
            monthlyAfter = after,
            deficitMonths = after.indices.filter { after[it] > production[it] * 1.5 }.toSet(),
        )
    }

    /** Coverage after adding [extraKwp] to the 6 kWp system. */
    fun coverageWithExtension(consumption: List<Double>, extraKwp: Double): Int {
        val production = PVGIS_6KWP_CLUJ.map { it * (6 + extraKwp) / 6 }
        return (100 * coveredKwh(production, consumption) / consumption.sum()).toInt()
    }

    // ---- Sarcina 8: electric car -----------------------------------------------------------------

    data class CarPlan(val gridKwhForMinimum: Double, val gridCostLei: Double, val finalPercent: Int)

    /** The car can reach [Car.minPercent] before [Car.minTime] only from the grid when the sun starts later. */
    fun carPlan(car: Car, tomorrowSurplusKwh: Double, sunStartsAfterMinTime: Boolean): CarPlan {
        val needed = (car.minPercent - car.percent).coerceAtLeast(0) * car.capacityKwh / 100.0
        val grid = if (sunStartsAfterMinTime) needed else 0.0
        val final = car.percent + (tomorrowSurplusKwh + if (car.acceptGrid) grid else 0.0) * 100 / car.capacityKwh
        return CarPlan(grid, grid * PRICE_LEI_PER_KWH, final.roundToInt().coerceAtMost(100))
    }

    // ---- Sarcina 7: production report for the supplier -----------------------------------------

    /** Reporting periods. All end on 31.12.2026, the date of the last meter index in the app. */
    enum class ReportPeriod(val label: String, val dates: String, val months: IntRange) {
        Semester("Ultimul semestru", "01.07.2026 – 31.12.2026", 6..11),
        Quarter("Ultimul trimestru", "01.10.2026 – 31.12.2026", 9..11),
        Year("Anul 2026", "01.01.2026 – 31.12.2026", 0..11),
    }

    /** The meter and the inverter count separately, so a difference up to this value is a match. */
    const val INDEX_TOLERANCE_KWH = 10

    enum class Reading { Match, Manual, BelowStart, TooHigh }

    data class SupplierReport(
        val injected: Int,
        val fromGrid: Int,
        val startIndex: Int,
        val endIndex: Int,
        val difference: Int,
        val reading: Reading,
    )

    /**
     * Totals and meter indexes (register 2.8.0) of [period]. A [meterReading] that differs from the app
     * by more than [INDEX_TOLERANCE_KWH] replaces the app index and changes the injected total (Sarcina 7, E1).
     */
    fun supplierReport(period: ReportPeriod, meterReading: Int? = null): SupplierReport {
        val appIndex = DemoData.METER_INDEX_INJECTED
        val appInjected = period.months.sumOf { DemoData.injected[it] }
        val start = appIndex - appInjected
        val difference = meterReading?.minus(appIndex) ?: 0
        val reading = when {
            meterReading == null || abs(difference) <= INDEX_TOLERANCE_KWH -> Reading.Match
            meterReading < start -> Reading.BelowStart
            meterReading > appIndex + appInjected -> Reading.TooHigh
            else -> Reading.Manual
        }
        val end = if (reading == Reading.Manual && meterReading != null) meterReading else appIndex
        return SupplierReport(end - start, period.months.sumOf { DemoData.fromGrid[it] }, start, end, difference, reading)
    }

    private val POD_FORMAT = Regex("RO\\d{3}E\\d{9}")

    /** POD code of a Romanian metering point: "RO", 3 digits, "E", 9 digits (invoice example: RO001E111050736). */
    fun isValidPod(code: String): Boolean = POD_FORMAT.matches(code.filterNot(Char::isWhitespace).uppercase())

    fun sameSerial(a: String, b: String): Boolean =
        a.filterNot(Char::isWhitespace).equals(b.filterNot(Char::isWhitespace), ignoreCase = true)
}
