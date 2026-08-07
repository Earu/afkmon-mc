package gg.earu.afk.platform

import java.nio.file.Path

/** The only loader-specific surface common code needs. */
interface Platform {
    val configDir: Path
    val isClient: Boolean
    val modVersion: String
}
