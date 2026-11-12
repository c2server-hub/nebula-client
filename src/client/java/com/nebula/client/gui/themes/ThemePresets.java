package com.nebula.client.gui.themes;

import java.util.LinkedHashMap;
import java.util.Map;

/** Built-in colour presets: background, panel, accent, accent 2, border, text, secondary text. */
public final class ThemePresets {
	public record Palette(int background, int panel, int accent, int accent2, int border, int text, int textDim) {
	}

	public static final Map<String, Palette> PRESETS = new LinkedHashMap<>();

	static {
		PRESETS.put("Nebula", new Palette(0xFF070A12, 0xFF141821, 0xFF3D8BFF, 0xFF8A5CFF, 0x1FFFFFFF, 0xFFF2F4F8, 0xFF8C93A3));
		PRESETS.put("Violet Haze", new Palette(0xFF0B0812, 0xFF181324, 0xFFA25CFF, 0xFFFF5CC8, 0x22FFFFFF, 0xFFF5F0FA, 0xFF968CA8));
		PRESETS.put("Aurora", new Palette(0xFF051010, 0xFF101C1E, 0xFF2EE6A8, 0xFF3DB8FF, 0x1EFFFFFF, 0xFFEFFAF6, 0xFF86A39C));
		PRESETS.put("Crimson", new Palette(0xFF100608, 0xFF1D1215, 0xFFFF4D6A, 0xFFFF9F43, 0x1EFFFFFF, 0xFFFBF1F2, 0xFFA8898E));
		PRESETS.put("Graphite", new Palette(0xFF0A0A0B, 0xFF18181B, 0xFFE4E4E7, 0xFF71717A, 0x24FFFFFF, 0xFFFAFAFA, 0xFF8B8B93));
	}

	private ThemePresets() {
	}

	public static String[] names() {
		return PRESETS.keySet().toArray(String[]::new);
	}
}
