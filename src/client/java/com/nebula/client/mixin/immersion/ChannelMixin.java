package com.nebula.client.mixin.immersion;

import com.mojang.blaze3d.audio.Channel;
import com.nebula.client.modules.immersion.SoundFx;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Attaches OpenAL EFX reverb sends and low-pass filters to every played source (sound thread). */
@Mixin(Channel.class)
public abstract class ChannelMixin {
    @Shadow @Final private int source;

    @Unique private Vec3 nebula$pos;
    @Unique private boolean nebula$relative;
    @Unique private int nebula$filter;

    @Inject(method = "setSelfPosition", at = @At("HEAD"))
    private void nebula$pos(Vec3 pos, CallbackInfo ci) {
        nebula$pos = pos;
    }

    @Inject(method = "setRelative", at = @At("HEAD"))
    private void nebula$relative(boolean relative, CallbackInfo ci) {
        nebula$relative = relative;
    }

    @Inject(method = "play", at = @At("HEAD"))
    private void nebula$play(CallbackInfo ci) {
        nebula$filter = SoundFx.onPlay(source, nebula$filter, nebula$pos, nebula$relative);
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void nebula$destroy(CallbackInfo ci) {
        SoundFx.release(nebula$filter);
        nebula$filter = 0;
    }
}
