package com.nebula.client.modules.ui;

import com.nebula.client.core.ClientManager;
import com.nebula.client.gui.Style;
import com.nebula.client.gui.hud.HudElement;
import com.nebula.client.gui.themes.Theme;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.GlowRenderer;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RenderUtils.NFont;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Glass toasts that slide in when a module is toggled. */
public final class Notifications extends Module {
	private static final int MAX = 5;
	private static final float W = 150, H = 32;

	public final ModeSetting position = add(new ModeSetting("Position", "Screen corner", "Bottom right", "Bottom right", "Top right", "Bottom left"));
	public final NumberSetting duration = add(new NumberSetting("Duration", "Time on screen", 2.2, 0.8, 6, 0.1, "s"));

	private final List<Toast> toasts = new ArrayList<>();

	private static final class Toast {
		final String title;
		final boolean on;
		final long born = System.nanoTime();
		final Animation slide = new Animation(0, 13);
		final Animation stack = new Animation(-1, 14);

		Toast(String title, boolean on) {
			this.title = title;
			this.on = on;
		}
	}

	public Notifications() {
		super("notifications", "Notifications", "Glass toasts when modules are toggled", Category.INTERFACE, true, false);
	}

	public void moduleToggled(Module module) {
		if (!isEnabled() || module.isAlwaysActive() || module == this || ClientManager.get().config().isLoading()) {
			return;
		}
		// A rapid re-toggle replaces the previous toast for the same module instead of stacking.
		toasts.removeIf(t -> t.title.equals(module.name()));
		toasts.add(new Toast(module.name(), module.isEnabled()));
		while (toasts.size() > MAX) {
			toasts.remove(0);
		}
	}

	public void render(GuiGraphics g, Theme t, List<HudElement> hud) {
		long now = System.nanoTime();
		float life = duration.floatValue();
		int sw = g.guiWidth(), sh = g.guiHeight();
		boolean top = position.is("Top right");
		boolean left = position.is("Bottom left");
		// Stack next to, not over, HUD panels that sit in the same corner column.
		float colL = left ? 8 : sw - W - 8, colR = colL + W;
		float edge = top ? 8 : sh - 8;
		for (HudElement e : hud) {
			if (!e.isVisible()) {
				continue;
			}
			float el = e.left(sw), et = e.top(sh);
			if (el >= colR || el + e.width() <= colL) {
				continue;
			}
			if (top && et + e.height() <= sh / 2F) {
				edge = Math.max(edge, et + e.height() + 6);
			} else if (!top && et >= sh / 2F) {
				edge = Math.min(edge, et - 6);
			}
		}
		int index = 0;
		for (Iterator<Toast> it = toasts.iterator(); it.hasNext(); ) {
			Toast toast = it.next();
			float age = (now - toast.born) / 1e9F;
			float in = toast.slide.update(age < life ? 1 : 0);
			if (age > life && in < 0.01F) {
				it.remove();
				continue;
			}
			int fromNewest = toasts.size() - 1 - index++;
			float slot = toast.stack.get() < 0 ? fromNewest : toast.stack.update(fromNewest);
			if (toast.stack.get() < 0) {
				toast.stack.snap(fromNewest);
			}
			float e = Animation.easeOutCubic(in);
			float off = (1 - e) * (W + 16);
			float x = left ? 8 - off : sw - W - 8 + off;
			float y = top ? edge + slot * (H + 6) : edge - H - slot * (H + 6);
			draw(g, t, toast, x, y, e, Math.clamp(1 - age / life, 0, 1));
		}
	}

	private void draw(GuiGraphics g, Theme t, Toast toast, float x, float y, float alpha, float remaining) {
		Style.panel(g, t, x, y, W, H, 10, alpha, false);
		int state = toast.on ? t.accent() : ColorUtil.withAlpha(t.textDim(), 0.8F);
		GlowRenderer.glow(g, x + 9, y + H / 2 - 5, 10, 10, 5, 7, state, (toast.on ? 0.9F : 0.3F) * alpha * t.glow());
		RoundedRenderer.circle(g, x + 14, y + H / 2, 5, ColorUtil.fade(state, alpha));
		RoundedRenderer.circle(g, x + 14, y + H / 2, 2, ColorUtil.fade(0xFFFFFFFF, alpha * (toast.on ? 0.9F : 0.4F)));
		RenderUtils.text(g, RenderUtils.ellipsize(toast.title, W - 40, NFont.TEXT), x + 26, y + 7, ColorUtil.fade(t.text(), alpha), NFont.TEXT);
		RenderUtils.text(g, toast.on ? "Enabled" : "Disabled", x + 26, y + 18, ColorUtil.fade(toast.on ? t.accent() : t.textDim(), alpha), NFont.TEXT, 0.9F);
		float barW = (W - 20) * remaining;
		if (barW > 1) {
			RoundedRenderer.gradient(g, x + 10, y + H - 3, barW, 1.5F, 0.75F, Style.accentGradient(t, alpha * 0.8F));
		}
	}
}
