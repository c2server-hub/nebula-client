package com.nebula.client.modules.ui;

import com.nebula.client.core.ClientManager;
import com.nebula.client.gui.hud.HudElement;
import com.nebula.client.gui.themes.Theme;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.GlowRenderer;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RenderUtils.NFont;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Glass cards for every active potion effect: a glowing dot in the effect colour, the name with
 * its amplifier, a live countdown and a depletion bar. Initial durations are remembered per
 * effect so the bar restarts when a potion is refreshed.
 */
public final class PotionHud extends Module {
	public final BoolSetting showAmplifier = add(new BoolSetting("Amplifier", "Show I/II/III levels", true));
	public final BoolSetting showBar = add(new BoolSetting("Duration bar", "Depletion bar under each effect", true));
	public final BoolSetting hideAmbient = add(new BoolSetting("Hide ambient", "Skip beacon-style ambient effects", false));
	public final NumberSetting maxEntries = add(new NumberSetting("Max effects", "Cards shown at once", 5, 1, 8, 1));
	public final NumberSetting scale = add(new NumberSetting("Scale", "Widget size", 1.0, 0.6, 1.6, 0.05, "x"));
	private final NumberSetting posX = add(HudElement.position("Potions X", 0.005));
	private final NumberSetting posY = add(HudElement.position("Potions Y", 0.08));

	public final HudElement element = new Element();

	public PotionHud() {
		super("potion_hud", "Potion HUD", "Glass cards for your active effects", Category.INTERFACE, true, false);
	}

	private final class Element extends HudElement {
		private static final float H = 24;

		private record Entry(String name, int color, int duration, boolean infinite, float fraction) {
		}

		private final List<Entry> entries = new ArrayList<>();
		/** {initialDuration, lastDuration} per effect, so refreshes restart the bar. */
		private final Map<MobEffect, int[]> history = new HashMap<>();

		Element() {
			super(PotionHud.this, "Potion HUD", posX, posY);
		}

		private void update(boolean preview) {
			entries.clear();
			if (preview) {
				entries.add(new Entry("Speed II", 0xFF7CAFC6, 1420, false, 0.42F));
				entries.add(new Entry("Night Vision", 0xFF1F1FA1, 4800, false, 0.85F));
				return;
			}
			var player = Minecraft.getInstance().player;
			if (player == null) {
				return;
			}
			List<MobEffectInstance> active = new ArrayList<>(player.getActiveEffects());
			if (hideAmbient.isOn()) {
				active.removeIf(MobEffectInstance::isAmbient);
			}
			active.removeIf(e -> !e.isVisible());
			// Most urgent first: the effect about to expire sits on top.
			active.sort((a, b) -> Integer.compare(a.isInfiniteDuration() ? Integer.MAX_VALUE : a.getDuration(),
					b.isInfiniteDuration() ? Integer.MAX_VALUE : b.getDuration()));
			for (MobEffectInstance instance : active.subList(0, Math.min(maxEntries.intValue(), active.size()))) {
				MobEffect effect = instance.getEffect().value();
				int duration = instance.getDuration();
				boolean infinite = instance.isInfiniteDuration();
				int[] h = history.computeIfAbsent(effect, k -> new int[]{duration, duration});
				if (duration > h[1] || duration <= 0) {
					// Refreshed (or brand new): restart the depletion clock.
					h[0] = duration;
				}
				h[1] = duration;
				String name = effect.getDisplayName().getString();
				if (showAmplifier.isOn() && instance.getAmplifier() > 0) {
					name += " " + roman(instance.getAmplifier() + 1);
				}
				float fraction = infinite ? 1 : Math.clamp(duration / (float) Math.max(1, h[0]), 0, 1);
				entries.add(new Entry(name, 0xFF000000 | effect.getColor(), duration, infinite, fraction));
			}
		}

		@Override
		public float width() {
			update(false);
			return contentWidth() * scale.floatValue();
		}

		private float contentWidth() {
			float w = 60;
			for (Entry e : entries) {
				w = Math.max(w, 10 + 8 + 6 + RenderUtils.width(e.name(), NFont.TEXT) + 8
						+ RenderUtils.width(durationText(e), NFont.TEXT) + 10);
			}
			return w;
		}

		@Override
		public float height() {
			update(false);
			return Math.max(H, entries.size() * (H + 4) - 4) * scale.floatValue();
		}

		@Override
		public void render(GuiGraphics g, Theme t, float x, float y, boolean preview) {
			update(preview);
			if (entries.isEmpty() && !preview) {
				return;
			}
			float now = System.nanoTime() / 1e9F;
			float s = scale.floatValue();
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.pose().scale(s, s);
			float w = contentWidth();
			float cy = 0;
			for (Entry e : entries) {
				float alpha = 1;
				if (!e.infinite() && e.duration() < 200) {
					// Last seconds: the card blinks gently.
					alpha = 0.62F + 0.38F * (float) Math.abs(Math.sin(now * 3.2F));
				}
				int border = ColorUtil.withAlpha(t.border(), alpha);
				RoundedRenderer.rect(g, 0, cy, w, H, Math.min(t.radius(), 10), ColorUtil.withAlpha(t.panel(), 0.66F * alpha), 1, border);
				// Glowing dot in the effect colour.
				int dot = ColorUtil.withAlpha(e.color(), alpha);
				GlowRenderer.glow(g, 8, cy + H / 2 - 3, 6, 6, 3, 5, e.color(), 0.7F * t.glow() * alpha);
				RoundedRenderer.circle(g, 11, cy + H / 2, 3, dot);
				RenderUtils.text(g, e.name(), 19, cy + 6.5F, ColorUtil.fade(t.text(), alpha), NFont.TEXT);
				RenderUtils.text(g, durationText(e), w - 10 - RenderUtils.width(durationText(e), NFont.TEXT), cy + 6.5F,
						ColorUtil.fade(t.textDim(), alpha), NFont.TEXT);
				if (showBar.isOn()) {
					float bw = w - 16;
					RoundedRenderer.rect(g, 8, cy + H - 5.5F, bw, 2, 1, ColorUtil.withAlpha(0xFFFFFFFF, 0.08F * alpha));
					int bar = ColorUtil.mix(e.color(), t.accent(), 0.25F);
					RoundedRenderer.rect(g, 8, cy + H - 5.5F, Math.max(2, bw * e.fraction()), 2, 1, ColorUtil.fade(bar, alpha));
				}
				cy += H + 4;
			}
			g.pose().popMatrix();
		}

		private String durationText(Entry e) {
			if (e.infinite()) {
				return "\u221E";
			}
			long totalSeconds = e.duration() / 20;
			long h = totalSeconds / 3600;
			long m = totalSeconds % 3600 / 60;
			long sec = totalSeconds % 60;
			return h > 0
					? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec)
					: String.format(Locale.ROOT, "%d:%02d", m, sec);
		}
	}

	private static String roman(int level) {
		if (level >= 1 && level <= 10) {
			return new String[]{"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"}[level - 1];
		}
		return Integer.toString(level);
	}
}
