package com.nebula.client.gui.components;

import com.nebula.client.gui.themes.Theme;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RenderUtils.NFont;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/** Single-line text field used to filter modules. Typing anywhere in the GUI focuses it. */
public final class SearchBar extends UiComponent {
	private final StringBuilder text = new StringBuilder();
	private final Animation focus = new Animation(16);
	private boolean focused;

	public String query() {
		return text.toString();
	}

	public boolean isFocused() {
		return focused;
	}

	public void setFocused(boolean focused) {
		this.focused = focused;
	}

	public void clear() {
		text.setLength(0);
		focused = false;
	}

	@Override
	public void render(GuiGraphics g, Theme t, double mx, double my) {
		float hv = updateHover(mx, my);
		float f = focus.update(focused ? 1 : 0);
		RoundedRenderer.rect(g, x, y, w, h, h / 2, ColorUtil.withAlpha(ColorUtil.mix(t.panel(), 0xFFFFFFFF, 0.06F + 0.04F * hv), 0.9F), 1,
			ColorUtil.mix(t.border(), t.accent(), f));
		// Magnifier icon.
		float ix = x + 10, iy = y + h / 2 - 1;
		RoundedRenderer.outline(g, ix - 3.5F, iy - 3.5F, 7, 7, 3.5F, 1.2F, ColorUtil.mix(t.textDim(), t.accent(), f));
		RoundedRenderer.rect(g, ix + 2.5F, iy + 2.5F, 3, 1.3F, 0.6F, ColorUtil.mix(t.textDim(), t.accent(), f));
		float tx = x + 20;
		if (text.isEmpty()) {
			RenderUtils.text(g, "Search modules…", tx, y + h / 2 - 4, ColorUtil.withAlpha(t.textDim(), 0.8F), NFont.TEXT);
		} else {
			String shown = text.toString();
			while (shown.length() > 1 && RenderUtils.width(shown, NFont.TEXT) > w - 40) {
				shown = shown.substring(1);
			}
			RenderUtils.text(g, shown, tx, y + h / 2 - 4, t.text(), NFont.TEXT);
			tx += RenderUtils.width(shown, NFont.TEXT);
			// Clear button.
			float cx = x + w - 12, cy = y + h / 2;
			RoundedRenderer.circle(g, cx, cy, 5, ColorUtil.withAlpha(t.textDim(), 0.35F));
			RenderUtils.centered(g, "×", cx, cy - 4.5F, t.text(), NFont.TEXT, 1);
		}
		if (focused && (System.currentTimeMillis() / 530) % 2 == 0) {
			RoundedRenderer.rect(g, tx + 1, y + h / 2 - 5, 1, 10, 0, t.accent());
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (isHovered(mx, my)) {
			if (!text.isEmpty() && mx > x + w - 20) {
				text.setLength(0);
			}
			focused = true;
			return true;
		}
		focused = false;
		return false;
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		if (!focused) {
			return false;
		}
		switch (key) {
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if (!text.isEmpty()) {
					if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
						text.setLength(0);
					} else {
						text.deleteCharAt(text.length() - 1);
					}
				}
				return true;
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				focused = false;
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	@Override
	public boolean charTyped(int codepoint) {
		if (Character.isISOControl(codepoint) || text.length() >= 32) {
			return false;
		}
		focused = true;
		text.appendCodePoint(codepoint);
		return true;
	}
}
