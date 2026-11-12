package com.nebula.client.modules.ui;

import com.nebula.client.gui.themes.ThemePresets;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.settings.ActionSetting;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.ModeSetting;

public final class ThemeEditor extends Module {
	private static final ThemePresets.Palette DEFAULT = ThemePresets.PRESETS.get("Nebula");

	public final ModeSetting preset = add(new ModeSetting("Preset", "Loads a complete colour scheme", "Nebula", ThemePresets.names()))
		.onChange(this::applyPreset);
	public final ColorSetting background = add(new ColorSetting("Background", "Window backdrop tint", DEFAULT.background()));
	public final ColorSetting panel = add(new ColorSetting("Panel", "Cards and panels", DEFAULT.panel()));
	public final ColorSetting accent = add(new ColorSetting("Accent", "Primary accent", DEFAULT.accent()));
	public final ColorSetting accent2 = add(new ColorSetting("Accent 2", "Gradient end colour", DEFAULT.accent2()));
	public final ColorSetting border = add(new ColorSetting("Border", "Hairline borders", DEFAULT.border(), true));
	public final ColorSetting text = add(new ColorSetting("Text", "Primary text", DEFAULT.text()));
	public final ColorSetting textDim = add(new ColorSetting("Secondary text", "Labels and hints", DEFAULT.textDim()));

	public ThemeEditor() {
		super("theme_editor", "Theme Editor", "Customise every colour of the interface", Category.INTERFACE, true, true);
		add(new ActionSetting("Reapply preset", "Discard custom colours and restore the selected preset", this::applyPreset));
	}

	private void applyPreset() {
		ThemePresets.Palette p = ThemePresets.PRESETS.get(preset.get());
		if (p == null) {
			return;
		}
		background.set(p.background());
		panel.set(p.panel());
		accent.set(p.accent());
		accent2.set(p.accent2());
		border.set(p.border());
		text.set(p.text());
		textDim.set(p.textDim());
	}
}
