package com.nebula.client.gui.components;

import com.nebula.client.gui.themes.Theme;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Scrollable, scissored vertical container. Children are laid out each frame from their
 * (possibly animating) preferred heights; scrolling is smoothed with an animation.
 */
public class Panel extends UiComponent {
	protected final Animation scroll = new Animation(18);
	protected float scrollTarget;
	protected float contentHeight;
	protected float padding = 6;
	protected float spacing = 4;

	protected void layoutAndRender(GuiGraphics g, Theme t, double mx, double my, List<? extends UiComponent> children) {
		float s = scroll.update(scrollTarget);
		float cy = y + padding - s;
		boolean inside = isHovered(mx, my);
		// Outside the viewport the mouse must not hover hidden rows.
		double hmx = inside ? mx : -1e4, hmy = inside ? my : -1e4;
		RenderUtils.scissor(g, x, y, w, h);
		for (UiComponent c : children) {
			float ch = c.preferredHeight();
			c.setBounds(x + padding, cy, w - padding * 2 - 4, ch);
			if (cy + ch >= y && cy <= y + h) {
				c.render(g, t, hmx, hmy);
			}
			cy += ch + spacing;
		}
		g.disableScissor();
		contentHeight = cy + s - y + padding - spacing;
		clampScroll();
		float max = maxScroll();
		if (max > 0) {
			float barH = Math.max(18, h * h / contentHeight);
			float barY = y + (h - barH) * (s / max);
			RoundedRenderer.rect(g, x + w - 4, barY, 2.5F, barH, 1.25F, ColorUtil.withAlpha(t.textDim(), 0.35F));
		}
	}

	protected float maxScroll() {
		return Math.max(0, contentHeight - h);
	}

	private void clampScroll() {
		scrollTarget = Math.clamp(scrollTarget, 0, maxScroll());
	}

	public boolean mouseScrolled(double mx, double my, double amount) {
		if (!isHovered(mx, my)) {
			return false;
		}
		scrollTarget -= (float) amount * 28;
		clampScroll();
		return true;
	}

	public void resetScroll() {
		scrollTarget = 0;
		scroll.snap(0);
	}

	protected static boolean clickChildren(List<? extends UiComponent> children, double mx, double my, int button, boolean inside) {
		boolean handled = false;
		for (UiComponent c : children) {
			// Every child is notified so editors can cancel/blur, but only the first consumer wins.
			if (!handled && inside) {
				handled = c.mouseClicked(mx, my, button);
			} else {
				c.mouseClicked(-1e4, -1e4, button);
			}
		}
		return handled;
	}

	@Override
	public void render(GuiGraphics g, Theme t, double mx, double my) {
	}
}
