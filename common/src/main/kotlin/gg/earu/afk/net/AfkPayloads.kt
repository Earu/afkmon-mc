package gg.earu.afk.net

import java.util.UUID

/**
 * Wire messages shared by every loader, plain data on this branch: 1.20.1 predates
 * CustomPacketPayload/StreamCodec, so each loader module owns its own serialization
 * (SimpleChannel on Forge, PacketByteBuf channels on Fabric). All channels are OPTIONAL:
 * vanilla clients and servers interoperate untouched.
 */
object AfkPayloads {
    const val NAMESPACE = "afk"

    sealed interface Message

    /** C->S: the client's own verdict on its input and window focus. */
    class ReportPayload(val afk: Boolean, val tabbedOut: Boolean) : Message {
        companion object {
            const val PATH = "report"
        }
    }

    /**
     * S->C: one player's status, broadcast on every change and replayed to joining clients.
     * [sinceEpochMs] is the server's clock so everyone shows the same duration.
     */
    class StatePayload(
        val player: UUID,
        val afk: Boolean,
        val tabbedOut: Boolean,
        val timingOut: Boolean,
        val sinceEpochMs: Long,
    ) : Message {
        companion object {
            const val PATH = "state"
        }
    }

    /** S->C: the server's afk threshold, so clients report on the server's terms. */
    class ConfigPayload(val afkTimeSeconds: Int) : Message {
        companion object {
            const val PATH = "config"
        }
    }
}
