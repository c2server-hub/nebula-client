package com.nebula.client.modules.visual;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.rendering.world.GlowBatch;
import com.nebula.client.rendering.world.WorldFrame;
import com.nebula.client.rendering.world.WorldRenderable;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/** A floating glowing ring above the head, with orbiting sparkles. */
public final class Halo extends Module implements WorldRenderable {
	private static final float RING_POS = 0.8F;

	public final NumberSetting radius = add(new NumberSetting("Radius", "Ring radius", 0.3, 0.15, 0.7, 0.01, "m"));
	public final NumberSetting height = add(new NumberSetting("Height", "Distance above the head", 0.22, 0.05, 0.8, 0.01, "m"));
	public final NumberSetting thickness = add(new NumberSetting("Thickness", "Ring thickness", 0.06, 0.02, 0.15, 0.005));
	public final NumberSetting glow = add(new NumberSetting("Glow", "Brightness", 1.0, 0.2, 2, 0.05));
	public final NumberSetting sparkles = add(new NumberSetting("Sparkles", "Orbiting sparkles (0 disables)", 4, 0, 12, 1));
	public final BoolSetting firstPerson = add(new BoolSetting("First person", "Render in first-person view", false));
	private final ColorChoice color = new ColorChoice(add(ColorChoice.modeSetting()), add(new ColorSetting("Color", "Custom halo colour", 0xFFFFD86B)));

	public Halo() {
		super("halo", "Halo", "Glowing angel ring floating above your head", Category.VISUAL);
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || p.isInvisible() || (mc.options.getCameraType().isFirstPerson() && !firstPerson.isOn())) {
			return;
		}
		float t = f.seconds();
		double px = Mth.lerp(f.partialTick(), p.xo, p.getX());
		double py = Mth.lerp(f.partialTick(), p.yo, p.getY()) + p.getBbHeight() + height.get() + Mth.sin(t * 2.2F) * 0.025F;
		double pz = Mth.lerp(f.partialTick(), p.zo, p.getZ());
		float r = radius.floatValue();
		float a = Math.min(1, glow.floatValue());
		batch.begin(f);
		batch.flat(f.rx(px), f.ry(py), f.rz(pz), r / RING_POS, ColorUtil.withAlpha(color.color(0, t), a));
		// Second, wider and fainter pass gives the ring a soft aura.
		batch.flat(f.rx(px), f.ry(py), f.rz(pz), r * 1.25F / RING_POS, ColorUtil.withAlpha(color.color(1, t), 0.25F * a));
		batch.end(NebulaPipelines.GLOW_RING, RING_POS, thickness.floatValue(), 0.15F * glow.floatValue());

		int n = sparkles.intValue();
		if (n > 0) {
			batch.begin(f);
			for (int i = 0; i < n; i++) {
				float angle = t * 1.6F + i * Mth.TWO_PI / n;
				float twinkle = 0.55F + 0.45F * Mth.sin(t * 5 + i * 2.1F);
				batch.billboard(f.rx(px + Mth.cos(angle) * r), f.ry(py + Mth.sin(angle * 2) * 0.03), f.rz(pz + Mth.sin(angle) * r),
					0.06F, ColorUtil.withAlpha(color.color((float) i / n, t), twinkle * a));
			}
			batch.end(NebulaPipelines.GLOW_SPARK, 0, 0, 0);
		}
	}
}
