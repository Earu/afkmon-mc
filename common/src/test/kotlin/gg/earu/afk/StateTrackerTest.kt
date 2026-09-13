package gg.earu.afk

import gg.earu.afk.api.AfkFlags
import gg.earu.afk.api.PlayerState
import gg.earu.afk.core.StateTracker
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class StateTrackerTest {

    private val uuid = UUID.randomUUID()

    @Test
    fun `timing out outranks afk outranks tabbed out`() {
        assertEquals(PlayerState.TIMING_OUT, StateTracker.reduce(AfkFlags(afk = true, tabbedOut = true, timingOut = true)))
        assertEquals(PlayerState.AFK, StateTracker.reduce(AfkFlags(afk = true, tabbedOut = true)))
        assertEquals(PlayerState.TABBED_OUT, StateTracker.reduce(AfkFlags(tabbedOut = true)))
        assertEquals(PlayerState.ACTIVE, StateTracker.reduce(AfkFlags()))
    }

    @Test
    fun `unknown players are active for zero seconds`() {
        val tracker = StateTracker(now = { 50_000L })
        assertEquals(PlayerState.ACTIVE, tracker.stateOf(uuid))
        assertEquals(0L, tracker.secondsInStateOf(uuid))
    }

    @Test
    fun `afk time counts from the backdated timestamp`() {
        var now = 100_000L
        val tracker = StateTracker(now = { now })
        tracker.update(uuid, AfkFlags(afk = true, sinceEpochMs = 10_000L))
        assertEquals(90L, tracker.secondsInStateOf(uuid))
        now = 110_000L
        assertEquals(100L, tracker.secondsInStateOf(uuid))
    }

    @Test
    fun `state time resets on a change and holds while flags shuffle underneath`() {
        var now = 100_000L
        val tracker = StateTracker(now = { now })
        tracker.update(uuid, AfkFlags(tabbedOut = true, sinceEpochMs = now))
        now = 105_000L
        assertEquals(5L, tracker.secondsInStateOf(uuid))
        // Timing out on top of tabbed out is a new state.
        tracker.update(uuid, AfkFlags(tabbedOut = true, timingOut = true, sinceEpochMs = 100_000L))
        assertEquals(PlayerState.TIMING_OUT, tracker.stateOf(uuid))
        assertEquals(0L, tracker.secondsInStateOf(uuid))
        // Going afk while still timing out keeps the state, so the clock keeps running.
        now = 108_000L
        tracker.update(uuid, AfkFlags(afk = true, tabbedOut = true, timingOut = true, sinceEpochMs = 100_000L))
        assertEquals(3L, tracker.secondsInStateOf(uuid))
        assertEquals(true, tracker.flagsOf(uuid).afk)
    }

    @Test
    fun `flagged players lists everyone but the active, as a snapshot`() {
        val tracker = StateTracker(now = { 0L })
        val other = UUID.randomUUID()
        assertEquals(emptyMap(), tracker.flaggedPlayers())
        tracker.update(uuid, AfkFlags(afk = true))
        tracker.update(other, AfkFlags())
        assertEquals(mapOf(uuid to PlayerState.AFK), tracker.flaggedPlayers())
        val snapshot = tracker.flaggedPlayers()
        tracker.update(uuid, AfkFlags())
        assertEquals(mapOf(uuid to PlayerState.AFK), snapshot)
        assertEquals(emptyMap(), tracker.flaggedPlayers())
        assertEquals(true, tracker.isActive(uuid))
    }

    @Test
    fun `listeners hear every reduced state change, never seen counts as active`() {
        val heard = mutableListOf<Pair<PlayerState, PlayerState>>()
        val tracker = StateTracker(now = { 0L }, onChange = { _, a, b, _ -> heard.add(a to b) })
        // A server join, or a clear payload for a stranger: active to active is silent.
        tracker.update(uuid, AfkFlags())
        assertEquals(0, heard.size)
        tracker.update(uuid, AfkFlags(afk = true))
        assertEquals(listOf(PlayerState.ACTIVE to PlayerState.AFK), heard)
        // Same reduced state, no event.
        tracker.update(uuid, AfkFlags(afk = true, tabbedOut = true))
        assertEquals(1, heard.size)
        tracker.update(uuid, AfkFlags())
        assertEquals(PlayerState.AFK to PlayerState.ACTIVE, heard.last())
        // The client's first news about a player is usually that they went away.
        val other = UUID.randomUUID()
        tracker.update(other, AfkFlags(timingOut = true))
        assertEquals(PlayerState.ACTIVE to PlayerState.TIMING_OUT, heard.last())
    }

    @Test
    fun `each flag keeps its own clock`() {
        var now = 100_000L
        val tracker = StateTracker(now = { now })
        assertEquals(0L, tracker.secondsAfk(uuid))
        tracker.update(uuid, AfkFlags(afk = true, sinceEpochMs = 10_000L))
        now = 120_000L
        tracker.update(uuid, AfkFlags(afk = true, tabbedOut = true, sinceEpochMs = 10_000L))
        now = 125_000L
        tracker.update(uuid, AfkFlags(afk = true, tabbedOut = true, timingOut = true, sinceEpochMs = 10_000L))
        now = 130_000L
        assertEquals(120L, tracker.secondsAfk(uuid))
        assertEquals(10L, tracker.secondsTabbedOut(uuid))
        assertEquals(5L, tracker.secondsTimingOut(uuid))
        // The reduced state is TIMING_OUT and its clock is the youngest of the three.
        assertEquals(5L, tracker.secondsInStateOf(uuid))
        // A flag going off drops its clock, the others keep theirs.
        tracker.update(uuid, AfkFlags(afk = true, timingOut = true, sinceEpochMs = 10_000L))
        assertEquals(0L, tracker.secondsTabbedOut(uuid))
        assertEquals(120L, tracker.secondsAfk(uuid))
    }

    @Test
    fun `state changes carry the seconds spent in the previous state`() {
        var now = 100_000L
        val heard = mutableListOf<Long>()
        val tracker = StateTracker(now = { now }, onChange = { _, _, _, seconds -> heard.add(seconds) })
        // Never seen: nothing to measure.
        tracker.update(uuid, AfkFlags(afk = true, sinceEpochMs = 40_000L))
        assertEquals(listOf(0L), heard)
        now = 160_000L
        tracker.update(uuid, AfkFlags())
        // Backdated afk: 160s minus the 40s start.
        assertEquals(listOf(0L, 120L), heard)
        now = 190_000L
        tracker.update(uuid, AfkFlags(tabbedOut = true, sinceEpochMs = now))
        assertEquals(listOf(0L, 120L, 30L), heard)
    }

    @Test
    fun `flag listeners hear every flip, state listeners only the reduced change`() {
        val flags = mutableListOf<Pair<AfkFlags, AfkFlags>>()
        val states = mutableListOf<Pair<PlayerState, PlayerState>>()
        val tracker = StateTracker(
            now = { 0L },
            onChange = { _, a, b, _ -> states.add(a to b) },
            onFlagsChange = { _, a, b -> flags.add(a to b) },
        )
        tracker.update(uuid, AfkFlags(afk = true))
        // Tabbing out while away: the reduced state stays AFK.
        tracker.update(uuid, AfkFlags(afk = true, tabbedOut = true))
        // Only the timestamp moved: silence on both.
        tracker.update(uuid, AfkFlags(afk = true, tabbedOut = true, sinceEpochMs = 5L))
        assertEquals(2, flags.size)
        assertEquals(AfkFlags(afk = true) to AfkFlags(afk = true, tabbedOut = true), flags[1])
        assertEquals(listOf(PlayerState.ACTIVE to PlayerState.AFK), states)
    }

    @Test
    fun `a flagged player leaving is a return to active, an active one leaves silently`() {
        var now = 100_000L
        val heard = mutableListOf<Triple<PlayerState, PlayerState, Long>>()
        val flags = mutableListOf<AfkFlags>()
        val tracker = StateTracker(
            now = { now },
            onChange = { _, a, b, s -> heard.add(Triple(a, b, s)) },
            onFlagsChange = { _, _, b -> flags.add(b) },
        )
        tracker.update(uuid, AfkFlags())
        tracker.remove(uuid)
        assertEquals(0, heard.size)
        tracker.update(uuid, AfkFlags(tabbedOut = true, sinceEpochMs = now))
        now = 107_000L
        tracker.remove(uuid)
        assertEquals(Triple(PlayerState.TABBED_OUT, PlayerState.ACTIVE, 7L), heard.last())
        assertEquals(AfkFlags(), flags.last())
        assertEquals(PlayerState.ACTIVE, tracker.stateOf(uuid))
        assertEquals(emptyMap(), tracker.flaggedPlayers())
    }

    @Test
    fun `threshold and mod check are whatever the side installed`() {
        val tracker = StateTracker(now = { 0L })
        assertEquals(0, tracker.afkTimeSeconds())
        assertEquals(true, tracker.hasMod(uuid))
        tracker.setAfkTimeSeconds(90)
        val modded = setOf(uuid)
        tracker.setHasMod(modded::contains)
        assertEquals(90, tracker.afkTimeSeconds())
        assertEquals(true, tracker.hasMod(uuid))
        assertEquals(false, tracker.hasMod(UUID.randomUUID()))
    }
}
