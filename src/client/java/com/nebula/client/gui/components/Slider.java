package com.nebula.client.gui.components;

import com.nebula.client.gui.Style;
import com.nebula.client.gui.themes.Theme;
import com.nebula.client.rendering.GlowRenderer;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RenderUtils.NFont;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/** Draggable slider; arrow keys nudge by one step while hovered. */
public final class Slider extends SettingComponent<NumberSetting> {
	private final Animation fill;
	private final Animation grab = new Animation(20);
	private boolean dragging;

	public Slider(NumberSetting setting) {
		super(setting);
		this.h = 30;
		this.fill = new Animation((float) setting.fraction(), 22);
	}

	@Override
	public void render(GuiGraphics g, Theme t, double mx, double my) {
		float hv = updateHover(mx, my);
		label(g, t, hv, w - 50);
		String value = setting.display();
		RenderUtils.text(g, value, x + w - RenderUtils.width(value, NFont.TEXT), y + ROW / 2 - 4, dragging ? t.accent() : t.textDim(), NFont.TEXT);
		float tx = x, ty = y + 21, tw = w, th = 4;
		float f = fill.update((float) setting.fraction());
		float gr = grab.update(dragging ? 1 : hv);
		RoundedRenderer.rect(g, tx, ty, tw, th, th / 2, ColorUtil.withAlpha(ColorUtil.mix(t.panel(), 0xFFFFFFFF, 0.16F), 1F));
		if (f > 0) {
			GlowRenderer.glow(g, tx, ty, tw * f, th, th / 2, 6, t.accent(), 0.45F * t.glow());
			RoundedRenderer.gradient(g, tx, ty, Math.max(th, tw * f), th, th / 2, Style.accentGradient(t, 1));
		}
		float knob = 8 + 2 * gr;
		float kx = tx + tw * f;
		GlowRenderer.glow(g, kx - knob / 2, ty + th / 2 - knob / 2, knob, knob, knob / 2, 6, t.accent(), 0.8F * gr * t.glow());
		RoundedRenderer.circle(g, kx, ty + th / 2, knob / 2, 0xFFF8FAFF);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0 && isHovered(mx, my) && my >= y + 14) {
			dragging = true;
			mouseDragged(mx, my);
			return true;
		}
		return false;
	}

	@Override
	public void mouseDragged(double mx, double my) {
		if (dragging) {
			setting.setFraction((mx - x) / w);
		}
	}

	@Override
	public void mouseReleased(double mx, double my, int button) {
		dragging = false;
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		if (hover.get() > 0.5F && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) {
			setting.set(setting.get() + (key == GLFW.GLFW_KEY_RIGHT ? setting.step() : -setting.step()));
			return true;
		}
		return false;
	}
}
