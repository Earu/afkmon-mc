package gg.earu.afk.neoforge

import gg.earu.afk.server.AfkServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.PacketDistributor

object ServerEvents {
    fun wire() {
        AfkServer.sendToPlayer = { player, payload -> PacketDistributor.sendToPlayer(player, payload) }
        AfkServer.canSendTo = { player, type -> player.connection.hasChannel(type) }
    }

    @SubscribeEvent
    fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        (event.entity as? ServerPlayer)?.let { AfkServer.onPlayerJoin(it) }
    }

    @SubscribeEvent
    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        (event.entity as? ServerPlayer)?.let { AfkServer.onPlayerLeave(it) }
    }

    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent.Post) {
        AfkServer.onTick(event.server)
    }
}
