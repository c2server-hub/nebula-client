package com.nebula.client.mixin.immersion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nebula.client.modules.immersion.ImmersionHooks;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Camera hooks. Every injection is optional (defaultRequire = 0, required = false):
 * if a signature drifts, only that effect is skipped, the game never crashes.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void nebula$bobView(CallbackInfo ci) {
        if (ImmersionHooks.cancelVanillaBob()) {
            ci.cancel();
        }
    }

    /** Runs for both the world pass and the hand pass, right after the vanilla hurt tilt. */
    @Inject(method = "bobHurt", at = @At("RETURN"))
    private void nebula$afterHurt(PoseStack pose, float partialTick, CallbackInfo ci) {
        ImmersionHooks.applyView(pose, partialTick);
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void nebula$handBegin(CallbackInfo ci) {
        ImmersionHooks.renderingHand = true;
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void nebula$handEnd(CallbackInfo ci) {
        ImmersionHooks.renderingHand = false;
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void nebula$fov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        if (useFovSetting) {
            cir.setReturnValue(ImmersionHooks.modifyFov(cir.getReturnValue()));
        }
    }
}
