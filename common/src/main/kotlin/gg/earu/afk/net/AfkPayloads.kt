package gg.earu.afk.net

import gg.earu.afk.Afk
import io.netty.buffer.ByteBuf
import net.minecraft.core.UUIDUtil
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import java.util.UUID

/**
 * Wire format shared by every loader. All channels are OPTIONAL: vanilla clients and servers
 * interoperate untouched, they simply never report or draw anything.
 */
object AfkPayloads {

    /** C->S: the client's own verdict on its input and window focus. */
    class ReportPayload(val afk: Boolean, val tabbedOut: Boolean) : CustomPacketPayload {
        companion object {
            val TYPE = CustomPacketPayload.Type<ReportPayload>(
                Identifier.fromNamespaceAndPath(Afk.MOD_ID, "report"),
            )
            val CODEC: StreamCodec<ByteBuf, ReportPayload> = StreamCodec.composite(
                ByteBufCodecs.BOOL, ReportPayload::afk,
                ByteBufCodecs.BOOL, ReportPayload::tabbedOut,
                ::ReportPayload,
            )
        }

        override fun type() = TYPE
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
    ) : CustomPacketPayload {
        companion object {
            val TYPE = CustomPacketPayload.Type<StatePayload>(
                Identifier.fromNamespaceAndPath(Afk.MOD_ID, "state"),
            )
            val CODEC: StreamCodec<ByteBuf, StatePayload> = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, StatePayload::player,
                ByteBufCodecs.BOOL, StatePayload::afk,
                ByteBufCodecs.BOOL, StatePayload::tabbedOut,
                ByteBufCodecs.BOOL, StatePayload::timingOut,
                ByteBufCodecs.VAR_LONG, StatePayload::sinceEpochMs,
                ::StatePayload,
            )
        }

        override fun type() = TYPE
    }

    /** S->C: the server's afk threshold, so clients report on the server's terms. */
    class ConfigPayload(val afkTimeSeconds: Int) : CustomPacketPayload {
        companion object {
            val TYPE = CustomPacketPayload.Type<ConfigPayload>(
                Identifier.fromNamespaceAndPath(Afk.MOD_ID, "config"),
            )
            val CODEC: StreamCodec<ByteBuf, ConfigPayload> =
                ByteBufCodecs.VAR_INT.map(::ConfigPayload) { it.afkTimeSeconds }
        }

        override fun type() = TYPE
    }
}
