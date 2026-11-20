package com.nebula.client.modules.visual;

import com.nebula.client.core.ClientManager;
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
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/** Glowing footprints, water ripples, sprint sparks on stone and a landing shockwave. */
public final class StepFx extends Module implements WorldRenderable {
	private static final int MAX_PRINTS = 64;
	private static final int MAX_RINGS = 24;

	public final BoolSetting footprints = add(new BoolSetting("Footprints", "Glowing footprints that fade out", true));
	public final NumberSetting printLife = add(new NumberSetting("Footprint life", "How long footprints stay", 3, 0.5, 10, 0.5, "s"));
	public final BoolSetting ripples = add(new BoolSetting("Water ripples", "Glowing ripples when moving on water", true));
	public final BoolSetting sparks = add(new BoolSetting("Sprint sparks", "Sparks on stone while sprinting", true));
	public final BoolSetting anyBlock = add(new BoolSetting("Any block", "Sparks on every block, not only stone-like ones", false));
	public final BoolSetting landing = add(new BoolSetting("Landing wave", "Shockwave after a fall", true));
	public final NumberSetting minFall = add(new NumberSetting("Min fall", "Minimum fall height for the shockwave", 2, 0.5, 10, 0.5, "m"));
	public final BoolSetting useAura = add(new BoolSetting("Use aura color", "Follow the Aura Glow colour", true));
	public final ColorSetting color = add(new ColorSetting("Color", "Colour when not following the aura", 0xFF4DE1FF));

	private final RandomSource random = RandomSource.create();
	private final SparkPool sparkPool = new SparkPool(160);

	private final double[] fpX = new double[MAX_PRINTS], fpY = new double[MAX_PRINTS], fpZ = new double[MAX_PRINTS];
	private final float[] fpDx = new float[MAX_PRINTS], fpDz = new float[MAX_PRINTS];
	private final long[] fpBorn = new long[MAX_PRINTS];
	private final int[] fpColor = new int[MAX_PRINTS];
	private int fpNext;
	private double stepAcc;
	private boolean leftFoot;

	// Rings: water ripples (type 0) and landing waves (type 1).
	private final double[] rX = new double[MAX_RINGS], rY = new double[MAX_RINGS], rZ = new double[MAX_RINGS];
	private final float[] rMax = new float[MAX_RINGS], rLife = new float[MAX_RINGS];
	private final long[] rBorn = new long[MAX_RINGS];
	private final int[] rColor = new int[MAX_RINGS];
	private int rNext;
	private int rippleTimer;

	private boolean wasOnGround = true;
	private double peakY;

	public StepFx() {
		super("step_fx", "Step FX", "Footprints, water ripples, sprint sparks, landing shockwave", Category.VISUAL);
	}

	private int col(float h) {
		float t = System.nanoTime() / 1e9F;
		return useAura.isOn() ? ClientManager.get().modules().auraGlow.color(h, t) : color.argb();
	}

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		ClientLevel level = mc.level;
		if (p == null || level == null || p.isSpectator()) {
			return;
		}
		long now = System.nanoTime();
		boolean onGround = p.onGround();
		double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
		double len = Math.sqrt(dx * dx + dz * dz);

		if (footprints.isOn() && onGround && !p.isInWater() && len > 1e-3) {
			stepAcc += len;
			double stride = p.isSprinting() ? 0.75 : 0.55;
			if (stepAcc >= stride) {
				stepAcc = stepAcc >= stride * 2 ? 0 : stepAcc - stride;
				float dirX = (float) (dx / len), dirZ = (float) (dz / len);
				float side = leftFoot ? 0.13F : -0.13F;
				leftFoot = !leftFoot;
				int i = fpNext;
				fpNext = (fpNext + 1) % MAX_PRINTS;
				fpX[i] = p.getX() - dirZ * side;
				fpY[i] = p.getY() + 0.015;
				fpZ[i] = p.getZ() + dirX * side;
				fpDx[i] = dirX;
				fpDz[i] = dirZ;
				fpColor[i] = col(0);
				fpBorn[i] = now;
			}
		}

		if (ripples.isOn() && p.isInWater() && !p.isUnderWater()) {
			if (--rippleTimer <= 0) {
				BlockPos bp = BlockPos.containing(p.getX(), p.getY(), p.getZ());
				for (int k = 0; k < 3 && level.getFluidState(bp.above()).is(FluidTags.WATER); k++) {
					bp = bp.above();
				}
				FluidState fs = level.getFluidState(bp);
				if (fs.is(FluidTags.WATER)) {
					double sy = bp.getY() + fs.getHeight(level, bp) + 0.02;
					int c = ColorUtil.mix(col(0.1F), 0xFFFFFFFF, 0.25F);
					ring(p.getX(), sy, p.getZ(), 1.3F, 1.4F, c, now);
					ring(p.getX(), sy, p.getZ(), 0.8F, 1.1F, ColorUtil.withAlpha(c, 0.6F), now + 250_000_000L);
				}
				rippleTimer = len > 0.03 ? 5 : 16;
			}
		}

