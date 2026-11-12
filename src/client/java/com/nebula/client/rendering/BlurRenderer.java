package com.nebula.client.rendering;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.nebula.client.rendering.effects.GaussianBlur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;
import org.jspecify.annotations.Nullable;

/**
 * Frosted "liquid glass" panels. Submitting a glass shape requests a backdrop; right before the
 * GUI is drawn the finished 3D frame is downsampled and gaussian-blurred once, and every glass
 * element samples that texture in screen space. Nothing runs on frames without glass.
 */
public final class BlurRenderer {
	public static final BlurRenderer INSTANCE = new BlurRenderer();

	private final GaussianBlur blur = new GaussianBlur("backdrop");
	private boolean requested;
	private float radius = 6;
	private int iterations = 2;
	private @Nullable GpuTextureView setupView;
	private @Nullable TextureSetup setup;

	private BlurRenderer() {
	}

	/** Blur radius is in quarter-resolution texels, so 6 is roughly a 24px kernel on screen. */
	public void configure(float radius, int iterations) {
		this.radius = Math.max(0.5F, radius);
		this.iterations = Math.max(1, iterations);
	}

	public static void glass(GuiGraphics g, float x, float y, float w, float h, float radius, int tint, float borderWidth, int border, float refraction) {
		TextureSetup textures = INSTANCE.requestBackdrop();
		ShapeElement.builder(x, y, w, h)
			.pipeline(NebulaPipelines.GLASS, textures)
			.radius(radius)
			.fill(tint)
			.border(borderWidth, border)
			.intensity(refraction)
			.submit(g);
	}

	private TextureSetup requestBackdrop() {
		RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
		blur.ensureSize(main.width, main.height, true);
		requested = true;
		GpuTextureView view = blur.output();
		if (setup == null || setupView != view) {
			setupView = view;
			setup = TextureSetup.singleTexture(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
		}
		return setup;
	}

	/** Called from the GUI renderer before it draws anything this frame. */
	public void renderBackdropIfRequested() {
		if (!requested) {
			return;
		}
		NebulaPipelines.verifyOnce();
		requested = false;
		RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
		GpuTextureView source = main.getColorTextureView();
		if (source == null || !blur.isAllocated()) {
			return;
		}
		blur.run(source, radius, iterations);
	}

	public void close() {
		blur.close();
		setup = null;
		setupView = null;
	}
}
