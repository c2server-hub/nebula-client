package com.nebula.client.modules.visual;

import com.nebula.client.gui.themes.Theme;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.rendering.world.GlowBatch;
import com.nebula.client.rendering.world.WorldFrame;
import com.nebula.client.rendering.world.WorldRenderable;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Items;

/**
 * Player aura: animated / reactive colour shared by the other player effects, vanilla-outline glow,
 * orbiting sparks / runes / orbs, rotating magic circle, rising energy mist, ground light pool,
 * emissive eyes and item reactions (enchanted weapon pulse, totem golden halo).
 */
public final class AuraGlow extends Module implements WorldRenderable {
	private static final int MIST_MAX = 96;
	private static final int CYAN = 0xFF35E8FF, RED = 0xFFFF3B3B, GOLD = 0xFFFFC83D, WHITE = 0xFFFFFFFF;

	public final ModeSetting colorMode = add(new ModeSetting("Color mode", "Fixed, RGB cycle, head-to-feet gradient or theme", "Gradient",
		"Fixed", "RGB cycle", "Gradient", "Theme"));
	public final ColorSetting headColor = add(new ColorSetting("Color", "Main aura colour (top of the gradient)", 0xFF9B6BFF));
	public final ColorSetting feetColor = add(new ColorSetting("Feet color", "Bottom colour of the gradient", 0xFF35D6FF));
	public final NumberSetting cycleSpeed = add(new NumberSetting("Cycle speed", "RGB cycle speed", 0.15, 0.02, 1, 0.01));
	public final BoolSetting pulse = add(new BoolSetting("Pulse", "Breathing intensity", true));
	public final NumberSetting bpm = add(new NumberSetting("Pulse rate", "Pulses per minute", 60, 15, 200, 1, " bpm"));
	public final BoolSetting reactive = add(new BoolSetting("Reactive", "Low health red, sprint cyan, potion colour, damage flash", true));
	public final NumberSetting intensity = add(new NumberSetting("Intensity", "Overall brightness", 1, 0.2, 2, 0.05));
	public final BoolSetting outline = add(new BoolSetting("Outline", "Vanilla glow outline in the aura colour (visible through walls)", false));
	public final ModeSetting orbitals = add(new ModeSetting("Orbitals", "What orbits around you", "Sparks", "Off", "Sparks", "Runes", "Orbs"));
	public final NumberSetting orbitCount = add(new NumberSetting("Orbit count", "Number of orbiting elements", 6, 1, 16, 1));
	public final NumberSetting orbitRadius = add(new NumberSetting("Orbit radius", "Radius at rest (grows with speed)", 0.6, 0.3, 1.5, 0.05, "m"));
	public final NumberSetting orbitSpeed = add(new NumberSetting("Orbit speed", "Rotation speed (grows with speed)", 1, 0.2, 3, 0.05));
	public final BoolSetting magicCircle = add(new BoolSetting("Magic circle", "Rotating rune circle under your feet", true));
	public final NumberSetting circleRadius = add(new NumberSetting("Circle radius", "Radius at rest (expands on sprint / jump)", 0.9, 0.4, 2.5, 0.05, "m"));
	public final BoolSetting mist = add(new BoolSetting("Energy mist", "Coloured smoke rising from the body, denser when still", true));
	public final NumberSetting mistDensity = add(new NumberSetting("Mist density", "Amount of mist", 1, 0.1, 3, 0.1));
	public final BoolSetting groundGlow = add(new BoolSetting("Ground glow", "Coloured light pool under your feet", true));
	public final BoolSetting eyes = add(new BoolSetting("Glowing eyes", "Two emissive points on the face", true));
	public final BoolSetting eyesDarkOnly = add(new BoolSetting("Eyes only in dark", "Fade the eyes in bright light", true));
	public final BoolSetting customEyes = add(new BoolSetting("Custom eye color", "Use the eye colour instead of the aura", false));
	public final ColorSetting eyeColor = add(new ColorSetting("Eye color", "Custom eye colour", 0xFFFF3B3B));
	public final BoolSetting items = add(new BoolSetting("Item reactions", "Enchanted weapon pulses the aura, totem adds a golden halo", true));
	public final BoolSetting firstPerson = add(new BoolSetting("First person", "Show body effects in first person", false));

