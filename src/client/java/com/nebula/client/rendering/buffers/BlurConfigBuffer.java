package com.nebula.client.rendering.buffers;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

/** A tiny std140 block {@code { vec2 Direction; float Radius; }} kept on the GPU and rewritten only when its value changes. */
public final class BlurConfigBuffer implements AutoCloseable {
	private static final int SIZE = 16;
	private final String label;
	private @Nullable GpuBuffer buffer;
	private float dx = Float.NaN, dy = Float.NaN, radius = Float.NaN;

	public BlurConfigBuffer(String label) {
		this.label = label;
	}

	public GpuBuffer get(float dx, float dy, float radius) {
		if (buffer == null || buffer.isClosed()) {
			buffer = RenderSystem.getDevice().createBuffer(() -> "Nebula " + label, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, SIZE);
			this.dx = Float.NaN;
		}
		if (dx != this.dx || dy != this.dy || radius != this.radius) {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				Std140Builder data = Std140Builder.onStack(stack, SIZE).putVec2(dx, dy).putFloat(radius);
				RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data.get());
			}
			this.dx = dx;
			this.dy = dy;
			this.radius = radius;
		}
		return buffer;
	}

	@Override
	public void close() {
		if (buffer != null) {
			buffer.close();
			buffer = null;
		}
	}
}
