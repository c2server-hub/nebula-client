package com.nebula.client.mixin.aura;

import com.nebula.client.core.ClientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forces the vanilla glow outline on the local player when Aura Glow's outline is enabled. */
@Mixin(Minecraft.class)
public abstract class MinecraftGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void nebula$auraOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (ClientManager.isReady() && ClientManager.get().modules().auraGlow.forcesOutline(entity)) {
			cir.setReturnValue(true);
		}
	}
}
