package com.nebula.client.modules.visual;

import com.nebula.client.gui.HudEditorScreen;
import com.nebula.client.gui.themes.Theme;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.GlowRenderer;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RenderUtils.NFont;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.rendering.world.GlowBatch;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Floating damage numbers anchored to the entities that take the hit. Health deltas are detected
 * on the client tick (the same data the health bar reads), each number is projected from world to
 * screen space at the end of the world pass, then drawn by the HUD layer with a spring pop, a
 * rising drift and a soft glow. Repeated hits on the same entity merge into one accumulating
 * number instead of spamming the screen.
 */
public final class DamageNumbers extends Module implements WorldRenderable {
	private static final int MAX = 24;
	private static final float MERGE_SECONDS = 0.6F;
	private static final float MARKER_SECONDS = 0.32F;

	public final NumberSetting range = add(new NumberSetting("Range", "Entities tracked around you", 24, 4, 48, 1, "m"));
	public final NumberSetting lifetime = add(new NumberSetting("Lifetime", "How long a number stays up", 1.1, 0.5, 2.2, 0.05, "s"));
	public final NumberSetting scale = add(new NumberSetting("Scale", "Text size", 1.0, 0.6, 1.8, 0.05, "x"));
	public final NumberSetting critThreshold = add(new NumberSetting("Big hit", "Damage that counts as a big hit", 4.5, 2, 10, 0.5));
	public final BoolSetting onSelf = add(new BoolSetting("On yourself", "Numbers when you take damage", true));
	public final BoolSetting hitMarker = add(new BoolSetting("Hit marker", "Diagonal ticks on the crosshair when your hit lands", true));
	private final ColorChoice color = new ColorChoice(add(ColorChoice.modeSetting()), add(new ColorSetting("Color", "Custom colour for your hits", 0xFFFF7A6B)));

	/** One live number: the entity it is attached to (or its last known position). */
	private final LivingEntity[] entity = new LivingEntity[MAX];
	private final double[] fx = new double[MAX], fy = new double[MAX], fz = new double[MAX];
	private final float[] value = new float[MAX], tone = new float[MAX], sx = new float[MAX], sy = new float[MAX], sAlpha = new float[MAX];
	private final long[] born = new long[MAX];
	private final boolean[] mine = new boolean[MAX];
	private int next;

	/** Entity id -> last seen health, so a drop between ticks is one damage event. */
	private final Map<Integer, Float> health = new HashMap<>();
	private ClientLevel lastLevel;
	private int lastAttackId = -1;
	private long lastAttack;
	private long markerBorn;

	public DamageNumbers() {
		super("damage_numbers", "Damage Numbers", "Floating damage values above hit entities", Category.COMBAT, true, false);
	}

	@Override
	protected void onDisable() {
		java.util.Arrays.fill(born, 0L);
		markerBorn = 0;
	}

