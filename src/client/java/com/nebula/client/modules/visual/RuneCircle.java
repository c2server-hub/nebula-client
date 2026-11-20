package com.nebula.client.modules.visual;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.rendering.world.GlowBatch;
import com.nebula.client.rendering.world.WorldFrame;
import com.nebula.client.rendering.world.WorldRenderable;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * A rotating magic circle under your feet (or everyone's): two crisp rings, a ring of drifting
 * rune sparks turning one way, a small counter-rotating ring inside, all coloured along the theme
 * gradient. The circle dims while its owner runs and gently fades in and out.
 */
public final class RuneCircle extends Module implements WorldRenderable {
	private static final float RING_POS = 0.8F;

	public final NumberSetting radius = add(new NumberSetting("Radius", "Circle size", 0.9, 0.5, 2, 0.05, "m"));
	public final NumberSetting speed = add(new NumberSetting("Speed", "Runes per second around the circle", 0.35, 0.05, 1.5, 0.05));
	public final NumberSetting runes = add(new NumberSetting("Runes", "Sparks on the outer ring", 10, 4, 16, 1));
	public final NumberSetting brightness = add(new NumberSetting("Brightness", "Glow strength", 1.0, 0.2, 2, 0.05));
	public final NumberSetting range = add(new NumberSetting("Range", "How far other players show a circle", 16, 4, 40, 1, "m"));
	public final ModeSetting targets = add(new ModeSetting("Show on", "Who gets a circle", "Self", "Self", "Everyone", "Others"));
	public final BoolSetting dimWhenMoving = add(new BoolSetting("Dim while moving", "Circle fades to 40% when you run", true));
	private final ColorChoice color = new ColorChoice(add(ColorChoice.modeSetting()), add(new ColorSetting("Color", "Custom rune colour", 0xFF9B7FFF)));

	public RuneCircle() {
		super("rune_circle", "Rune Circle", "Rotating magic circle beneath your feet", Category.PLAYER);
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		Minecraft mc = Minecraft.getInstance();
		var self = mc.player;
		var level = mc.level;
		if (self == null || level == null) {
			return;
		}
		boolean everyone = targets.is("Everyone");
		boolean others = targets.is("Others");
		double rangeSq = range.floatValue() * range.floatValue();
		float seconds = f.seconds();
		float r = radius.floatValue();
		float b = brightness.floatValue();
		float spin = seconds * speed.floatValue() * Mth.TWO_PI;
		float pulse = 0.8F + 0.2F * Mth.sin(seconds * 1.8F);

		// Base rings first (one pipeline call), then the rune sparks.
		boolean drewRing = false;
		for (Player p : level.players()) {
			if (!include(p, self, everyone, others, rangeSq)) {
				continue;
			}
			float x = (float) Mth.lerp(f.partialTick(), p.xo, p.getX());
			float y = (float) Mth.lerp(f.partialTick(), p.yo, p.getY()) + 0.045F;
			float z = (float) Mth.lerp(f.partialTick(), p.zo, p.getZ());
			float dim = dimFor(p);
			if (!drewRing) {
				batch.begin(f);
			}
			drewRing = true;
			int c = color.color(0.5F, seconds);
			// Outer ring.
			batch.flat(f.rx(x), f.ry(y), f.rz(z), r / RING_POS, ColorUtil.withAlpha(c, 0.5F * b * dim * pulse));
			// Inner ring, slightly brighter.
			batch.flat(f.rx(x), f.ry(y + 0.012F), f.rz(z), r * 0.52F / RING_POS, ColorUtil.withAlpha(c, 0.65F * b * dim * pulse));
		}
		if (drewRing) {
			batch.end(NebulaPipelines.GLOW_RING, RING_POS, 0.045F, 0.10F);
		}

		int n = runes.intValue();
		batch.begin(f);
		boolean any = false;
		for (Player p : level.players()) {
			if (!include(p, self, everyone, others, rangeSq)) {
				continue;
			}
			float x = (float) Mth.lerp(f.partialTick(), p.xo, p.getX());
			float y = (float) Mth.lerp(f.partialTick(), p.yo, p.getY()) + 0.05F;
			float z = (float) Mth.lerp(f.partialTick(), p.zo, p.getZ());
			float dim = dimFor(p);
			// Rune sparks travel around the circle, each pulsing on its own phase.
			for (int i = 0; i < n; i++) {
				float a = spin + i * Mth.TWO_PI / n;
				float rr = r * 0.76F;
				float size = 0.05F * (1 + 0.4F * Mth.sin(seconds * 2.5F + i * 1.9F));
				int c = color.color(i / (float) n, seconds);
				batch.billboard(f.rx(x + Mth.cos(a) * rr), f.ry(y + 0.02F * Mth.sin(a * 2F + i)), f.rz(z + Mth.sin(a) * rr), size, ColorUtil.withAlpha(c, 0.9F * b * dim));
				any = true;
			}
			// Counter-rotating inner triplet.
			for (int i = 0; i < 3; i++) {
				float a = -spin * 1.6F + i * Mth.TWO_PI / 3;
				float rr = r * 0.34F;
				int c = ColorUtil.brighten(color.color(0.85F, seconds), 0.25F);
				batch.billboard(f.rx(x + Mth.cos(a) * rr), f.ry(y + 0.03F), f.rz(z + Mth.sin(a) * rr), 0.038F, ColorUtil.withAlpha(c, 0.8F * b * dim));
				any = true;
			}
		}
		if (any) {
			batch.end(NebulaPipelines.GLOW_SPARK, 0, 0, 0);
		}
	}

	private boolean include(Player p, Player self, boolean everyone, boolean others, double rangeSq) {
		if (p.isSpectator()) {
			return false;
		}
		if (p == self) {
			return !others;
		}
		return (everyone || others) && p.distanceToSqr(self) <= rangeSq;
	}

	private float dimFor(Player p) {
		if (!dimWhenMoving.isOn()) {
			return 1;
		}
		// Velocity is already continuous, so this dim factor eases on its own.
		float speed = (float) p.getDeltaMovement().horizontalDistance();
		return 1 - 0.6F * Math.clamp((speed - 0.4F) / 1.6F, 0, 1);
	}
}
