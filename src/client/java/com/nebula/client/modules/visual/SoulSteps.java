package com.nebula.client.modules.visual;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.rendering.world.GlowBatch;
import com.nebula.client.rendering.world.WorldFrame;
import com.nebula.client.rendering.world.WorldRenderable;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Glowing footprints left behind with every step: alternating left/right pads that pulse and
 * fade, plus a few sparks drifting up from each one. Steps are measured by accumulated walking
 * distance on the client tick, so the rhythm matches the actual animation.
 */
public final class SoulSteps extends Module implements WorldRenderable {
	private static final int MAX = 32;
	private static final float RING_POS = 0.8F;

	public final NumberSetting stride = add(new NumberSetting("Stride", "Blocks between footprints", 1.4, 0.8, 3, 0.05, "m"));
	public final NumberSetting size = add(new NumberSetting("Size", "Footprint size", 1.0, 0.5, 2, 0.05, "x"));
	public final NumberSetting lifetime = add(new NumberSetting("Lifetime", "How long a print glows", 1.1, 0.4, 2.5, 0.05, "s"));
	public final NumberSetting brightness = add(new NumberSetting("Brightness", "Footprint glow", 1.0, 0.2, 2, 0.05));
	public final NumberSetting sprintBoost = add(new NumberSetting("Sprint boost", "Extra glow while sprinting", 1.5, 1, 2.5, 0.05, "x"));
	private final ColorChoice color = new ColorChoice(add(ColorChoice.modeSetting()), add(new ColorSetting("Color", "Custom step colour", 0xFF7FE7FF)));

	private final double[] x = new double[MAX], y = new double[MAX], z = new double[MAX];
	private final double[] nx = new double[MAX], nz = new double[MAX];
	private final float[] born = new float[MAX], tone = new float[MAX], boost = new float[MAX];
	private int next;
	private float walked;
	private boolean left;

	public SoulSteps() {
		super("soul_steps", "Soul Steps", "Glowing footprints with drifting sparks as you walk", Category.PLAYER);
	}

	@Override
	public void onTick() {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) {
			return;
		}
		double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
		double d = Math.sqrt(dx * dx + dz * dz);
		if (!p.onGround() || d < 0.005) {
			return;
		}
		walked += (float) d;
		if (walked < stride.floatValue()) {
			return;
		}
		walked = 0;
		left = !left;
		// Perpendicular offset so left and right feet alternate.
		double inv = 1 / Math.max(1e-4, d);
		double ox = -dz * inv, oz = dx * inv;
		double side = left ? 0.15 : -0.15;
		x[next] = p.getX() - dx * 0.15 + ox * side;
		z[next] = p.getZ() - dz * 0.15 + oz * side;
		y[next] = p.getY() + 0.03;
		nx[next] = ox;
		nz[next] = oz;
		tone[next] = (next & 3) / 4F;
		boost[next] = p.isSprinting() ? sprintBoost.floatValue() : 1;
		born[next] = System.nanoTime() / 1e9F;
		next = (next + 1) % MAX;
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		if (Minecraft.getInstance().player == null) {
			return;
		}
		float now = System.nanoTime() / 1e9F;
		float life = lifetime.floatValue();
		float s = size.floatValue();
		batch.begin(f);
		boolean prints = false;
		for (int i = 0; i < MAX; i++) {
			if (born[i] <= 0) {
				continue;
			}
			float age = now - born[i];
			if (age >= life) {
				born[i] = 0;
				continue;
			}
			float k = age / life;
			float fade = (1 - k) * (1 - k);
			float b = Math.min(1.6F, brightness.floatValue() * boost[i]) * fade;
			int c = color.color(tone[i], now);
			// The pad itself: a soft filled disc lying on the ground.
			float r = (0.13F + 0.06F * k) * s;
			batch.flat(f.rx(x[i]), f.ry(y[i]), f.rz(z[i]), r / RING_POS, ColorUtil.withAlpha(c, 0.75F * b));
			prints = true;
			// Two sparks per print drift upwards along the step direction.
			for (int j = 0; j < 2; j++) {
				float wob = Mth.sin(age * 5F + i * 2.7F + j * 3.1F) * 0.09F;
				float rise = age * (0.5F + j * 0.25F);
				batch.billboard(f.rx(x[i] + nx[i] * (0.12F + j * 0.14) * (left(i) ? 1 : -1) + wob),
						f.ry(y[i] + 0.08F + rise), f.rz(z[i] + nz[i] * (0.12F + j * 0.14) * (left(i) ? 1 : -1) + wob),
						0.045F * s * (1 - k * 0.6F), ColorUtil.withAlpha(ColorUtil.brighten(c, 0.4F), 0.8F * fade));
			}
		}
		batch.end(NebulaPipelines.GLOW_RING, RING_POS, 0.6F, 0.85F);
	}

	private boolean left(int i) {
		return (i & 1) == 0;
	}
}
