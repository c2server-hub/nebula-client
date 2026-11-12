package com.nebula.client.rendering.effects;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.rendering.buffers.BlurConfigBuffer;
import com.nebula.client.rendering.buffers.ManagedTexture;
import org.jspecify.annotations.Nullable;

import java.util.OptionalInt;

/**
 * Separable gaussian blur running at a reduced resolution: one box-filtered downsample per
 * halving, then horizontal/vertical gaussian passes ping-ponging between two textures.
 */
public final class GaussianBlur implements AutoCloseable {
	private final String label;
	private final ManagedTexture half;
	private final ManagedTexture ping;
	private final ManagedTexture pong;
	private final BlurConfigBuffer horizontal;
	private final BlurConfigBuffer vertical;

	public GaussianBlur(String label) {
		this.label = label;
		this.half = new ManagedTexture(label + " half");
		this.ping = new ManagedTexture(label + " ping");
		this.pong = new ManagedTexture(label + " pong");
		this.horizontal = new BlurConfigBuffer(label + " H");
		this.vertical = new BlurConfigBuffer(label + " V");
	}

	/** Allocates targets for a source of the given size; quarter resolution when {@code quarter} is set. */
	public void ensureSize(int sourceWidth, int sourceHeight, boolean quarter) {
		half.ensureSize(sourceWidth / 2, sourceHeight / 2);
		int div = quarter ? 4 : 2;
		ping.ensureSize(sourceWidth / div, sourceHeight / div);
		pong.ensureSize(sourceWidth / div, sourceHeight / div);
	}

	/** The blurred result; valid once {@link #ensureSize} has been called, filled by {@link #run}. */
	public GpuTextureView output() {
		return ping.view();
	}

	public boolean isAllocated() {
		return ping.isAllocated();
	}

	public void run(GpuTextureView source, float radius, int iterations) {
		boolean quarter = ping.width() < half.width();
		if (quarter) {
			pass(NebulaPipelines.DOWNSAMPLE, source, half.view(), null);
			pass(NebulaPipelines.DOWNSAMPLE, half.view(), ping.view(), null);
		} else {
			pass(NebulaPipelines.DOWNSAMPLE, source, ping.view(), null);
		}
		GpuBuffer h = horizontal.get(1, 0, radius);
		GpuBuffer v = vertical.get(0, 1, radius);
		for (int i = 0; i < Math.max(1, iterations); i++) {
			pass(NebulaPipelines.GAUSSIAN, ping.view(), pong.view(), h);
			pass(NebulaPipelines.GAUSSIAN, pong.view(), ping.view(), v);
		}
	}

	static void pass(RenderPipeline pipeline, GpuTextureView input, GpuTextureView output, @Nullable GpuBuffer config) {
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
			.createRenderPass(() -> "Nebula " + pipeline.getLocation().getPath(), output, OptionalInt.empty())) {
			pass.setPipeline(pipeline);
			pass.bindTexture("InSampler", input, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			if (config != null) {
				pass.setUniform("BlurConfig", config);
			}
			pass.draw(0, 3);
		}
	}

	@Override
	public void close() {
		half.close();
		ping.close();
		pong.close();
		horizontal.close();
		vertical.close();
	}

	@Override
	public String toString() {
		return "GaussianBlur[" + label + "]";
	}
}
