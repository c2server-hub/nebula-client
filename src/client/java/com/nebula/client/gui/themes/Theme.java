package com.nebula.client.gui.themes;

import com.nebula.client.core.ClientManager;
import com.nebula.client.modules.ui.GuiSettings;
import com.nebula.client.modules.ui.ThemeEditor;
import com.nebula.client.utilities.ColorUtil;

/** Resolved colours + geometry for one frame, derived from the Theme Editor and GUI Settings modules. */
public record Theme(
	int background,
	int panel,
	int accent,
	int accent2,
	int border,
	int text,
	int textDim,
	float radius,
	float borderWidth,
	float opacity,
	float glow
) {
	public static Theme current() {
		ThemeEditor t = ClientManager.get().modules().themeEditor;
		GuiSettings s = ClientManager.get().modules().guiSettings;
		return new Theme(
			t.background.argb(), t.panel.argb(), t.accent.argb(), t.accent2.argb(), t.border.argb(), t.text.argb(), t.textDim.argb(),
			s.radius.floatValue(), s.borderWidth.floatValue(), s.opacity.floatValue(), s.glow.floatValue()
		);
	}

	/** Translucent panel tint honouring the global opacity setting. */
	public int panelTint(float extra) {
		return ColorUtil.withAlpha(panel, opacity * extra);
	}

	public int surface(float amount) {
		return ColorUtil.withAlpha(ColorUtil.mix(panel, 0xFFFFFFFF, 0.06F), amount);
	}

	public int accentMix(float t) {
		return ColorUtil.mix(accent, accent2, t);
	}
}
