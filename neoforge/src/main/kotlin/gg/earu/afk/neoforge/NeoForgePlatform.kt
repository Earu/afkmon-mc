package gg.earu.afk.neoforge

import gg.earu.afk.platform.Platform
import java.nio.file.Path

class NeoForgePlatform(
    override val configDir: Path,
    override val isClient: Boolean,
    override val modVersion: String,
) : Platform
