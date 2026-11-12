package com.nebula.client.modules.visual;

import com.nebula.client.gui.themes.Theme;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.GlowRenderer;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

/** Cinematic screen-edge vignette (gaussian SDF falloff) with an optional low-health pulse. */
public final class Vignette extends Module {
	private static final float FAR = 4000;

	public final NumberSetting strength = add(new NumberSetting("Strength", "Darkness at the edges", 0.55, 0.05, 1, 0.05));
	public final NumberSetting size = add(new NumberSetting("Size", "How far the vignette reaches inwards", 90, 20, 250, 5, "px"));
	public final ModeSetting tint = add(new ModeSetting("Tint", "Vignette colour", "Black", "Black", "Theme", "Custom"));
	public final ColorSetting color = add(new ColorSetting("Color", "Custom tint", 0xFF1A0B3D))
		.visibleWhen(() -> tint.is("Custom"));
	public final BoolSetting lowHealth = add(new BoolSetting("Low health pulse", "Red heartbeat pulse below 30% health", true));

	private final Animation danger = new Animation(4);

	public Vignette() {
		super("vignette", "Vignette", "Soft cinematic screen edges, red pulse at low health", Category.VISUAL);
	}

	public void render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.options.hideGui) {
			return;
		}
		int c = switch (tint.get()) {
			case "Theme" -> ColorUtil.mix(Theme.current().accent(), 0xFF000000, 0.35F);
			case "Custom" -> color.argb();
			default -> 0xFF000000;
		};
		edges(g, c, strength.floatValue(), size.floatValue());
		boolean hurt = lowHealth.isOn() && !p.isCreative() && p.getHealth() / p.getMaxHealth() < 0.3F && p.isAlive();
		float d = danger.update(hurt ? 1 : 0);
		if (d > 0.01F) {
			float t = System.nanoTime() / 1e9F;
			// Two quick beats per cycle, like a heartbeat.
			float beat = (float) (Math.pow(Math.max(0, Math.sin(t * 6.0)), 8) + 0.6 * Math.pow(Math.max(0, Math.sin(t * 6.0 - 1.0)), 8));
			edges(g, 0xFFE0102A, d * (0.35F + 0.4F * beat), size.floatValue() * 1.3F);
		}
	}

	/** Four huge soft rectangles just outside the screen; their gaussian tails fade inwards. */
	private static void edges(GuiGraphics g, int color, float alpha, float softness) {
		int w = g.guiWidth(), h = g.guiHeight();
		int c = ColorUtil.withAlpha(color, alpha);
		float intensity = 2; // the tail is 50% at the shape edge, so double it to reach full strength at the border
		GlowRenderer.softRect(g, -FAR, -FAR, FAR, h + 2 * FAR, softness, c, intensity);
		GlowRenderer.softRect(g, w, -FAR, FAR, h + 2 * FAR, softness, c, intensity);
		GlowRenderer.softRect(g, 0, -FAR, w, FAR, softness, c, intensity);
		GlowRenderer.softRect(g, 0, h, w, FAR, softness, c, intensity);
	}
}
