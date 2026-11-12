package com.nebula.client.gui.components;

import com.nebula.client.gui.themes.Theme;
import com.nebula.client.rendering.GlowRenderer;
import com.nebula.client.rendering.Gradient;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RenderUtils.NFont;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;

/**
 * HSV picker: saturation/value square, hue strip and optional alpha strip. The square is one SDF
 * quad with corner colours (white, hue, black, black); bilinear interpolation of those corners is
 * exactly v·lerp(white, hue, s), i.e. the true HSV plane.
 */
public final class ColorPicker extends SettingComponent<ColorSetting> {
	private static final float PICKER_H = 70;
	private final Animation open = new Animation(16);
	private boolean expanded;
	private float hue, sat, val, alpha;
	private int lastSynced;
	private Drag drag = Drag.NONE;

	private enum Drag { NONE, SV, HUE, ALPHA }

	public ColorPicker(ColorSetting setting) {
		super(setting);
		syncFromSetting();
	}

	private void syncFromSetting() {
		int c = setting.argb();
		float[] hsv = ColorUtil.toHsv(c);
		if (hsv[1] > 0.001F && hsv[2] > 0.001F) {
			hue = hsv[0];
		}
		sat = hsv[1];
		val = hsv[2];
		alpha = (c >>> 24) / 255F;
		lastSynced = c;
	}

	private float expandedHeight() {
		return PICKER_H + 8 + (setting.allowsAlpha() ? 14 : 0) + 4;
	}

	@Override
	public float preferredHeight() {
		return ROW + expandedHeight() * open.update(expanded ? 1 : 0);
	}

	@Override
	public void render(GuiGraphics g, Theme t, double mx, double my) {
		if (drag == Drag.NONE && setting.argb() != lastSynced) {
			syncFromSetting();
		}
		float hv = hover.update(mx >= x && mx < x + w && my >= y && my < y + ROW ? 1 : 0);
		label(g, t, hv, w - 90);
		int color = setting.argb();
		String hex = ColorUtil.hex(color);
		float sx = x + w - 18;
		RenderUtils.text(g, hex, sx - 6 - RenderUtils.width(hex, NFont.TEXT), y + ROW / 2 - 4, t.textDim(), NFont.TEXT);
		GlowRenderer.glow(g, sx, y + 5, 18, 14, 5, 6, color | 0xFF000000, 0.5F * hv * t.glow());
		RoundedRenderer.rect(g, sx, y + 5, 18, 14, 5, color, 1, ColorUtil.withAlpha(0xFFFFFFFF, 0.25F));

		float o = open.get();
		if (o < 0.01F) {
			return;
		}
		float top = y + ROW + 4;
		RenderUtils.scissor(g, x, y + ROW, w, expandedHeight() * o);
		float boxW = w - 20;
		int hueColor = ColorUtil.hsv(hue, 1, 1, 255);
		RoundedRenderer.gradient(g, x, top, boxW, PICKER_H, 6, Gradient.corners(0xFFFFFFFF, hueColor, 0xFF000000, 0xFF000000), 1, t.border());
		float px = x + sat * boxW, py = top + (1 - val) * PICKER_H;
		RoundedRenderer.rect(g, px - 4, py - 4, 8, 8, 4, ColorUtil.hsv(hue, sat, val, 255), 1.5F, 0xFFFFFFFF);

		float hx = x + w - 12;
		RoundedRenderer.gradient(g, hx, top, 12, PICKER_H, 6,
			Gradient.vertical(0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000), 1, t.border());
		RoundedRenderer.rect(g, hx - 1, top + hue * PICKER_H - 2, 14, 4, 2, 0xFFFFFFFF);

		if (setting.allowsAlpha()) {
			float ay = top + PICKER_H + 6;
			RoundedRenderer.rect(g, x, ay, w, 9, 4.5F, 0xFF2A2D35);
			RoundedRenderer.gradient(g, x, ay, w, 9, 4.5F, Gradient.horizontal(color & 0xFFFFFF, color | 0xFF000000), 1, t.border());
			RoundedRenderer.rect(g, x + alpha * w - 2, ay - 1, 4, 11, 2, 0xFFFFFFFF);
		}
		g.disableScissor();
	}

	private void apply() {
		int rgb = ColorUtil.hsv(hue, sat, val, 0) & 0xFFFFFF;
		int a = setting.allowsAlpha() ? Math.round(alpha * 255) : 255;
		int c = (a << 24) | rgb;
		lastSynced = c;
		setting.set(c);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button != 0) {
			return false;
		}
		if (my >= y && my < y + ROW && mx >= x && mx < x + w) {
			expanded = !expanded;
			return true;
		}
		if (!expanded || open.get() < 0.9F) {
			return false;
		}
		float top = y + ROW + 4;
		if (my >= top && my <= top + PICKER_H) {
			if (mx >= x && mx <= x + w - 20) {
				drag = Drag.SV;
			} else if (mx >= x + w - 12 && mx <= x + w) {
				drag = Drag.HUE;
			}
		} else if (setting.allowsAlpha() && my >= top + PICKER_H + 4 && my <= top + PICKER_H + 17 && mx >= x && mx <= x + w) {
			drag = Drag.ALPHA;
		}
		if (drag != Drag.NONE) {
			mouseDragged(mx, my);
			return true;
		}
		return false;
	}

	@Override
	public void mouseDragged(double mx, double my) {
		float top = y + ROW + 4;
		switch (drag) {
			case SV -> {
				sat = (float) Math.clamp((mx - x) / (w - 20), 0, 1);
				val = 1 - (float) Math.clamp((my - top) / PICKER_H, 0, 1);
			}
			case HUE -> hue = (float) Math.clamp((my - top) / PICKER_H, 0, 0.999);
			case ALPHA -> alpha = (float) Math.clamp((mx - x) / w, 0, 1);
			default -> {
				return;
			}
		}
		apply();
	}

	@Override
	public void mouseReleased(double mx, double my, int button) {
		drag = Drag.NONE;
	}
}