	private final RandomSource random = RandomSource.create();
	private final double[] mx = new double[MIST_MAX], my = new double[MIST_MAX], mz = new double[MIST_MAX];
	private final long[] mBorn = new long[MIST_MAX];
	private final float[] mLife = new float[MIST_MAX], mSize = new float[MIST_MAX], mH = new float[MIST_MAX];
	private int mNext;
	private float mistAcc;
	private long lastFrame;

	private int stateTick = Integer.MIN_VALUE;
	private int potionColor;
	private float hurt;
	private boolean lowHealth, enchanted, totem;
	private float sprintK;

	private double groundY;
	private boolean hasGround;
	private long jumpTime;
	private boolean wasOnGround = true;
	private double speed;

	private final float[] eye = new float[6];

	public AuraGlow() {
		super("aura_glow", "Aura Glow", "Reactive aura, orbitals, magic circle, mist, glowing eyes", Category.VISUAL);
	}

	@Override
	public void onTick() {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) {
			return;
		}
		boolean onGround = p.onGround();
		if (onGround) {
			groundY = p.getY();
			hasGround = true;
		}
		if (wasOnGround && !onGround && p.getDeltaMovement().y > 0.1) {
			jumpTime = System.nanoTime();
		}
		wasOnGround = onGround;
		double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
		speed = Math.sqrt(dx * dx + dz * dz) * 20;
	}

	/** Per-tick player state used by the reactive colour; cached so many calls per frame stay cheap. */
	private void refreshState(LocalPlayer p) {
		if (p.tickCount == stateTick) {
			return;
		}
		stateTick = p.tickCount;
		hurt = p.hurtTime > 0 ? Math.min(1F, p.hurtTime / 10F) : 0;
		lowHealth = p.getMaxHealth() > 0 && p.getHealth() / p.getMaxHealth() < 0.3F;
		potionColor = 0;
		for (MobEffectInstance effect : p.getActiveEffects()) {
			if (effect.isVisible()) {
				potionColor = 0xFF000000 | effect.getEffect().value().getColor();
				break;
			}
		}
		enchanted = p.getMainHandItem().isEnchanted();
		totem = p.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || p.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
		sprintK += ((p.isSprinting() ? 1 : 0) - sprintK) * 0.3F;
	}

	private int baseColor(float h, float seconds) {
		return switch (colorMode.get()) {
			case "Fixed" -> headColor.argb();
			case "RGB cycle" -> ColorUtil.hsv(((seconds * cycleSpeed.floatValue() + h * 0.3F) % 1F + 1F) % 1F, 0.75F, 1F, 255);
			case "Theme" -> Theme.current().accentMix(h);
			default -> ColorUtil.mix(feetColor.argb(), headColor.argb(), h);
		};
	}

	/**
	 * Aura colour at a height fraction {@code h} (0 = feet, 1 = head), including the reactive states.
	 * Shared by Energy Trails, Step FX and Death FX.
	 */
	public int color(float h, float seconds) {
		int c = baseColor(h, seconds);
		LocalPlayer p = Minecraft.getInstance().player;
		if (!reactive.isOn() || p == null) {
			return c | 0xFF000000;
		}
		refreshState(p);
		if (potionColor != 0) {
			c = ColorUtil.mix(c, potionColor, 0.55F);
		}
		if (sprintK > 0.01F) {
			c = ColorUtil.mix(c, CYAN, 0.7F * sprintK);
		}
		if (lowHealth) {
			c = ColorUtil.mix(c, RED, 0.55F + 0.35F * Mth.sin(seconds * 9));
		}
		if (hurt > 0) {
			c = ColorUtil.mix(c, RED, hurt);
			c = ColorUtil.mix(c, WHITE, Math.max(0, hurt * 2 - 1));
		}
		return c | 0xFF000000;
	}

	/** Global alpha multiplier: intensity, breathing pulse and enchanted-weapon throb. */
	public float alpha(float seconds) {
		float a = intensity.floatValue();
		if (pulse.isOn()) {
			a *= 0.7F + 0.3F * Mth.sin(seconds * bpm.floatValue() / 60F * Mth.TWO_PI);
		}
		if (items.isOn() && enchanted) {
			a *= 1 + 0.35F * Mth.sin(seconds * 7);
		}
		return a;
	}

	/** Used by the outline mixins. */
	public boolean forcesOutline(Entity entity) {
		return isEnabled() && outline.isOn() && entity == Minecraft.getInstance().player;
	}

	public int outlineRgb() {
		float t = System.nanoTime() / 1e9F;
		int c = color(0.6F, t);
		float k = Mth.clamp(alpha(t), 0, 1);
		return ColorUtil.mix(0xFF000000, c, 0.35F + 0.65F * k) & 0xFFFFFF;
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || p.isInvisible() || p.isDeadOrDying()) {
			return;
		}
		refreshState(p);
		boolean fp = mc.options.getCameraType().isFirstPerson();
		boolean body = !fp || firstPerson.isOn();
		float t = f.seconds();
		float pt = f.partialTick();
		double px = Mth.lerp(pt, p.xo, p.getX());
		double py = Mth.lerp(pt, p.yo, p.getY());
		double pz = Mth.lerp(pt, p.zo, p.getZ());
		float bb = p.getBbHeight();
		float a = alpha(t);
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0 : Math.min(0.1F, (now - lastFrame) / 1e9F);
		lastFrame = now;
		float moveK = (float) Math.min(1, speed / 5.6);

		double gy;
		float groundFade;
		if (p.onGround() || !hasGround) {
			gy = py;
			groundFade = 1;
		} else {
			gy = groundY;
			groundFade = Mth.clamp(1 - (float) (py - groundY) / 3F, 0, 1);
		}
		gy += 0.02;

		boolean showEyes = computeEyes(mc, p, pt, py, fp);
		if (mist.isOn() && body) {
			spawnMist(px, py, pz, bb, dt, moveK, now);
		}

		// Soft pass: ground light pool, mist, orb glow, eye glow.
		batch.begin(f);
		if (groundGlow.isOn() && groundFade > 0) {
			batch.flat(f.rx(px), f.ry(gy), f.rz(pz), 1.3F, ColorUtil.withAlpha(color(0, t), 0.32F * a * groundFade));
		}
		renderMist(f, batch, now, t, a);
		int n = orbitCount.intValue();
		float orbitR = orbitRadius.floatValue() * (1 + 0.5F * moveK);
		float orbitW = orbitSpeed.floatValue() * (1.2F + 2.5F * moveK);
		if (body && orbitals.is("Orbs")) {
			for (int i = 0; i < n; i++) {
				float hf = orbitHeight(i, t);
				float ang = t * orbitW + i * Mth.TWO_PI / n;
				batch.billboard(f.rx(px + Mth.cos(ang) * orbitR), f.ry(py + bb * hf), f.rz(pz + Mth.sin(ang) * orbitR), 0.17F,
					ColorUtil.withAlpha(color(hf, t), 0.7F * a));
			}
		}
		if (showEyes) {
			int ec = eyeColorNow(t);
			batch.billboard(f.rx(eye[0]), f.ry(eye[1]), f.rz(eye[2]), 0.1F, ColorUtil.withAlpha(ec, 0.6F * eyeAlpha));
			batch.billboard(f.rx(eye[3]), f.ry(eye[4]), f.rz(eye[5]), 0.1F, ColorUtil.withAlpha(ec, 0.6F * eyeAlpha));
		}
		batch.end(NebulaPipelines.GLOW_SOFT, 0, 0, 0);

		// Magic circle.
		if (magicCircle.isOn() && groundFade > 0) {
			float jump = jumpTime == 0 ? 0 : 0.45F * (float) Math.exp(-(now - jumpTime) / 1e9F * 4);
			float r = circleRadius.floatValue() * (1 + 0.35F * sprintK + jump);
			float rot = (t * 0.6F * (1 + 1.5F * sprintK)) % (10 * Mth.PI);
			batch.begin(f);
			batch.flat(f.rx(px), f.ry(gy + 0.005), f.rz(pz), r / 0.86F, ColorUtil.withAlpha(color(0.2F, t), Math.min(1, 0.85F * a) * groundFade));
			batch.end(NebulaPipelines.GLOW_RUNE, rot, 0.022F, 1);
		}

		// Ring pass: rune orbitals and the totem halo.
		boolean halo = items.isOn() && totem && body;
		if ((body && orbitals.is("Runes")) || halo) {
			batch.begin(f);
			if (body && orbitals.is("Runes")) {
				for (int i = 0; i < n; i++) {
					float hf = orbitHeight(i, t);
					float ang = t * orbitW + i * Mth.TWO_PI / n;
					batch.billboard(f.rx(px + Mth.cos(ang) * orbitR), f.ry(py + bb * hf), f.rz(pz + Mth.sin(ang) * orbitR), 0.11F,
						ColorUtil.withAlpha(color(hf, t), Math.min(1, a)));
				}
			}
			if (halo) {
				float hy = (float) (py + bb * 0.55 + Mth.sin(t * 2) * 0.05);
				batch.flat(f.rx(px), f.ry(hy), f.rz(pz), 0.75F / 0.6F, ColorUtil.withAlpha(GOLD, 0.9F));
			}
			batch.end(NebulaPipelines.GLOW_RING, 0.6F, 0.1F, 0.3F);
		}

		// Spark pass: spark orbitals with comet tails, orb cores, eye cores, totem sparkles.
		batch.begin(f);
		if (body && (orbitals.is("Sparks") || orbitals.is("Orbs"))) {
			boolean sparks = orbitals.is("Sparks");
			for (int i = 0; i < n; i++) {
				float hf = orbitHeight(i, t);
				int c = color(hf, t);
				int tail = sparks ? 4 : 1;
				for (int k = 0; k < tail; k++) {
					float ang = t * orbitW + i * Mth.TWO_PI / n - k * 0.13F;
					float fade = k == 0 ? 1 : 0.5F / k;
					batch.billboard(f.rx(px + Mth.cos(ang) * orbitR), f.ry(py + bb * hf), f.rz(pz + Mth.sin(ang) * orbitR),
						(sparks ? 0.075F : 0.05F) * (k == 0 ? 1 : 0.8F), ColorUtil.withAlpha(c, Math.min(1, fade * a)));
				}
			}
		}
		if (showEyes) {
			int ec = eyeColorNow(t);
			batch.billboard(f.rx(eye[0]), f.ry(eye[1]), f.rz(eye[2]), 0.035F, ColorUtil.withAlpha(ec, eyeAlpha));
			batch.billboard(f.rx(eye[3]), f.ry(eye[4]), f.rz(eye[5]), 0.035F, ColorUtil.withAlpha(ec, eyeAlpha));
		}
		if (halo) {
			float hy = (float) (py + bb * 0.55 + Mth.sin(t * 2) * 0.05);
			for (int i = 0; i < 4; i++) {
				float ang = t * 1.4F + i * Mth.HALF_PI;
				batch.billboard(f.rx(px + Mth.cos(ang) * 0.75), f.ry(hy), f.rz(pz + Mth.sin(ang) * 0.75), 0.07F, ColorUtil.withAlpha(GOLD, 0.9F));
			}
		}
		batch.end(NebulaPipelines.GLOW_SPARK, 0, 0, 0);
	}

	private float orbitHeight(int i, float t) {
		return 0.5F + 0.35F * Mth.sin(t * 0.9F + i * 1.7F);
	}

	private float eyeAlpha;

	private int eyeColorNow(float t) {
		return customEyes.isOn() ? eyeColor.argb() : ColorUtil.brighten(color(1, t), 0.25F);
	}

	/** Positions both eyes on the face from the interpolated head yaw / pitch. Third person only. */
	private boolean computeEyes(Minecraft mc, LocalPlayer p, float pt, double py, boolean fp) {
		if (!eyes.isOn() || fp || (p.getPose() != Pose.STANDING && p.getPose() != Pose.CROUCHING) || mc.level == null) {
			return false;
		}
		float dark = 1;
		if (eyesDarkOnly.isOn()) {
			int light = mc.level.getMaxLocalRawBrightness(BlockPos.containing(p.getEyePosition()));
			dark = Mth.clamp((11 - light) / 6F, 0, 1);
		}
		if (dark <= 0) {
			return false;
		}
		eyeAlpha = Math.min(1, dark * intensity.floatValue());
		float yaw = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot) * Mth.DEG_TO_RAD;
		float pitch = Mth.lerp(pt, p.xRotO, p.getXRot()) * Mth.DEG_TO_RAD;
		float s = p.getScale();
		double nx = Mth.lerp(pt, p.xo, p.getX());
		double ny = py + (p.isCrouching() ? 1.16 : 1.41) * s;
		double nz = Mth.lerp(pt, p.zo, p.getZ());
		float fx = -Mth.sin(yaw) * Mth.cos(pitch), fy = -Mth.sin(pitch), fz = Mth.cos(yaw) * Mth.cos(pitch);
		float ux = -Mth.sin(yaw) * Mth.sin(pitch), uy = Mth.cos(pitch), uz = Mth.cos(yaw) * Mth.sin(pitch);
		float rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
		float up = 0.205F * s, fwd = 0.255F * s;
		for (int e = 0; e < 2; e++) {
			float side = (e == 0 ? -0.117F : 0.117F) * s;
			eye[e * 3] = (float) (nx + rx * side + ux * up + fx * fwd);
			eye[e * 3 + 1] = (float) (ny + uy * up + fy * fwd);
			eye[e * 3 + 2] = (float) (nz + rz * side + uz * up + fz * fwd);
		}
		return true;
	}

	private void spawnMist(double px, double py, double pz, float bb, float dt, float moveK, long now) {
		mistAcc += dt * mistDensity.floatValue() * (6 + 18 * (1 - moveK));
		while (mistAcc >= 1) {
			mistAcc -= 1;
			int i = mNext;
			mNext = (mNext + 1) % MIST_MAX;
			float ang = random.nextFloat() * Mth.TWO_PI;
			float rr = 0.12F + random.nextFloat() * 0.25F;
			float h = random.nextFloat() * 0.95F;
			mx[i] = px + Mth.cos(ang) * rr;
			my[i] = py + bb * h;
			mz[i] = pz + Mth.sin(ang) * rr;
			mH[i] = h;
			mLife[i] = 1.2F + random.nextFloat() * 0.8F;
			mSize[i] = 0.14F + random.nextFloat() * 0.1F;
			mBorn[i] = now;
		}
	}

	private void renderMist(WorldFrame f, GlowBatch batch, long now, float t, float a) {
		for (int i = 0; i < MIST_MAX; i++) {
			if (mBorn[i] == 0) {
				continue;
			}
			float age = (now - mBorn[i]) / 1e9F;
			float tt = age / mLife[i];
			if (tt >= 1) {
				mBorn[i] = 0;
				continue;
			}
			double x = mx[i] + Mth.sin(age * 2 + i) * 0.05;
			double y = my[i] + age * 0.5;
			double z = mz[i] + Mth.cos(age * 2 + i) * 0.05;
			batch.billboard(f.rx(x), f.ry(y), f.rz(z), mSize[i] * (1 + 1.8F * tt),
				ColorUtil.withAlpha(color(mH[i], t), 0.2F * Mth.sin(Mth.PI * tt) * Math.min(1, a)));
		}
	}
}
