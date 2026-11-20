package com.nebula.client.modules.immersion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.KeybindSetting;
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * CS:GO-style knife handling for the embedded CounterCraft knife pack:
 * keyframed inspect (F-style flourish), draw on switch, horizontal slash
 * swing and a walking weapon bob, applied to the first-person hand pass.
 */
public final class CsKnives extends Module {
	/** Names the CounterCraft pack maps to knife models (item model "select" cases). */
	private static final List<String> KNIFE_NAMES = List.of(
		"Stilletto", "Dark Stilletto",
		"M9 Bayonet", "M9 Bayonet Gradient", "M9 Bayonet Violet",
		"Ursus", "Ursus Violet", "Ursus Gem",
		"Bayonet", "Bayonet Violet", "Bayonet Gradient",
		"Guonith", "Guonith Au"
	);

	public final BoolSetting inspect = add(new BoolSetting("Inspect", "Turn the blade over and admire it", true));
	public final KeybindSetting inspectKey = add(new KeybindSetting("Inspect key", "Key that plays the inspect animation", GLFW.GLFW_KEY_G));
	public final ModeSetting inspectStyle = add(new ModeSetting("Inspect style", "Standard turn or a full spin", "Standard", "Standard", "Spin"));
	public final NumberSetting inspectDuration = add(new NumberSetting("Inspect duration", "Length of the inspect animation", 2.5, 1.5, 5, 0.1, "s"));
	public final BoolSetting inspectSound = add(new BoolSetting("Inspect sound", "Soft metallic rustle when the inspect starts", true));
	public final BoolSetting draw = add(new BoolSetting("Draw animation", "Knife is drawn from below with a settle on switch", true));
	public final NumberSetting drawDuration = add(new NumberSetting("Draw duration", "Length of the draw animation", 0.55, 0.3, 1.2, 0.05, "s"));
	public final BoolSetting slash = add(new BoolSetting("Slash swing", "Attacks become horizontal CS:GO-style slashes", true));
	public final NumberSetting slashStrength = add(new NumberSetting("Slash strength", "Slash arc strength", 1.0, 0, 2, 0.05, "x"));
	public final BoolSetting walkBob = add(new BoolSetting("Walk bob", "Weapon dips with each step while walking", true));
	public final NumberSetting bobStrength = add(new NumberSetting("Bob strength", "Walk bob strength", 1.0, 0, 2, 0.05, "x"));

	private long inspectStart;
	private boolean keyWasDown;
	private ItemStack lastStack = ItemStack.EMPTY;
	private long drawStart;

