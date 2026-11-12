package com.nebula.client.gui.components;

import com.nebula.client.gui.themes.Theme;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RenderUtils.NFont;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;

/** Mode selector that expands inline (pushing following rows) so it never overlaps other widgets. */
public final class Dropdown extends SettingComponent<ModeSetting> {
	private static final float OPTION = 17;
	private final Animation open = new Animation(16);
	private final Animation[] optionHover;
	private boolean expanded;

	public Dropdown(ModeSetting setting) {
		super(setting);
		optionHover = new Animation[setting.modes().size()];
		for (int i = 0; i < optionHover.length; i++) {
			optionHover[i] = new Animation(18);
		}
	}

	@Override
	public float preferredHeight() {
		return ROW + (setting.modes().size() * OPTION + 4) * open.update(expanded ? 1 : 0);
	}

	@Override
	public void render(GuiGraphics g, Theme t, double mx, double my) {
		float hv = hover.update(mx >= x && mx < x + w && my >= y && my < y + ROW ? 1 : 0);
		float boxW = Math.min(110, w * 0.55F);
		label(g, t, hv, w - boxW - 8);
		float bx = x + w - boxW, by = y + 3, bh = ROW - 6;
		float o = open.get();
		RoundedRenderer.rect(g, bx, by, boxW, bh, 6, ColorUtil.withAlpha(ColorUtil.mix(t.panel(), 0xFFFFFFFF, 0.08F + 0.06F * hv), 1F), 1,
			ColorUtil.mix(t.border(), t.accent(), o));
		RenderUtils.text(g, RenderUtils.ellipsize(setting.get(), boxW - 22, NFont.TEXT), bx + 7, by + bh / 2 - 4, t.text(), NFont.TEXT);
		// Chevron rotates by flipping between two strokes.
		float cx = bx + boxW - 10, cy = by + bh / 2;
		float dir = 1 - 2 * o;
		RoundedRenderer.rect(g, cx - 3, cy - 1 * dir - 0.5F, 3.5F, 1.2F, 0.6F, t.textDim());
		RoundedRenderer.rect(g, cx - 0.5F, cy - 1 * dir - 0.5F, 3.5F, 1.2F, 0.6F, t.textDim());
		if (o > 0.01F) {
			float listY = y + ROW;
			float listH = (setting.modes().size() * OPTION + 4) * o;
			RenderUtils.scissor(g, bx, listY, boxW, listH);
			RoundedRenderer.rect(g, bx, listY, boxW, setting.modes().size() * OPTION + 2, 6, ColorUtil.withAlpha(ColorUtil.mix(t.panel(), 0xFFFFFFFF, 0.06F), 1F), 1, t.border());
			for (int i = 0; i < setting.modes().size(); i++) {
				String mode = setting.modes().get(i);
				float oy = listY + 1 + i * OPTION;
				boolean hovered = mx >= bx && mx < bx + boxW && my >= oy && my < oy + OPTION && my < listY + listH;
				float oh = optionHover[i].update(hovered ? 1 : 0);
				boolean selected = setting.is(mode);
				if (oh > 0.01F || selected) {
					RoundedRenderer.rect(g, bx + 2, oy + 1, boxW - 4, OPTION - 2, 5, ColorUtil.withAlpha(t.accent(), selected ? 0.28F : 0.14F * oh));
				}
				RenderUtils.text(g, mode, bx + 8 + 2 * oh, oy + OPTION / 2 - 4, selected ? t.text() : ColorUtil.mix(t.textDim(), t.text(), oh), NFont.TEXT);
			}
			g.disableScissor();
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		float boxW = Math.min(110, w * 0.55F);
		float bx = x + w - boxW;
		if (my >= y && my < y + ROW && mx >= x && mx < x + w) {
			expanded = !expanded;
			return true;
		}
		if (expanded && mx >= bx && mx < bx + boxW && my >= y + ROW) {
			int index = (int) ((my - y - ROW - 1) / OPTION);
			if (index >= 0 && index < setting.modes().size()) {
				setting.set(setting.modes().get(index));
				expanded = false;
				return true;
			}
		}
		return false;
	}
}
