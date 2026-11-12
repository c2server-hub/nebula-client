package com.nebula.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.nebula.client.rendering.BlurRenderer;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public class GuiRendererMixin {
	/** The main target holds the finished 3D frame here, before any GUI geometry is drawn. */
	@Inject(method = "render", at = @At("HEAD"))
	private void nebula$captureBackdrop(GpuBufferSlice fog, CallbackInfo ci) {
		BlurRenderer.INSTANCE.renderBackdropIfRequested();
	}
}
