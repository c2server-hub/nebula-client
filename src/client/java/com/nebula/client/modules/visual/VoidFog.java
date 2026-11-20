package com.nebula.client.modules.visual;

import com.nebula.client.gui.themes.Theme;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.GlowRenderer;
import com.nebula.client.rendering.Gradient;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;

/**
 * Atmospheric screen fog: violet void mist rising from the depths below Y 0, an otherworldly
 * haze in the End and an ember glow in the Nether. Everything is drawn with analytic gaussian
 * SDFs, so the fog stays perfectly soft and costs a handful of quads.
 */
public final class VoidFog extends Module {
	private static final float FAR = 4000;

	public final ModeSetting trigger = add(new ModeSetting("Trigger", "Where the fog appears", "Auto", "Auto", "Always"));
	public final NumberSetting strength = add(new NumberSetting("Strength", "Fog density", 0.7, 0.1, 1.5, 0.05));
	public final NumberSetting reach = add(new NumberSetting("Reach", "How far the fog climbs on screen", 120, 40, 300, 5, "px"));
	public final BoolSetting endMist = add(new BoolSetting("End mist", "Purple haze in the End", true));
	public final BoolSetting netherHaze = add(new BoolSetting("Nether haze", "Ember glow in the Nether", true));
	public final ModeSetting tint = add(new ModeSetting("Tint", "Fog colour", "Auto", "Auto", "Theme", "Custom"));
	public final ColorSetting color = add(new ColorSetting("Color", "Custom fog colour", 0xFF160B2E))
			.visibleWhen(() -> tint.is("Custom"));

	public VoidFog() {
		super("void_fog", "Void Fog", "Void mist below Y 0, End haze and Nether embers", Category.WORLD);
	}

	public void render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		var level = mc.level;
		if (p == null || level == null || mc.options.hideGui) {
			return;
		}
		float intensity;
		boolean nether = false;
		if (trigger.is("Always")) {
			intensity = 1;
		} else if (level.dimension() == Level.END) {
			if (!endMist.isOn()) {
				return;
			}
			intensity = 0.6F;
		} else if (level.dimension() == Level.NETHER) {
			if (!netherHaze.isOn()) {
				return;
			}
			intensity = 0.55F;
			nether = true;
		} else if (p.getY() < 0) {
			// The deeper below Y 0, the thicker; full strength at Y -45.
			intensity = Math.min(1, (float) (-p.getY() / 45));
		} else {
			return;
		}

		int auto = nether ? 0xFF38130A : (level.dimension() == Level.END ? 0xFF2A1040 : 0xFF140A28);
		int c = switch (tint.get()) {
			case "Theme" -> Theme.current().accent();
			case "Custom" -> color.argb();
			default -> auto;
		};
		float now = System.nanoTime() / 1e9F;
		// Slow breathing so the fog feels alive instead of glued on.
		float breathe = 0.86F + 0.14F * (float) Math.sin(now * 0.55F);
		float a = Math.min(1, strength.floatValue() * intensity * breathe);
		float soft = reach.floatValue();
		int w = g.guiWidth(), h = g.guiHeight();

		// Fog climbing up from the bottom edge.
		GlowRenderer.softRect(g, -FAR, h - soft * 0.12F, w + 2 * FAR, FAR, soft, ColorUtil.withAlpha(c, 0.85F * a), 1.6F);
		// Side banks, a step fainter.
		GlowRenderer.softRect(g, -FAR, -FAR, FAR, h + 2 * FAR, soft * 0.8F, ColorUtil.withAlpha(c, 0.5F * a), 1.2F);
		GlowRenderer.softRect(g, w, -FAR, FAR, h + 2 * FAR, soft * 0.8F, ColorUtil.withAlpha(c, 0.5F * a), 1.2F);
		// A faint ceiling wisp in enclosed dimensions.
		if (nether || level.dimension() == Level.END) {
			GlowRenderer.softRect(g, -FAR, -FAR, w + 2 * FAR, FAR, soft * 0.7F, ColorUtil.withAlpha(c, 0.3F * a), 1);
		}
		// Full-screen vertical gradient settles the mood without hiding the world.
		RoundedRenderer.gradient(g, 0, 0, w, h, 0, Gradient.vertical(ColorUtil.withAlpha(c, 0), ColorUtil.withAlpha(c, 0.22F * a)));
	}
}
