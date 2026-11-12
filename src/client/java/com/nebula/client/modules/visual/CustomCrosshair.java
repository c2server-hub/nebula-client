package com.nebula.client.modules.visual;

import com.nebula.client.gui.themes.Theme;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.GlowRenderer;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;

/** SDF crosshair that spreads while moving or recovering from an attack, replacing the vanilla one. */
public final class CustomCrosshair extends Module {
	public final ModeSetting style = add(new ModeSetting("Style", "Crosshair shape", "Cross + Dot", "Cross", "Cross + Dot", "Dot", "Circle"));
	public final NumberSetting length = add(new NumberSetting("Length", "Arm length", 4, 1, 10, 0.5, "px"))
		.visibleWhen(() -> style.get().startsWith("Cross"));
	public final NumberSetting gap = add(new NumberSetting("Gap", "Distance from the centre", 2.5, 0, 8, 0.5, "px"));
	public final NumberSetting thickness = add(new NumberSetting("Thickness", "Line thickness", 1.5, 0.5, 4, 0.25, "px"));
	public final BoolSetting dynamic = add(new BoolSetting("Dynamic", "Spread while moving and after attacking", true));
	public final BoolSetting outline = add(new BoolSetting("Outline", "Dark outline for contrast", true));
	public final BoolSetting glow = add(new BoolSetting("Glow", "Accent glow", true));
	public final BoolSetting cooldown = add(new BoolSetting("Cooldown bar", "Attack cooldown indicator", true));
	public final BoolSetting targetTint = add(new BoolSetting("Target tint", "Change colour when aiming at an entity", true));
	private final ColorChoice color = new ColorChoice(add(ColorChoice.modeSetting()), add(new ColorSetting("Color", "Custom colour", 0xFFFFFFFF)));

	private final Animation spread = new Animation(12);
	private final Animation aim = new Animation(14);
	private final Animation cool = new Animation(1, 16);

	public CustomCrosshair() {
		super("custom_crosshair", "Custom Crosshair", "Smooth dynamic crosshair with cooldown indicator", Category.VISUAL, true, false);
	}

	/** Returns false when the vanilla crosshair should be drawn instead. */
	public boolean render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (!isEnabled() || p == null) {
			return false;
		}
		if (!mc.options.getCameraType().isFirstPerson() || p.isSpectator()) {
			return true;
		}
		Theme t = Theme.current();
		float seconds = System.nanoTime() / 1e9F;
		double speed = p.getDeltaMovement().horizontalDistance();
		float strength = p.getAttackStrengthScale(0);
		float s = spread.update(dynamic.isOn() ? (float) Math.min(1, speed * 6) + (1 - strength) * 1.5F : 0);
		float aiming = aim.update(targetTint.isOn() && mc.crosshairPickEntity instanceof LivingEntity ? 1 : 0);
		int base = ColorUtil.mix(color.color(0, seconds), 0xFFFF4D6A, aiming);
		int dark = 0xB0000000;
		float cx = g.guiWidth() / 2F, cy = g.guiHeight() / 2F;
		float th = thickness.floatValue();
		float gp = gap.floatValue() + s * 3;
		float ln = length.floatValue();
		float o = outline.isOn() ? 1 : 0;

		if (glow.isOn()) {
			GlowRenderer.glow(g, cx - gp - ln, cy - th / 2, (gp + ln) * 2, th, th / 2, 5, base, 0.35F * t.glow());
		}
		switch (style.get()) {
			case "Dot" -> dot(g, cx, cy, Math.max(1.5F, th), base, dark, o);
			case "Circle" -> {
				float r = gp + 2.5F;
				if (o > 0) {
					RoundedRenderer.outline(g, cx - r - 0.75F, cy - r - 0.75F, (r + 0.75F) * 2, (r + 0.75F) * 2, r + 0.75F, th + 1.5F, dark);
				}
				RoundedRenderer.outline(g, cx - r, cy - r, r * 2, r * 2, r, th, base);
				dot(g, cx, cy, th * 0.8F, base, dark, o);
			}
			default -> {
				arm(g, cx - gp - ln, cy - th / 2, ln, th, base, dark, o);
				arm(g, cx + gp, cy - th / 2, ln, th, base, dark, o);
				arm(g, cx - th / 2, cy - gp - ln, th, ln, base, dark, o);
				arm(g, cx - th / 2, cy + gp, th, ln, base, dark, o);
				if (style.is("Cross + Dot")) {
					dot(g, cx, cy, th * 0.75F, base, dark, o);
				}
			}
		}
		float c = cool.update(strength);
		if (cooldown.isOn() && c < 0.995F) {
			float w = 16, bx = cx - w / 2, by = cy + gp + ln + 5;
			RoundedRenderer.rect(g, bx, by, w, 2, 1, 0x80000000);
			RoundedRenderer.rect(g, bx, by, w * c, 2, 1, base);
		}
		return true;
	}

	private static void arm(GuiGraphics g, float x, float y, float w, float h, int color, int dark, float outline) {
		if (outline > 0) {
			RoundedRenderer.rect(g, x - 0.75F, y - 0.75F, w + 1.5F, h + 1.5F, Math.min(w, h) / 2 + 0.75F, dark);
		}
		RoundedRenderer.rect(g, x, y, w, h, Math.min(w, h) / 2, color);
	}

	private static void dot(GuiGraphics g, float cx, float cy, float r, int color, int dark, float outline) {
		if (outline > 0) {
			RoundedRenderer.circle(g, cx, cy, r + 0.75F, dark);
		}
		RoundedRenderer.circle(g, cx, cy, r, color);
	}
}
