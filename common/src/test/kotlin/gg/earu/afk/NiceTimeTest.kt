package gg.earu.afk

import gg.earu.afk.core.NiceTime
import kotlin.test.Test
import kotlin.test.assertEquals

class NiceTimeTest {

    @Test
    fun `sub second floors to zero`() {
        assertEquals("0 seconds", NiceTime.format(0))
        assertEquals("0 seconds", NiceTime.format(-5))
    }

    @Test
    fun `seconds pluralise`() {
        assertEquals("1 second", NiceTime.format(1))
        assertEquals("59 seconds", NiceTime.format(59))
    }

    @Test
    fun `minute boundaries`() {
        assertEquals("1 minute", NiceTime.format(60))
        assertEquals("1 minute 1 second", NiceTime.format(61))
        assertEquals("5 minutes", NiceTime.format(300))
    }

    @Test
    fun `hours drop the smallest unit`() {
        assertEquals("1 hour", NiceTime.format(3600))
        assertEquals("1 hour 3 minutes", NiceTime.format(3600 + 180))
        // Two units max: the trailing seconds are not printed.
        assertEquals("1 hour 3 minutes", NiceTime.format(3600 + 180 + 7))
    }

    @Test
    fun `days and weeks`() {
        assertEquals("1 day", NiceTime.format(86_400))
        assertEquals("2 days 4 hours", NiceTime.format(2 * 86_400 + 4 * 3600))
        assertEquals("1 week 1 day", NiceTime.format(8 * 86_400))
    }
}
