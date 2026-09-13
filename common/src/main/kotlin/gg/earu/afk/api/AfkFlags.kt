package gg.earu.afk.api

/**
 * One player's raw flags as the server replicates them. Several can be on at once; [PlayerState]
 * is the highest of them. [sinceEpochMs] is a server-clock timestamp: when [afk] turns on it is
 * backdated by the afk threshold, so displayed durations count from the last real activity rather
 * than from the moment the client noticed (GMod parity).
 */
data class AfkFlags(
    val afk: Boolean = false,
    val tabbedOut: Boolean = false,
    val timingOut: Boolean = false,
    val sinceEpochMs: Long = 0L,
) {
    val flagged: Boolean get() = afk || tabbedOut || timingOut

    /** Compares the three flags and ignores the timestamp. */
    fun sameFlagsAs(other: AfkFlags): Boolean =
        afk == other.afk && tabbedOut == other.tabbedOut && timingOut == other.timingOut
}
