package gg.earu.afk.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The only mixin in the mod. Vanilla tracks keepalive state privately and exposes no API for it,
 * so reading these two fields is the only way to know a connection is stalling before the 15s
 * disconnect. On 1.20.1 they live on ServerGamePacketListenerImpl (they moved to
 * ServerCommonPacketListenerImpl in 1.20.2).
 */
@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerConnectionAccessor {
    @Accessor("keepAlivePending")
    boolean afk$isKeepAlivePending();

    @Accessor("keepAliveTime")
    long afk$getKeepAliveTime();
}
