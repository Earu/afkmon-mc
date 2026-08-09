package gg.earu.afk.core

import gg.earu.afk.Afk
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

@Serializable
data class ServerConfig(
    /** Idle seconds before a client flags itself away. Replicated to clients, like mp_afktime. */
    val afkTimeSeconds: Int = 90,
    /** Sound cue when someone goes away or comes back. */
    val soundsEnabled: Boolean = true,
    /** Chat announcements and the welcome-back message. */
    val announceEnabled: Boolean = true,
    /** How long a keepalive may go unanswered before the player is shown as timing out. */
    val timingOutThresholdSeconds: Double = 5.0,
)

@Serializable
data class ClientConfig(
    /** Master toggle for the rings, like the afkrings convar. */
    val ringsEnabled: Boolean = true,
    /** Rings on other players closer than this are hidden so they do not block your view. */
    val minDistance: Double = 2.0,
    /** Rings past this distance are skipped, and so are chat announcements about those players. */
    val maxDistance: Double = 64.0,
    /** Blocks to raise the halo. The port sits at the player's feet, where the Lua put it. */
    val heightOffset: Double = 0.0,
    /** GMod forced the depth buffer off so rings showed through walls; off here by default. */
    val seeThroughWalls: Boolean = false,
    /** The on-screen "Away HH:MM:SS" timer while you are away, like cl_afkui. */
    val awayOverlayEnabled: Boolean = true,
)

object AfkConfig {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun loadServer(configDir: Path): ServerConfig = load(configDir.resolve("server.json"), ServerConfig())

    fun loadClient(configDir: Path): ClientConfig = load(configDir.resolve("client.json"), ClientConfig())

    private inline fun <reified T> load(file: Path, defaults: T): T {
        try {
            if (Files.exists(file)) {
                return json.decodeFromString<T>(Files.readString(file))
            }
            Files.createDirectories(file.parent)
            Files.writeString(file, json.encodeToString(defaults))
        } catch (e: Exception) {
            Afk.LOGGER.error("Failed to load {}, using defaults", file, e)
        }
        return defaults
    }
}
