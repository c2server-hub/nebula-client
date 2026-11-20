package com.nebula.client.modules.visual;

import com.nebula.client.gui.themes.Theme;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.rendering.world.GlowBatch;
import com.nebula.client.rendering.world.WorldFrame;
import com.nebula.client.rendering.world.WorldRenderable;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;

/**
 * Glowing bead ribbons trailing every projectile: arrows, tridents, snowballs, eggs, ender pearls,
 * splash potions and fireballs. Each projectile keeps a small ring buffer of recent positions
 * (sampled at most every 25 ms) so the trail is smooth at any frame rate, and drains away once the
 * projectile lands or sticks into a block.
 */
public final class ProjectileTrails extends Module implements WorldRenderable {
	private static final int SLOTS = 56;
	private static final int SAMPLES = 10;
	private static final long SAMPLE_INTERVAL = 25_000_000L;
	private static final long KEEP_NANOS = 800_000_000L;

	public final NumberSetting range = add(new NumberSetting("Range", "Projectiles tracked around you", 28, 8, 64, 1, "m"));
	public final NumberSetting size = add(new NumberSetting("Size", "Bead size", 1.0, 0.4, 2, 0.05, "x"));
	public final NumberSetting density = add(new NumberSetting("Density", "Trail length", 1.0, 0.4, 1.6, 0.05, "x"));
	public final ModeSetting colors = add(new ModeSetting("Colors", "Colour scheme", "Type", "Type", "Theme", "Custom", "Rainbow"));
	public final ColorSetting color = add(new ColorSetting("Color", "Custom colour", 0xFFFFB259))
			.visibleWhen(() -> colors.is("Custom"));

	// One slot per live projectile, keyed by entity id, found by a short linear scan.
	private final int[] id = new int[SLOTS];
	private final float[][] sx = new float[SLOTS][SAMPLES];
	private final float[][] sy = new float[SLOTS][SAMPLES];
	private final float[][] sz = new float[SLOTS][SAMPLES];
	private final long[] lastSample = new long[SLOTS];
	private final long[] lastSeen = new long[SLOTS];
	private final byte[] type = new byte[SLOTS];
	private final int[] head = new int[SLOTS];

	public ProjectileTrails() {
		super("projectile_trails", "Projectile Trails", "Glowing ribbons behind arrows and thrown items", Category.WORLD, true, false);
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		Minecraft mc = Minecraft.getInstance();
		var self = mc.player;
		var level = mc.level;
		if (self == null || level == null) {
			return;
		}
		long now = System.nanoTime();
		double rangeSq = range.floatValue() * range.floatValue();
		for (var e : level.entitiesForRendering()) {
			byte t = classify(e);
			if (t < 0 || e.distanceToSqr(self) > rangeSq) {
				continue;
			}
			var motion = e.getDeltaMovement();
			boolean moving = motion.horizontalDistanceSqr() + motion.y * motion.y > 0.003;
			int slot = slotFor(e.getId());
			lastSeen[slot] = now;
			type[slot] = t;
			if (moving && now - lastSample[slot] >= SAMPLE_INTERVAL) {
				lastSample[slot] = now;
				float pt = f.partialTick();
				int h = head[slot];
				sx[slot][h] = (float) Mth.lerp(pt, e.xo, e.getX());
				sy[slot][h] = (float) Mth.lerp(pt, e.yo, e.getY()) + 0.1F;
				sz[slot][h] = (float) Mth.lerp(pt, e.zo, e.getZ());
				head[slot] = (h + 1) % SAMPLES;
			}
		}

		float seconds = now / 1e9F;
		int used = Math.max(1, Math.round(SAMPLES * density.floatValue()));
		float s = size.floatValue();
		batch.begin(f);
		boolean any = false;
		for (int i = 0; i < SLOTS; i++) {
			if (id[i] == 0 || now - lastSeen[i] > KEEP_NANOS) {
				id[i] = 0;
				continue;
			}
			long idle = now - lastSample[i];
			boolean fresh = idle < SAMPLE_INTERVAL * 3;
			// A landed projectile stops feeding samples; let its ribbon evaporate gently.
			float drain = fresh ? 1 : Math.clamp(1 - (idle - SAMPLE_INTERVAL * 3) / 4.5e8F, 0, 1);
			if (drain <= 0.01F) {
				id[i] = 0;
				continue;
			}
			int cBase = trailColor(type[i], seconds);
			for (int k = 0; k < used; k++) {
				// k = 0 is the newest sample, right behind the projectile.
				int idx = (head[i] - 1 - k + SAMPLES * 2) % SAMPLES;
				float fade = 1 - k / (float) used;
				float a = fade * fade * 0.8F * drain;
				if (!fresh && k == 0) {
					continue; // projectile stopped: the head bead evaporates first
				}
				float bead = (0.15F - k * 0.010F) * s * (1 - 0.35F * k / (float) SAMPLES);
				int c = ColorUtil.withAlpha(cBase, a);
				batch.billboard(f.rx(sx[i][idx]), f.ry(sy[i][idx]), f.rz(sz[i][idx]), Math.max(0.03F, bead), c);
				if (k == 0) {
					// Hot white core right at the projectile.
					batch.billboard(f.rx(sx[i][idx]), f.ry(sy[i][idx]), f.rz(sz[i][idx]), 0.06F * s, ColorUtil.withAlpha(0xFFFFFFFF, 0.8F * fade));
				}
				any = true;
			}
		}
		if (any) {
			batch.end(NebulaPipelines.GLOW_SPARK, 0, 0, 0);
		}
	}

	private int slotFor(int entityId) {
		for (int i = 0; i < SLOTS; i++) {
			if (id[i] == entityId) {
				return i;
			}
		}
		// Take a free slot, or evict the one whose projectile was seen longest ago.
		int best = 0;
		long bestSeen = Long.MAX_VALUE;
		for (int i = 0; i < SLOTS; i++) {
			long seen = id[i] == 0 ? Long.MIN_VALUE : lastSeen[i];
			if (seen < bestSeen) {
				bestSeen = seen;
				best = i;
			}
		}
		id[best] = entityId;
		head[best] = 0;
		lastSample[best] = 0;
		return best;
	}

	private int trailColor(byte t, float seconds) {
		return switch (colors.get()) {
			case "Theme" -> Theme.current().accentMix(t / 7F);
			case "Custom" -> color.argb();
			case "Rainbow" -> ColorUtil.hsv(((seconds * 0.12F + t * 0.1F) % 1F + 1F) % 1F, 0.7F, 1F, 255);
			default -> switch (t) {
				case 1 -> 0xFF7FE7FF; // trident
				case 2 -> 0xFFBFE8FF; // snowball
				case 3 -> 0xFFF5E7C0; // egg
				case 4 -> 0xFF6FE0C0; // ender pearl
				case 5 -> 0xFFE77FD4; // potion
				case 6 -> 0xFFFFA53D; // fireball
				default -> Theme.current().accentMix(0.3F); // arrow
			};
		};
	}

	private static byte classify(Entity e) {
		if (e instanceof ThrownTrident) {
			return 1;
		}
		if (e instanceof AbstractArrow) {
			return 0;
		}
		if (e instanceof Snowball) {
			return 2;
		}
		if (e instanceof ThrownEgg) {
			return 3;
		}
		if (e instanceof ThrownEnderpearl) {
			return 4;
		}
		if (e instanceof AbstractThrownPotion) {
			return 5;
		}
		if (e instanceof AbstractHurtingProjectile) {
			return 6;
		}
		if (e instanceof ThrowableProjectile) {
			return 2; // any other thrown item reads as icy
		}
		return -1;
	}
}
