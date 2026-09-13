package gg.earu.afk.api

import gg.earu.afk.client.AfkClient
import gg.earu.afk.client.AfkTabList
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.network.chat.Component
import java.util.UUID

/**
 * Client-only half of the API. Touches client classes, so never load it on a dedicated server.
 *
 * The tab list is decorated by rewriting each flagged entry's display name, so anything reading
 * `PlayerInfo.getTabListDisplayName` for its own UI gets the tag along with the name. These give
 * the name the tab list would show without it: the server's display name, or the vanilla team
 * formatting when the server set none. Only the tag this mod appended comes off, and only while
 * it is on: a name the server rewrote mid-AFK is returned as the server wrote it.
 * To show status in your own layout use [PlayerState.tag] and [PlayerState.displayName].
 */
object AfkmonClient {

    @JvmStatic
    fun undecoratedName(info: PlayerInfo): Component = AfkTabList.displayName(info)

    /** As [undecoratedName], null when the player has no tab list entry or there is no connection. */
    @JvmStatic
    fun undecoratedName(uuid: UUID): Component? =
        Minecraft.getInstance().connection?.getPlayerInfo(uuid)?.let(AfkTabList::displayName)

    /**
     * Seconds since the local player last did anything the detector counts as input, 0 for a
     * moment after joining. The player goes away once this or [secondsUnfocused] reaches
     * `Afkmon.client().afkTimeSeconds()`.
     */
    @JvmStatic
    fun secondsSinceInput(): Double = AfkClient.detector.secondsSinceInput()

    /** Seconds the game window has been unfocused, 0 while focused. */
    @JvmStatic
    fun secondsUnfocused(): Double = AfkClient.detector.secondsUnfocused()
}
