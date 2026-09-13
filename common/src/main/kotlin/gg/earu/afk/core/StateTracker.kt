package gg.earu.afk.core

import gg.earu.afk.api.AfkFlags
import gg.earu.afk.api.AfkStateView
import gg.earu.afk.api.PlayerState
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Backs the public [gg.earu.afk.api.Afkmon] API: one instance per side, fed by [AfkServer] and
 * [AfkClient], handed out to other mods as an [AfkStateView]. Reduces the raw flags to a single
 * [PlayerState], keeps a clock per state and per flag, and reports changes to both through the
 * callbacks. Pure, so it is unit testable.
 */
class StateTracker(
    private val now: () -> Long = System::currentTimeMillis,
    /** (player, previous, current, seconds spent in previous). */
    private val onChange: (UUID, PlayerState, PlayerState, Long) -> Unit = { _, _, _, _ -> },
    private val onFlagsChange: (UUID, AfkFlags, AfkFlags) -> Unit = { _, _, _ -> },
) : AfkStateView {

    private class Entry(
        val flags: AfkFlags,
        val state: PlayerState,
        val sinceMs: Long,
        val afkSinceMs: Long?,
        val tabbedOutSinceMs: Long?,
        val timingOutSinceMs: Long?,
    )

    private val entries = ConcurrentHashMap<UUID, Entry>()

    @Volatile
    private var threshold: Int = 0

    @Volatile
    private var modCheck: (UUID) -> Boolean = { true }

    override fun stateOf(uuid: UUID): PlayerState = entries[uuid]?.state ?: PlayerState.ACTIVE

    override fun flagsOf(uuid: UUID): AfkFlags = entries[uuid]?.flags ?: AfkFlags()

    override fun secondsInStateOf(uuid: UUID): Long = seconds(entries[uuid]?.sinceMs)

    override fun secondsAfk(uuid: UUID): Long = seconds(entries[uuid]?.afkSinceMs)

    override fun secondsTabbedOut(uuid: UUID): Long = seconds(entries[uuid]?.tabbedOutSinceMs)

    override fun secondsTimingOut(uuid: UUID): Long = seconds(entries[uuid]?.timingOutSinceMs)

    override fun flaggedPlayers(): Map<UUID, PlayerState> {
        val flagged = HashMap<UUID, PlayerState>()
        for ((uuid, entry) in entries) {
            if (entry.state != PlayerState.ACTIVE) flagged[uuid] = entry.state
        }
        return flagged
    }

    override fun afkTimeSeconds(): Int = threshold

    override fun hasMod(uuid: UUID): Boolean = modCheck(uuid)

    fun setAfkTimeSeconds(seconds: Int) {
        threshold = seconds
    }

    fun setHasMod(check: (UUID) -> Boolean) {
        modCheck = check
    }

    /** Records [flags] for [uuid] and reports through the callbacks whatever changed. */
    fun update(uuid: UUID, flags: AfkFlags) {
        val now = now()
        val previous = entries[uuid]
        val previousFlags = previous?.flags ?: AfkFlags()
        val previousState = previous?.state ?: PlayerState.ACTIVE
        val state = reduce(flags)
        val stateChanged = previousState != state
        // Going away is backdated by the afk threshold, so the readout matches the halo and chat.
        val since = when {
            !stateChanged -> previous?.sinceMs ?: now
            state == PlayerState.AFK -> flags.sinceEpochMs
            else -> now
        }
        entries[uuid] = Entry(
            flags, state, since,
            afkSinceMs = flagClock(flags.afk, previous?.afkSinceMs, flags.sinceEpochMs),
            tabbedOutSinceMs = flagClock(flags.tabbedOut, previous?.tabbedOutSinceMs, now),
            timingOutSinceMs = flagClock(flags.timingOut, previous?.timingOutSinceMs, now),
        )
        if (!flags.sameFlagsAs(previousFlags)) onFlagsChange(uuid, previousFlags, flags)
        if (stateChanged) onChange(uuid, previousState, state, seconds(previous?.sinceMs, now))
    }

    /** Forgets [uuid]. A flagged player leaving is reported as a change back to ACTIVE. */
    fun remove(uuid: UUID) {
        val previous = entries.remove(uuid) ?: return
        if (previous.state == PlayerState.ACTIVE) return
        onFlagsChange(uuid, previous.flags, AfkFlags())
        onChange(uuid, previous.state, PlayerState.ACTIVE, seconds(previous.sinceMs))
    }

    fun clear() = entries.clear()

    /** A flag keeps its start while on, takes [start] when it turns on, and has none while off. */
    private fun flagClock(on: Boolean, previous: Long?, start: Long): Long? =
        if (on) previous ?: start else null

    private fun seconds(sinceMs: Long?, at: Long = now()): Long =
        if (sinceMs == null) 0L else ((at - sinceMs) / 1000L).coerceAtLeast(0L)

    companion object {
        fun reduce(flags: AfkFlags): PlayerState = when {
            flags.timingOut -> PlayerState.TIMING_OUT
            flags.afk -> PlayerState.AFK
            flags.tabbedOut -> PlayerState.TABBED_OUT
            else -> PlayerState.ACTIVE
        }
    }
}
