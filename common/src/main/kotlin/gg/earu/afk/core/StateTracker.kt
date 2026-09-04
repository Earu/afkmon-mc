package gg.earu.afk.core

import gg.earu.afk.api.PlayerState
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Backs the public [gg.earu.afk.api.Afkmon] API: one instance per side, fed by [AfkServer] and
 * [AfkClient]. Reduces the raw flags to a single [PlayerState] and remembers when it last changed.
 * Pure, so it is unit testable. A player never seen counts as ACTIVE.
 */
class StateTracker(
    private val now: () -> Long = System::currentTimeMillis,
    private val onChange: (UUID, PlayerState, PlayerState) -> Unit = { _, _, _ -> },
) {

    private class Entry(val flags: PlayerAfkState, val state: PlayerState, val sinceMs: Long)

    private val entries = ConcurrentHashMap<UUID, Entry>()

    fun stateOf(uuid: UUID): PlayerState = entries[uuid]?.state ?: PlayerState.ACTIVE

    fun flagsOf(uuid: UUID): PlayerAfkState = entries[uuid]?.flags ?: PlayerAfkState()

    fun secondsInStateOf(uuid: UUID): Long {
        val entry = entries[uuid] ?: return 0L
        return ((now() - entry.sinceMs) / 1000L).coerceAtLeast(0L)
    }

    /** Records [flags] for [uuid] and reports through [onChange] when the reduced state changed. */
    fun update(uuid: UUID, flags: PlayerAfkState) {
        val previous = entries[uuid]
        val state = reduce(flags)
        val previousState = previous?.state ?: PlayerState.ACTIVE
        if (previousState == state) {
            entries[uuid] = Entry(flags, state, previous?.sinceMs ?: now())
            return
        }
        // Going away is backdated by the afk threshold, so the readout matches the halo and chat.
        val since = if (state == PlayerState.AFK) flags.sinceEpochMs else now()
        entries[uuid] = Entry(flags, state, since)
        onChange(uuid, previousState, state)
    }

    fun remove(uuid: UUID) {
        entries.remove(uuid)
    }

    fun clear() = entries.clear()

    companion object {
        fun reduce(flags: PlayerAfkState): PlayerState = when {
            flags.timingOut -> PlayerState.TIMING_OUT
            flags.afk -> PlayerState.AFK
            flags.tabbedOut -> PlayerState.TABBED_OUT
            else -> PlayerState.ACTIVE
        }
    }
}
