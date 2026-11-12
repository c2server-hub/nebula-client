package com.nebula.client.rendering;

import net.minecraft.client.gui.GuiGraphics;

/** Anti-aliased SDF rounded rectangles, borders and gradients. */
public final class RoundedRenderer {
	private RoundedRenderer() {
	}

	public static void rect(GuiGraphics g, float x, float y, float w, float h, float radius, int color) {
		ShapeElement.builder(x, y, w, h).radius(radius).fill(color).submit(g);
	}

	public static void rect(GuiGraphics g, float x, float y, float w, float h, float radius, int fill, float borderWidth, int border) {
		ShapeElement.builder(x, y, w, h).radius(radius).fill(fill).border(borderWidth, border).submit(g);
	}

	public static void outline(GuiGraphics g, float x, float y, float w, float h, float radius, float borderWidth, int color) {
		ShapeElement.builder(x, y, w, h).radius(radius).border(borderWidth, color).submit(g);
	}

	public static void circle(GuiGraphics g, float cx, float cy, float r, int color) {
		rect(g, cx - r, cy - r, r * 2, r * 2, r, color);
	}

	/** Gradient fill; multi-stop gradients are split into segments that share one SDF so the corners stay exact. */
	public static void gradient(GuiGraphics g, float x, float y, float w, float h, float radius, Gradient gradient) {
		gradient(g, x, y, w, h, radius, gradient, 0, 0);
	}

	public static void gradient(GuiGraphics g, float x, float y, float w, float h, float radius, Gradient gradient, float borderWidth, int border) {
		int[] c = gradient.colors();
		if (c.length == 0) {
			return;
		}
		if (gradient.direction() == Gradient.Direction.CORNERS && c.length == 4) {
			ShapeElement.builder(x, y, w, h).radius(radius).fill(c[0], c[1], c[2], c[3]).border(borderWidth, border).submit(g);
			return;
		}
		if (c.length == 1) {
			rect(g, x, y, w, h, radius, c[0], borderWidth, border);
			return;
		}
		int segments = c.length - 1;
		boolean horizontal = gradient.direction() == Gradient.Direction.HORIZONTAL;
		for (int i = 0; i < segments; i++) {
			float s = (float) i / segments;
			float e = (float) (i + 1) / segments;
			ShapeElement.Builder b = ShapeElement.builder(x, y, w, h).radius(radius).border(borderWidth, border);
			if (horizontal) {
				b.segment(s, e).fill(c[i], c[i + 1], c[i + 1], c[i]);
			} else {
				b.segmentY(s, e).fill(c[i], c[i], c[i + 1], c[i + 1]);
			}
			b.submit(g);
		}
	}

	/** Border whose colour follows the gradient, with an optional solid interior. */
	public static void gradientOutline(GuiGraphics g, float x, float y, float w, float h, float radius, float borderWidth, Gradient gradient, int fill) {
		int[] c = gradient.colors();
		int tl, tr, br, bl;
		switch (gradient.direction()) {
			case CORNERS -> {
				tl = c[0];
				tr = c[1];
				br = c[2];
				bl = c[3];
			}
			case VERTICAL -> {
				tl = tr = c[0];
				bl = br = c[c.length - 1];
			}
			default -> {
				tl = bl = c[0];
				tr = br = c[c.length - 1];
			}
		}
		ShapeElement.builder(x, y, w, h).radius(radius).fill(fill).border(borderWidth, tl, tr, br, bl).submit(g);
	}
}
