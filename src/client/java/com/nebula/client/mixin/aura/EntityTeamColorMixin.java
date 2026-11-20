package com.nebula.client.mixin.aura;

import com.nebula.client.core.ClientManager;
import com.nebula.client.modules.visual.AuraGlow;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tints the glow outline of the local player with the animated / reactive aura colour. */
@Mixin(Entity.class)
public abstract class EntityTeamColorMixin {
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void nebula$auraOutlineColor(CallbackInfoReturnable<Integer> cir) {
		if (!ClientManager.isReady()) {
			return;
		}
		AuraGlow aura = ClientManager.get().modules().auraGlow;
		if (aura.forcesOutline((Entity) (Object) this)) {
			cir.setReturnValue(aura.outlineRgb());
		}
	}
}
