package com.nebula.client.modules.ui;

import com.nebula.client.gui.hud.HudElement;
import com.nebula.client.gui.themes.Theme;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Armour and hands as glass slots with live durability bars and remaining-use counters.
 * Empty slots collapse away (or stay visible as dim outlines, your choice), and the widget
 * hides itself completely when you wear nothing.
 */
public final class ArmorHud extends Module {
	public final BoolSetting showHands = add(new BoolSetting("Hands", "Include main and off hand", true));
	public final BoolSetting showDurability = add(new BoolSetting("Durability bar", "Colour bar under each item", true));
	public final BoolSetting showNumbers = add(new BoolSetting("Numbers", "Remaining uses in the corner", false));
	public final BoolSetting showWhenEmpty = add(new BoolSetting("Empty slots", "Keep a placeholder when a slot is empty", false));
	public final NumberSetting scale = add(new NumberSetting("Scale", "Widget size", 1.0, 0.6, 1.6, 0.05, "x"));
	private final NumberSetting posX = add(HudElement.position("Armor X", 0.995));
	private final NumberSetting posY = add(HudElement.position("Armor Y", 0.08));

	public final HudElement element = new Element();

	public ArmorHud() {
		super("armor_hud", "Armor HUD", "Equipment slots with live durability", Category.INTERFACE);
	}

	private final class Element extends HudElement {
		private static final float SLOT = 20, GAP = 4;

		private final List<ItemStack> stacks = new ArrayList<>();
		private final List<Boolean> fresh = new ArrayList<>();

		Element() {
			super(ArmorHud.this, "Armor HUD", posX, posY);
		}

		private void update(boolean preview) {
			stacks.clear();
			fresh.clear();
			var player = Minecraft.getInstance().player;
			if (player == null && !preview) {
				return;
			}
			EquipmentSlot[] armor = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
			for (EquipmentSlot slot : armor) {
				ItemStack s = player == null ? ItemStack.EMPTY : player.getItemBySlot(slot);
				stacks.add(s);
				fresh.add(!s.isEmpty());
			}
			if (showHands.isOn()) {
				ItemStack main = player == null ? ItemStack.EMPTY : player.getMainHandItem();
				ItemStack off = player == null ? ItemStack.EMPTY : player.getOffhandItem();
				stacks.add(main);
				fresh.add(!main.isEmpty());
				stacks.add(off);
				fresh.add(!off.isEmpty());
			}
		}

		private boolean anythingVisible(boolean preview) {
			if (preview) {
				return true;
			}
			if (showWhenEmpty.isOn()) {
				return true;
			}
			for (boolean f : fresh) {
				if (f) {
					return true;
				}
			}
			return false;
		}

		@Override
		public float width() {
			update(false);
			int n = 0;
			for (int i = 0; i < stacks.size(); i++) {
				if (fresh.get(i) || showWhenEmpty.isOn()) {
					n++;
				}
			}
			return (n * SLOT + Math.max(0, n - 1) * GAP) * scale.floatValue();
		}

		@Override
		public float height() {
			return (SLOT + (showDurability.isOn() ? 4 : 0)) * scale.floatValue();
		}

		@Override
		public boolean isVisible() {
			return super.isVisible() && anythingVisible(false);
		}

		@Override
		public void render(GuiGraphics g, Theme t, float x, float y, boolean preview) {
			update(preview);
			if (stacks.isEmpty() || !anythingVisible(preview)) {
				return;
			}
			float s = scale.floatValue();
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.pose().scale(s, s);
			float cx = 0;
			for (int i = 0; i < stacks.size(); i++) {
				ItemStack stack = stacks.get(i);
				boolean filled = fresh.get(i);
				if (!filled && !showWhenEmpty.isOn() && !preview) {
					continue;
				}
				// Slot card.
				int fill = filled ? ColorUtil.withAlpha(t.panel(), 0.62F) : ColorUtil.withAlpha(t.panel(), 0.28F);
				RoundedRenderer.rect(g, cx, 0, SLOT, SLOT, Math.min(t.radius(), 8), fill, 1, t.border());
				if (!filled) {
					// A dim dash marks the empty slot.
					RoundedRenderer.rect(g, cx + SLOT / 2 - 4, SLOT / 2 - 0.75F, 8, 1.5F, 0.75F,
							ColorUtil.withAlpha(t.textDim(), 0.35F));
				} else {
					g.renderItem(stack, (int) cx, 0);
					if (showDurability.isOn() && stack.isDamaged()) {
						float frac = 1 - stack.getDamageValue() / (float) Math.max(1, stack.getMaxDamage());
						int bar = ColorUtil.mix(0xFFE1433D, 0xFF3DDC5B, frac);
						RoundedRenderer.rect(g, cx + 1.5F, SLOT + 1, SLOT - 3, 2, 1, ColorUtil.withAlpha(0xFFFFFFFF, 0.1F));
						RoundedRenderer.rect(g, cx + 1.5F, SLOT + 1, Math.max(2, (SLOT - 3) * frac), 2, 1, bar);
					}
					if (showNumbers.isOn() && stack.isDamaged()) {
						int left = stack.getMaxDamage() - stack.getDamageValue();
						String text = Integer.toString(left);
						RenderUtils.text(g, text, cx + SLOT - 3 - RenderUtils.width(text, RenderUtils.NFont.TEXT) * 0.62F,
								SLOT - 8, 0xFFFFFFFF, RenderUtils.NFont.TEXT, 0.62F);
					}
				}
				cx += SLOT + GAP;
			}
			g.pose().popMatrix();
		}
	}
}
