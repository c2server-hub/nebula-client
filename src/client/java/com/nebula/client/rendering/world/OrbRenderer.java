package com.nebula.client.rendering.world;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.rendering.buffers.BlurConfigBuffer;
import com.nebula.client.rendering.buffers.ManagedTexture;
import com.nebula.client.rendering.effects.GaussianBlur;
import com.nebula.client.utilities.ColorUtil;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Draws the orbs in three stages:
 * <ol>
 *   <li>emissive cores: real sphere geometry, depth-tested and depth-written into the scene;</li>
 *   <li>halo: camera-facing quads with an analytic gaussian falloff, additively blended;</li>
 *   <li>bloom: cores re-rendered into an off-screen emissive buffer against the scene depth
 *       (so occluded orbs do not bloom through walls), gaussian-blurred, then added onto the frame.</li>
 * </ol>
 * None of this lights blocks: Minecraft's lighting is per-block light levels, which this mod does not modify.
 */
public final class OrbRenderer implements AutoCloseable {
	private final SphereMesh sphere = new SphereMesh(3);
	private final ManagedTexture emissive = new ManagedTexture("orb emissive");
	private final GaussianBlur bloomBlur = new GaussianBlur("orb bloom");
	private final BlurConfigBuffer compositeConfig = new BlurConfigBuffer("orb bloom composite");
	private final GpuBufferSlice[] coreTransforms = new GpuBufferSlice[OrbFrame.MAX];
	private final GpuBufferSlice[] haloTransforms = new GpuBufferSlice[OrbFrame.MAX];
	private final Matrix4f scratch = new Matrix4f();
	private final Vector4f color = new Vector4f();
	private final Vector3f params = new Vector3f();
	private @Nullable GpuBuffer quad;

	public void render(OrbFrame frame, RenderTarget target, Matrix4fc view) {
		if (frame.count == 0 || target.getColorTextureView() == null || target.getDepthTextureView() == null) {
			return;
		}
		Matrix4f identity = new Matrix4f();
		for (int i = 0; i < frame.count; i++) {
			int c = frame.color[i];
			color.set(ColorUtil.red(c), ColorUtil.green(c), ColorUtil.blue(c), frame.intensity);
			params.set(frame.time, i * 0.618F, frame.radius[i] * frame.haloScale);
			scratch.set(view).translate(frame.x[i], frame.y[i], frame.z[i]).scale(frame.radius[i]);
			coreTransforms[i] = RenderSystem.getDynamicUniforms().writeTransform(scratch, color, params, identity);
			color.w = frame.haloIntensity;
			scratch.set(view).translate(frame.x[i], frame.y[i], frame.z[i]);
			haloTransforms[i] = RenderSystem.getDynamicUniforms().writeTransform(scratch, color, params, identity);
		}

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		RenderSystem.AutoStorageIndexBuffer quadIndices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
		try (RenderPass pass = encoder.createRenderPass(() -> "Nebula orbs", target.getColorTextureView(), OptionalInt.empty(),
			target.getDepthTextureView(), OptionalDouble.empty())) {
			RenderSystem.bindDefaultUniforms(pass);
			drawCores(pass, frame, true);
			if (frame.haloIntensity > 0) {
				pass.setPipeline(NebulaPipelines.ORB_HALO);
				pass.setVertexBuffer(0, quad());
				pass.setIndexBuffer(quadIndices.getBuffer(6), quadIndices.type());
				for (int i = 0; i < frame.count; i++) {
					pass.setUniform("DynamicTransforms", haloTransforms[i]);
					pass.drawIndexed(0, 0, 6, 1);
				}
			}
		}

		if (frame.bloom && frame.bloomStrength > 0) {
			emissive.ensureSize(target.width, target.height);
			try (RenderPass pass = encoder.createRenderPass(() -> "Nebula orb emissive", emissive.view(), OptionalInt.of(0),
				target.getDepthTextureView(), OptionalDouble.empty())) {
				RenderSystem.bindDefaultUniforms(pass);
				drawCores(pass, frame, false);
			}
			bloomBlur.ensureSize(target.width, target.height, false);
			bloomBlur.run(emissive.view(), frame.bloomRadius, 3);
			composite(encoder, bloomBlur.output(), target.getColorTextureView(), frame.bloomStrength);
		}
	}

	private void drawCores(RenderPass pass, OrbFrame frame, boolean writeDepth) {
		pass.setPipeline(writeDepth ? NebulaPipelines.ORB : NebulaPipelines.ORB_EMISSIVE);
		pass.setVertexBuffer(0, sphere.vertices());
		pass.setIndexBuffer(sphere.indices(), VertexFormat.IndexType.INT);
		for (int i = 0; i < frame.count; i++) {
			pass.setUniform("DynamicTransforms", coreTransforms[i]);
			pass.drawIndexed(0, 0, sphere.indexCount(), 1);
		}
	}

	private void composite(CommandEncoder encoder, GpuTextureView bloom, GpuTextureView output, float strength) {
		// Buffer writes are commands too, so they must happen before the pass is opened.
		GpuBuffer config = compositeConfig.get(0, 0, strength);
		try (RenderPass pass = encoder.createRenderPass(() -> "Nebula bloom composite", output, OptionalInt.empty())) {
			pass.setPipeline(NebulaPipelines.COMPOSITE_ADD);
			pass.bindTexture("InSampler", bloom, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			pass.setUniform("BlurConfig", config);
			pass.draw(0, 3);
		}
	}

	private GpuBuffer quad() {
		if (quad == null) {
			ByteBuffer data = MemoryUtil.memAlloc(4 * 12);
			try {
				data.putFloat(-1).putFloat(-1).putFloat(0)
					.putFloat(1).putFloat(-1).putFloat(0)
					.putFloat(1).putFloat(1).putFloat(0)
					.putFloat(-1).putFloat(1).putFloat(0)
					.flip();
				quad = RenderSystem.getDevice().createBuffer(() -> "Nebula halo quad", GpuBuffer.USAGE_VERTEX, data);
			} finally {
				MemoryUtil.memFree(data);
			}
		}
		return quad;
	}

	@Override
	public void close() {
		sphere.close();
		emissive.close();
		bloomBlur.close();
		compositeConfig.close();
		if (quad != null) {
			quad.close();
			quad = null;
		}
	}
}
