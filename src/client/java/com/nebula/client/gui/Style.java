package com.nebula.client.gui;

import com.nebula.client.core.ClientManager;
import com.nebula.client.gui.themes.Theme;
import com.nebula.client.modules.ui.GuiSettings;
import com.nebula.client.rendering.BlurRenderer;
import com.nebula.client.rendering.GlowRenderer;
import com.nebula.client.rendering.Gradient;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;

/** Shared visual vocabulary for every Nebula surface, so components stay consistent. */
public final class Style {
	private Style() {
	}

	/** Shadowed panel: liquid glass when blur is enabled, otherwise a translucent tinted card. */
	public static void panel(GuiGraphics g, Theme t, float x, float y, float w, float h, float radius, float alpha, boolean glassAllowed) {
		if (alpha <= 0.01F) {
			return;
		}
		GuiSettings s = ClientManager.get().modules().guiSettings;
		GlowRenderer.shadow(g, x, y + 3, w, h, radius, 14, ColorUtil.withAlpha(0xFF000000, 0.45F * alpha));
		int border = ColorUtil.fade(t.border(), alpha);
		if (glassAllowed && s.blur.isOn() && alpha > 0.98F) {
			BlurRenderer.glass(g, x, y, w, h, radius, ColorUtil.withAlpha(t.panel(), t.opacity()), t.borderWidth(), border, s.refraction.floatValue());
		} else {
			RoundedRenderer.rect(g, x, y, w, h, radius, ColorUtil.withAlpha(t.panel(), Math.min(1, t.opacity() + 0.18F) * alpha), t.borderWidth(), border);
		}
	}

	public static Gradient accentGradient(Theme t, float alpha) {
		return Gradient.horizontal(ColorUtil.fade(t.accent(), alpha), ColorUtil.fade(t.accent2(), alpha));
	}

	/** Accent pill with a soft glow, used for selection and active states. */
	public static void accentPill(GuiGraphics g, Theme t, float x, float y, float w, float h, float radius, float alpha) {
		if (alpha <= 0.01F) {
			return;
		}
		GlowRenderer.glow(g, x, y, w, h, radius, 8, ColorUtil.fade(t.accent(), alpha), 0.6F * t.glow());
		RoundedRenderer.gradient(g, x, y, w, h, radius, accentGradient(t, alpha));
	}
}
