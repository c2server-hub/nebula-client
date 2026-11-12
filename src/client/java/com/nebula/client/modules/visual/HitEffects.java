package com.nebula.client.modules.visual;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.rendering.world.GlowBatch;
import com.nebula.client.rendering.world.SparkPool;
import com.nebula.client.rendering.world.WorldFrame;
import com.nebula.client.rendering.world.WorldRenderable;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;

/** Burst of glowing sparks and a shockwave where you hit an entity. */
public final class HitEffects extends Module implements WorldRenderable {
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
	private final SparkPool sparks = new SparkPool(256);
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
		var player = Minecraft.getInstance().player;
		if (player != null) {
			double dx = player.getX() - x, dz = player.getZ() - z;
			double len = Math.sqrt(dx * dx + dz * dz);
			if (len > 1e-3) {
				double push = entity.getBbWidth() * 0.5 + 0.15;
				x += dx / len * push;
				z += dz / len * push;
			}
		}
		float seconds = System.nanoTime() / 1e9F;
		burst(sparks, random, x, y, z, count.intValue(), speed.floatValue(), lifetime.floatValue(), size.floatValue(), i -> color.color(random.nextFloat(), seconds));
		wx[nextWave] = x;
		wy[nextWave] = y;
		wz[nextWave] = z;
		waveBorn[nextWave] = System.nanoTime();
		nextWave = (nextWave + 1) % MAX_WAVES;
	}

	/** Emits {@code n} sparks in a fountain-biased random direction. Shared with block break effects. */
	static void burst(SparkPool pool, RandomSource random, double x, double y, double z, int n, float speed, float life, float size,
					  java.util.function.IntUnaryOperator colorOf) {
		for (int i = 0; i < n; i++) {
			float theta = random.nextFloat() * Mth.TWO_PI;
			float cosPhi = random.nextFloat() * 1.6F - 0.6F;
			float sinPhi = Mth.sqrt(1 - Math.min(1, cosPhi * cosPhi));
			float mag = speed * (0.45F + random.nextFloat() * 0.55F);
			pool.emit(x, y, z, Mth.cos(theta) * sinPhi * mag, cosPhi * mag, Mth.sin(theta) * sinPhi * mag, colorOf.applyAsInt(i),
				life * (0.75F + random.nextFloat() * 0.5F), size);
		}
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		sparks.render(f, batch, gravity.isOn() ? GRAVITY : 0);
		if (shockwave.isOn()) {
			long now = System.nanoTime();
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
