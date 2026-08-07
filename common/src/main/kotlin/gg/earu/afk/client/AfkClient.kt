package gg.earu.afk.client

import gg.earu.afk.core.AfkDetector
import gg.earu.afk.core.ClientConfig
import gg.earu.afk.core.PlayerAfkState
import gg.earu.afk.net.AfkPayloads
import gg.earu.afk.platform.Platform
import gg.earu.afk.core.AfkConfig
import net.minecraft.client.Minecraft
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Client half of the mod: tracks our own idleness, reports it, and holds everyone else's status
 * for the renderer. Loaders inject the two send functions.
 */
object AfkClient {

    var sendPayload: (AfkPayloads.Message) -> Unit = {}
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

        // Our own state echoing back means the inputs that woke us are still arriving; ignore them
        // briefly so they do not churn the tracker (GMod's ignoreinput).
        if (payload.player == Minecraft.getInstance().player?.uuid) detector.suppressInput()
    }

    fun onConfig(payload: AfkPayloads.ConfigPayload) {
        detector.afkTimeSeconds = payload.afkTimeSeconds
    }

    fun onDisconnect() {
        states.clear()
        detector.reset()
    }

    fun tick(mc: Minecraft) {
        if (mc.player == null) return

        val out = detector.tick(InputSampler.sample(mc))
        if (out.changed && canSend()) sendPayload(AfkPayloads.ReportPayload(out.afk, out.tabbedOut))
    }
}
