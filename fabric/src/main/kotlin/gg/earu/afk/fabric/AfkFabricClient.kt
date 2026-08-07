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
import net.minecraft.client.renderer.MultiBufferSource

class AfkFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        AfkClient.init(Afk.platform)
        AfkClient.sendPayload = { payload -> ClientPlayNetworking.send(payload) }
        AfkClient.canSend = { ClientPlayNetworking.canSend(AfkPayloads.ReportPayload.TYPE) }

        ClientPlayNetworking.registerGlobalReceiver(AfkPayloads.StatePayload.TYPE) { payload, context ->
            context.client().execute { AfkClient.onState(payload) }
        }
        ClientPlayNetworking.registerGlobalReceiver(AfkPayloads.ConfigPayload.TYPE) { payload, context ->
            context.client().execute { AfkClient.onConfig(payload) }
        }

        ClientTickEvents.END_CLIENT_TICK.register { mc -> AfkClient.tick(mc) }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> AfkClient.onDisconnect() }
        HudRenderCallback.EVENT.register { graphics, _ -> AfkOverlay.render(graphics) }

        WorldRenderEvents.AFTER_TRANSLUCENT.register { context ->
            val pose = context.matrixStack() ?: return@register
            val buffers = context.consumers() as? MultiBufferSource.BufferSource ?: return@register
            AfkRingsRenderer.render(
                pose,
                buffers,
                context.camera(),
                context.tickCounter().getGameTimeDeltaPartialTick(false),
            )
        }
    }
}
