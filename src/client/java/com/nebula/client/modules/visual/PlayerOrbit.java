package com.nebula.client.modules.visual;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.world.OrbFrame;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class PlayerOrbit extends Module {
	public final NumberSetting count = add(new NumberSetting("Count", "Number of orbs", 5, 1, OrbFrame.MAX, 1));
	public final NumberSetting size = add(new NumberSetting("Size", "Orb radius in blocks", 0.09, 0.03, 0.4, 0.01));
	public final ModeSetting trajectory = add(new ModeSetting("Trajectory", "Orbit shape", "Atom", "Circle", "Ellipse", "Atom", "Helix", "Lissajous"));
	public final NumberSetting speed = add(new NumberSetting("Speed", "Revolutions per second", 0.35, 0, 2, 0.01));
	public final NumberSetting speedVariance = add(new NumberSetting("Speed variance", "How differently each orb moves", 0.3, 0, 1, 0.05));
	public final NumberSetting distance = add(new NumberSetting("Distance", "Orbit radius", 1.0, 0.3, 3, 0.05, "m"));
	public final NumberSetting height = add(new NumberSetting("Height", "Orbit height above the feet", 1.0, 0, 2.5, 0.05, "m"));
	public final NumberSetting heightSpread = add(new NumberSetting("Height spread", "Vertical offset between orbs", 0.3, 0, 1, 0.05, "m"));
	public final NumberSetting bob = add(new NumberSetting("Bob", "Floating amplitude", 0.08, 0, 0.5, 0.01, "m"));
	public final ModeSetting colorMode = add(new ModeSetting("Color mode", "How orbs are coloured", "Gradient", "Single", "Gradient", "Rainbow"));
	public final ColorSetting color = add(new ColorSetting("Color", "Primary orb colour", 0xFF4D8DFF));
	public final ColorSetting color2 = add(new ColorSetting("Color 2", "Second gradient colour", 0xFFA45CFF))
		.visibleWhen(() -> colorMode.is("Gradient"));
	public final NumberSetting intensity = add(new NumberSetting("Intensity", "Surface emission strength", 1.4, 0.2, 3, 0.05));
	public final NumberSetting halo = add(new NumberSetting("Halo", "Halo brightness (0 disables)", 0.9, 0, 2, 0.05));
	public final NumberSetting haloSize = add(new NumberSetting("Halo size", "Halo radius relative to the orb", 3.5, 1.5, 8, 0.1, "x"));
	public final BoolSetting bloom = add(new BoolSetting("Bloom", "Screen-space bloom pass on the orb emission", true));
	public final NumberSetting bloomStrength = add(new NumberSetting("Bloom strength", "Bloom intensity", 1.1, 0, 3, 0.05))
		.visibleWhen(bloom::isOn);
	public final NumberSetting bloomRadius = add(new NumberSetting("Bloom radius", "Bloom spread", 6, 1, 14, 0.5))
		.visibleWhen(bloom::isOn);
	public final BoolSetting firstPerson = add(new BoolSetting("First person", "Also render in first-person view", false));

	private final long start = System.nanoTime();

	public PlayerOrbit() {
		super("player_orbit", "Player Orbit", "Glowing 3D orbs gravitating around you", Category.VISUAL, true, false);
	}

	/** Fills {@code out} with camera-relative orb positions; returns false when nothing should be drawn. */
	public boolean compute(OrbFrame out, Vec3 camera, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || player.isSpectator() || (mc.options.getCameraType().isFirstPerson() && !firstPerson.isOn())) {
			out.count = 0;
			return false;
		}
		float time = (System.nanoTime() - start) / 1_000_000_000F;
		double px = Mth.lerp(partialTick, player.xo, player.getX());
		double py = Mth.lerp(partialTick, player.yo, player.getY());
		double pz = Mth.lerp(partialTick, player.zo, player.getZ());
		int n = Math.min(count.intValue(), OrbFrame.MAX);
		float r = distance.floatValue();
		String mode = trajectory.get();
		for (int i = 0; i < n; i++) {
			float slot = (float) i / n;
			// Deterministic per-orb speed factor so orbs drift relative to each other.
			float variance = 1 + speedVariance.floatValue() * ((i * 0.618034F) % 1F - 0.5F);
			float phase = (float) (Mth.TWO_PI * (slot + time * speed.floatValue() * variance));
			float ox, oy, oz;
			switch (mode) {
				case "Circle" -> {
					ox = Mth.cos(phase) * r;
					oy = 0;
					oz = Mth.sin(phase) * r;
				}
				case "Ellipse" -> {
					ox = Mth.cos(phase) * r * 1.35F;
					oy = Mth.sin(phase * 2) * 0.12F;
					oz = Mth.sin(phase) * r * 0.7F;
				}
				case "Helix" -> {
					ox = Mth.cos(phase) * r;
					oz = Mth.sin(phase) * r;
					oy = Mth.sin(phase * 0.5F + slot * Mth.TWO_PI) * 0.6F;
				}
				case "Lissajous" -> {
					ox = Mth.sin(phase) * r;
					oz = Mth.sin(phase * 2) * r * 0.55F;
					oy = Mth.cos(phase * 3) * 0.25F;
				}
				default -> {
					// Atom: each orb on its own ring, rings tilted evenly around the vertical axis.
					float cx = Mth.cos(phase) * r;
					float cz = Mth.sin(phase) * r;
					float tilt = 1.0F;
					float ringAngle = slot * Mth.PI;
					float ty = -cz * Mth.sin(tilt);
					float tz = cz * Mth.cos(tilt);
					ox = cx * Mth.cos(ringAngle) - tz * Mth.sin(ringAngle);
					oz = cx * Mth.sin(ringAngle) + tz * Mth.cos(ringAngle);
					oy = ty * 0.8F;
				}
			}
			float spread = (i % 3 - 1) * heightSpread.floatValue();
			if (mode.equals("Atom")) {
				spread *= 0.3F;
			}
			oy += height.floatValue() + spread + Mth.sin(time * 1.9F + i * 1.3F) * bob.floatValue();
			out.x[i] = (float) (px + ox - camera.x);
			out.y[i] = (float) (py + oy - camera.y);
			out.z[i] = (float) (pz + oz - camera.z);
			out.radius[i] = size.floatValue() * (1 + 0.06F * Mth.sin(time * 3.1F + i));
			out.color[i] = orbColor(i, n, time);
		}
		out.count = n;
		out.time = time;
		out.intensity = intensity.floatValue();
		out.haloIntensity = halo.floatValue();
		out.haloScale = haloSize.floatValue();
		out.bloom = bloom.isOn();
		out.bloomStrength = bloomStrength.floatValue();
		out.bloomRadius = bloomRadius.floatValue();
		return n > 0;
	}

	private int orbColor(int i, int n, float time) {
		return switch (colorMode.get()) {
			case "Gradient" -> ColorUtil.mix(color.argb(), color2.argb(), n <= 1 ? 0 : (float) i / (n - 1));
			case "Rainbow" -> ColorUtil.hsv(((float) i / n + time * 0.08F) % 1F, 0.75F, 1F, 255);
			default -> color.argb();
		};
	}
}