		if (sparks.isOn() && p.isSprinting() && onGround && !p.isInWater()) {
			BlockState state = level.getBlockState(p.getOnPos());
			if (!state.isAir() && (anyBlock.isOn() || state.is(BlockTags.MINEABLE_WITH_PICKAXE))) {
				int c = ColorUtil.mix(col(0), 0xFFFFE08A, 0.5F);
				for (int k = 0; k < 2; k++) {
					sparkPool.emit(p.getX() + (random.nextFloat() - 0.5F) * 0.4, p.getY() + 0.05, p.getZ() + (random.nextFloat() - 0.5F) * 0.4,
						(float) -dx * 8 + (random.nextFloat() - 0.5F) * 2, 1.5F + random.nextFloat() * 2, (float) -dz * 8 + (random.nextFloat() - 0.5F) * 2,
						c, 0.35F + random.nextFloat() * 0.25F, 0.04F);
				}
			}
		}

		if (p.getAbilities().flying || p.isInWater() || p.onClimbable()) {
			peakY = p.getY();
		} else if (!onGround) {
			peakY = wasOnGround ? p.getY() : Math.max(peakY, p.getY());
		} else if (!wasOnGround) {
			double fall = peakY - p.getY();
			if (landing.isOn() && fall >= minFall.floatValue()) {
				shockwave(p, (float) fall, now);
			}
		}
		wasOnGround = onGround;
	}

	private void ring(double x, double y, double z, float maxRadius, float life, int argb, long born) {
		int i = rNext;
		rNext = (rNext + 1) % MAX_RINGS;
		rX[i] = x;
		rY[i] = y;
		rZ[i] = z;
		rMax[i] = maxRadius;
		rLife[i] = life;
		rColor[i] = argb;
		rBorn[i] = born;
	}

	private void shockwave(LocalPlayer p, float fall, long now) {
		float radius = Math.min(5.5F, 1.2F + fall * 0.35F);
		int c = col(0);
		double y = p.getY() + 0.03;
		ring(p.getX(), y, p.getZ(), radius, 0.8F, c, now);
		ring(p.getX(), y, p.getZ(), radius * 0.6F, 0.6F, ColorUtil.mix(c, 0xFFFFFFFF, 0.5F), now + 80_000_000L);
		int n = (int) Math.min(40, 10 + fall * 3);
		float sp = 3 + fall * 0.3F;
		for (int k = 0; k < n; k++) {
			float ang = k * Mth.TWO_PI / n + random.nextFloat() * 0.2F;
			float v = sp * (0.7F + random.nextFloat() * 0.5F);
			sparkPool.emit(p.getX(), y + 0.05, p.getZ(), Mth.cos(ang) * v, 0.8F + random.nextFloat() * 1.5F, Mth.sin(ang) * v, c,
				0.5F + random.nextFloat() * 0.3F, 0.05F);
		}
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		long now = System.nanoTime();
		float life = printLife.floatValue();

		batch.begin(f);
		for (int i = 0; i < MAX_PRINTS; i++) {
			if (fpBorn[i] == 0) {
				continue;
			}
			float tt = (now - fpBorn[i]) / 1e9F / life;
			if (tt >= 1) {
				fpBorn[i] = 0;
				continue;
			}
			float a = (float) Math.pow(1 - tt, 1.3) * 0.9F;
			float hl = 0.15F, hw = 0.075F;
			float cx = f.rx(fpX[i]), cy = f.ry(fpY[i]), cz = f.rz(fpZ[i]);
			float ax = fpDx[i] * hl, az = fpDz[i] * hl;
			float sx = -fpDz[i] * hw, sz = fpDx[i] * hw;
			batch.quad(cx - ax - sx, cy, cz - az - sz, cx - ax + sx, cy, cz - az + sz, cx + ax + sx, cy, cz + az + sz, cx + ax - sx, cy, cz + az - sz,
				ColorUtil.withAlpha(fpColor[i], a));
		}
		batch.end(NebulaPipelines.GLOW_SOFT, 0, 0, 0);

		batch.begin(f);
		for (int i = 0; i < MAX_RINGS; i++) {
			if (rBorn[i] == 0 || now < rBorn[i]) {
				continue;
			}
			float tt = (now - rBorn[i]) / 1e9F / rLife[i];
			if (tt >= 1) {
				rBorn[i] = 0;
				continue;
			}
			float ease = 1 - (1 - tt) * (1 - tt) * (1 - tt);
			float r = 0.15F + (rMax[i] - 0.15F) * ease;
			float a = (1 - tt) * (1 - tt) * ColorUtil.alpha(rColor[i]) / 255F;
			batch.flat(f.rx(rX[i]), f.ry(rY[i]), f.rz(rZ[i]), r / 0.8F, ColorUtil.withAlpha(rColor[i], a));
		}
		batch.end(NebulaPipelines.GLOW_RING, 0.8F, 0.035F, 0.12F);

		sparkPool.render(f, batch, -9F);
	}
}
