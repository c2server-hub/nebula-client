package com.nebula.client.rendering.world;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.utilities.ColorUtil;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/** Builds the motion-trail ribbon on the CPU each frame and draws it in one additive call. */
public final class TrailRenderer implements AutoCloseable {
	private final ByteBufferBuilder bytes = new ByteBufferBuilder(16 * 1024);

	/**
	 * Points are camera-relative; {@code ages} in [0, 1] (0 = newest). The ribbon spans
	 * {@code bottom..top} above each point.
	 */
	public void render(RenderTarget target, Matrix4fc view, float[] px, float[] py, float[] pz, float[] ages, int count,
					   float bottom, float top, int headColor, int tailColor, float opacity) {
		if (count < 2 || target.getColorTextureView() == null) {
			return;
		}
		BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		for (int i = 0; i < count - 1; i++) {
			int c0 = ColorUtil.fade(ColorUtil.mix(headColor, tailColor, ages[i]), opacity);
			int c1 = ColorUtil.fade(ColorUtil.mix(headColor, tailColor, ages[i + 1]), opacity);
			builder.addVertex(px[i], py[i] + bottom, pz[i]).setUv(ages[i], 0).setColor(c0);
			builder.addVertex(px[i], py[i] + top, pz[i]).setUv(ages[i], 1).setColor(c0);
			builder.addVertex(px[i + 1], py[i + 1] + top, pz[i + 1]).setUv(ages[i + 1], 1).setColor(c1);
			builder.addVertex(px[i + 1], py[i + 1] + bottom, pz[i + 1]).setUv(ages[i + 1], 0).setColor(c1);
		}
		try (MeshData mesh = builder.build()) {
			if (mesh == null) {
				return;
			}
			GpuBuffer vertices = DefaultVertexFormat.POSITION_TEX_COLOR.uploadImmediateVertexBuffer(mesh.vertexBuffer());
			RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
			int indexCount = mesh.drawState().indexCount();
			GpuBufferSlice transform = RenderSystem.getDynamicUniforms()
				.writeTransform(view, new Vector4f(1, 1, 1, 1), new Vector3f(), new Matrix4f());
			try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Nebula trail",
				target.getColorTextureView(), OptionalInt.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
				pass.setPipeline(NebulaPipelines.TRAIL);
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("DynamicTransforms", transform);
				pass.setVertexBuffer(0, vertices);
				pass.setIndexBuffer(indices.getBuffer(indexCount), indices.type());
				pass.drawIndexed(0, 0, indexCount, 1);
			}
		}
	}

	@Override
	public void close() {
		bytes.close();
	}
}
