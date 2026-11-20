package com.nebula.client.modules.ui;

import com.nebula.client.gui.HudEditorScreen;
import com.nebula.client.gui.themes.Theme;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.GlowRenderer;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RenderUtils.NFont;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

/**
 * A dotted progress ring that wraps around the crosshair while you use an item: drawing a bow
 * fills it over one second (then pulses gold at full draw), eating and drinking fill smoothly,
 * and open-ended actions like the spyglass get orbiting dots instead of a fake bar.
 */
public final class ItemUse extends Module {
	public final NumberSetting radius = add(new NumberSetting("Radius", "Ring size around the crosshair", 16, 8, 30, 0.5, "px"));
	public final NumberSetting dots = add(new NumberSetting("Dots", "Resolution of the ring", 22, 12, 36, 1));
	public final BoolSetting showIcon = add(new BoolSetting("Icon", "Show the item next to the ring", true));
	public final BoolSetting showPercent = add(new BoolSetting("Percent", "Progress value under the ring", false));

	private final Animation progress = new Animation(20);

	public ItemUse() {
		super("item_use", "Item Use", "Progress ring around the crosshair while using items", Category.INTERFACE, true, false);
	}

	/** Screen pass registered on top of the HUD, centred like the crosshair. */
	public void render(GuiGraphics g, Theme t) {
		Minecraft mc = Minecraft.getInstance();
		var p = mc.player;
		if (p == null || mc.options.hideGui || p.isSpectator() || !mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		boolean editor = mc.screen instanceof HudEditorScreen;
		float seconds = System.nanoTime() / 1e9F;

		ItemStack stack = p.isUsingItem() ? p.getUseItem() : ItemStack.EMPTY;
		ItemUseAnimation anim = stack.isEmpty() ? ItemUseAnimation.NONE : stack.getUseAnimation();
		float target;
		boolean indeterminate = false;
		boolean fullDraw = false;
		if (editor) {
			target = 0.62F;
		} else if (stack.isEmpty()) {
			return;
		} else {
			switch (anim) {
				case BOW -> {
					target = Math.clamp((72000 - p.getUseItemRemainingTicks()) / 20F, 0, 1);
					fullDraw = target >= 1;
				}
				case CROSSBOW -> target = Math.clamp((72000 - p.getUseItemRemainingTicks()) / 25F, 0, 1);
				case TRIDENT -> target = Math.clamp((72000 - p.getUseItemRemainingTicks()) / 10F, 0, 1);
				case EAT, DRINK -> target = 1 - Math.clamp(
						p.getUseItemRemainingTicks() / (float) Math.max(1, stack.getUseDuration(p)), 0, 1);
				default -> {
					target = 0;
					indeterminate = true;
				}
			}
		}
		float value = progress.update(target);
		if (!indeterminate && value < 0.02F && target < 0.02F) {
			return;
		}

		float cx = g.guiWidth() / 2F, cy = g.guiHeight() / 2F;
		float r = radius.floatValue();
		int n = dots.intValue();
		float baseAlpha = Math.clamp(value * 4, 0, 1);

		if (indeterminate) {
			// Faint full ring plus three orbiting sparks.
			for (int i = 0; i < n; i++) {
				float a = -Mth.HALF_PI + i * Mth.TWO_PI / n;
				RoundedRenderer.circle(g, cx + Mth.cos(a) * r, cy + Mth.sin(a) * r, 1.1F, ColorUtil.withAlpha(t.textDim(), 0.25F));
			}
			for (int i = 0; i < 3; i++) {
				float a = seconds * 3.2F + i * Mth.TWO_PI / 3;
				float dx = cx + Mth.cos(a) * r, dy = cy + Mth.sin(a) * r;
				GlowRenderer.glow(g, dx - 3, dy - 3, 6, 6, 3, 5, t.accent(), 0.8F * t.glow());
				RoundedRenderer.circle(g, dx, dy, 1.8F, t.accent());
			}
		} else {
			int filled = Math.round(value * n);
			for (int i = 0; i < n; i++) {
				float a = -Mth.HALF_PI + i * Mth.TWO_PI / n; // starts at the top, fills clockwise
				float dx = cx + Mth.cos(a) * r, dy = cy + Mth.sin(a) * r;
				if (i < filled) {
					int c = ColorUtil.mix(t.accent(), t.accent2(), i / (float) n);
					if (fullDraw) {
						// Fully drawn bow: the whole ring breathes gold.
						c = ColorUtil.mix(c, 0xFFFFE9A8, 0.7F);
					}
					boolean head = i == filled - 1 && value < 1;
					if (head) {
						GlowRenderer.glow(g, dx - 3.5F, dy - 3.5F, 7, 7, 3.5F, 6, c, 0.9F * t.glow());
					}
					float pulse = fullDraw ? 0.65F + 0.35F * Mth.sin(seconds * 9) : 1;
					RoundedRenderer.circle(g, dx, dy, head ? 2 : 1.4F, ColorUtil.withAlpha(c, pulse));
				} else {
					RoundedRenderer.circle(g, dx, dy, 1.1F, ColorUtil.withAlpha(t.textDim(), 0.3F));
				}
			}
		}

		if (showIcon.isOn() && !stack.isEmpty()) {
			float ix = cx + r + 9, iy = cy - 8;
			g.pose().pushMatrix();
			g.pose().translate(ix, iy);
			g.pose().scale(0.75F, 0.75F);
			g.renderItem(stack, 0, 0);
			g.pose().popMatrix();
		}
		if (showPercent.isOn() && !indeterminate) {
			String text = Math.round(value * 100) + "%";
			RenderUtils.centered(g, text, cx, cy + r + 4, ColorUtil.withAlpha(t.textDim(), 0.9F), NFont.TEXT, 0.8F);
		}
	}
}
