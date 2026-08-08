package gg.earu.afk

import gg.earu.afk.core.AfkDetector
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AfkDetectorTest {

    private var now = 0.0
    private fun detector(afkSeconds: Int = 90) = AfkDetector { now }.also { it.afkTimeSeconds = afkSeconds }

    private fun sample(
        mouseX: Double = 0.0,
        mouseY: Double = 0.0,
        anyKeyDown: Boolean = false,
        inputEvents: Long = 0L,
        focused: Boolean = true,
    ) = AfkDetector.Sample(mouseX, mouseY, anyKeyDown, inputEvents, focused)

    /** Final state after a run of idle ticks, plus whether any tick in it reported a transition. */
    private data class Run(val last: AfkDetector.Output, val sawChange: Boolean) {
        val afk get() = last.afk
        val tabbedOut get() = last.tabbedOut
    }

    /** Advance the clock, feeding the same idle sample every simulated tick. */
    private fun idle(d: AfkDetector, seconds: Double, focused: Boolean = true): Run {
        var out = d.tick(sample(focused = focused))
        var sawChange = out.changed
        val end = now + seconds
        while (now < end) {
            now += 0.05
            out = d.tick(sample(focused = focused))
            sawChange = sawChange || out.changed
        }
        return Run(out, sawChange)
    }

    @Test
    fun `warm up suppresses any report`() {
        now = 0.0
        val d = detector(afkSeconds = 1)
        // Well past the 1s threshold but inside the 10s warm-up.
        val out = idle(d, 5.0)
        assertFalse(out.afk)
        assertFalse(out.sawChange)
    }

    @Test
    fun `goes afk after the threshold`() {
        now = 0.0
        val d = detector(afkSeconds = 30)
        assertFalse(idle(d, 20.0).afk)
        val out = idle(d, 30.0)
        assertTrue(out.afk)
    }

    @Test
    fun `mouse movement clears afk`() {
        now = 0.0
        val d = detector(afkSeconds = 30)
        assertTrue(idle(d, 60.0).afk)

        now += 0.05
        val out = d.tick(sample(mouseX = 12.0))
        assertFalse(out.afk)
        assertTrue(out.changed)
    }

    @Test
    fun `key edge counts as input but holding does not`() {
        now = 0.0
        val d = detector(afkSeconds = 30)
        idle(d, 20.0)

        // Press: an edge, so activity.
        now += 0.05
        assertFalse(d.tick(sample(anyKeyDown = true)).afk)

        // Held for the whole threshold with nothing else moving: still goes afk, like the Lua.
        var out = d.tick(sample(anyKeyDown = true))
        val end = now + 40.0
        while (now < end) {
            now += 0.05
            out = d.tick(sample(anyKeyDown = true))
        }
        assertTrue(out.afk)
    }

    @Test
    fun `typing clears afk`() {
        now = 0.0
        val d = detector(afkSeconds = 30)
        assertTrue(idle(d, 60.0).afk)

        // Straight into chat: no bound key, no mouse movement, only raw key events.
        now += 0.05
        val out = d.tick(sample(inputEvents = 1L))
        assertFalse(out.afk)
        assertTrue(out.changed)
    }

    @Test
    fun `losing focus flags afk once the threshold passes`() {
        now = 0.0
        val d = detector(afkSeconds = 30)
        idle(d, 15.0)
        // Unfocused but still moving the mouse: focus alone drives this once it ages out.
        var out = d.tick(sample(focused = false))
        val end = now + 40.0
        var x = 0.0
        while (now < end) {
            now += 0.05
            x += 1.0
            out = d.tick(sample(mouseX = x, focused = false))
        }
        assertTrue(out.afk)
        assertTrue(out.tabbedOut)
    }

    @Test
    fun `tabbed out is debounced`() {
        now = 0.0
        val d = detector()
        idle(d, 12.0)

        now += 0.05
        assertFalse(d.tick(sample(focused = false)).tabbedOut, "instant alt-tab should not report yet")

        val out = idle(d, 2.0, focused = false)
        assertTrue(out.tabbedOut)
    }

    @Test
    fun `tabbed out clears on refocus`() {
        now = 0.0
        val d = detector()
        idle(d, 12.0)
        assertTrue(idle(d, 2.0, focused = false).tabbedOut)

        now += 0.05
        assertFalse(d.tick(sample(focused = true)).tabbedOut)
    }

    @Test
    fun `suppressed input does not register`() {
        now = 0.0
        val d = detector(afkSeconds = 30)
        assertTrue(idle(d, 60.0).afk)

        d.suppressInput(0.1)
        now += 0.05
        // Mouse jumped during the suppress window, so it must not count as waking up.
        assertTrue(d.tick(sample(mouseX = 99.0)).afk)

        now += 0.2
        assertFalse(d.tick(sample(mouseX = 120.0)).afk, "input after the window counts again")
    }

    @Test
    fun `changed only fires on transitions`() {
        now = 0.0
        val d = detector(afkSeconds = 30)
        assertTrue(idle(d, 60.0).sawChange, "first flip reports")
        assertFalse(idle(d, 10.0).sawChange, "staying afk does not re-report")
    }

    @Test
    fun `reset re-arms the warm up`() {
        now = 0.0
        val d = detector(afkSeconds = 30)
        assertTrue(idle(d, 60.0).afk)

        d.reset()
        assertFalse(idle(d, 5.0).afk)
    }
}
