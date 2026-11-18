package ro.solarcasa.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun `numbers use the Romanian format and keep their value`() {
        assertEquals("4,8\u00A0kW", 4.8.kw())
        assertEquals("6.800\u00A0kWh", 6800.0.kwh())
        assertEquals("1,30", 1.3.fmt(2))
        assertEquals("+64%", 64.signedPercent())
    }
}
