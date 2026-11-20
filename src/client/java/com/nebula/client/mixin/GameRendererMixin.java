package com.nebula.client.mixin;

import com.nebula.client.core.ClientManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	/**
 	* Applies the Zoom module's animated FOV multiplier to every projection the game builds.
 	* The multiplier is evaluated even while easing back to 1, so the transition always runs;
 	* the menu panorama is left untouched.
 	*/
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void nebula$zoomFov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
		if (ClientManager.isReady() && Minecraft.getInstance().player != null) {
			float multiplier = ClientManager.get().modules().zoom.multiplier();
			if (multiplier < 0.999F) {
				cir.setReturnValue(cir.getReturnValueF() * multiplier);
			}
		}
	}
}
