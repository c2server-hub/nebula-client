package com.nebula.client.modules.visual;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Anime-style speed lines that radiate from the screen centre as you go fast: sprinting charges
 * them up, elytra flight fills the edges. Intensity follows your real velocity with a smooth
 * filter, and each streak is a rotated capsule that shoots outwards and fades before the corners.
 */
public final class SprintFx extends Module {
	private static final int MAX_STREAKS = 24;

	public final NumberSetting threshold = add(new NumberSetting("Threshold", "Speed where lines start to appear", 5.2, 2, 15, 0.2, "m/s"));
	public final NumberSetting intensity = add(new NumberSetting("Density", "How many lines fill the screen", 1.0, 0.2, 2, 0.05, "x"));
	public final NumberSetting length = add(new NumberSetting("Length", "Streak length", 1.0, 0.3, 2, 0.05, "x"));
	public final NumberSetting width = add(new NumberSetting("Width", "Streak thickness", 1.6, 0.6, 4, 0.1, "px"));
	public final NumberSetting alpha = add(new NumberSetting("Opacity", "Base opacity", 0.55, 0.1, 1, 0.05));
	private final ColorChoice color = new ColorChoice(add(ColorChoice.modeSetting()), add(new ColorSetting("Color", "Custom streak colour", 0xFFBFD9FF)));

	private final float[] angle = new float[MAX_STREAKS];
	private final float[] radius = new float[MAX_STREAKS]; // fraction of the half-diagonal
	private final float[] len = new float[MAX_STREAKS];
	private final float[] speed = new float[MAX_STREAKS];
	private final boolean[] active = new boolean[MAX_STREAKS];
	private final Animation factor = new Animation(6);
	private long lastNanos;
	private float spawnBudget;
	private final java.util.Random random = new java.util.Random();

	public SprintFx() {
		super("sprint_fx", "Sprint FX", "Speed lines when sprinting or flying", Category.PLAYER, true, false);
	}

	/** Screen pass registered underneath the HUD panels. */
	public void render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		var p = mc.player;
		if (p == null || mc.options.hideGui || p.isSpectator() || !mc.options.getCameraType().isFirstPerson()) {
			clear();
			return;
		}
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0 : Math.min((now - lastNanos) / 1e9F, 0.1F);
		lastNanos = now;
		float seconds = now / 1e9F;

		// Elytra flight reaches much higher speeds, so its trigger is proportionally higher too.
		var motion = p.getDeltaMovement();
		double speedNow = p.isFallFlying() ? motion.length() : motion.horizontalDistance();
		float base = threshold.floatValue() * (p.isFallFlying() ? 1.15F : 1);
		float target = Math.clamp((float) ((speedNow - base) / (base * 0.9)), 0, 1);
		float f = factor.update(target);

		if (f > 0.02F) {
			spawnBudget += dt * (4 + 26 * f) * intensity.floatValue();
		}
		while (spawnBudget >= 1) {
			spawnBudget--;
			spawn(f);
		}
		if (f < 0.01F && noneActive() && target == 0) {
			return; // nothing left to draw
		}

		float cx = g.guiWidth() / 2F, cy = g.guiHeight() / 2F;
		float diag = (float) Math.hypot(g.guiWidth() / 2F, g.guiHeight() / 2F);
		float w = width.floatValue();
		for (int i = 0; i < MAX_STREAKS; i++) {
			if (!active[i]) {
				continue;
			}
			radius[i] += speed[i] * dt;
			if (radius[i] > 1.35F) {
				active[i] = false;
				continue;
			}
			float a = alpha.floatValue() * f * Math.clamp((1.35F - radius[i]) / 0.5F, 0, 1);
			if (a < 0.01F) {
				continue;
			}
			float sx = cx + Mth.cos(angle[i]) * radius[i] * diag;
			float sy = cy + Mth.sin(angle[i]) * radius[i] * diag;
			float l = len[i] * length.floatValue() * (0.5F + 0.7F * f);
			int c = ColorUtil.mix(color.color(angle[i] / 6.2832F, seconds), 0xFFFFFFFF, 0.35F);
			g.pose().pushMatrix();
			g.pose().translate(sx, sy);
			g.pose().rotate(angle[i]);
			// Long soft tail plus a bright capsule at the leading (outer) end.
			RoundedRenderer.rect(g, 0, -w / 2, l, w, w / 2, ColorUtil.withAlpha(c, 0.3F * a));
			RoundedRenderer.rect(g, l * 0.6F, -w / 2, l * 0.4F, w, w / 2, ColorUtil.withAlpha(ColorUtil.brighten(c, 0.3F), 0.85F * a));
			g.pose().popMatrix();
		}
	}

	private void spawn(float f) {
		for (int i = 0; i < MAX_STREAKS; i++) {
			if (active[i]) {
				continue;
			}
			angle[i] = random.nextFloat() * 6.2831855F;
			radius[i] = 0.5F + random.nextFloat() * 0.35F;
			len[i] = 46 + random.nextFloat() * 78;
			speed[i] = 0.55F + random.nextFloat() * 0.55F;
			active[i] = true;
			return;
		}
	}

	private boolean noneActive() {
		for (boolean a : active) {
			if (a) {
				return false;
			}
		}
		return true;
	}

	private void clear() {
		java.util.Arrays.fill(active, false);
		factor.update(0);
	}
}
