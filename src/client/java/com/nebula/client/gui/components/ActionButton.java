package com.nebula.client.gui.components;

import com.nebula.client.gui.themes.Theme;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RenderUtils.NFont;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.settings.ActionSetting;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;

public final class ActionButton extends SettingComponent<ActionSetting> {
	private final Animation press = new Animation(12);

	public ActionButton(ActionSetting setting) {
		super(setting);
	}

	@Override
	public void render(GuiGraphics g, Theme t, double mx, double my) {
		float hv = updateHover(mx, my);
		float p = press.update(0);
		float inset = p * 1.5F;
		RoundedRenderer.rect(g, x + inset, y + 2 + inset, w - inset * 2, ROW - 4 - inset * 2, 7,
			ColorUtil.withAlpha(t.accent(), 0.12F + 0.14F * hv + 0.2F * p), 1, ColorUtil.withAlpha(t.accent(), 0.35F + 0.4F * hv));
		RenderUtils.centered(g, setting.name(), x + w / 2, y + ROW / 2 - 4, t.text(), NFont.TEXT, 1);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0 && isHovered(mx, my)) {
			press.snap(1);
			setting.run();
			return true;
		}
		return false;
	}
}
