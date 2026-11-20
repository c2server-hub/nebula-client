package com.nebula.client.modules.visual;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.GlowRenderer;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.rendering.world.GlowBatch;
import com.nebula.client.rendering.world.SparkPool;
import com.nebula.client.rendering.world.WorldFrame;
import com.nebula.client.rendering.world.WorldRenderable;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Damage feedback: a spark burst and shockwave ring on every player that takes a hit, plus a
 * short red flash around your own screen. Detection rides the synced {@code hurtTime} field,
 * so it works for every player in range without any server round-trip.
 */
public final class HurtEffects extends Module implements WorldRenderable {
	private static final int MAX_WAVES = 8;
	private static final float WAVE_SECONDS = 0.5F;
	private static final float FLASH_SECONDS = 0.45F;
	private static final float FAR = 4000;

	public final BoolSetting self = add(new BoolSetting("On yourself", "Effects when you take damage", true));
	public final BoolSetting others = add(new BoolSetting("On other players", "Effects when other players are hit", true));
	public final NumberSetting range = add(new NumberSetting("Range", "How far other players are tracked", 24, 4, 48, 1, "m"));
	public final NumberSetting count = add(new NumberSetting("Count", "Sparks per hit", 18, 4, 40, 1));
	public final NumberSetting speed = add(new NumberSetting("Speed", "Burst speed", 3.5, 0.5, 8, 0.1, "m/s"));
	public final NumberSetting lifetime = add(new NumberSetting("Lifetime", "Spark lifetime", 0.7, 0.2, 1.5, 0.05, "s"));
	public final NumberSetting size = add(new NumberSetting("Size", "Spark size", 0.14, 0.03, 0.3, 0.005));
	public final BoolSetting shockwave = add(new BoolSetting("Shockwave", "Expanding ring around the hit player", true));
	public final BoolSetting screenFlash = add(new BoolSetting("Screen flash", "Red edge flash when you are hit", true));
	private final ColorChoice color = new ColorChoice(add(ColorChoice.modeSetting()), add(new ColorSetting("Color", "Custom effect colour", 0xFFFF4D4D)));

	private final RandomSource random = RandomSource.create();
	private final SparkPool sparks = new SparkPool(192);
	private final Map<Integer, Integer> hurtSeen = new HashMap<>();
	private final double[] wx = new double[MAX_WAVES], wy = new double[MAX_WAVES], wz = new double[MAX_WAVES];
	private final long[] waveBorn = new long[MAX_WAVES];
	private int nextWave;
	private ClientLevel lastLevel;
	private long flashBorn;

	public HurtEffects() {
		super("hurt_effects", "Hurt Effects", "Spark burst, shockwave and screen flash on damage", Category.COMBAT);
	}

	@Override
	protected void onDisable() {
		hurtSeen.clear();
		flashBorn = 0;
	}

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		LocalPlayer self = mc.player;
		if (level == null || self == null) {
			hurtSeen.clear();
			lastLevel = null;
			return;
		}
		if (level != lastLevel) {
			hurtSeen.clear();
			lastLevel = level;
		}
		double rangeSq = range.floatValue() * range.floatValue();
		for (Player p : level.players()) {
			if (p.isSpectator()) {
				continue;
			}
			boolean isSelf = p == self;
			if (isSelf && !this.self.isOn()) {
				continue;
			}
			if (!isSelf && !others.isOn()) {
				continue;
			}
			if (!isSelf && p.distanceToSqr(self) > rangeSq) {
				continue;
			}
			int hurt = p.hurtTime;
			Integer previous = hurtSeen.put(p.getId(), hurt);
			if (previous != null && previous == 0 && hurt > 0) {
				trigger(p, isSelf);
			}
		}
	}

	private void trigger(Player p, boolean isSelf) {
		float seconds = System.nanoTime() / 1e9F;
		double x = p.getX();
		double y = p.getY() + p.getBbHeight() * 0.6;
		double z = p.getZ();
		HitEffects.burst(sparks, random, x, y, z, count.intValue(), speed.floatValue(), lifetime.floatValue(), size.floatValue(),
				i -> color.color(random.nextFloat(), seconds));
		wx[nextWave] = x;
		wy[nextWave] = y;
		wz[nextWave] = z;
		waveBorn[nextWave] = System.nanoTime();
		nextWave = (nextWave + 1) % MAX_WAVES;
		if (isSelf && screenFlash.isOn()) {
			flashBorn = System.nanoTime();
		}
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		sparks.render(f, batch, -4.5F);
		if (!shockwave.isOn()) {
			return;
		}
		long now = System.nanoTime();
		batch.begin(f);
		boolean any = false;
		for (int w = 0; w < MAX_WAVES; w++) {
			if (waveBorn[w] == 0) {
				continue;
			}
			float t = (now - waveBorn[w]) / 1e9F / WAVE_SECONDS;
			if (t >= 1) {
				continue;
			}
			float r = 0.3F + 1.0F * Animation.easeOutCubic(t);
			batch.billboard(f.rx(wx[w]), f.ry(wy[w]), f.rz(wz[w]), r / 0.8F,
					ColorUtil.withAlpha(color.color(0.5F, f.seconds()), (1 - t) * (1 - t)));
			any = true;
		}
		if (any) {
			batch.end(NebulaPipelines.GLOW_RING, 0.8F, 0.05F, 0.2F);
		}
	}

	/** Screen edge flash, registered underneath the vanilla HUD like the vignette. */
	public void renderFlash(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		if (!screenFlash.isOn() || mc.options.hideGui || flashBorn == 0) {
			return;
		}
		float t = (System.nanoTime() - flashBorn) / 1e9F / FLASH_SECONDS;
		if (t >= 1) {
			flashBorn = 0;
			return;
		}
		float fade = (1 - t) * (1 - t);
		int c = ColorUtil.withAlpha(ColorUtil.mix(color.color(0.5F, 0), 0xFFFFFFFF, 0.15F), 0.9F);
		edges(g, c, fade);
	}

	private static void edges(GuiGraphics g, int color, float alpha) {
		int w = g.guiWidth(), h = g.guiHeight();
		int c = ColorUtil.withAlpha(color, alpha);
		GlowRenderer.softRect(g, -FAR, -FAR, FAR, h + 2 * FAR, 120, c, 2);
		GlowRenderer.softRect(g, w, -FAR, FAR, h + 2 * FAR, 120, c, 2);
		GlowRenderer.softRect(g, 0, -FAR, w, FAR, 120, c, 2);
		GlowRenderer.softRect(g, 0, h, w, FAR, 120, c, 2);
	}
}
