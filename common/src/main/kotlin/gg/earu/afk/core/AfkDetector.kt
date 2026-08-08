package gg.earu.afk.core

/**
 * Client-side AFK state machine, a port of the tracking half of afkmon.lua. Pure logic with an
 * injected clock so it can be unit tested; the caller feeds it samples once per client tick.
 */
class AfkDetector(private val clock: () -> Double) {

    /**
     * One tick's worth of raw input. Deltas between consecutive samples count as activity.
     *
     * Everything here must be something the player did. View rotation deliberately is not sampled:
     * boats, mounts and server position corrections (being shoved by another player or a mob) all
     * rewrite yaw and pitch, which used to wake people up without them touching anything. Looking
     * around already shows up as mouse movement.
     */
    data class Sample(
        val mouseX: Double,
        val mouseY: Double,
        val anyKeyDown: Boolean,
        /** Monotonic count of raw key/char events; see [gg.earu.afk.client.RawInput]. */
        val inputEvents: Long,
        val windowFocused: Boolean,
    )

    data class Output(val afk: Boolean, val tabbedOut: Boolean, val changed: Boolean)

    var afkTimeSeconds: Int = DEFAULT_AFK_SECONDS

    private var prev: Sample? = null
    private var lastInput = 0.0
    private var lastFocus = 0.0
    private var warmUpUntil = 0.0
    private var suppressUntil = 0.0
    private var unfocusedSince: Double? = null
    private var reported = false to false

    init {
        reset()
    }

    /** Call on world join / disconnect. Mirrors GMod's 10s delayed start plus its Now()+5 seeding. */
    fun reset() {
        val now = clock()
        prev = null
        lastInput = now + GRACE_SECONDS
        lastFocus = now + GRACE_SECONDS
        warmUpUntil = now + WARM_UP_SECONDS
        suppressUntil = 0.0
        unfocusedSince = null
        reported = false to false
    }

    /**
     * Ignore input for a moment. GMod does this whenever the local player's mode changes so the
     * burst of keypresses that woke them up does not immediately re-arm the tracker.
     */
    fun suppressInput(seconds: Double = SUPPRESS_SECONDS) {
        suppressUntil = clock() + seconds
    }

    fun tick(sample: Sample): Output {
        val now = clock()
        val suppressed = now < suppressUntil

        val before = prev
        prev = sample
        if (before != null && !suppressed && hasActivity(before, sample)) {
            lastInput = now
        }

        if (sample.windowFocused) {
            lastFocus = now
            unfocusedSince = null
        } else if (unfocusedSince == null) {
            unfocusedSince = now
        }

        if (now < warmUpUntil) return Output(reported.first, reported.second, false)

        val idleSince = now - afkTimeSeconds
        val afk = lastInput < idleSince || lastFocus < idleSince
        // Debounced so a quick alt-tab does not flicker a ring on everyone's screen.
        val tabbedOut = unfocusedSince?.let { now - it >= TABBED_OUT_DEBOUNCE_SECONDS } ?: false

        val changed = afk != reported.first || tabbedOut != reported.second
        reported = afk to tabbedOut
        return Output(afk, tabbedOut, changed)
    }

    private fun hasActivity(a: Sample, b: Sample): Boolean =
        a.mouseX != b.mouseX ||
            a.mouseY != b.mouseY ||
            a.inputEvents != b.inputEvents ||
            // Edge triggered like the Lua's unrolled key loop: holding a key is not activity.
            a.anyKeyDown != b.anyKeyDown

    companion object {
        const val DEFAULT_AFK_SECONDS = 90
        private const val GRACE_SECONDS = 5.0
        private const val WARM_UP_SECONDS = 10.0
        private const val SUPPRESS_SECONDS = 0.1
        private const val TABBED_OUT_DEBOUNCE_SECONDS = 1.0
    }
}
