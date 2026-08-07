package gg.earu.afk.neoforge

import gg.earu.afk.server.AfkServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.util.ObfuscationReflectionHelper

object ServerEvents {
    // SRG reflection instead of the accessor mixin: MDG legacy cannot produce the refmap a
    // production 1.20.1 Forge mixin needs. ObfuscationReflectionHelper maps SRG to runtime names.
    private val keepAliveTime =
        ObfuscationReflectionHelper.findField(ServerGamePacketListenerImpl::class.java, "f_9747_")
    private val keepAlivePending =
        ObfuscationReflectionHelper.findField(ServerGamePacketListenerImpl::class.java, "f_9748_")

    fun wire() {
        AfkServer.sendToPlayer = Payloads::sendToPlayer
        AfkServer.canSendTo = Payloads::canSendTo
        AfkServer.keepAliveAge = { player, now ->
            if (keepAlivePending.getBoolean(player.connection)) now - keepAliveTime.getLong(player.connection) else -1L
        }
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
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        AfkServer.onTick(event.server)
    }
}
