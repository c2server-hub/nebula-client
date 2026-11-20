package com.nebula.client.mixin.immersion;

import com.nebula.client.modules.immersion.ImmersionHooks;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    /** RETURN is only reached on the main thread (the netty call bails out early). */
    @Inject(method = "handleExplosion", at = @At("RETURN"))
    private void nebula$explosion(ClientboundExplodePacket packet, CallbackInfo ci) {
        ImmersionHooks.onExplosion(packet.center());
    }
}
