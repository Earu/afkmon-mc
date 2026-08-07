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
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier

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
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(Afk.MOD_ID, "overlay")) { graphics, _ ->
            AfkOverlay.render(graphics)
        }

        // END_MAIN is the last hook with translucent terrain already drawn; the 1.21.11 render
        // context lost its camera and tick accessors, so both come from the client instead.
        WorldRenderEvents.END_MAIN.register { context ->
            val pose = context.matrices() ?: return@register
            val mc = Minecraft.getInstance()
            AfkRingsRenderer.render(
                pose,
                mc.renderBuffers().bufferSource(),
                mc.gameRenderer.mainCamera,
                mc.deltaTracker.getGameTimeDeltaPartialTick(false),
            )
        }
    }
}
