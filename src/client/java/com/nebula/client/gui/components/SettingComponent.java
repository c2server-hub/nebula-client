package com.nebula.client.gui.components;

import com.nebula.client.gui.themes.Theme;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RenderUtils.NFont;
import com.nebula.client.settings.ActionSetting;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.KeybindSetting;
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.settings.Setting;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;

/** A row in the settings panel bound to one {@link Setting}. */
public abstract class SettingComponent<S extends Setting<?>> extends UiComponent {
	protected static final float ROW = 24;
	protected final S setting;

	protected SettingComponent(S setting) {
		this.setting = setting;
		this.h = ROW;
	}

	public S setting() {
		return setting;
	}

	protected void label(GuiGraphics g, Theme t, float hoverAmount, float maxWidth) {
		int color = ColorUtil.mix(ColorUtil.mix(t.text(), t.textDim(), 0.15F), t.text(), hoverAmount);
		RenderUtils.text(g, RenderUtils.ellipsize(setting.name(), maxWidth, NFont.TEXT), x, y + ROW / 2 - 4, color, NFont.TEXT);
	}

	public static SettingComponent<?> create(Setting<?> setting) {
		return switch (setting) {
			case BoolSetting b -> new Toggle(b);
			case NumberSetting n -> new Slider(n);
			case ModeSetting m -> new Dropdown(m);
			case ColorSetting c -> new ColorPicker(c);
			case KeybindSetting k -> new KeybindEditor(k);
			case ActionSetting a -> new ActionButton(a);
			default -> throw new IllegalArgumentException("No component for " + setting.getClass());
		};
	}
}
