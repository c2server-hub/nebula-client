package com.nebula.client.modules.visual;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.effects.WorldBloom;
import com.nebula.client.settings.NumberSetting;

/**
 * Whole-frame cinematic bloom, applied at the very end of the world pass so terrain, entities and
 * every Nebula glow effect contribute. A soft-knee bright pass keeps colours alive; the blurred
 * result is additively composited, which makes bright edges and emissive effects breathe.
 */
public final class Bloom extends Module {
	public final NumberSetting strength = add(new NumberSetting("Strength", "Bloom intensity", 0.7, 0, 2, 0.05));
	public final NumberSetting threshold = add(new NumberSetting("Threshold", "Brightness needed to bloom", 0.55, 0.05, 1, 0.05));
	public final NumberSetting radius = add(new NumberSetting("Radius", "Bloom spread", 7, 1, 14, 0.5));
	public final NumberSetting quality = add(new NumberSetting("Quality", "Blur iterations (higher is softer)", 2, 1, 4, 1));

	public Bloom() {
		super("bloom", "Bloom", "Cinematic glow on the brightest parts of the frame", Category.SKY);
	}

	/** Called by WorldEffects after every module has drawn, with the main render target. */
	public void apply(WorldBloom bloom, RenderTarget target) {
		if (strength.floatValue() <= 0.01F) {
			return;
		}
		bloom.render(target, threshold.floatValue(), strength.floatValue(), radius.floatValue(), quality.intValue());
	}
}
