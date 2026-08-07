package gg.earu.afk.fabric

import gg.earu.afk.Afk
import gg.earu.afk.mixin.ServerConnectionAccessor
import gg.earu.afk.net.AfkPayloads
import gg.earu.afk.platform.Platform
import gg.earu.afk.server.AfkServer
import net.fabricmc.api.EnvType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Path

/** Channel ids + buf codecs for the 1.20.1 raw-channel networking (pre-payload-types API). */
object FabricChannels {
    val REPORT = ResourceLocation(AfkPayloads.NAMESPACE, AfkPayloads.ReportPayload.PATH)
    val STATE = ResourceLocation(AfkPayloads.NAMESPACE, AfkPayloads.StatePayload.PATH)
    val CONFIG = ResourceLocation(AfkPayloads.NAMESPACE, AfkPayloads.ConfigPayload.PATH)

    fun sendToPlayer(player: ServerPlayer, message: AfkPayloads.Message) {
        val buf = PacketByteBufs.create()
        val channel = when (message) {
            is AfkPayloads.ReportPayload -> return // C->S only, never sent to players
            is AfkPayloads.StatePayload -> {
                writeState(buf, message)
                STATE
            }
            is AfkPayloads.ConfigPayload -> {
                buf.writeVarInt(message.afkTimeSeconds)
                CONFIG
            }
        }
        ServerPlayNetworking.send(player, channel, buf)
    }

    fun writeState(buf: FriendlyByteBuf, message: AfkPayloads.StatePayload) {
        buf.writeUUID(message.player)
        buf.writeBoolean(message.afk)
        buf.writeBoolean(message.tabbedOut)
        buf.writeBoolean(message.timingOut)
        buf.writeVarLong(message.sinceEpochMs)
    }

    fun readState(buf: FriendlyByteBuf) = AfkPayloads.StatePayload(
        buf.readUUID(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarLong(),
    )
}

class AfkFabric : ModInitializer {
    class FabricPlatform : Platform {
        override val configDir: Path = FabricLoader.getInstance().configDir.resolve(Afk.MOD_ID)
        override val isClient: Boolean = FabricLoader.getInstance().environmentType == EnvType.CLIENT
        override val modVersion: String = FabricLoader.getInstance()
            .getModContainer(Afk.MOD_ID).map { it.metadata.version.friendlyString }.orElse("dev")
    }

    override fun onInitialize() {
        val platform = FabricPlatform()
        Afk.init(platform)
        AfkServer.init(platform)

        AfkServer.sendToPlayer = FabricChannels::sendToPlayer
        AfkServer.canSendTo = { player -> ServerPlayNetworking.canSend(player, FabricChannels.STATE) }
        AfkServer.keepAliveAge = { player, now ->
            val listener = player.connection as ServerConnectionAccessor
            if (listener.`afk$isKeepAlivePending`()) now - listener.`afk$getKeepAliveTime`() else -1L
        }

        ServerPlayNetworking.registerGlobalReceiver(FabricChannels.REPORT) { server, player, _, buf, _ ->
            // Read on the netty thread, apply on the server thread.
            val report = AfkPayloads.ReportPayload(buf.readBoolean(), buf.readBoolean())
            server.execute { AfkServer.onReport(player, report) }
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> AfkServer.onPlayerJoin(handler.player) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> AfkServer.onPlayerLeave(handler.player) }
        ServerTickEvents.END_SERVER_TICK.register { server -> AfkServer.onTick(server) }
    }
}
