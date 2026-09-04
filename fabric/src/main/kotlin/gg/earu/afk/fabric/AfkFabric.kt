package gg.earu.afk.fabric

import gg.earu.afk.Afk
import gg.earu.afk.api.AfkEvents
import gg.earu.afk.api.Afkmon
import gg.earu.afk.net.AfkPayloads
import gg.earu.afk.platform.Platform
import gg.earu.afk.server.AfkServer
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Path

class AfkFabric : ModInitializer {
    override fun onInitialize() {
        val platform = FabricPlatform()
        Afk.init(platform)
        AfkServer.init(platform)
        Afkmon.addListener { change -> AfkEvents.STATE_CHANGE.invoker().onStateChange(change) }

        PayloadTypeRegistry.playC2S().register(AfkPayloads.ReportPayload.TYPE, AfkPayloads.ReportPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(AfkPayloads.StatePayload.TYPE, AfkPayloads.StatePayload.CODEC)
        PayloadTypeRegistry.playS2C().register(AfkPayloads.ConfigPayload.TYPE, AfkPayloads.ConfigPayload.CODEC)

        AfkServer.sendToPlayer = { player, payload -> ServerPlayNetworking.send(player, payload) }
        AfkServer.canSendTo = { player, type -> ServerPlayNetworking.canSend(player, type) }

        ServerPlayNetworking.registerGlobalReceiver(AfkPayloads.ReportPayload.TYPE) { payload, context ->
            context.server().execute { AfkServer.onReport(context.player(), payload) }
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> AfkServer.onPlayerJoin(handler.player) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> AfkServer.onPlayerLeave(handler.player) }
        ServerTickEvents.END_SERVER_TICK.register { server -> AfkServer.onTick(server) }
    }

    private class FabricPlatform : Platform {
        override val configDir: Path = FabricLoader.getInstance().configDir.resolve(Afk.MOD_ID)
        override val isClient: Boolean = FabricLoader.getInstance().environmentType.name == "CLIENT"
        override val modVersion: String = FabricLoader.getInstance()
            .getModContainer(Afk.MOD_ID)
            .map { it.metadata.version.friendlyString }
            .orElse("unknown")
    }
}
