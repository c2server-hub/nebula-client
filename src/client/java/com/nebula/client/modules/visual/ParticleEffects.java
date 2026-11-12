package com.nebula.client.modules.visual;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Client-side decorative particles; they only exist locally and are never sent to the server. */
public final class ParticleEffects extends Module {
	public final ModeSetting type = add(new ModeSetting("Type", "Particle kind", "Stardust", "Stardust", "Aura", "Sparkle", "Glow", "Electric", "Soul"));
	public final ModeSetting pattern = add(new ModeSetting("Pattern", "Where particles spawn", "Aura", "Aura", "Feet", "Spiral", "Moving only"));
	public final NumberSetting density = add(new NumberSetting("Density", "Particles per tick", 3, 1, 20, 1));
	public final NumberSetting radius = add(new NumberSetting("Radius", "Spawn radius", 0.8, 0.2, 2.5, 0.05, "m"));
	public final ColorSetting color = add(new ColorSetting("Color", "Tint for colourable types", 0xFF7AA7FF))
		.visibleWhen(this::colorable);
	public final BoolSetting rainbow = add(new BoolSetting("Rainbow", "Cycle hue over time", false))
		.visibleWhen(this::colorable);
	public final NumberSetting scale = add(new NumberSetting("Scale", "Dust particle size", 0.8, 0.2, 2, 0.05))
		.visibleWhen(() -> type.is("Stardust"));

	private final RandomSource random = RandomSource.create();
	private int ticks;

	public ParticleEffects() {
		super("particle_effects", "Particle Effects", "Decorative particles around your character", Category.VISUAL);
	}

	private boolean colorable() {
		return type.is("Stardust") || type.is("Aura");
	}

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return;
		}
		ticks++;
		if (pattern.is("Moving only") && player.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4) {
			return;
		}
		int n = density.intValue();
		float r = radius.floatValue();
		for (int i = 0; i < n; i++) {
			double ox, oy, oz;
			switch (pattern.get()) {
				case "Feet" -> {
					float a = random.nextFloat() * Mth.TWO_PI;
					float d = r * Mth.sqrt(random.nextFloat());
					ox = Mth.cos(a) * d;
					oy = 0.05;
					oz = Mth.sin(a) * d;
				}
				case "Spiral" -> {
					float a = (ticks * 0.35F + i * Mth.TWO_PI / n);
					ox = Mth.cos(a) * r;
					oy = (ticks * 0.05 + (double) i / n) % player.getBbHeight();
					oz = Mth.sin(a) * r;
				}
				default -> {
					float a = random.nextFloat() * Mth.TWO_PI;
					ox = Mth.cos(a) * r;
					oy = random.nextFloat() * player.getBbHeight();
					oz = Mth.sin(a) * r;
				}
			}
			mc.level.addParticle(options(), player.getX() + ox, player.getY() + oy, player.getZ() + oz,
				(random.nextFloat() - 0.5F) * 0.02, 0.01 + random.nextFloat() * 0.02, (random.nextFloat() - 0.5F) * 0.02);
		}
	}

	private ParticleOptions options() {
		int c = rainbow.isOn() ? ColorUtil.hsv((ticks * 0.01F) % 1F, 0.7F, 1, 255) : color.argb();
		return switch (type.get()) {
			case "Aura" -> ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, c);
			case "Sparkle" -> ParticleTypes.END_ROD;
			case "Glow" -> ParticleTypes.GLOW;
			case "Electric" -> ParticleTypes.ELECTRIC_SPARK;
			case "Soul" -> ParticleTypes.SOUL_FIRE_FLAME;
			default -> new DustParticleOptions(c & 0xFFFFFF, scale.floatValue());
		};
	}
}
