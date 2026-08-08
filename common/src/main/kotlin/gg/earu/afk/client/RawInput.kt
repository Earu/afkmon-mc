package gg.earu.afk.client

/**
 * Raw keyboard event counter, fed by the client mixins.
 *
 * Polling key bindings is not enough: vanilla only marks them down while no screen is open, so a
 * player who comes back and starts typing straight into chat produces no bound key, no mouse
 * movement and stays flagged as away. Every key and character event bumps this instead, which the
 * sampler reads once per tick.
 */
object RawInput {

    @Volatile
    var events: Long = 0L
        private set

    @JvmStatic
    fun record() {
        events++
    }
}
