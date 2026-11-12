package com.nebula.client.rendering.buffers;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import org.jspecify.annotations.Nullable;

/** An RGBA8 colour attachment that is (re)allocated lazily and only when its size actually changes. */
public final class ManagedTexture implements AutoCloseable {
	private final String label;
	private @Nullable GpuTexture texture;
	private @Nullable GpuTextureView view;
	private int width;
	private int height;

	public ManagedTexture(String label) {
		this.label = label;
	}

	public void ensureSize(int width, int height) {
		width = Math.max(1, width);
		height = Math.max(1, height);
		if (texture != null && !texture.isClosed() && this.width == width && this.height == height) {
			return;
		}
		close();
		GpuDevice device = RenderSystem.getDevice();
		int usage = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST;
		texture = device.createTexture(() -> "Nebula " + label, usage, TextureFormat.RGBA8, width, height, 1, 1);
		view = device.createTextureView(texture);
		this.width = width;
		this.height = height;
	}

	public GpuTexture texture() {
		if (texture == null) {
			throw new IllegalStateException("Texture " + label + " not allocated");
		}
		return texture;
	}

	public GpuTextureView view() {
		if (view == null) {
			throw new IllegalStateException("Texture " + label + " not allocated");
		}
		return view;
	}

	public boolean isAllocated() {
		return view != null;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	@Override
	public void close() {
		if (view != null) {
			view.close();
			view = null;
		}
		if (texture != null) {
			texture.close();
			texture = null;
		}
	}
}
