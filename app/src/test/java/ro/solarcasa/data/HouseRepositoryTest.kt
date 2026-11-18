package ro.solarcasa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HouseRepositoryTest {
    @Test
    fun `undo from the history restores the house and marks the action`() {
        val repo = HouseRepository()
        repo.perform("Rezervă 30%", undo = { it.copy(reservePercent = 10) }) { it.copy(reservePercent = 30) }
        val id = repo.house.value.history.first().id

        repo.undo(id)
        assertEquals(10, repo.house.value.reservePercent)
        assertTrue(repo.house.value.history.first().undone)

        repo.set { it.copy(reservePercent = 40) }
        repo.undo(id) // a second undo changes nothing
        assertEquals(40, repo.house.value.reservePercent)
    }

    @Test
    fun `a demo scenario keeps the history and the car (Scenarii demo)`() {
        val repo = HouseRepository()
        repo.perform("Acțiune") { it }
        repo.set { it.copy(car = it.car.copy(connected = false)) }

        repo.loadScenario(Scenario.LongOutage)
        val h = repo.house.value
        assertEquals(1, h.history.size)
        assertFalse(h.car.connected)
        assertTrue(h.gridDown)
        assertFalse(h.emergencyMinimized)
    }

    @Test
    fun `long outage keeps only the essentials on, about 2 hours left (Sarcina 6, scenariul B)`() {
        val h = DemoData.house(Scenario.LongOutage)
        assertEquals(18, h.batteryPercent)
        assertEquals(h.circuits.filter { it.essential }, h.circuits.filter { it.on })
        assertEquals(2.1, EnergyMath.autonomyHours(h.batteryPercent, h.reservePercent, EnergyMath.houseLoad(h)), 0.1)
    }
}
