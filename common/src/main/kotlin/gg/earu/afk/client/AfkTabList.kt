package gg.earu.afk.client

import gg.earu.afk.core.PlayerAfkState
import net.minecraft.ChatFormatting
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.world.scores.PlayerTeam
import java.util.UUID

/**
 * Tags flagged players in the tab list with their status: `[Timing Out]`, `[AFK]` or `[Tabbed Out]`.
 * One tag at a time, worst news first, unlike the halo which alternates between the two it can show.
 *
 * The entry's display name is rewritten rather than read through a render mixin, because the
 * 1.20.1 Forge build cannot ship one (no refmap) and this keeps every branch on one path. Whatever
 * name the server set is stashed and put back on return, so servers that format their own tab
 * names keep them; a server changing the name while a player is away is picked up on the next tick.
 */
object AfkTabList {

    /** The red the halo paints TIMING OUT with, so the tag and the ring read as one status. */
    private val TIMING_OUT: Component = Component.literal(" [Timing Out]")
        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF5731)))

    private val AFK: Component = Component.literal(" [AFK]").withStyle(ChatFormatting.GRAY)
    private val TABBED_OUT: Component = Component.literal(" [Tabbed Out]").withStyle(ChatFormatting.GRAY)

    private class Decoration(val serverName: Component?, val applied: Component)

    private val decorations = HashMap<UUID, Decoration>()

    fun sync(connection: ClientPacketListener) {
        if (AfkClient.states.isEmpty() && decorations.isEmpty()) return

        // Only flagged players and ones we still hold a name for; the tab list itself can be large.
        val subjects = HashSet(decorations.keys)
        subjects.addAll(AfkClient.states.keys)
        for (uuid in subjects) {
            val info = connection.getPlayerInfo(uuid) ?: continue
            apply(uuid, info)
        }
        // Players who logged out took their entry with them, so there is nothing left to restore.
        decorations.keys.retainAll { connection.getPlayerInfo(it) != null }
    }

    fun clear() = decorations.clear()

    /**
     * The name the server gave the player, with our tag taken back off if one is currently on.
     * Callers that want to print the name elsewhere need this rather than the raw entry.
     */
    fun undecorated(info: PlayerInfo): Component? {
        val previous = decorations[info.profile.id()]
        val current: Component? = info.tabListDisplayName
        // A name we did not write means the server has set its own since; that one is the truth.
        return if (previous != null && current == previous.applied) previous.serverName else current
    }

    private fun tagFor(state: PlayerAfkState?): Component? = when {
        state == null -> null
        state.timingOut -> TIMING_OUT
        state.afk -> AFK
        state.tabbedOut -> TABBED_OUT
        else -> null
    }

    private fun apply(uuid: UUID, info: PlayerInfo) {
        val previous = decorations[uuid]
        val current: Component? = info.tabListDisplayName
        val serverName = undecorated(info)

        val tag = tagFor(AfkClient.states[uuid])
        if (tag == null) {
            decorations.remove(uuid)
            if (previous != null && current == previous.applied) info.tabListDisplayName = previous.serverName
            return
        }

        // Vanilla only team-formats when the server left the display name unset, so match that.
        val base = serverName ?: PlayerTeam.formatNameForTeam(info.team, Component.literal(info.profile.name()))
        val applied = Component.empty().append(base).append(tag)
        if (current != applied) info.tabListDisplayName = applied
        decorations[uuid] = Decoration(serverName, applied)
    }
}
