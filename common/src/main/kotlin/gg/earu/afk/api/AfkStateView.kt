package gg.earu.afk.api

import java.util.UUID

/**
 * Read-only view of one side's player states, keyed by UUID so callers can ask about players they
 * have no entity for: the client hears about everyone on the server, loaded or not. A player never
 * seen counts as ACTIVE. Get one from [Afkmon.server], [Afkmon.client] or [Afkmon.side].
 */
interface AfkStateView {

    fun stateOf(uuid: UUID): PlayerState

    /** Seconds the player has been in their current state, 0 if unknown. */
    fun secondsInStateOf(uuid: UUID): Long

    /** The raw flags. Several can be set at once; [stateOf] is the highest of them. */
    fun flagsOf(uuid: UUID): AfkFlags

    /** True whenever the away flag is set, even if the player is also timing out. */
    fun isAfk(uuid: UUID): Boolean = flagsOf(uuid).afk

    /** True whenever the tabbed-out flag is set, even if the player is also away or timing out. */
    fun isTabbedOut(uuid: UUID): Boolean = flagsOf(uuid).tabbedOut

    fun isTimingOut(uuid: UUID): Boolean = flagsOf(uuid).timingOut

    fun isActive(uuid: UUID): Boolean = !flagsOf(uuid).flagged

    /** Seconds the away flag has been on, 0 when off. Backdated by the threshold like [AfkFlags.sinceEpochMs]. */
    fun secondsAfk(uuid: UUID): Long

    /** Seconds the tabbed-out flag has been on, 0 when off. Counted from when this side heard about it. */
    fun secondsTabbedOut(uuid: UUID): Long

    /** Seconds the timing-out flag has been on, 0 when off. Counted from when this side heard about it. */
    fun secondsTimingOut(uuid: UUID): Long

    /** Every player whose state is anything but ACTIVE. A snapshot, safe to iterate while states change. */
    fun flaggedPlayers(): Map<UUID, PlayerState>

    /** The afk threshold this side runs with. On the client, 0 until the server has sent its config. */
    fun afkTimeSeconds(): Int

    /**
     * Whether the player's client runs the mod. Vanilla players never report, so they are only ever
     * TIMING_OUT or ACTIVE. Server side it is false until the join handshake completes, a few
     * seconds after login. The client cannot tell, so its view answers true for everyone.
     */
    fun hasMod(uuid: UUID): Boolean
}
