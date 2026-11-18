package ro.solarcasa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyMathTest {
    private val sunny = DemoData.house(Scenario.SunnyNoon)

    @Test
    fun `sunny noon splits the surplus between battery and grid (Sarcina 2)`() {
        val f = EnergyMath.flows(sunny)
        assertEquals(Balance.Surplus, f.balance)
        assertEquals(1.2, f.house, 0.01)
        assertEquals(2.1, f.battery, 0.01)
        assertEquals(1.5, f.grid, 0.01)
    }

    @Test
    fun `washing machine uses the surplus and stops the export`() {
        val running = sunny.copy(appliances = sunny.appliances.map { if (it.id == "washer") it.copy(state = RunState.RunningSolar) else it })
        val f = EnergyMath.flows(running)
        assertEquals(3.2, f.house, 0.01)
        assertEquals(1.6, f.battery, 0.01)
        assertEquals(0.0, f.grid, 0.01)
    }

    @Test
    fun `cloudy morning is a deficit covered by the battery`() {
        val f = EnergyMath.flows(DemoData.house(Scenario.CloudyMorning))
        assertEquals(Balance.Deficit, f.balance)
        assertEquals(-0.8, f.battery, 0.01)
        assertEquals(0.0, f.grid, 0.01)
    }

    @Test
    fun `only essentials give about 14 hours, plus the kids lights about 12 hours (Sarcina 6)`() {
        val circuits = DemoData.house(Scenario.GridOutage).circuits
        val essentials = circuits.filter { it.essential }.sumOf { it.kw }
        assertEquals(14.0, EnergyMath.autonomyHours(64, 10, essentials), 0.5)
        val withKids = essentials + circuits.first { it.id == "kids" }.kw
        assertEquals(12.0, EnergyMath.autonomyHours(64, 10, withKids), 0.5)
    }

    @Test
    fun `reserve of 20 percent costs 1 kWh per winter day (Sarcina 11)`() {
        assertEquals(1.0, EnergyMath.extraWinterGridKwhPerDay(20), 0.001)
        assertEquals(0.0, EnergyMath.extraWinterGridKwhPerDay(10), 0.001)
    }

    @Test
    fun `planning recommends 12 panels for the Florești house and respects the roof (Sarcina 1)`() {
        val consumption = DemoData.plannedConsumption
        assertEquals(12, EnergyMath.recommendedPanels(consumption, EnergyMath.Orientation.South, roofM2 = 40.0))
        assertEquals(9, EnergyMath.recommendedPanels(consumption, EnergyMath.Orientation.South, roofM2 = 20.0))
        val plan = EnergyMath.plan(consumption, 12, EnergyMath.Orientation.South)
        assertEquals(5.4, plan.kwp, 0.001)
        assertTrue(plan.coveragePercent in 60..80)
        assertEquals(10, EnergyMath.panelsForBudget(4500))
    }

    @Test
    fun `heat pump raises consumption from 5900 to 9400 kWh (Sarcina 10)`() {
        val item = DemoData.catalog.first()
        val sim = EnergyMath.simulate(DemoData.CONSUMPTION_2026, item.shares.map { it * item.annualKwh })
        assertEquals(5900.0, sim.consumptionBefore, 1.0)
        assertEquals(9400.0, sim.consumptionAfter, 1.0)
        assertTrue(sim.coverageAfter < sim.coverageBefore)
        assertTrue(0 in sim.deficitMonths && 11 in sim.deficitMonths)
    }

    @Test
    fun `solar-only charging reaches about 72 percent and 50 percent needs 9 kWh from the grid (Sarcina 8)`() {
        val plan = EnergyMath.carPlan(Car(), tomorrowSurplusKwh = 22.0, sunStartsAfterMinTime = true)
        assertEquals(9.0, plan.gridKwhForMinimum, 0.01)
        assertEquals(11.7, plan.gridCostLei, 0.01)
        assertEquals(72, plan.finalPercent)
    }

    @Test
    fun `assistant asks for clarification on an ambiguous command (Sarcina 3, scenario B)`() {
        assertTrue(SolarAssistant.interpret("Când e urât afară", sunny) is SolarAssistant.Reply.Clarify)
        val reply = SolarAssistant.interpret("Când mâine e înnorat, pune încălzirea pe economic la 19 grade", sunny)
        assertTrue(reply is SolarAssistant.Reply.Draft)
        assertEquals(19, (reply as SolarAssistant.Reply.Draft).draft.temperature)
    }

    @Test
    fun `hours are rounded to 10 minutes`() {
        assertEquals("≈ 3 h", SolarAssistant.hours(2.98))
        assertEquals("≈ 2 h 30 min", SolarAssistant.hours(2.5))
        assertEquals("≈ 40 min", SolarAssistant.hours(0.66))
        assertEquals("≈ 14 ore", SolarAssistant.hours(14.2))
    }

    @Test
    fun `second half of 2026 matches the Etapa 5 scenario (Sarcina 7)`() {
        val r = EnergyMath.supplierReport(EnergyMath.ReportPeriod.Semester)
        assertEquals(2184, r.injected)
        assertEquals(1045, r.fromGrid)
        assertEquals(6228, r.startIndex)
        assertEquals(EnergyMath.Reading.Match, r.reading)
    }

    @Test
    fun `a meter reading 27 kWh higher replaces the app index (Sarcina 7, E1)`() {
        val r = EnergyMath.supplierReport(EnergyMath.ReportPeriod.Semester, meterReading = 8439)
        assertEquals(EnergyMath.Reading.Manual, r.reading)
        assertEquals(27, r.difference)
        assertEquals(8439, r.endIndex)
        assertEquals(2211, r.injected)
    }

    @Test
    fun `small differences are a match and impossible readings are rejected`() {
        val p = EnergyMath.ReportPeriod.Semester
        assertEquals(EnergyMath.Reading.Match, EnergyMath.supplierReport(p, 8418).reading)
        assertEquals(8412, EnergyMath.supplierReport(p, 8418).endIndex)
        assertEquals(EnergyMath.Reading.BelowStart, EnergyMath.supplierReport(p, 6000).reading)
        assertEquals(EnergyMath.Reading.TooHigh, EnergyMath.supplierReport(p, 84120).reading)
    }

    @Test
    fun `POD codes need RO, 3 digits, E and 9 digits`() {
        assertTrue(EnergyMath.isValidPod("RO005E512345678"))
        assertTrue(EnergyMath.isValidPod("ro005e 512 345 678"))
        assertTrue(!EnergyMath.isValidPod("RO005E51234567"))
        assertTrue(!EnergyMath.isValidPod(""))
        assertTrue(EnergyMath.sameSerial("ZMD36210455", "ZMD 36 210 455"))
    }
}
