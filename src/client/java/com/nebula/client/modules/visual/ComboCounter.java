package com.nebula.client.modules.visual;

import com.nebula.client.core.ClientManager;
import com.nebula.client.gui.Style;
import com.nebula.client.gui.hud.HudElement;
import com.nebula.client.gui.themes.Theme;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.GlowRenderer;
import com.nebula.client.rendering.Gradient;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RenderUtils.NFont;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Counts the hits you land in a row: every attack within the combo window raises the counter with
 * a spring pop, taking damage yourself or letting the window drain breaks it. The pill is centred
 * under the crosshair, with a live bar showing how much of the window is left.
 */
public final class ComboCounter extends Module {
	public final NumberSetting window = add(new NumberSetting("Window", "Time between hits that keeps the combo alive", 2.0, 0.5, 6, 0.1, "s"));
	public final BoolSetting breakOnHurt = add(new BoolSetting("Break on hurt", "Reset when you take damage", true));
	public final BoolSetting showBar = add(new BoolSetting("Window bar", "Draining bar under the number", true));
	public final NumberSetting scale = add(new NumberSetting("Scale", "Widget size", 1.0, 0.6, 1.6, 0.05, "x"));
	private final NumberSetting posX = add(HudElement.position("Combo X", 0.5));
	private final NumberSetting posY = add(HudElement.position("Combo Y", 0.3));

	public final HudElement element = new Element();

	private int combo;
	private long lastHit;
	private boolean wasHurt;
	private int shownCombo;

	public ComboCounter() {
		super("combo_counter", "Combo Counter", "Chain counter with a live window bar", Category.COMBAT, true, false);
	}

	/** Registered by the client manager on every attack. */
	public void onAttack(net.minecraft.world.entity.LivingEntity target) {
		if (!isEnabled()) {
			return;
		}
		long now = System.nanoTime();
		if (now - lastHit > (long) (window.floatValue() * 1e9F)) {
			combo = 0;
		}
		combo++;
		lastHit = now;
	}

	@Override
	public void onTick() {
		var p = Minecraft.getInstance().player;
		if (p == null) {
			combo = 0;
			return;
		}
		boolean hurt = p.hurtTime > 0;
		if (breakOnHurt.isOn() && hurt && !wasHurt && combo > 0) {
			combo = 0;
		}
		wasHurt = hurt;
		if (combo > 0 && System.nanoTime() - lastHit > (long) (window.floatValue() * 1e9F)) {
			combo = 0;
		}
	}

	private final class Element extends HudElement {
		private static final float H = 34;
		private final Animation appear = new Animation(14);
		private final Animation pop = new Animation(22);
		private int lastCombo;

		Element() {
			super(ComboCounter.this, "Combo Counter", posX, posY);
		}

		@Override
		public float width() {
			String text = "×" + Math.max(2, shownCombo);
			return (26 + RenderUtils.width("COMBO", NFont.TEXT) * 0.72F + 6 + RenderUtils.width(text, NFont.DISPLAY) * 0.95F + 14) * scale.floatValue();
		}

		@Override
		public float height() {
			return (H + (showBar.isOn() ? 4 : 0)) * scale.floatValue();
		}

		/** Always centred on its X anchor, wherever it is dragged. */
		@Override
		public float left(int screenWidth) {
			float left = (float) (posX.get() * screenWidth) - width() / 2;
			return Math.clamp(left, 2, Math.max(2, screenWidth - width() - 2));
		}

		@Override
		public void render(GuiGraphics g, Theme t, float x, float y, boolean preview) {
			int combo = preview ? 7 : ComboCounter.this.combo;
			shownCombo = combo;
			float a = appear.update(combo >= 2 ? 1 : 0);
			if (a < 0.01F) {
				return;
			}
			if (combo != lastCombo) {
				pop.snap(0);
				lastCombo = combo;
			}
			float spring = Animation.easeOutBack(pop.update(1));
			float s = scale.floatValue();
			float w = width(), h = H * s;
			float ease = Animation.easeOutBack(a);
			g.pose().pushMatrix();
			g.pose().translate(x + w / 2, y + h / 2);
			g.pose().scale(0.85F + 0.15F * ease, 0.85F + 0.15F * ease);
			g.pose().translate(-w / 2, -h / 2);

			Style.panel(g, t, 0, 0, w, h, Math.min(t.radius() + 2, 12), a, ClientManager.get().modules().guiSettings.hudGlass.isOn());
			// Big number, popping in from 1.5x on every hit.
			float numberScale = s * 0.95F * (1 + 0.5F * (1 - spring));
			String number = "×" + combo;
			float nw = RenderUtils.width(number, NFont.DISPLAY) * numberScale;
			float nx = w - 14 - nw;
			float nh = RenderUtils.height() * numberScale;
			GlowRenderer.glow(g, nx - 2, h / 2 - nh / 2 - 2, nw + 4, nh + 4, 6, 8, t.accent(), 0.5F * a * t.glow());
			RenderUtils.text(g, number, nx, h / 2 - nh / 2, ColorUtil.fade(t.text(), a), NFont.DISPLAY, numberScale);
			RenderUtils.text(g, "COMBO", 14, h / 2 - RenderUtils.height() * 0.72F / 2 - 1, ColorUtil.fade(t.textDim(), a), NFont.TEXT, 0.72F);

			if (showBar.isOn()) {
				float remaining = preview ? 0.62F : 1 - Math.clamp((System.nanoTime() - lastHit) / 1e9F / window.floatValue(), 0, 1);
				float barW = w - 24;
				float barY = h - 3 * s;
				RoundedRenderer.rect(g, 12, barY, barW, 3 * s, 1.5F * s, ColorUtil.withAlpha(0xFF000000, 0.35F * a));
				if (remaining > 0.01F) {
					int from = ColorUtil.mix(t.accent(), 0xFFFF4D5E, 1 - remaining);
					int to = ColorUtil.mix(t.accent2(), 0xFFFF4D5E, 1 - remaining);
					RoundedRenderer.gradient(g, 12, barY, Math.max(2, barW * remaining), 3 * s, 1.5F * s, Gradient.horizontal(ColorUtil.fade(from, a), ColorUtil.fade(to, a)));
				}
			}
			g.pose().popMatrix();
		}
	}
}
