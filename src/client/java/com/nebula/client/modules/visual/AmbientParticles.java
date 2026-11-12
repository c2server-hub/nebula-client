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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Glowing ambient motes floating around the player (fireflies, stardust, snow, embers).
 * Each mote's path is a closed-form function of its age, so it is frame-rate independent.
 */
public final class AmbientParticles extends Module implements WorldRenderable {
	private static final int MAX = 240;

	public final ModeSetting type = add(new ModeSetting("Type", "Kind of motes", "Fireflies", "Fireflies", "Stardust", "Snow", "Embers", "Spirits"));
	public final NumberSetting count = add(new NumberSetting("Count", "Number of motes", 70, 10, MAX, 5));
	public final NumberSetting radius = add(new NumberSetting("Radius", "Area around you", 10, 3, 24, 1, "m"));
	public final NumberSetting size = add(new NumberSetting("Size", "Mote size", 1.0, 0.3, 3, 0.05, "x"));
	public final NumberSetting brightness = add(new NumberSetting("Brightness", "Mote brightness", 1.0, 0.2, 2, 0.05));
	public final ModeSetting colorMode = add(new ModeSetting("Color mode", "Colours", "Auto", "Auto", "Theme", "Custom", "Rainbow"));
	public final ColorSetting color = add(new ColorSetting("Color", "Custom colour", 0xFF9BE15D))
		.visibleWhen(() -> colorMode.is("Custom"));

	private final RandomSource random = RandomSource.create();
	private final double[] bx = new double[MAX], by = new double[MAX], bz = new double[MAX];
	private final float[] phase = new float[MAX], life = new float[MAX], tone = new float[MAX];
	private final long[] born = new long[MAX];
	private String lastType = "";

	public AmbientParticles() {
		super("ambient_particles", "Ambient Particles", "Fireflies, stardust, snow or embers floating around you", Category.VISUAL);
	}

	@Override
	protected void onDisable() {
		java.util.Arrays.fill(born, 0);
	}

	private void spawn(int i, LocalPlayer p, boolean anywhereInLife) {
		float r = radius.floatValue();
		float a = random.nextFloat() * Mth.TWO_PI;
		float d = r * Mth.sqrt(random.nextFloat());
		bx[i] = p.getX() + Mth.cos(a) * d;
		bz[i] = p.getZ() + Mth.sin(a) * d;
		by[i] = switch (type.get()) {
			case "Snow" -> p.getY() + 4 + random.nextFloat() * 5;
			case "Embers" -> p.getY() - 0.5 + random.nextFloat() * 1.5;
			default -> p.getY() - 0.5 + random.nextFloat() * 4.5;
		};
		phase[i] = random.nextFloat() * 100;
		tone[i] = random.nextFloat();
		life[i] = 5 + random.nextFloat() * 7;
		// On (re)activation spread ages so motes do not all fade in at once.
		long ageOffset = anywhereInLife ? (long) (random.nextFloat() * life[i] * 0.8F * 1e9) : 0;
		born[i] = System.nanoTime() - ageOffset;
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) {
			return;
		}
		String kind = type.get();
		boolean reset = !kind.equals(lastType);
		lastType = kind;
		long now = System.nanoTime();
		int n = count.intValue();
		float r = radius.floatValue();
		float baseSize = 0.07F * size.floatValue();
		batch.begin(f);
		for (int i = 0; i < n; i++) {
			if (born[i] == 0 || reset) {
				spawn(i, p, true);
			}
			float age = (now - born[i]) / 1e9F;
			double dx = bx[i] - p.getX(), dz = bz[i] - p.getZ();
			if (age > life[i] || dx * dx + dz * dz > r * r * 1.6) {
				spawn(i, p, false);
				age = 0;
			}
			float ph = phase[i];
			double x = bx[i], y = by[i], z = bz[i];
			float flicker = 1;
			float s = baseSize;
			int c;
			switch (kind) {
				case "Stardust" -> {
					x += Mth.sin(age * 0.5F + ph) * 0.35F;
					y += age * 0.22F;
					z += Mth.cos(age * 0.45F + ph) * 0.35F;
					flicker = 0.6F + 0.4F * Mth.sin(age * 4 + ph);
					c = pick(tone[i], f.seconds(), Theme.current().accentMix(tone[i]));
				}
				case "Snow" -> {
					x += Mth.sin(age * 0.8F + ph) * 0.4F;
					y -= age * 0.7F;
					z += Mth.cos(age * 0.6F + ph) * 0.4F;
					s *= 1.3F;
					flicker = 0.55F;
					c = pick(tone[i], f.seconds(), 0xFFEFF6FF);
				}
				case "Embers" -> {
					x += Mth.sin(age * 1.3F + ph) * 0.25F;
					y += age * 0.55F;
					z += Mth.cos(age * 1.1F + ph) * 0.25F;
					s *= 0.8F;
					flicker = 0.55F + 0.45F * Mth.sin(age * 11 + ph * 3);
					c = pick(tone[i], f.seconds(), ColorUtil.mix(0xFFFF5A1F, 0xFFFFC44D, tone[i]));
				}
				case "Spirits" -> {
					float orbit = age * 0.35F + ph;
					x += Mth.cos(orbit) * 1.2F;
					z += Mth.sin(orbit) * 1.2F;
					y += Mth.sin(age * 0.7F + ph) * 0.6F;
					s *= 1.6F;
					flicker = 0.5F + 0.5F * Mth.sin(age * 1.7F + ph);
					c = pick(tone[i], f.seconds(), ColorUtil.mix(0xFF7FE7FF, 0xFFB98CFF, tone[i]));
				}
				default -> {
					// Fireflies wander on a smooth path and blink.
					x += Mth.sin(age * 0.6F + ph) * 0.9F + Mth.sin(age * 1.7F + ph * 2) * 0.2F;
					y += Mth.sin(age * 0.9F + ph * 1.3F) * 0.45F;
					z += Mth.cos(age * 0.5F + ph) * 0.9F + Mth.cos(age * 1.3F + ph * 3) * 0.2F;
					float blink = Mth.sin(age * 1.8F + ph);
					flicker = Mth.clamp((blink - 0.1F) / 0.9F, 0, 1);
					c = pick(tone[i], f.seconds(), ColorUtil.mix(0xFFC8FF5A, 0xFFFFE27A, tone[i]));
				}
			}
			float envelope = Math.min(1, age / 0.8F) * Math.min(1, (life[i] - age) / 1.2F);
			float alpha = Math.clamp(envelope * flicker * brightness.floatValue(), 0, 1);
			if (alpha < 0.02F) {
				continue;
			}
			batch.billboard(f.rx(x), f.ry(y), f.rz(z), s, ColorUtil.withAlpha(c, alpha));
		}
		batch.end(NebulaPipelines.GLOW_SPARK, 0, 0, 0);
	}

	private int pick(float t, float seconds, int auto) {
		return switch (colorMode.get()) {
			case "Theme" -> Theme.current().accentMix(t);
			case "Custom" -> color.argb();
			case "Rainbow" -> ColorUtil.hsv((t + seconds * 0.05F) % 1F, 0.65F, 1, 255);
			default -> auto;
		};
	}
}
