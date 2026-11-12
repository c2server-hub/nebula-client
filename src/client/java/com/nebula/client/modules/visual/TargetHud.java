package com.nebula.client.modules.visual;

import com.nebula.client.core.ClientManager;
import com.nebula.client.gui.Style;
import com.nebula.client.gui.hud.HudElement;
import com.nebula.client.gui.themes.Theme;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.GlowRenderer;
import com.nebula.client.rendering.Gradient;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RenderUtils.NFont;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

public final class TargetHud extends Module {
	public final NumberSetting range = add(new NumberSetting("Range", "Maximum distance of the looked-at entity", 8, 3, 32, 1, "m"));
	public final NumberSetting hold = add(new NumberSetting("Hold time", "Keep showing the last attacked target", 3, 0, 10, 0.5, "s"));
	public final ModeSetting healthStyle = add(new ModeSetting("Health bar", "Health bar colouring", "Gradient", "Gradient", "Health", "Theme"));
	public final BoolSetting showArmor = add(new BoolSetting("Show armor", "Display the target's armor points", true));
	public final BoolSetting showDistance = add(new BoolSetting("Show distance", "Display distance to the target", true));
	private final NumberSetting posX = add(HudElement.position("Target X", 0.56));
	private final NumberSetting posY = add(HudElement.position("Target Y", 0.56));

	public final HudElement element = new Element();
	private @Nullable LivingEntity target;
	private @Nullable LivingEntity lastAttacked;
	private long lastAttackTime;

	public TargetHud() {
		super("target_hud", "Target HUD", "Health and info of the entity you are fighting or looking at", Category.VISUAL, true, false);
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() && entity instanceof LivingEntity living) {
				lastAttacked = living;
				lastAttackTime = System.currentTimeMillis();
			}
			return InteractionResult.PASS;
		});
	}

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		Entity looked = mc.crosshairPickEntity;
		LivingEntity next = null;
		if (looked instanceof LivingEntity living && mc.player != null && living.distanceTo(mc.player) <= range.get()) {
			next = living;
		} else if (lastAttacked != null && lastAttacked.isAlive() && System.currentTimeMillis() - lastAttackTime < hold.get() * 1000) {
			next = lastAttacked;
		}
		target = next;
	}

	private final class Element extends HudElement {
		private final Animation appear = new Animation(14);
		private final Animation health = new Animation(1, 8);
		private @Nullable LivingEntity shown;

		Element() {
			super(TargetHud.this, "Target HUD", posX, posY);
		}

		@Override
		public float width() {
			return 150;
		}

		@Override
		public float height() {
			return 46;
		}

		@Override
		public void render(GuiGraphics g, Theme t, float x, float y, boolean preview) {
			Minecraft mc = Minecraft.getInstance();
			LivingEntity current = preview && target == null ? mc.player : target;
			float a = appear.update(current != null ? 1 : 0);
			if (current != null) {
				shown = current;
			}
			if (a < 0.01F || shown == null) {
				return;
			}
			LivingEntity e = shown;
			float frac = Math.clamp(e.getHealth() / Math.max(1, e.getMaxHealth()), 0, 1);
			float hp = health.update(frac);
			float ease = Animation.easeOutBack(a);
			g.pose().pushMatrix();
			g.pose().translate(x + width() / 2, y + height() / 2);
			g.pose().scale(0.85F + 0.15F * ease, 0.85F + 0.15F * ease);
			g.pose().translate(-width() / 2, -height() / 2);
			float w = width(), h = height();
			Style.panel(g, t, 0, 0, w, h, Math.min(t.radius() + 2, 14), a, a > 0.98F && ClientManager.get().modules().guiSettings.hudGlass.isOn());

			// Avatar: player face for players, accent monogram otherwise.
			float av = 30;
			if (e instanceof AbstractClientPlayer player) {
				RoundedRenderer.rect(g, 7, 7, av + 2, av + 2, 7, ColorUtil.fade(t.accent(), a * 0.6F));
				PlayerFaceRenderer.draw(g, player.getSkin(), 8, 8, (int) av, ColorUtil.withAlpha(0xFFFFFF, a));
			} else {
				RoundedRenderer.gradient(g, 8, 8, av, av, 8, Style.accentGradient(t, a));
				RenderUtils.centered(g, e.getName().getString().substring(0, 1).toUpperCase(Locale.ROOT), 8 + av / 2, 8 + av / 2 - 7, ColorUtil.fade(0xFFFFFFFF, a), NFont.DISPLAY, 1.2F);
			}

			float tx = 8 + av + 8;
			String name = RenderUtils.ellipsize(e.getName().getString(), w - tx - 8, NFont.TEXT);
			RenderUtils.text(g, name, tx, 8, ColorUtil.fade(t.text(), a), NFont.TEXT);
			StringBuilder info = new StringBuilder(String.format(Locale.ROOT, "%.1f HP", e.getHealth() + e.getAbsorptionAmount()));
			if (showArmor.isOn() && e.getArmorValue() > 0) {
				info.append("  ·  ").append(e.getArmorValue()).append(" armor");
			}
			if (showDistance.isOn() && mc.player != null && e != mc.player) {
				info.append(String.format(Locale.ROOT, "  ·  %.1fm", e.distanceTo(mc.player)));
			}
			RenderUtils.text(g, RenderUtils.ellipsize(info.toString(), w - tx - 8, NFont.TEXT), tx, 19, ColorUtil.fade(t.textDim(), a), NFont.TEXT);

			float barW = w - tx - 8;
			RoundedRenderer.rect(g, tx, 32, barW, 5, 2.5F, ColorUtil.withAlpha(0xFF000000, 0.35F * a));
			Gradient gradient = switch (healthStyle.get()) {
				case "Health" -> Gradient.solid(ColorUtil.fade(ColorUtil.mix(0xFFFF4D5E, 0xFF3DDC84, hp), a));
				case "Theme" -> Gradient.solid(ColorUtil.fade(t.accent(), a));
				default -> Style.accentGradient(t, a);
			};
			if (hp > 0.01F) {
				GlowRenderer.glow(g, tx, 32, barW * hp, 5, 2.5F, 6, ColorUtil.fade(t.accent(), a), 0.6F * t.glow());
				RoundedRenderer.gradient(g, tx, 32, barW * hp, 5, 2.5F, gradient);
			}
			g.pose().popMatrix();
		}
	}
}