	public CsKnives() {
		super("cs_knives", "CS Knives", "CS:GO knife inspect, draw, slash & walk bob", Category.IMMERSION, true, false);
	}

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null) {
			return;
		}
		ItemStack now = p.getMainHandItem();
		if (!ItemStack.isSameItem(now, lastStack)) {
			drawStart = System.nanoTime();
			inspectStart = 0;
		}
		lastStack = now.copy();

		if (inspect.isOn() && inspectKey.isBound() && mc.screen == null && !now.isEmpty() && isKnifeLike(now)) {
			boolean down = GLFW.glfwGetKey(mc.getWindow().handle(), inspectKey.key()) == GLFW.GLFW_PRESS;
			if (down && !keyWasDown) {
				inspectStart = System.nanoTime();
				if (inspectSound.isOn()) {
					p.playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 0.35F, 1.25F);
				}
			}
			keyWasDown = down;
		}
	}

	/** True when the stack renders as one of the pack's knives (weapon component, axe or pack name). */
	public boolean isKnifeLike(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.has(DataComponents.WEAPON) || stack.getItem() instanceof AxeItem) {
			return true;
		}
		return KNIFE_NAMES.contains(stack.getHoverName().getString());
	}

	private static float ease(float t) {
		t = Mth.clamp(t, 0F, 1F);
		return t * t * (3F - 2F * t);
	}

	/** Smooth out-of-cubic settle, used by the draw animation. */
	private static float easeOut(float t) {
		t = Mth.clamp(t, 0F, 1F);
		return 1F - (1F - t) * (1F - t) * (1F - t);
	}

	/** One animation keyframe: time (0-1), position offset and rotation in degrees. */
	private record Kf(float t, float x, float y, float z, float rx, float ry, float rz) {
	}

	// Values are mirrored on the X axis / yaw / roll for the left hand at apply time.
	private static final Kf[] INSPECT_STANDARD = {
		new Kf(0.00F, 0, 0, 0, 0, 0, 0),
		new Kf(0.10F, -0.05F, 0.05F, -0.08F, 4, 22, 6),
		new Kf(0.28F, -0.10F, 0.09F, -0.12F, -6, 58, 16),
		new Kf(0.45F, -0.12F, 0.08F, -0.14F, -2, 64, 28),
		new Kf(0.62F, -0.09F, 0.10F, -0.10F, 3, 30, 42),
		new Kf(0.80F, -0.03F, 0.05F, -0.04F, 1, 10, 12),
		new Kf(1.00F, 0, 0, 0, 0, 0, 0),
	};

	private static final Kf[] INSPECT_SPIN = {
		new Kf(0.00F, 0, 0, 0, 0, 0, 0),
		new Kf(0.10F, -0.04F, 0.08F, -0.10F, 5, 10, 4),
		new Kf(0.28F, -0.07F, 0.10F, -0.12F, -4, 150, 10),
		new Kf(0.48F, -0.08F, 0.09F, -0.12F, 6, 260, 16),
		new Kf(0.68F, -0.05F, 0.08F, -0.08F, -2, 352, 8),
		new Kf(0.85F, -0.02F, 0.04F, -0.03F, 0, 360, 3),
		new Kf(1.00F, 0, 0, 0, 0, 0, 0),
	};

	private static Kf sample(Kf[] keys, float t) {
		for (int i = 0; i < keys.length - 1; i++) {
			Kf a = keys[i];
			Kf b = keys[i + 1];
			if (t <= b.t()) {
				float u = (t - a.t()) / Math.max(1.0E-5F, b.t() - a.t());
				float e = ease(u);
				return new Kf(t,
					Mth.lerp(a.x(), b.x(), e), Mth.lerp(a.y(), b.y(), e), Mth.lerp(a.z(), b.z(), e),
					Mth.lerp(a.rx(), b.rx(), e), Mth.lerp(a.ry(), b.ry(), e), Mth.lerp(a.rz(), b.rz(), e));
			}
		}
		return keys[keys.length - 1];
	}

	private static void applyKf(PoseStack pose, float side, Kf k) {
		pose.translate(side * k.x(), k.y(), k.z());
		pose.mulPose(Axis.XP.rotationDegrees(k.rx()));
		pose.mulPose(Axis.YP.rotationDegrees(side * k.ry()));
		pose.mulPose(Axis.ZP.rotationDegrees(side * k.rz()));
	}

	public void apply(PoseStack pose, float pt) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null) {
			return;
		}
		ItemStack main = p.getMainHandItem();
		if (main.isEmpty() || !isKnifeLike(main)) {
			return;
		}
		long nowNs = System.nanoTime();
		float side = p.getMainArm() == HumanoidArm.LEFT ? -1F : 1F;
		float k = ImmersionHooks.global();
		float attackAnim = p.getAttackAnim(pt);

		// Rotations pivot around the hand grip so the knife turns in the fingers.
		pose.translate(0.56F * side, -0.52F, -0.72F);

		if (draw.isOn() && drawStart != 0) {
			float t = (nowNs - drawStart) / 1.0E9F / Math.max(0.05F, drawDuration.floatValue());
			if (t < 1F) {
				float e = easeOut(t);
				float d = 1F - e;
				pose.translate(side * 0.16F * d, -0.30F * d, 0.10F * d);
				pose.mulPose(Axis.XP.rotationDegrees(65F * d));
				pose.mulPose(Axis.YP.rotationDegrees(side * -18F * d));
				pose.mulPose(Axis.ZP.rotationDegrees(side * -30F * d));
				float settle = Mth.sin(e * Mth.PI) * (1F - t) * 0.5F;
				pose.mulPose(Axis.ZP.rotationDegrees(side * settle * 7F));
			} else {
				drawStart = 0;
			}
		}

		if (inspect.isOn() && inspectStart != 0) {
			float t = (nowNs - inspectStart) / 1.0E9F / Math.max(0.5F, inspectDuration.floatValue());
			if (t >= 1F || p.isUsingItem() || p.swinging || attackAnim > 0.01F) {
				inspectStart = 0;
			} else {
				applyKf(pose, side, sample(inspectStyle.is("Spin") ? INSPECT_SPIN : INSPECT_STANDARD, t));
			}
		}

		if (slash.isOn() && attackAnim > 0.01F && !p.isUsingItem()) {
			float bell = Mth.sin(attackAnim * Mth.PI);
			bell *= bell * (3F - 2F * bell); // sharper attack, soft release
			float s = slashStrength.floatValue() * k;
			pose.translate(side * -0.05F * bell * s, 0.02F * bell * s, 0);
			pose.mulPose(Axis.YP.rotationDegrees(side * -26F * bell * s));
			pose.mulPose(Axis.ZP.rotationDegrees(side * (attackAnim - 0.3F) * 22F * bell * s));
			pose.mulPose(Axis.XP.rotationDegrees(-14F * bell * s));
		}

		if (walkBob.isOn()) {
			float wd = p.walkAnimation.position(pt);
			float phase = wd * 1.9F;
			float s = bobStrength.floatValue() * k;
			pose.translate(0, Math.abs(Mth.sin(phase)) * 0.007F * s, 0);
			pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(phase) * 1.1F * s));
			pose.mulPose(Axis.XP.rotationDegrees(Mth.cos(phase * 2F) * 0.7F * s));
		}

		pose.translate(-0.56F * side, 0.52F, 0.72F);
	}
}
