package com.nebula.client.modules.visual;

import com.nebula.client.gui.themes.Theme;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.GlowRenderer;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Cinematic screen-space lens flare driven by the sun (or moon) position, tracked by
 * {@link com.nebula.client.rendering.world.SunTracker} during the world pass. A warm core, an
 * anamorphic horizontal streak with chromatic fringes, and a chain of ghost bokeh walking
 * towards the screen centre - all drawn with the analytic gaussian SDFs of the UI engine.
 */
public final class LensFlare extends Module {
	// Ghost distances along the sun -> centre axis, sizes and relative alphas.
	private static final float[] GHOST_POS = {0.42F, 0.75F, 1.15F, 1.55F, 1.95F, 2.4F};
	private static final float[] GHOST_SIZE = {8, 17, 11, 24, 13, 9};
	private static final float[] GHOST_ALPHA = {0.34F, 0.2F, 0.28F, 0.16F, 0.24F, 0.3F};

	public final NumberSetting intensity = add(new NumberSetting("Intensity", "Overall flare strength", 0.8, 0.1, 2, 0.05));
	public final NumberSetting streak = add(new NumberSetting("Streak", "Anamorphic horizontal ray", 0.8, 0, 1.5, 0.05));
	public final NumberSetting ghosts = add(new NumberSetting("Ghosts", "Bokeh circles towards the centre", 4, 0, 6, 1));
	public final NumberSetting ghostSize = add(new NumberSetting("Ghost size", "Scale of the bokeh circles", 1.0, 0.5, 2, 0.05, "x"));
	public final BoolSetting moonFlare = add(new BoolSetting("Moon flare", "A dim blue flare from the moon", true));
	public final BoolSetting hideInCaves = add(new BoolSetting("Hide under cover", "Fade out when you stand below a roof", true));
	public final BoolSetting spectral = add(new BoolSetting("Spectral ghosts", "Rainbow bokeh instead of theme colours", false));

	public LensFlare() {
		super("lens_flare", "Lens Flare", "Cinematic sun flare with anamorphic streak and bokeh ghosts", Category.SKY);
	}

	public void render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || mc.screen != null) {
			return;
		}
		var tracker = com.nebula.client.rendering.world.SunTracker.INSTANCE;
		if (tracker.strength <= 0.02F || (tracker.moon && !moonFlare.isOn())) {
			return;
		}
		Theme t = Theme.current();
		float now = System.nanoTime() / 1e9F;
		float shimmer = 0.92F + 0.08F * (float) Math.sin(now * 2.3F);
		float alpha = Math.min(1, tracker.strength * intensity.floatValue() * shimmer);
		float sx = tracker.screenX, sy = tracker.screenY;
		int w = g.guiWidth(), h = g.guiHeight();

		int base = tracker.moon ? 0xFFBFD4FF : 0xFFFFD9A0;
		base = ColorUtil.mix(base, t.accent(), 0.3F);
		int core = ColorUtil.brighten(base, 0.55F);

		// Bright gaussian core where the sun is.
		float coreR = 13 + 3 * shimmer;
		GlowRenderer.softRect(g, sx - coreR, sy - coreR, coreR * 2, coreR * 2, coreR * 0.6F, ColorUtil.withAlpha(core, alpha * 0.85F), 1.35F);
		// Soft second layer so the core blooms wide.
		GlowRenderer.softRect(g, sx - coreR * 2.4F, sy - coreR * 2.4F, coreR * 4.8F, coreR * 4.8F, coreR * 1.6F,
				ColorUtil.withAlpha(base, alpha * 0.3F), 1);

		// Anamorphic streak with chromatic fringes.
		float st = streak.floatValue();
		if (st > 0.02F) {
			float len = w * 0.75F * Math.min(1, st) * (0.85F + 0.15F * shimmer);
			int warm = ColorUtil.withAlpha(core, alpha * 0.5F * st);
			GlowRenderer.softRect(g, sx - len / 2, sy - 1.4F, len, 2.8F, 1.8F, warm, 1.6F);
			GlowRenderer.softRect(g, sx - len * 0.32F, sy - 3.4F, len * 0.64F, 1.6F, 1.2F,
					ColorUtil.withAlpha(0xFFFF5A5A, alpha * 0.16F * st), 1);
			GlowRenderer.softRect(g, sx - len * 0.32F, sy + 1.9F, len * 0.64F, 1.6F, 1.2F,
					ColorUtil.withAlpha(0xFF5AA0FF, alpha * 0.16F * st), 1);
		}

		// Ghost bokeh marching from the sun towards (and past) the screen centre.
		int n = ghosts.intValue();
		if (n > 0) {
			float dx = w / 2F - sx, dy = h / 2F - sy;
			float gs = ghostSize.floatValue();
			for (int i = 0; i < n; i++) {
				float k = GHOST_POS[i];
				float cx = sx + dx * k, cy = sy + dy * k;
				float r = GHOST_SIZE[i] * gs * (0.9F + 0.12F * (float) Math.sin(now * 1.7F + i * 2.1F));
				int c = spectral.isOn()
						? ColorUtil.hsv((i * 0.14F + now * 0.02F) % 1F, 0.45F, 1F, 255)
						: t.accentMix(i / (float) Math.max(1, n - 1));
				c = ColorUtil.mix(c, base, 0.25F);
				GlowRenderer.softRect(g, cx - r, cy - r, r * 2, r * 2, r * 0.5F,
						ColorUtil.withAlpha(c, alpha * GHOST_ALPHA[i]), 1.1F);
			}
		}
	}
}
