package gg.earu.afk.client

import gg.earu.afk.core.AfkDetector
import gg.earu.afk.core.ClientConfig
import gg.earu.afk.core.NiceTime
import gg.earu.afk.core.PlayerAfkState
import gg.earu.afk.net.AfkPayloads
import gg.earu.afk.platform.Platform
import gg.earu.afk.core.AfkConfig
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Client half of the mod: tracks our own idleness, reports it, and holds everyone else's status
 * for the renderer. Loaders inject the two send functions.
 */
object AfkClient {

    var sendPayload: (CustomPacketPayload) -> Unit = {}
    var canSend: () -> Boolean = { false }

    var config: ClientConfig = ClientConfig()
        private set

    val states = ConcurrentHashMap<UUID, PlayerAfkState>()

    private val detector = AfkDetector { System.nanoTime() / 1_000_000_000.0 }

    fun init(platform: Platform) {
        config = AfkConfig.loadClient(platform.configDir)
    }

    fun onState(payload: AfkPayloads.StatePayload) {
        val state = PlayerAfkState(payload.afk, payload.tabbedOut, payload.timingOut, payload.sinceEpochMs)
        if (state.flagged) states[payload.player] = state else states.remove(payload.player)

        if (payload.announceSeconds >= 0) announce(payload)

        // Our own state echoing back means the inputs that woke us are still arriving; ignore them
        // briefly so they do not churn the tracker (GMod's ignoreinput).
        if (payload.player == Minecraft.getInstance().player?.uuid) detector.suppressInput()
    }

    /**
     * The server hands us the transition rather than broadcasting it, so the chat line obeys the
     * same [ClientConfig.maxDistance] as the halo: no news about players you could not see anyway.
     * A player past the view distance is not loaded at all, which counts as too far.
     */
    private fun announce(payload: AfkPayloads.StatePayload) {
        val mc = Minecraft.getInstance()
        val self = mc.player ?: return
        val subject = mc.level?.players()?.firstOrNull { it.uuid == payload.player } ?: return
        if (subject.distanceToSqr(self) > config.maxDistance * config.maxDistance) return

        val duration = NiceTime.format(payload.announceSeconds.toLong())
        val transition = if (payload.afk) {
            " away (present for $duration)"
        } else {
            " back (away for $duration)"
        }
        // The tab list entry is where a server puts the name it coloured; the entity only carries
        // the vanilla team colour, which is the right fallback when the server set nothing. Read
        // through AfkTabList so an away player's own status tag does not end up in the sentence.
        val info = mc.connection?.getPlayerInfo(payload.player)
        val name = info?.let(AfkTabList::undecorated) ?: subject.displayName
        mc.gui.chat.addMessage(
            Component.empty()
                .append(name)
                .append(Component.literal(transition).withStyle(ChatFormatting.DARK_GRAY)),
        )
    }

    fun onConfig(payload: AfkPayloads.ConfigPayload) {
        detector.afkTimeSeconds = payload.afkTimeSeconds
    }

    fun onDisconnect() {
        states.clear()
        detector.reset()
        AfkTabList.clear()
    }

    fun tick(mc: Minecraft) {
        mc.connection?.let(AfkTabList::sync)
        if (mc.player == null) return

        val out = detector.tick(InputSampler.sample(mc))
        if (out.changed && canSend()) sendPayload(AfkPayloads.ReportPayload(out.afk, out.tabbedOut))
    }
}
