package com.nebula.client.modules.visual;

import com.nebula.client.gui.themes.Theme;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.rendering.world.GlowBatch;
import com.nebula.client.rendering.world.SkyPass;
import com.nebula.client.rendering.world.WorldFrame;
import com.nebula.client.rendering.world.WorldRenderable;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Custom sun and moon drawn exactly where the vanilla ones are. The discs are rendered on the far
 * plane based on the view angle, so distant terrain still hides them correctly. Works with or
 * without Custom Sky: without it the glow enhances the vanilla sun, with it the celestial bodies
 * come back over the procedural sky.
 */
public final class Celestial extends Module implements WorldRenderable {
	public final ModeSetting sunStyle = add(new ModeSetting("Sun style", "Look of the sun", "Radiant", "Radiant", "Soft", "Hidden"));
	public final ColorSetting sunColor = add(new ColorSetting("Sun color", "Colour of the sun disc", 0xFFFFCE7A));
	public final NumberSetting sunSize = add(new NumberSetting("Sun size", "Apparent size in the sky", 15, 6, 30, 1, "deg"));
	public final NumberSetting sunGlow = add(new NumberSetting("Sun glow", "Disc and halo brightness", 1.0, 0.2, 2, 0.05));
	public final NumberSetting rays = add(new NumberSetting("Rays", "Slowly rotating flare rays", 0.8, 0, 2, 0.05))
			.visibleWhen(() -> sunStyle.is("Radiant"));
	public final NumberSetting raySpeed = add(new NumberSetting("Ray speed", "How fast the flares rotate", 1.0, 0, 2, 0.05))
			.visibleWhen(() -> sunStyle.is("Radiant"));
	public final ModeSetting moonStyle = add(new ModeSetting("Moon style", "Look of the moon", "Phases", "Phases", "Full", "Hidden"));
	public final ColorSetting moonColor = add(new ColorSetting("Moon color", "Colour of the moon disc", 0xFFD6E6FF));
	public final NumberSetting moonSize = add(new NumberSetting("Moon size", "Apparent size in the sky", 11, 6, 30, 1, "deg"));
	public final NumberSetting moonGlow = add(new NumberSetting("Moon glow", "Disc and rim brightness", 1.0, 0.2, 2, 0.05));

	private final Vector4f modulator = new Vector4f();
	private final Vector3f offset = new Vector3f();
	private final Matrix4f colors = new Matrix4f();
	private final Vector4f column = new Vector4f();

	public Celestial() {
		super("celestial", "Celestial Bodies", "Stylised sun and moon with flare rays and phases", Category.SKY);
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		var level = Minecraft.getInstance().level;
		if (level == null || !level.dimensionType().hasSkyLight()) {
			return;
		}
		boolean sunHidden = sunStyle.is("Hidden");
		boolean moonHidden = moonStyle.is("Hidden");
		if (sunHidden && moonHidden) {
			return;
		}
		// The discs fade out in rain, like the vanilla sun.
		float rain = 1F - level.getRainLevel(f.partialTick()) * 0.85F;
		long dayTime = level.getDayTime();
		float fraction = Math.floorMod(dayTime, 24000L) / 24000F; // noon = 0.25, matches the shader
		int phase = (int) Math.floorMod(dayTime / 24000L, 8L); // 0 = full moon
		float moonPhase = moonStyle.is("Phases") ? phase / 7F : 0F;

		colors.setColumn(0, rgb(sunColor.argb()));
		colors.setColumn(1, rgb(moonColor.argb()));
		// Rays mix the sun colour with the current theme accent, so the sky follows the UI palette.
		colors.setColumn(2, rgb(ColorUtil.mix(sunColor.argb(), Theme.current().accent(), 0.5F)));
		column.set(moonSize.floatValue() / 30F, raySpeed.floatValue(), 0, 1);
		colors.setColumn(3, column);
		modulator.set(sunHidden ? 0 : sunGlow.floatValue() * rain, moonHidden ? 0 : moonGlow.floatValue() * rain,
				sunHidden ? 0 : rays.floatValue() * rain, f.seconds());
		offset.set(fraction, (float) Math.cos(Math.toRadians(sunSize.floatValue())), moonPhase);
		SkyPass.draw(NebulaPipelines.CELESTIAL, f, modulator, offset, colors);
	}

	private Vector4f rgb(int c) {
		return column.set(ColorUtil.red(c), ColorUtil.green(c), ColorUtil.blue(c), 1);
	}
}
