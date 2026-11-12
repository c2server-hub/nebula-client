package com.nebula.client.mixin;

import com.nebula.client.core.ClientManager;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
	/** Vanilla clouds are drawn after the sky and would cover the custom starfield with flat blocks. */
	@Inject(method = "addCloudsPass", at = @At("HEAD"), cancellable = true)
	private void nebula$hideClouds(CallbackInfo ci) {
		if (ClientManager.isReady() && ClientManager.get().modules().customSky.shouldHideClouds()) {
			ci.cancel();
		}
	}
}
