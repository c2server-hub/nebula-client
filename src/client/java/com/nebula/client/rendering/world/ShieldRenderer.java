package com.nebula.client.rendering.world;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.utilities.ColorUtil;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Draws one fresnel energy bubble around a player: real sphere geometry, additively blended and
 * depth-tested against the scene (no depth write), so the shell wraps the body and disappears
 * behind walls. Shading lives in shield.fsh; the vertex shader is shared with the orbs.
 */
public final class ShieldRenderer implements AutoCloseable {
	private final SphereMesh sphere = new SphereMesh(4);
	private final Matrix4f scratch = new Matrix4f();
	private final Vector4f color = new Vector4f();
	private final Vector3f params = new Vector3f();
	private final Matrix4f identity = new Matrix4f();

	/**
	 * @param x, y, z camera-relative centre of the bubble
	 * @param sx, sy, sz per-axis radius (an ellipsoid hugs the player body better than a sphere)
	 * @param pulse damage wave phase in 0..1, or a negative value when idle
	 */
	public void render(RenderTarget target, Matrix4fc view, float x, float y, float z, float sx, float sy, float sz,
			int argb, float intensity, float time, float pulse, float pattern) {
		if (target.getColorTextureView() == null || target.getDepthTextureView() == null) {
			return;
		}
		color.set(ColorUtil.red(argb), ColorUtil.green(argb), ColorUtil.blue(argb), intensity);
		params.set(time, pulse, pattern);
		scratch.set(view).translate(x, y, z).scale(sx, sy, sz);
		GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(scratch, color, params, identity);
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (RenderPass pass = encoder.createRenderPass(() -> "Nebula shield", target.getColorTextureView(), OptionalInt.empty(),
				target.getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(NebulaPipelines.SHIELD);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transform);
			pass.setVertexBuffer(0, sphere.vertices());
			pass.setIndexBuffer(sphere.indices(), VertexFormat.IndexType.INT);
			pass.drawIndexed(0, 0, sphere.indexCount(), 1);
		}
	}

	@Override
	public void close() {
		sphere.close();
	}
}
