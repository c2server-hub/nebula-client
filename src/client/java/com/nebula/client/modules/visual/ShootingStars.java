package com.nebula.client.modules.visual;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.rendering.world.GlowBatch;
import com.nebula.client.rendering.world.SkyPass;
import com.nebula.client.rendering.world.WorldFrame;
import com.nebula.client.rendering.world.WorldRenderable;
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Meteors that streak across the night sky, each with a sparkling tail. Every meteor follows a
 * closed-form path (start + direction * speed * age), so the effect is frame-rate independent and
 * cheap: one billboard per tail segment, one batched draw call.
 */
public final class ShootingStars extends Module implements WorldRenderable {
	private static final int MAX = 6;

	public final ModeSetting trigger = add(new ModeSetting("Trigger", "When meteors appear", "Night", "Night", "Always"));
	public final NumberSetting frequency = add(new NumberSetting("Frequency", "Meteors per minute", 9, 1, 40, 1));
	public final NumberSetting size = add(new NumberSetting("Size", "Meteor size", 1.0, 0.4, 2.5, 0.05, "x"));
	public final NumberSetting trail = add(new NumberSetting("Trail", "Tail length in segments", 11, 4, 20, 1));
	public final NumberSetting brightness = add(new NumberSetting("Brightness", "Head and tail glow", 1.0, 0.2, 2, 0.05));
	private final ColorChoice color = new ColorChoice(add(ColorChoice.modeSetting()), add(new ColorSetting("Color", "Custom meteor colour", 0xFFBFD9FF)));

	private final RandomSource random = RandomSource.create();
	private final double[] sx = new double[MAX], sy = new double[MAX], sz = new double[MAX];
	private final double[] dx = new double[MAX], dy = new double[MAX], dz = new double[MAX];
	private final float[] speed = new float[MAX], life = new float[MAX], born = new float[MAX], tone = new float[MAX];
	private float nextSpawn = 2;

	public ShootingStars() {
		super("shooting_stars", "Shooting Stars", "Meteors streaking across the night sky", Category.SKY);
	}

	@Override
	public void onDisable() {
		nextSpawn = 2;
		for (int i = 0; i < MAX; i++) {
			born[i] = 0;
		}
	}

	private int active(float now) {
		int n = 0;
		for (int i = 0; i < MAX; i++) {
			if (born[i] > 0 && now - born[i] < life[i]) {
				n++;
			}
		}
		return n;
	}

	private void spawn(LocalPlayer p, float now) {
		for (int i = 0; i < MAX; i++) {
			if (born[i] > 0 && now - born[i] < life[i]) {
				continue;
			}
			float around = random.nextFloat() * Mth.TWO_PI;
			float dist = 80 + random.nextFloat() * 100;
			sx[i] = p.getX() + Mth.cos(around) * dist;
			sz[i] = p.getZ() + Mth.sin(around) * dist;
			sy[i] = p.getY() + 55 + random.nextFloat() * 60;
			// Mostly horizontal dive with a slight downward tilt.
			float heading = random.nextFloat() * Mth.TWO_PI;
			float dive = 0.18F + random.nextFloat() * 0.3F;
			dx[i] = Mth.cos(heading);
			dy[i] = -dive;
			dz[i] = Mth.sin(heading);
			float len = (float) Math.sqrt(dx[i] * dx[i] + dy[i] * dy[i] + dz[i] * dz[i]);
			dx[i] /= len;
			dy[i] /= len;
			dz[i] /= len;
			speed[i] = 30 + random.nextFloat() * 26;
			life[i] = 1.5F + random.nextFloat() * 1.6F;
			tone[i] = random.nextFloat();
			born[i] = now;
			return;
		}
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		var level = mc.level;
		if (p == null || level == null || !level.dimensionType().hasSkyLight()) {
			return;
		}
		if (trigger.is("Night") && SkyPass.nightFactor(level.getDayTime()) < 0.5F) {
			return;
		}
		float now = f.seconds();
		// Deterministic spawn schedule so the rate does not depend on the frame rate.
		float interval = 60F / Math.max(1, frequency.intValue());
		int slots = Math.min(MAX, Math.max(1, frequency.intValue() / 6 + 1));
		while (now >= nextSpawn) {
			if (active(now) < slots) {
				spawn(p, now);
			}
			nextSpawn += interval * (0.6F + random.nextFloat() * 0.8F);
		}

		int segments = trail.intValue();
		float s = size.floatValue();
		float b = brightness.floatValue();
		batch.begin(f);
		for (int i = 0; i < MAX; i++) {
			if (born[i] <= 0) {
				continue;
			}
			float age = now - born[i];
			if (age >= life[i]) {
				continue;
			}
			float t01 = age / life[i];
			float envelope = Math.min(1, t01 / 0.08F) * Math.min(1, (1 - t01) / 0.22F);
			if (envelope <= 0.02F) {
				continue;
			}
			float hx = (float) (sx[i] + dx[i] * speed[i] * age);
			float hy = (float) (sy[i] + dy[i] * speed[i] * age);
			float hz = (float) (sz[i] + dz[i] * speed[i] * age);
			int headColor = ColorUtil.mix(color.color(tone[i], now), 0xFFFFFFFF, 0.55F);
			// Wide, faint halo riding along with the head.
			batch.billboard(f.rx(hx), f.ry(hy), f.rz(hz), 0.42F * s, ColorUtil.withAlpha(color.color(tone[i], now), 0.16F * envelope * b));
			// The tail: frozen samples of the path behind the head, fading with distance.
			float dtSeg = 0.05F;
			for (int k = 0; k < segments; k++) {
				float ta = age - k * dtSeg;
				if (ta < 0) {
					break;
				}
				float tx = (float) (sx[i] + dx[i] * speed[i] * ta);
				float ty = (float) (sy[i] + dy[i] * speed[i] * ta);
				float tz = (float) (sz[i] + dz[i] * speed[i] * ta);
				float fade = (1 - (float) k / segments);
				float a = envelope * fade * fade * 0.85F * Math.min(1, b);
				if (a < 0.02F) {
					continue;
				}
				batch.billboard(f.rx(tx), f.ry(ty), f.rz(tz), (0.13F - k * 0.004F) * s,
						ColorUtil.withAlpha(color.color(tone[i] + k * 0.02F, now), a));
			}
			// White-hot head on top of everything.
			batch.billboard(f.rx(hx), f.ry(hy), f.rz(hz), 0.11F * s, ColorUtil.withAlpha(headColor, envelope * Math.min(1, b)));
		}
		batch.end(NebulaPipelines.GLOW_SPARK, 0, 0, 0);
	}
}
