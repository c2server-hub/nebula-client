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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A little glowing wisp that accompanies you: it trails behind your shoulder while you explore,
 * orbits lazily around your head when you stand still, and wraps itself in sparkles. The follow
 * motion is a spring integrated on the client tick and interpolated with the frame's partial tick.
 */
public final class Companion extends Module implements WorldRenderable {
	private static final float RING_POS = 0.8F;

	public final NumberSetting size = add(new NumberSetting("Size", "Wisp size", 1.0, 0.5, 2.2, 0.05, "x"));
	public final NumberSetting followSpeed = add(new NumberSetting("Follow speed", "How eagerly it keeps up", 6, 2, 14, 0.5));
	public final NumberSetting distance = add(new NumberSetting("Distance", "Trailing distance", 1.0, 0.5, 2.5, 0.05, "m"));
	public final NumberSetting brightness = add(new NumberSetting("Brightness", "Core and halo glow", 1.0, 0.2, 2, 0.05));
	public final NumberSetting sparkles = add(new NumberSetting("Sparkles", "Orbiting sparks", 3, 0, 8, 1));
	public final BoolSetting firstPerson = add(new BoolSetting("First person", "Visible in first-person view", false));
	private final ColorChoice color = new ColorChoice(add(ColorChoice.modeSetting()), add(new ColorSetting("Color", "Custom wisp colour", 0xFF8FE8FF)));

	private double x, y, z, px, py, pz;
	private boolean initialised;

	public Companion() {
		super("companion", "Companion", "A little glowing wisp that follows you around", Category.PLAYER);
	}

	@Override
	protected void onEnable() {
		initialised = false;
	}

	@Override
	public void onTick() {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) {
			initialised = false;
			return;
		}
		double headY = p.getY() + p.getBbHeight() * 0.85;
		if (!initialised) {
			x = px = p.getX();
			y = py = headY + 0.4;
			z = pz = p.getZ();
			initialised = true;
		}
		px = x;
		py = y;
		pz = z;

		double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
		boolean moving = dx * dx + dz * dz > 0.003;
		double tx, ty, tz;
		float t = System.nanoTime() / 1e9F;
		if (moving) {
			// Hover behind the shoulder, against the look direction.
			Vec3 look = p.getLookAngle();
			double d = distance.floatValue();
			tx = p.getX() - look.x * d + Math.cos(t * 1.7F) * 0.12;
			ty = headY + 0.25 + Mth.sin(t * 2.1F) * 0.08;
			tz = p.getZ() - look.z * d + Math.sin(t * 1.7F) * 0.12;
		} else {
			// Idle: a slow orbit around the head.
			float a = t * 1.1F;
			float r = 0.72F;
			tx = p.getX() + Mth.cos(a) * r;
			ty = headY + 0.18 + Mth.sin(t * 0.9F) * 0.12;
			tz = p.getZ() + Mth.sin(a) * r;
		}
		// Critically damped-ish spring integrated at a fixed 20 Hz step.
		float k = 1 - (float) Math.exp(-followSpeed.floatValue() / 20F);
		x += (tx - x) * k;
		y += (ty - y) * k;
		z += (tz - z) * k;
		// Dimension change or teleport: snap instead of flying across the world.
		if (Math.abs(x - p.getX()) > 12 || Math.abs(z - p.getZ()) > 12) {
			x = px = p.getX();
			y = py = headY + 0.4;
			z = pz = p.getZ();
		}
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null || !initialised || (Minecraft.getInstance().options.getCameraType().isFirstPerson() && !firstPerson.isOn())) {
			return;
		}
		float pt = f.partialTick();
		float t = f.seconds();
		double ix = Mth.lerp(pt, px, x);
		double iy = Mth.lerp(pt, py, y) + Mth.sin(t * 2.8F) * 0.05;
		double iz = Mth.lerp(pt, pz, z);
		float s = size.floatValue();
		float b = Math.min(1.4F, brightness.floatValue());
		int core = ColorUtil.mix(color.color(0, t), 0xFFFFFFFF, 0.5F);

		batch.begin(f);
		// Wide halo, coloured core, white heart.
		batch.billboard(f.rx(ix), f.ry(iy), f.rz(iz), 0.34F * s, ColorUtil.withAlpha(color.color(0.3F, t), 0.25F * b));
		batch.billboard(f.rx(ix), f.ry(iy), f.rz(iz), 0.12F * s, ColorUtil.withAlpha(color.color(0, t), b));
		batch.billboard(f.rx(ix), f.ry(iy), f.rz(iz), 0.055F * s, ColorUtil.withAlpha(ColorUtil.brighten(core, 0.6F), Math.min(1, b)));
		int n = sparkles.intValue();
		for (int i = 0; i < n; i++) {
			float a = t * 2.2F + i * Mth.TWO_PI / Math.max(1, n);
			float wobble = Mth.sin(t * 3.1F + i * 1.9F) * 0.06F;
			batch.billboard(f.rx(ix + Mth.cos(a) * 0.26 * s), f.ry(iy + wobble), f.rz(iz + Mth.sin(a) * 0.26 * s),
					0.045F * s, ColorUtil.withAlpha(color.color((float) i / Math.max(1, n), t), 0.85F * b));
		}
		batch.end(NebulaPipelines.GLOW_SPARK, 0, 0, 0);

		// A soft ring pulses down to the ground every few seconds.
		float phase = (t % 2.8F) / 2.8F;
		float r = (0.14F + phase * 0.42F) * s;
		batch.begin(f);
		batch.flat(f.rx(ix), f.ry(p.getY() + 0.04), f.rz(iz), r / RING_POS,
				ColorUtil.withAlpha(color.color(0.15F, t), (1 - phase) * (1 - phase) * 0.3F * b));
		batch.end(NebulaPipelines.GLOW_RING, RING_POS, 0.16F, 0.2F);
	}
}
