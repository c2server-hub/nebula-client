package com.nebula.client.rendering;

import net.minecraft.client.gui.GuiGraphics;

/** Soft shadows and glows, computed analytically in the fragment shader (gaussian-convolved rounded box). */
public final class GlowRenderer {
	private GlowRenderer() {
	}

	/** Drop shadow: filled blurred shape, typically drawn under a panel. */
	public static void shadow(GuiGraphics g, float x, float y, float w, float h, float radius, float softness, int color) {
		if (softness <= 0) {
			return;
		}
		ShapeElement.builder(x, y, w, h).radius(radius).fill(color).soft(softness, 1, false).submit(g);
	}

	/** Halo that only exists outside the shape, so it can sit under translucent content without tinting it. */
	public static void glow(GuiGraphics g, float x, float y, float w, float h, float radius, float softness, int color, float intensity) {
		if (softness <= 0 || intensity <= 0) {
			return;
		}
		ShapeElement.builder(x, y, w, h).radius(radius).fill(color).soft(softness, intensity, true).submit(g);
	}
}
