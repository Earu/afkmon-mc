package gg.earu.afk

import gg.earu.afk.api.PlayerState
import gg.earu.afk.core.PlayerAfkState
import gg.earu.afk.core.StateTracker
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class StateTrackerTest {

    private val uuid = UUID.randomUUID()

    @Test
    fun `timing out outranks afk outranks tabbed out`() {
        assertEquals(PlayerState.TIMING_OUT, StateTracker.reduce(PlayerAfkState(afk = true, tabbedOut = true, timingOut = true)))
        assertEquals(PlayerState.AFK, StateTracker.reduce(PlayerAfkState(afk = true, tabbedOut = true)))
        assertEquals(PlayerState.TABBED_OUT, StateTracker.reduce(PlayerAfkState(tabbedOut = true)))
        assertEquals(PlayerState.ACTIVE, StateTracker.reduce(PlayerAfkState()))
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
        tracker.update(uuid, PlayerAfkState(afk = true, sinceEpochMs = 10_000L))
        assertEquals(90L, tracker.secondsInStateOf(uuid))
        now = 110_000L
        assertEquals(100L, tracker.secondsInStateOf(uuid))
    }

    @Test
    fun `state time resets on a change and holds while flags shuffle underneath`() {
        var now = 100_000L
        val tracker = StateTracker(now = { now })
        tracker.update(uuid, PlayerAfkState(tabbedOut = true, sinceEpochMs = now))
        now = 105_000L
        assertEquals(5L, tracker.secondsInStateOf(uuid))
        // Timing out on top of tabbed out is a new state.
        tracker.update(uuid, PlayerAfkState(tabbedOut = true, timingOut = true, sinceEpochMs = 100_000L))
        assertEquals(PlayerState.TIMING_OUT, tracker.stateOf(uuid))
        assertEquals(0L, tracker.secondsInStateOf(uuid))
        // Going afk while still timing out keeps the state, so the clock keeps running.
        now = 108_000L
        tracker.update(uuid, PlayerAfkState(afk = true, tabbedOut = true, timingOut = true, sinceEpochMs = 100_000L))
        assertEquals(3L, tracker.secondsInStateOf(uuid))
        assertEquals(true, tracker.flagsOf(uuid).afk)
    }

    @Test
    fun `listeners hear every reduced state change, never seen counts as active`() {
        val heard = mutableListOf<Pair<PlayerState, PlayerState>>()
        val tracker = StateTracker(now = { 0L }, onChange = { _, a, b -> heard.add(a to b) })
        // A server join, or a clear payload for a stranger: active to active is silent.
        tracker.update(uuid, PlayerAfkState())
        assertEquals(0, heard.size)
        tracker.update(uuid, PlayerAfkState(afk = true))
        assertEquals(listOf(PlayerState.ACTIVE to PlayerState.AFK), heard)
        // Same reduced state, no event.
        tracker.update(uuid, PlayerAfkState(afk = true, tabbedOut = true))
        assertEquals(1, heard.size)
        tracker.update(uuid, PlayerAfkState())
        assertEquals(PlayerState.AFK to PlayerState.ACTIVE, heard.last())
        // The client's first news about a player is usually that they went away.
        val other = UUID.randomUUID()
        tracker.update(other, PlayerAfkState(timingOut = true))
        assertEquals(PlayerState.ACTIVE to PlayerState.TIMING_OUT, heard.last())
    }
}
