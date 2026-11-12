package com.nebula.client.gui.components;

import com.nebula.client.gui.Style;
import com.nebula.client.gui.themes.Theme;
import com.nebula.client.rendering.GlowRenderer;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;

/** iOS-style switch bound to a boolean setting. */
public final class Toggle extends SettingComponent<BoolSetting> {
	private final Animation state;

	public Toggle(BoolSetting setting) {
		super(setting);
		this.state = new Animation(setting.isOn() ? 1 : 0, 16);
	}

	@Override
	public void render(GuiGraphics g, Theme t, double mx, double my) {
		float hv = updateHover(mx, my);
		label(g, t, hv, w - 40);
		drawSwitch(g, t, x + w - 26, y + ROW / 2 - 6, state.update(setting.isOn() ? 1 : 0), hv);
	}

	/** Shared with ModuleButton so module and setting switches look identical. */
	public static void drawSwitch(GuiGraphics g, Theme t, float sx, float sy, float on, float hover) {
		float sw = 24, sh = 12;
		int off = ColorUtil.withAlpha(ColorUtil.mix(t.panel(), 0xFFFFFFFF, 0.14F + 0.06F * hover), 1F);
		RoundedRenderer.rect(g, sx, sy, sw, sh, sh / 2, off, 1, t.border());
		if (on > 0.01F) {
			GlowRenderer.glow(g, sx, sy, sw, sh, sh / 2, 7, t.accent(), 0.55F * t.glow() * on);
			RoundedRenderer.gradient(g, sx, sy, sw, sh, sh / 2, Style.accentGradient(t, on));
		}
		float knob = sh - 4;
		float kx = sx + 2 + (sw - knob - 4) * Animation.easeOutCubic(on);
		RoundedRenderer.circle(g, kx + knob / 2, sy + sh / 2, knob / 2, 0xFFF8FAFF);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0 && isHovered(mx, my)) {
			setting.toggle();
			return true;
		}
		return false;
	}
}
