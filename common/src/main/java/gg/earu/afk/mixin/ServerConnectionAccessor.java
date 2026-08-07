package gg.earu.afk.mixin;

import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The only mixin in the mod. Vanilla tracks keepalive state privately and exposes no API for it,
 * so reading these two fields is the only way to know a connection is stalling before the 15s
 * disconnect. Both have lived on ServerCommonPacketListenerImpl since 1.20.2 (on 1.20.1 they sit
 * on ServerGamePacketListenerImpl instead).
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public interface ServerConnectionAccessor {
    @Accessor("keepAlivePending")
    boolean afk$isKeepAlivePending();

    @Accessor("keepAliveTime")
    long afk$getKeepAliveTime();
}
