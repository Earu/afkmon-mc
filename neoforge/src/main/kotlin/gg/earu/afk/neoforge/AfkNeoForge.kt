package gg.earu.afk.neoforge

import gg.earu.afk.Afk
import gg.earu.afk.api.AfkStateChangedEvent
import gg.earu.afk.api.Afkmon
import gg.earu.afk.server.AfkServer
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraftforge.fml.loading.FMLPaths

@Mod(Afk.MOD_ID)
class AfkNeoForge {
    init {
        val platform = NeoForgePlatform(
            configDir = FMLPaths.CONFIGDIR.get().resolve(Afk.MOD_ID),
            isClient = FMLEnvironment.dist.isClient,
            modVersion = ModList.get().getModContainerById(Afk.MOD_ID)
                .map { it.modInfo.version.toString() }.orElse("dev"),
        )
        Afk.init(platform)
        AfkServer.init(platform)
        Afkmon.addListener { change -> MinecraftForge.EVENT_BUS.post(AfkStateChangedEvent(change)) }

        Payloads.register()
        ServerEvents.wire()
        MinecraftForge.EVENT_BUS.register(ServerEvents)
        if (FMLEnvironment.dist.isClient) {
            ClientEvents.wire()
            MinecraftForge.EVENT_BUS.register(ClientEvents)
        }
    }
}
