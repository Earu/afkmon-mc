package gg.earu.afk.core

/**
 * Port of GMod's string.NiceTime: at most the two largest non-zero units.
 */
object NiceTime {
    private val UNITS = listOf(
        "year" to 31_536_000L,
        "week" to 604_800L,
        "day" to 86_400L,
        "hour" to 3_600L,
        "minute" to 60L,
        "second" to 1L,
    )

    fun format(seconds: Long): String {
        if (seconds < 1) return "0 seconds"

        val parts = mutableListOf<String>()
        var left = seconds
        for ((name, size) in UNITS) {
            if (parts.size == 2) break
            val count = left / size
            // Skip leading zero units, but once something is printed keep going so
            // "1 hour 3 minutes" works while "1 hour 0 minutes" stays "1 hour".
            if (count == 0L) continue
            parts += "$count $name" + if (count == 1L) "" else "s"
            left -= count * size
        }

        return parts.joinToString(" ")
    }
}
