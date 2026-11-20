package com.nebula.client.modules.visual;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.rendering.world.GlowBatch;
import com.nebula.client.rendering.world.WorldFrame;
import com.nebula.client.rendering.world.WorldRenderable;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * When an entity you recently attacked dies, its soul bursts free: an expanding shock ring,
 * a swirl of sparks and a few rising soul wisps. All sparks are closed-form functions of the
 * burst age, so a burst needs no per-spark state and stays perfectly frame-rate independent.
 */
public final class DeathFx extends Module implements WorldRenderable {
	private static final int MAX = 4;
	private static final int SPARKS = 16;
	private static final float RING_POS = 0.8F;

	public final BoolSetting ring = add(new BoolSetting("Ring", "Expanding shock ring", true));
	public final NumberSetting sparks = add(new NumberSetting("Sparks", "Sparks per burst", 16, 0, 30, 1));
	public final NumberSetting souls = add(new NumberSetting("Souls", "Rising soul wisps", 3, 0, 6, 1));
	public final NumberSetting size = add(new NumberSetting("Size", "Burst radius", 1.0, 0.5, 2.5, 0.05, "x"));
	public final NumberSetting duration = add(new NumberSetting("Duration", "Burst lifetime", 1.2, 0.5, 2.5, 0.05, "s"));
	public final NumberSetting brightness = add(new NumberSetting("Brightness", "Burst glow", 1.0, 0.2, 2, 0.05));
	private final ColorChoice color = new ColorChoice(add(ColorChoice.modeSetting()), add(new ColorSetting("Color", "Custom soul colour", 0xFF9BE8FF)));

	private final double[] x = new double[MAX], y = new double[MAX], z = new double[MAX];
	private final float[] born = new float[MAX], seed = new float[MAX];
	private int next;
	private @Nullable LivingEntity watched;

	public DeathFx() {
		super("death_fx", "Death FX", "A soul burst when the target you struck dies", Category.COMBAT);
	}

	public void onAttack(LivingEntity entity) {
		watched = entity;
	}

	@Override
	public void onTick() {
		LivingEntity e = watched;
		if (e == null) {
			return;
		}
		if (e.isRemoved() && !e.isDeadOrDying()) {
			// Unloaded or despawned, not killed: forget it silently.
			watched = null;
			return;
		}
		if (e.isDeadOrDying()) {
			burst(e.getX(), e.getY() + e.getBbHeight() * 0.45, e.getZ());
			watched = null;
		}
	}

	private void burst(double bx, double by, double bz) {
		x[next] = bx;
		y[next] = by;
		z[next] = bz;
		seed[next] = Minecraft.getInstance().player == null ? 0 : (float) Math.random();
		born[next] = System.nanoTime() / 1e9F;
		next = (next + 1) % MAX;
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		float now = System.nanoTime() / 1e9F;
		float life = duration.floatValue();
		float s = size.floatValue();
		float b = Math.min(1.5F, brightness.floatValue());
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
			int c = color.color(seed[i], now);
			float ix = (float) x[i], iy = (float) y[i], iz = (float) z[i];

			if (ring.isOn()) {
				// Expanding, thinning ring around the body.
				float r = (0.3F + 1.25F * s * k) * 1.0F;
				batch.begin(f);
				batch.flat(f.rx(ix), f.ry(iy - 0.2F), f.rz(iz), r / RING_POS, ColorUtil.withAlpha(c, fade * 0.6F * b));
				batch.end(NebulaPipelines.GLOW_RING, RING_POS, Math.max(0.04F, 0.3F * (1 - k)), 0.1F);
			}

			batch.begin(f);
			// Sparks flying outwards on closed-form arcs.
			int n = sparks.intValue();
			for (int j = 0; j < n; j++) {
				float a = j * Mth.TWO_PI / Math.max(1, n) + seed[i] * 7;
				float rr = (0.25F + 0.7F * fract(seed[i] * 13.7F + j * 0.618F)) * s;
				float sx = ix + Mth.cos(a) * rr * k * 1.6F;
				float sz = iz + Mth.sin(a) * rr * k * 1.6F;
				float rise = k * (1.1F + fract(j * 0.37F) * 0.9F) - k * k * 0.35F;
				float sz2 = iy + rise * s;
				float twinkle = 0.7F + 0.3F * Mth.sin(age * 12 + j * 2.3F);
				batch.billboard(f.rx(sx), f.ry(sz2), f.rz(sz), 0.055F * s * (1 - k * 0.5F),
						ColorUtil.withAlpha(ColorUtil.mix(c, 0xFFFFFFFF, 0.35F), fade * twinkle * b));
			}
			// Soul wisps rising slowly, wobbling as they go.
			int w = souls.intValue();
			for (int j = 0; j < w; j++) {
				float phase = seed[i] * 5 + j * 2.4F;
				float wob = Mth.sin(age * 2.6F + phase) * 0.22F;
				batch.billboard(f.rx(ix + wob), f.ry(iy + 0.2F + k * (1.4F + j * 0.3F)), f.rz(iz + Mth.cos(age * 2.2F + phase) * 0.22F),
						0.1F * s * (1 - k * 0.4F), ColorUtil.withAlpha(c, fade * 0.8F * b));
			}
			batch.end(NebulaPipelines.GLOW_SPARK, 0, 0, 0);
		}
	}

	private static float fract(float v) {
		return v - (float) Math.floor(v);
	}
}
