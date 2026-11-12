package com.nebula.client.modules.visual;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.rendering.world.GlowBatch;
import com.nebula.client.rendering.world.SkyPass;
import com.nebula.client.rendering.world.WorldFrame;
import com.nebula.client.rendering.world.WorldRenderable;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Replaces the visible sky with a procedural starfield, nebula band and shooting stars. */
public final class CustomSky extends Module implements WorldRenderable {
	private record Preset(int zenith, int horizon, int nebulaA, int nebulaB) {
	}

	public final ModeSetting preset = add(new ModeSetting("Preset", "Sky palette", "Nebula", "Nebula", "Twilight", "Deep Space", "Aurora Night", "Custom"));
	public final ColorSetting zenith = add(new ColorSetting("Zenith", "Colour at the top of the sky", 0xFF070B1F))
		.visibleWhen(() -> preset.is("Custom"));
	public final ColorSetting horizon = add(new ColorSetting("Horizon", "Colour at the horizon", 0xFF2A1E5C))
		.visibleWhen(() -> preset.is("Custom"));
	public final ColorSetting nebulaA = add(new ColorSetting("Nebula 1", "First nebula colour", 0xFF6A3DFF))
		.visibleWhen(() -> preset.is("Custom"));
	public final ColorSetting nebulaB = add(new ColorSetting("Nebula 2", "Second nebula colour", 0xFF2EC5FF))
		.visibleWhen(() -> preset.is("Custom"));
	public final NumberSetting stars = add(new NumberSetting("Stars", "Star density", 0.4, 0, 1, 0.05));
	public final NumberSetting nebula = add(new NumberSetting("Nebula", "Nebula band strength", 0.8, 0, 1.5, 0.05));
	public final NumberSetting rotation = add(new NumberSetting("Rotation", "Slow rotation of the starfield", 0.3, 0, 2, 0.05));
	public final BoolSetting shootingStars = add(new BoolSetting("Shooting stars", "Occasional meteors", true));
	public final ModeSetting visibility = add(new ModeSetting("Visible", "When the custom sky shows", "Always", "Always", "Night only"));
	public final NumberSetting opacity = add(new NumberSetting("Opacity", "Blend over the vanilla sky", 1.0, 0.1, 1, 0.05));
	public final NumberSetting fogMatch = add(new NumberSetting("Fog match", "Blend the horizon into the world fog", 0.55, 0, 1, 0.05));
	public final BoolSetting hideClouds = add(new BoolSetting("Hide clouds", "Hide vanilla clouds while the custom sky is visible", true));

	public boolean shouldHideClouds() {
		if (!isEnabled() || !hideClouds.isOn() || !hasSky()) {
			return false;
		}
		var level = Minecraft.getInstance().level;
		return !visibility.is("Night only") || SkyPass.nightFactor(level.getDayTime()) > 0.5F;
	}

	private final Vector4f modulator = new Vector4f();
	private final Vector3f offset = new Vector3f();
	private final Matrix4f colors = new Matrix4f();
	private final Vector4f column = new Vector4f();

	public CustomSky() {
		super("custom_sky", "Custom Sky", "Procedural starfield, nebula and shooting stars", Category.VISUAL);
	}

	private Preset palette() {
		return switch (preset.get()) {
			case "Twilight" -> new Preset(0xFF1B1446, 0xFFFF7A59, 0xFFFF4FA3, 0xFF7A5CFF);
			case "Deep Space" -> new Preset(0xFF010205, 0xFF0A1024, 0xFF3050FF, 0xFFA040FF);
			case "Aurora Night" -> new Preset(0xFF020A12, 0xFF0B2A33, 0xFF1FD8A0, 0xFF3A7BFF);
			case "Custom" -> new Preset(zenith.argb(), horizon.argb(), nebulaA.argb(), nebulaB.argb());
			default -> new Preset(0xFF070B1F, 0xFF2A1E5C, 0xFF6A3DFF, 0xFF2EC5FF);
		};
	}

	static boolean hasSky() {
		var level = Minecraft.getInstance().level;
		return level != null && level.dimensionType().hasSkyLight();
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		var level = Minecraft.getInstance().level;
		if (!hasSky()) {
			return;
		}
		float vis = visibility.is("Night only") ? SkyPass.nightFactor(level.getDayTime()) : 1;
		float alpha = opacity.floatValue() * vis;
		if (alpha < 0.01F) {
			return;
		}
		Preset p = palette();
		colors.setColumn(0, rgb(p.zenith()));
		colors.setColumn(1, rgb(p.horizon()));
		colors.setColumn(2, rgb(p.nebulaA()));
		colors.setColumn(3, rgb(p.nebulaB()));
		modulator.set(alpha, stars.floatValue(), nebula.floatValue(), f.seconds());
		offset.set(fogMatch.floatValue(), rotation.floatValue(), shootingStars.isOn() ? 1 : 0);
		SkyPass.draw(NebulaPipelines.SKY, f, modulator, offset, colors);
	}

	private Vector4f rgb(int c) {
		return column.set(ColorUtil.red(c), ColorUtil.green(c), ColorUtil.blue(c), 1);
	}
}
