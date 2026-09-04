package gg.earu.afk.neoforge

import gg.earu.afk.Afk
import gg.earu.afk.api.AfkStateChangedEvent
import gg.earu.afk.api.Afkmon
import gg.earu.afk.server.AfkServer
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

@Mod(Afk.MOD_ID)
class AfkNeoForge(container: ModContainer, modBus: IEventBus) {
    init {
        val platform = NeoForgePlatform(
            configDir = FMLPaths.CONFIGDIR.get().resolve(Afk.MOD_ID),
            isClient = FMLEnvironment.getDist().isClient,
            modVersion = container.modInfo.version.toString(),
        )
        Afk.init(platform)
        AfkServer.init(platform)
        Afkmon.addListener { change -> NeoForge.EVENT_BUS.post(AfkStateChangedEvent(change)) }

        modBus.register(ModBusEvents)
        ServerEvents.wire()
        NeoForge.EVENT_BUS.register(ServerEvents)
        if (FMLEnvironment.getDist().isClient) {
            ClientEvents.wire()
            NeoForge.EVENT_BUS.register(ClientEvents)
        }
    }

    object ModBusEvents {
        @SubscribeEvent
        fun onRegisterPayloads(event: RegisterPayloadHandlersEvent) {
            Payloads.register(event)
        }
    }
}
