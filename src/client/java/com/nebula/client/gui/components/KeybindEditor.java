package com.nebula.client.gui.components;

import com.nebula.client.gui.themes.Theme;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RenderUtils.NFont;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.settings.KeybindSetting;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/** Click, then press a key to bind it; Escape cancels, Backspace/Delete unbinds. */
public final class KeybindEditor extends SettingComponent<KeybindSetting> {
	private final Animation listen = new Animation(14);
	private boolean listening;

	public KeybindEditor(KeybindSetting setting) {
		super(setting);
	}

	public boolean isListening() {
		return listening;
	}

	@Override
	public void render(GuiGraphics g, Theme t, double mx, double my) {
		float hv = updateHover(mx, my);
		float l = listen.update(listening ? 1 : 0);
		String text = listening ? "Press a key…" : setting.display();
		float bw = Math.max(44, RenderUtils.width(text, NFont.TEXT) + 16);
		label(g, t, hv, w - bw - 8);
		float bx = x + w - bw, by = y + 4;
		// Pulsing accent border while waiting for input.
		float pulse = listening ? 0.6F + 0.4F * (float) Math.sin(System.nanoTime() / 1.6e8) : 0;
		RoundedRenderer.rect(g, bx, by, bw, ROW - 8, 5, ColorUtil.withAlpha(ColorUtil.mix(t.panel(), 0xFFFFFFFF, 0.08F + 0.06F * hv), 1F), 1,
			ColorUtil.mix(t.border(), t.accent(), l * pulse));
		RenderUtils.centered(g, text, bx + bw / 2, by + (ROW - 8) / 2 - 4, listening ? t.accent() : t.text(), NFont.TEXT, 1);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (isHovered(mx, my) && button == 0) {
			listening = !listening;
			return true;
		}
		if (listening) {
			listening = false;
		}
		return false;
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		if (!listening) {
			return false;
		}
		if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
			setting.set(KeybindSetting.NONE);
		} else if (key != GLFW.GLFW_KEY_ESCAPE) {
			setting.set(key);
		}
		listening = false;
		return true;
	}
}
