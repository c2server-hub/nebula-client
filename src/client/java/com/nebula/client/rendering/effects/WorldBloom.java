package com.nebula.client.rendering.effects;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.rendering.buffers.BlurConfigBuffer;
import com.nebula.client.rendering.buffers.ManagedTexture;

/**
 * Whole-frame cinematic bloom: a soft-knee bright pass at half resolution, a separable gaussian
 * blur at quarter resolution, then an additive composite back onto the main target. The blur and
 * composite stages reuse the exact pipelines of the orb bloom, so the look stays consistent.
 */
public final class WorldBloom implements AutoCloseable {
	private final ManagedTexture bright = new ManagedTexture("world bloom bright");
	private final GaussianBlur blur = new GaussianBlur("world bloom");
	private final BlurConfigBuffer brightConfig = new BlurConfigBuffer("world bloom brightpass");
	private final BlurConfigBuffer compositeConfig = new BlurConfigBuffer("world bloom composite");

	public void render(RenderTarget target, float threshold, float strength, float radius, int iterations) {
		if (target.getColorTextureView() == null || strength <= 0.01F) {
			return;
		}
		bright.ensureSize(target.width / 2, target.height / 2);
		blur.ensureSize(target.width, target.height, true);
		if (!bright.isAllocated() || !blur.isAllocated()) {
			return;
		}
		// Buffer writes are commands too, so they must happen before the passes are opened.
		GpuBuffer brightPass = brightConfig.get(0, 0, threshold);
		GpuBuffer composite = compositeConfig.get(0, 0, strength);
		GaussianBlur.pass(NebulaPipelines.BRIGHTPASS, target.getColorTextureView(), bright.view(), brightPass);
		blur.run(bright.view(), radius, iterations);
		GaussianBlur.pass(NebulaPipelines.COMPOSITE_ADD, blur.output(), target.getColorTextureView(), composite);
	}

	@Override
	public void close() {
		bright.close();
		blur.close();
		brightConfig.close();
		compositeConfig.close();
	}
}
