package gg.earu.afk.core

/**
 * One player's replicated status. [sinceEpochMs] is a server-clock timestamp: when [afk] turns on
 * it is backdated by the afk threshold, so displayed durations count from the last real activity
 * rather than from the moment the client noticed (GMod parity).
 */
data class PlayerAfkState(
    val afk: Boolean = false,
    val tabbedOut: Boolean = false,
    val timingOut: Boolean = false,
    val sinceEpochMs: Long = 0L,
) {
    val flagged: Boolean get() = afk || tabbedOut || timingOut
}
