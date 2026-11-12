package com.nebula.client.gui.components;

import com.nebula.client.gui.Style;
import com.nebula.client.gui.themes.Theme;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RenderUtils.NFont;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;

/** Sidebar navigation entry with a vector icon and an animated selection pill. */
public final class CategoryTab extends UiComponent {
	public enum Icon { ORB, GRID, PALETTE, LAYOUT, SAVE }

	private final String label;
	private final Icon icon;
	private final Runnable onClick;
	private final Animation selected = new Animation(14);
	private boolean isSelected;

	public CategoryTab(String label, Icon icon, Runnable onClick) {
		this.label = label;
		this.icon = icon;
		this.onClick = onClick;
		this.h = 24;
	}

	public void setSelected(boolean selected) {
		this.isSelected = selected;
	}

	@Override
	public void render(GuiGraphics g, Theme t, double mx, double my) {
		float hv = updateHover(mx, my);
		float s = selected.update(isSelected ? 1 : 0);
		if (hv > 0.01F) {
			RoundedRenderer.rect(g, x, y, w, h, 8, ColorUtil.withAlpha(0xFFFFFFFF, 0.05F * hv));
		}
		if (s > 0.01F) {
			RoundedRenderer.rect(g, x, y, w, h, 8, ColorUtil.withAlpha(t.accent(), 0.16F * s), 1, ColorUtil.withAlpha(t.accent(), 0.3F * s));
			Style.accentPill(g, t, x - 1, y + h / 2 - 6 * s, 3, 12 * s, 1.5F, s);
		}
		int fg = ColorUtil.mix(ColorUtil.mix(t.textDim(), t.text(), hv), t.text(), s);
		int ic = ColorUtil.mix(fg, t.accent(), s);
		drawIcon(g, x + 14, y + h / 2, ic);
		RenderUtils.text(g, label, x + 26 + 2 * hv, y + h / 2 - 4, fg, NFont.TEXT);
	}

	private void drawIcon(GuiGraphics g, float cx, float cy, int c) {
		switch (icon) {
			case ORB -> {
				RoundedRenderer.outline(g, cx - 5, cy - 5, 10, 10, 5, 1.2F, c);
				RoundedRenderer.circle(g, cx, cy, 2, c);
			}
			case GRID -> {
				for (int i = 0; i < 4; i++) {
					RoundedRenderer.rect(g, cx - 5 + (i % 2) * 5.5F, cy - 5 + (i / 2) * 5.5F, 4.5F, 4.5F, 1.2F, c);
				}
			}
			case PALETTE -> {
				RoundedRenderer.outline(g, cx - 5, cy - 5, 10, 10, 5, 1.2F, c);
				RoundedRenderer.circle(g, cx - 1.8F, cy - 1.5F, 1.2F, c);
				RoundedRenderer.circle(g, cx + 1.8F, cy - 1.5F, 1.2F, c);
				RoundedRenderer.circle(g, cx, cy + 2, 1.2F, c);
			}
			case LAYOUT -> {
				RoundedRenderer.outline(g, cx - 5.5F, cy - 4.5F, 11, 9, 2, 1.2F, c);
				RoundedRenderer.rect(g, cx - 3.5F, cy - 2.5F, 3, 2, 0.5F, c);
				RoundedRenderer.rect(g, cx + 0.5F, cy + 0.5F, 3, 2, 0.5F, c);
			}
			case SAVE -> {
				RoundedRenderer.outline(g, cx - 5, cy - 5, 10, 10, 2, 1.2F, c);
				RoundedRenderer.rect(g, cx - 2.5F, cy - 5, 5, 3.5F, 0.5F, c);
			}
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0 && isHovered(mx, my)) {
			onClick.run();
			return true;
		}
		return false;
	}
}
