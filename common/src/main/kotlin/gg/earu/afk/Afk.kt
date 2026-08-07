package gg.earu.afk

import gg.earu.afk.platform.Platform
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object Afk {
    const val MOD_ID = "afk"

    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    lateinit var platform: Platform
        private set

    fun init(platform: Platform) {
        this.platform = platform
        LOGGER.info("afk {} initialising", platform.modVersion)
    }
}
