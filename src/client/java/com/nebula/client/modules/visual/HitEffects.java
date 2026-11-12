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
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Burst of glowing sparks and a shockwave where you hit an entity. Spark motion is evaluated
 * analytically from the spawn time (ballistic), so it is identical at any frame rate.
 */
public final class HitEffects extends Module implements WorldRenderable {
	private static final int MAX_SPARKS = 256;
	private static final int MAX_WAVES = 8;
	private static final float GRAVITY = -6.5F;

	public final NumberSetting count = add(new NumberSetting("Count", "Sparks per hit", 24, 4, 48, 1));
	public final NumberSetting speed = add(new NumberSetting("Speed", "Burst speed", 4.5, 0.5, 10, 0.1, "m/s"));
	public final NumberSetting lifetime = add(new NumberSetting("Lifetime", "Spark lifetime", 0.9, 0.2, 2, 0.05, "s"));
	public final NumberSetting size = add(new NumberSetting("Size", "Spark size", 0.16, 0.03, 0.3, 0.005));
	public final BoolSetting gravity = add(new BoolSetting("Gravity", "Sparks fall down", true));
	public final BoolSetting shockwave = add(new BoolSetting("Shockwave", "Expanding ring at the impact", true));
	private final ColorChoice color = new ColorChoice(add(ColorChoice.modeSetting()), add(new ColorSetting("Color", "Custom spark colour", 0xFFFF7A3D)));

	private final RandomSource random = RandomSource.create();
	private final double[] sx = new double[MAX_SPARKS], sy = new double[MAX_SPARKS], sz = new double[MAX_SPARKS];
	private final float[] vx = new float[MAX_SPARKS], vy = new float[MAX_SPARKS], vz = new float[MAX_SPARKS], tone = new float[MAX_SPARKS];
	private final long[] sparkBorn = new long[MAX_SPARKS];
	private int nextSpark;
	private final double[] wx = new double[MAX_WAVES], wy = new double[MAX_WAVES], wz = new double[MAX_WAVES];
	private final long[] waveBorn = new long[MAX_WAVES];
	private int nextWave;

	public HitEffects() {
		super("hit_effects", "Hit Effects", "Glowing spark burst and shockwave when you hit", Category.VISUAL);
	}

	/** Registered once by the client manager; ignored while the module is disabled. */
	public void onHit(LivingEntity entity) {
		if (!isEnabled()) {
			return;
		}
		double x = entity.getX(), y = entity.getY() + entity.getBbHeight() * 0.6, z = entity.getZ();
		// Emit from the side facing the player so the burst is not hidden inside the entity's body.
		var player = net.minecraft.client.Minecraft.getInstance().player;
		if (player != null) {
			double dx = player.getX() - x, dz = player.getZ() - z;
			double len = Math.sqrt(dx * dx + dz * dz);
			if (len > 1e-3) {
				double push = entity.getBbWidth() * 0.5 + 0.15;
				x += dx / len * push;
				z += dz / len * push;
			}
		}
		long now = System.nanoTime();
		int n = count.intValue();
		float v = speed.floatValue();
		for (int i = 0; i < n; i++) {
			int s = nextSpark;
			nextSpark = (nextSpark + 1) % MAX_SPARKS;
			// Uniform direction on the sphere, biased upwards for a fountain-like burst.
			float theta = random.nextFloat() * Mth.TWO_PI;
			float cosPhi = random.nextFloat() * 1.6F - 0.6F;
			float sinPhi = Mth.sqrt(1 - Math.min(1, cosPhi * cosPhi));
			float mag = v * (0.45F + random.nextFloat() * 0.55F);
			sx[s] = x;
			sy[s] = y;
			sz[s] = z;
			vx[s] = Mth.cos(theta) * sinPhi * mag;
			vy[s] = cosPhi * mag;
			vz[s] = Mth.sin(theta) * sinPhi * mag;
			tone[s] = random.nextFloat();
			sparkBorn[s] = now;
		}
		wx[nextWave] = x;
		wy[nextWave] = y;
		wz[nextWave] = z;
		waveBorn[nextWave] = now;
		nextWave = (nextWave + 1) % MAX_WAVES;
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		long now = System.nanoTime();
		float life = lifetime.floatValue();
		float g = gravity.isOn() ? GRAVITY : 0;
		float baseSize = size.floatValue();
		batch.begin(f);
		for (int s = 0; s < MAX_SPARKS; s++) {
			if (sparkBorn[s] == 0) {
				continue;
			}
			float age = (now - sparkBorn[s]) / 1e9F;
			float t = age / life;
			if (t >= 1) {
				continue;
			}
			int c = color.color(tone[s], f.seconds());
			// Head plus two trailing samples along the trajectory read as a motion-blurred streak.
			for (int k = 0; k < 3; k++) {
				float ta = Math.max(0, age - k * 0.018F);
				float drag = 1 - Math.min(0.6F, ta * 0.8F);
				double x = sx[s] + vx[s] * ta * drag;
				double y = sy[s] + vy[s] * ta * drag + 0.5 * g * ta * ta;
				double z = sz[s] + vz[s] * ta * drag;
				float fade = (1 - t) * (k == 0 ? 1 : 0.5F / k);
				batch.billboard(f.rx(x), f.ry(y), f.rz(z), baseSize * (1 - 0.5F * t) * (k == 0 ? 1 : 0.7F), ColorUtil.withAlpha(c, fade));
			}
		}
		batch.end(NebulaPipelines.GLOW_SPARK, 0, 0, 0);

		if (shockwave.isOn()) {
			batch.begin(f);
			for (int w = 0; w < MAX_WAVES; w++) {
				if (waveBorn[w] == 0) {
					continue;
				}
				float t = (now - waveBorn[w]) / 1e9F / 0.45F;
				if (t >= 1) {
					continue;
				}
				float r = 0.2F + 1.1F * Animation.easeOutCubic(t);
				batch.billboard(f.rx(wx[w]), f.ry(wy[w]), f.rz(wz[w]), r / 0.8F, ColorUtil.withAlpha(color.color(0.5F, f.seconds()), (1 - t) * (1 - t)));
			}
			batch.end(NebulaPipelines.GLOW_RING, 0.8F, 0.04F, 0.25F);
		}
	}
}
