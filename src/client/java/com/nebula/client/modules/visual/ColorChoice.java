package com.nebula.client.modules.visual;

import com.nebula.client.gui.themes.Theme;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.utilities.ColorUtil;

/** "Theme / Custom / Rainbow" colour choice shared by the glow modules. */
final class ColorChoice {
	final ModeSetting mode;
	final ColorSetting custom;

	ColorChoice(ModeSetting mode, ColorSetting custom) {
		this.mode = mode;
		this.custom = custom;
	}

	static ModeSetting modeSetting() {
		return new ModeSetting("Color mode", "Where the colour comes from", "Theme", "Theme", "Custom", "Rainbow");
	}

	/** {@code t} in [0, 1] picks along the theme gradient or shifts the rainbow hue. */
	int color(float t, float seconds) {
		return switch (mode.get()) {
			case "Custom" -> custom.argb();
			case "Rainbow" -> ColorUtil.hsv(((seconds * 0.12F + t * 0.35F) % 1F + 1F) % 1F, 0.7F, 1F, 255);
			default -> Theme.current().accentMix(t);
		};
	}
}
