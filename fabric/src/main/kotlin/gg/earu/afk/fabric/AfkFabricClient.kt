package gg.earu.afk.fabric

import gg.earu.afk.Afk
import gg.earu.afk.client.AfkClient
import gg.earu.afk.client.AfkOverlay
import gg.earu.afk.client.render.AfkRingsRenderer
import gg.earu.afk.net.AfkPayloads
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.renderer.MultiBufferSource

class AfkFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        AfkClient.init(Afk.platform)
        AfkClient.sendPayload = { message ->
            if (message is AfkPayloads.ReportPayload) {
                val buf = PacketByteBufs.create()
                buf.writeBoolean(message.afk)
                buf.writeBoolean(message.tabbedOut)
                ClientPlayNetworking.send(FabricChannels.REPORT, buf)
            }
        }
        AfkClient.canSend = { ClientPlayNetworking.canSend(FabricChannels.REPORT) }

        ClientPlayNetworking.registerGlobalReceiver(FabricChannels.STATE) { client, _, buf, _ ->
            val state = FabricChannels.readState(buf)
            client.execute { AfkClient.onState(state) }
        }
        ClientPlayNetworking.registerGlobalReceiver(FabricChannels.CONFIG) { client, _, buf, _ ->
            val config = AfkPayloads.ConfigPayload(buf.readVarInt())
            client.execute { AfkClient.onConfig(config) }
        }

        ClientTickEvents.END_CLIENT_TICK.register { mc -> AfkClient.tick(mc) }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> AfkClient.onDisconnect() }
        HudRenderCallback.EVENT.register { graphics, _ -> AfkOverlay.render(graphics) }

        WorldRenderEvents.AFTER_TRANSLUCENT.register { context ->
            val buffers = context.consumers() as? MultiBufferSource.BufferSource ?: return@register
            AfkRingsRenderer.render(
                context.matrixStack(),
                buffers,
                context.camera(),
                context.tickDelta(),
            )
        }
    }
}
