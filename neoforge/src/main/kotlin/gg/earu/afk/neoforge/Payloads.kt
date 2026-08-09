package gg.earu.afk.neoforge

import gg.earu.afk.net.AfkPayloads
import gg.earu.afk.server.AfkServer
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.PacketDistributor
import java.util.function.Supplier

/**
 * Forge 1.20.1 SimpleChannel wiring for the shared messages. The channel is OPTIONAL
 * (acceptMissingOr) so vanilla clients and servers interoperate untouched. Client-side handling
 * lives in [ClientPayloadHandler], whose class must only load when a handler actually runs,
 * never on a dedicated server.
 */
object Payloads {
    private const val PROTOCOL = "1"

    val channel = NetworkRegistry.ChannelBuilder
        .named(ResourceLocation(AfkPayloads.NAMESPACE, "main"))
        .networkProtocolVersion { PROTOCOL }
        .clientAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL))
        .serverAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL))
        .simpleChannel()

    fun register() {
        channel.registerMessage(
            0,
            AfkPayloads.ReportPayload::class.java,
            { msg, buf -> buf.writeBoolean(msg.afk); buf.writeBoolean(msg.tabbedOut) },
            { buf -> AfkPayloads.ReportPayload(buf.readBoolean(), buf.readBoolean()) },
            ::handleReport,
        )
        channel.registerMessage(
            1,
            AfkPayloads.StatePayload::class.java,
            { msg, buf ->
                buf.writeUUID(msg.player)
                buf.writeBoolean(msg.afk)
                buf.writeBoolean(msg.tabbedOut)
                buf.writeBoolean(msg.timingOut)
                buf.writeVarLong(msg.sinceEpochMs)
                buf.writeInt(msg.announceSeconds)
            },
            { buf ->
                AfkPayloads.StatePayload(
                    buf.readUUID(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarLong(),
                    buf.readInt(),
                )
            },
            ::handleState,
        )
        channel.registerMessage(
            2,
            AfkPayloads.ConfigPayload::class.java,
            { msg, buf -> buf.writeVarInt(msg.afkTimeSeconds) },
            { buf -> AfkPayloads.ConfigPayload(buf.readVarInt()) },
            ::handleConfig,
        )
    }

    fun sendToPlayer(player: ServerPlayer, message: AfkPayloads.Message) {
        channel.send(PacketDistributor.PLAYER.with { player }, message)
    }

    fun canSendTo(player: ServerPlayer): Boolean =
        channel.isRemotePresent(player.connection.connection)

    private fun handleReport(msg: AfkPayloads.ReportPayload, ctx: Supplier<NetworkEvent.Context>) {
        val player = ctx.get().sender
        if (player != null) {
            ctx.get().enqueueWork { AfkServer.onReport(player, msg) }
        }
        ctx.get().packetHandled = true
    }

    private fun handleState(msg: AfkPayloads.StatePayload, ctx: Supplier<NetworkEvent.Context>) {
        ctx.get().enqueueWork { ClientPayloadHandler.handleState(msg) }
        ctx.get().packetHandled = true
    }

    private fun handleConfig(msg: AfkPayloads.ConfigPayload, ctx: Supplier<NetworkEvent.Context>) {
        ctx.get().enqueueWork { ClientPayloadHandler.handleConfig(msg) }
        ctx.get().packetHandled = true
    }
}
