package com.nebula.client.modules.visual;

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

/** Ray-marched aurora curtains drawn additively over the sky. */
public final class Aurora extends Module implements WorldRenderable {
	public final ModeSetting palette = add(new ModeSetting("Palette", "Aurora colours", "Borealis", "Borealis", "Violet", "Fire", "Theme", "Custom"));
	public final ColorSetting low = add(new ColorSetting("Low color", "Lower edge of the curtains", 0xFF2DFF9A))
		.visibleWhen(() -> palette.is("Custom"));
	public final ColorSetting mid = add(new ColorSetting("Mid color", "Middle of the curtains", 0xFF30C8FF))
		.visibleWhen(() -> palette.is("Custom"));
	public final ColorSetting top = add(new ColorSetting("Top color", "Upper fringes", 0xFFB45CFF))
		.visibleWhen(() -> palette.is("Custom"));
	public final NumberSetting intensity = add(new NumberSetting("Intensity", "Brightness", 1.2, 0.2, 3, 0.05));
	public final NumberSetting speed = add(new NumberSetting("Speed", "Movement speed", 1.0, 0, 4, 0.05, "x"));
	public final NumberSetting height = add(new NumberSetting("Height", "Altitude of the curtains", 1.0, 0.4, 2, 0.05));
	public final NumberSetting quality = add(new NumberSetting("Quality", "Number of layers (higher is smoother but slower)", 24, 8, 64, 2));
	public final ModeSetting visibility = add(new ModeSetting("Visible", "When the aurora shows", "Always", "Always", "Night only"));

	private final Vector4f modulator = new Vector4f();
	private final Vector3f offset = new Vector3f();
	private final Matrix4f colors = new Matrix4f();
	private final Vector4f column = new Vector4f();

	public Aurora() {
		super("aurora", "Aurora", "Animated northern lights in the sky", Category.VISUAL);
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		var level = Minecraft.getInstance().level;
		if (!CustomSky.hasSky()) {
			return;
		}
		float vis = visibility.is("Night only") ? SkyPass.nightFactor(level.getDayTime()) : 1;
		vis *= 1 - 0.8F * level.getRainLevel(f.partialTick());
		if (vis < 0.01F) {
			return;
		}
		int a, b, c;
		switch (palette.get()) {
			case "Violet" -> {
				a = 0xFF7A5CFF;
				b = 0xFFC05CFF;
				c = 0xFFFF5CC8;
			}
			case "Fire" -> {
				a = 0xFFFF3D3D;
				b = 0xFFFF8A3D;
				c = 0xFFFFD06B;
			}
			case "Theme" -> {
				var t = com.nebula.client.gui.themes.Theme.current();
				a = t.accent();
				b = t.accentMix(0.5F);
				c = t.accent2();
			}
			case "Custom" -> {
				a = low.argb();
				b = mid.argb();
				c = top.argb();
			}
			default -> {
				a = 0xFF2DFF9A;
				b = 0xFF30C8FF;
				c = 0xFFB45CFF;
			}
		}
		colors.setColumn(0, rgb(a));
		colors.setColumn(1, rgb(b));
		colors.setColumn(2, rgb(c));
		modulator.set(intensity.floatValue() * vis, speed.floatValue(), height.floatValue(), f.seconds());
		offset.set(quality.floatValue(), 0, 0);
		SkyPass.draw(NebulaPipelines.AURORA, f, modulator, offset, colors);
	}

	private Vector4f rgb(int c) {
		return column.set(ColorUtil.red(c), ColorUtil.green(c), ColorUtil.blue(c), 1);
	}
}
