package com.nebula.client.rendering.world;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/** Draws one full-screen sky pass; shader parameters travel in the dynamic transform block. */
public final class SkyPass {
	private SkyPass() {
	}

	public static void draw(RenderPipeline pipeline, WorldFrame f, Vector4fc modulator, Vector3fc offset, Matrix4fc colors) {
		if (f.target().getColorTextureView() == null || f.target().getDepthTextureView() == null) {
			return;
		}
		GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(f.view(), modulator, offset, colors);
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Nebula " + pipeline.getLocation().getPath(),
			f.target().getColorTextureView(), OptionalInt.empty(), f.target().getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transform);
			pass.draw(0, 3);
		}
	}

	/** 0 at full day, 1 at full night, from the world clock (sun elevation). */
	public static float nightFactor(long dayTime) {
		float t = Math.floorMod(dayTime, 24000L) / 24000F;
		float elevation = (float) Math.cos(2 * Math.PI * (t - 0.25));
		return Math.clamp((0.15F - elevation) / 0.4F, 0, 1);
	}
}
