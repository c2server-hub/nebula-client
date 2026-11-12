package com.nebula.client.rendering.world;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Collects camera-relative quads for one glow pipeline and draws them in a single call.
 * Quad UVs span [-1, 1] so the fragment shader can shape them (rings, soft sparks).
 */
public final class GlowBatch implements AutoCloseable {
	private final ByteBufferBuilder bytes = new ByteBufferBuilder(32 * 1024);
	private final Vector3f right = new Vector3f();
	private final Vector3f up = new Vector3f();
	private final Vector4f white = new Vector4f(1, 1, 1, 1);
	private final Vector3f params = new Vector3f();
	private final Matrix4f identity = new Matrix4f();
	private @Nullable BufferBuilder builder;
	private @Nullable WorldFrame frame;

	public void begin(WorldFrame frame) {
		this.frame = frame;
		this.builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		Quaternionfc camera = frame.cameraRotation();
		camera.transform(1, 0, 0, right);
		camera.transform(0, 1, 0, up);
	}

	/** Horizontal quad (lying on the XZ plane) centred on a camera-relative point. */
	public void flat(float x, float y, float z, float half, int argb) {
		BufferBuilder b = builder();
		b.addVertex(x - half, y, z - half).setUv(-1, -1).setColor(argb);
		b.addVertex(x - half, y, z + half).setUv(-1, 1).setColor(argb);
		b.addVertex(x + half, y, z + half).setUv(1, 1).setColor(argb);
		b.addVertex(x + half, y, z - half).setUv(1, -1).setColor(argb);
	}

	/** Camera-facing quad centred on a camera-relative point. */
	public void billboard(float x, float y, float z, float half, int argb) {
		BufferBuilder b = builder();
		float rx = right.x * half, ry = right.y * half, rz = right.z * half;
		float ux = up.x * half, uy = up.y * half, uz = up.z * half;
		b.addVertex(x - rx - ux, y - ry - uy, z - rz - uz).setUv(-1, -1).setColor(argb);
		b.addVertex(x - rx + ux, y - ry + uy, z - rz + uz).setUv(-1, 1).setColor(argb);
		b.addVertex(x + rx + ux, y + ry + uy, z + rz + uz).setUv(1, 1).setColor(argb);
		b.addVertex(x + rx - ux, y + ry - uy, z + rz - uz).setUv(1, -1).setColor(argb);
	}

	/** Arbitrary quad; corners get UVs (-1,-1), (-1,1), (1,1), (1,-1) in order. */
	public void quad(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, int argb) {
		BufferBuilder b = builder();
		b.addVertex(x0, y0, z0).setUv(-1, -1).setColor(argb);
		b.addVertex(x1, y1, z1).setUv(-1, 1).setColor(argb);
		b.addVertex(x2, y2, z2).setUv(1, 1).setColor(argb);
		b.addVertex(x3, y3, z3).setUv(1, -1).setColor(argb);
	}

	/** All six faces of a camera-relative box; side faces use V along the vertical axis. */
	public void box(float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
		quad(x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, argb);
		quad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, argb);
		quad(x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, argb);
		quad(x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, argb);
		quad(x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, argb);
		quad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, argb);
	}

	/** Draws everything added since {@link #begin}; the three shader parameters are passed through ModelOffset. */
	public void end(RenderPipeline pipeline, float p0, float p1, float p2) {
		BufferBuilder b = builder();
		builder = null;
		WorldFrame f = frame;
		try (MeshData mesh = b.build()) {
			if (mesh == null || f == null || f.target().getColorTextureView() == null) {
				return;
			}
			GpuBuffer vertices = DefaultVertexFormat.POSITION_TEX_COLOR.uploadImmediateVertexBuffer(mesh.vertexBuffer());
			RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
			int indexCount = mesh.drawState().indexCount();
			params.set(p0, p1, p2);
			GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(f.view(), white, params, identity);
			try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Nebula glow " + pipeline.getLocation().getPath(),
				f.target().getColorTextureView(), OptionalInt.empty(), f.target().getDepthTextureView(), OptionalDouble.empty())) {
				pass.setPipeline(pipeline);
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("DynamicTransforms", transform);
				pass.setVertexBuffer(0, vertices);
				pass.setIndexBuffer(indices.getBuffer(indexCount), indices.type());
				pass.drawIndexed(0, 0, indexCount, 1);
			}
		}
	}

	private BufferBuilder builder() {
		if (builder == null) {
			throw new IllegalStateException("GlowBatch.begin() was not called");
		}
		return builder;
	}

	@Override
	public void close() {
		bytes.close();
	}
}