	/** Registered by the client manager on every attack, to flag your own hits. */
	public void onAttack(LivingEntity target) {
		if (isEnabled()) {
			lastAttackId = target.getId();
			lastAttack = System.nanoTime();
		}
	}

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		var self = mc.player;
		if (level == null || self == null) {
			health.clear();
			lastLevel = null;
			return;
		}
		if (level != lastLevel) {
			health.clear();
			lastLevel = level;
		}
		long now = System.nanoTime();
		boolean myHitFresh = now - lastAttack < 1_000_000_000L;
		double rangeSq = range.floatValue() * range.floatValue();
		for (var e : level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity living) || living.isSpectator() || living.isRemoved()) {
				continue;
			}
			if (living == self && !onSelf.isOn()) {
				continue;
			}
			if (living != self && living.distanceToSqr(self) > rangeSq) {
				continue;
			}
			float hp = living.getHealth();
			Float previous = health.put(living.getId(), hp);
			if (previous == null || hp >= previous - 0.01F) {
				continue;
			}
			float damage = previous - hp;
			boolean mine = living.getId() == lastAttackId && myHitFresh;
			merge(living, damage, mine);
			if (mine && hitMarker.isOn()) {
				markerBorn = now;
			}
		}
		// The map only ever grows with new entities; a full clear is invisible (one missed tick).
		if (health.size() > 512) {
			health.clear();
		}
	}

	/** Adds to the entity's live number when one exists, otherwise takes the next slot. */
	private void merge(LivingEntity living, float damage, boolean mineHit) {
		long now = System.nanoTime();
		for (int i = 0; i < MAX; i++) {
			int slot = (next + i) % MAX;
			if (entity[slot] == living && (now - born[slot]) / 1e9F < MERGE_SECONDS + lifetime.floatValue()) {
				value[slot] += damage;
				born[slot] = now;
				this.mine[slot] |= mineHit;
				return;
			}
		}
		int slot = next;
		next = (next + 1) % MAX;
		entity[slot] = living;
		fx[slot] = living.getX();
		fy[slot] = living.getY() + living.getBbHeight() * 0.85;
		fz[slot] = living.getZ();
		value[slot] = damage;
		tone[slot] = (slot & 3) / 4F;
		born[slot] = now;
		mine[slot] = mineHit;
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		Minecraft mc = Minecraft.getInstance();
		long now = System.nanoTime();
		float life = lifetime.floatValue();
		// Project every live number while the 3D matrices are still bound; the HUD layer reads the results.
		var projection = mc.gameRenderer.getProjectionMatrix(f.partialTick());
		Vector3f rel = new Vector3f();
		Vector4f clip = new Vector4f();
		int guiW = mc.getWindow().getGuiScaledWidth();
		int guiH = mc.getWindow().getGuiScaledHeight();
		double range = this.range.floatValue();
		for (int i = 0; i < MAX; i++) {
			sx[i] = 0;
			sy[i] = 0;
			sAlpha[i] = 0;
			if (born[i] == 0) {
				continue;
			}
			float age = (now - born[i]) / 1e9F;
			if (age >= life) {
				born[i] = 0;
				entity[i] = null;
				continue;
			}
			double x = fx[i], y = fy[i], z = fz[i];
			LivingEntity living = entity[i];
			if (living != null && !living.isRemoved()) {
				float pt = f.partialTick();
				x = Mth.lerp(pt, living.xo, living.getX());
				y = Mth.lerp(pt, living.yo, living.getY()) + living.getBbHeight() * 0.85;
				z = Mth.lerp(pt, living.zo, living.getZ());
				fx[i] = x;
				fy[i] = y;
				fz[i] = z;
			}
			rel.set((float) (x - f.camera().x), (float) (y - f.camera().y), (float) (z - f.camera().z));
			float dist = rel.length();
			f.view().transformPosition(rel);
			projection.transform(rel.x, rel.y, rel.z, 1, clip);
			if (clip.w < 0.01F) {
				continue; // Behind the camera.
			}
			sx[i] = (clip.x / clip.w * 0.5F + 0.5F) * guiW;
			sy[i] = (0.5F - clip.y / clip.w * 0.5F) * guiH;
			float distanceFade = (float) (1 - Math.clamp((dist - range * 0.75F) / (range * 0.25F), 0.0, 1.0));
			sAlpha[i] = distanceFade * Math.clamp(age * 12F, 0, 1);
		}
	}

	/** Screen pass: draws the projected numbers and the hit marker. Registered under the HUD. */
	public void renderHud(GuiGraphics g, Theme t) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui) {
			return;
		}
		long now = System.nanoTime();
		float life = lifetime.floatValue();
		float seconds = now / 1e9F;
		if (mc.screen instanceof HudEditorScreen) {
			drawNumber(g, t, "3.5", mc.getWindow().getGuiScaledWidth() / 2F + 34, mc.getWindow().getGuiScaledHeight() / 2F - 26, 1, color.color(0.2F, seconds), false, 1);
			drawNumber(g, t, "9.5", mc.getWindow().getGuiScaledWidth() / 2F - 52, mc.getWindow().getGuiScaledHeight() / 2F - 8, 1, 0xFFFFD24A, true, 1);
			return;
		}
		for (int i = 0; i < MAX; i++) {
			if (sAlpha[i] <= 0.01F) {
				continue;
			}
			float age = (now - born[i]) / 1e9F;
			float k = age / life;
			boolean crit = value[i] >= critThreshold.floatValue();
			int c = mine[i] ? color.color(tone[i], seconds) : 0xFFC9CDD8;
			if (crit) {
				c = ColorUtil.mix(c, 0xFFFFD24A, mine[i] ? 0.55F : 0.35F);
			}
			drawNumber(g, t, format(value[i]), sx[i], sy[i] - k * 26 * scale.floatValue(), sAlpha[i] * (1 - k) * (1 - k * 0.4F), c, crit, age);
		}
		if (hitMarker.isOn() && markerBorn > 0) {
			float k = (now - markerBorn) / 1e9F / MARKER_SECONDS;
			if (k >= 1) {
				markerBorn = 0;
			} else {
				drawMarker(g, t, 1 - k, Animation.easeOutCubic(k));
			}
		}
	}

	private void drawNumber(GuiGraphics g, Theme t, String text, float x, float y, float alpha, int c, boolean crit, float age) {
		if (alpha < 0.02F) {
			return;
		}
		float pop = Animation.easeOutBack(Math.min(1, age / 0.2F));
		float s = scale.floatValue() * (0.8F + 0.28F * pop) * (crit ? 1.12F : 1);
		float w = RenderUtils.width(text, NFont.DISPLAY) * s;
		float h = RenderUtils.height() * s;
		GlowRenderer.glow(g, x - w / 2 - 3, y - h - 3, w + 6, h + 6, 6, 9, c, 0.4F * alpha * t.glow());
		RenderUtils.text(g, text, x - w / 2, y - h, ColorUtil.withAlpha(ColorUtil.brighten(c, 0.15F), alpha), NFont.DISPLAY, s);
	}

	/** Four diagonal ticks flying away from the crosshair, like a classic hit marker. */
	private void drawMarker(GuiGraphics g, Theme t, float alpha, float expand) {
		float cx = g.guiWidth() / 2F, cy = g.guiHeight() / 2F;
		float gap = 5 + 5 * expand;
		float len = 5 + 3 * expand;
		int c = ColorUtil.withAlpha(0xFFFFFFFF, alpha);
		for (int i = 0; i < 4; i++) {
			float a = (float) (Math.PI / 4 + i * Math.PI / 2);
			float dx = Mth.cos(a), dy = Mth.sin(a);
			g.pose().pushMatrix();
			g.pose().translate(cx + dx * gap, cy + dy * gap);
			g.pose().rotate(a);
			RoundedRenderer.rect(g, 0, -0.8F, len, 1.6F, 0.8F, c);
			g.pose().popMatrix();
		}
		GlowRenderer.glow(g, cx - 4, cy - 4, 8, 8, 4, 6, color.color(0.5F, System.nanoTime() / 1e9F), 0.5F * alpha * t.glow());
	}

	private static String format(float value) {
		if (value >= 10 || value == (float) Math.floor(value)) {
			return String.format(Locale.ROOT, "%.0f", value);
		}
		return String.format(Locale.ROOT, "%.1f", value);
	}
}
