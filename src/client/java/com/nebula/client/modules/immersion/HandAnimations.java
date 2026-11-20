package com.nebula.client.modules.immersion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.KeybindSetting;
import com.nebula.client.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** Inspect key, equip dip, hand sway, visible bow tension, weighted shield raise and idle breathing. */
public final class HandAnimations extends Module {
    public final NumberSetting intensity = add(new NumberSetting("Intensity", "Master strength of hand animations", 1.0, 0, 2, 0.05, "x"));
    public final BoolSetting inspect = add(new BoolSetting("Inspect", "Turn the held item over with a key", true));
    public final KeybindSetting inspectKey = add(new KeybindSetting("Inspect key", "Key that plays the inspect animation", GLFW.GLFW_KEY_G));
    public final NumberSetting inspectDuration = add(new NumberSetting("Inspect duration", "Length of the inspect animation", 2.4, 1, 5, 0.1, "s"));
    public final BoolSetting equip = add(new BoolSetting("Equip dip", "Item dips and comes back up when switching", true));
    public final NumberSetting equipAmount = add(new NumberSetting("Equip amount", "Equip dip strength", 1.0, 0, 2, 0.05, "x"));
    public final BoolSetting sway = add(new BoolSetting("Hand sway", "Hands lag slightly behind the camera", true));
    public final NumberSetting swayAmount = add(new NumberSetting("Sway amount", "Hand sway strength", 1.0, 0, 2, 0.05, "x"));
    public final BoolSetting bowTension = add(new BoolSetting("Bow tension", "Bow is pulled back and trembles when fully drawn", true));
    public final NumberSetting bowShake = add(new NumberSetting("Bow tremble", "Tremble when holding a full draw", 1.0, 0, 2, 0.05, "x"));
    public final BoolSetting shield = add(new BoolSetting("Heavy shield", "Shield is raised with weight and recoil", true));
    public final BoolSetting breathing = add(new BoolSetting("Idle breathing", "Very slow breathing motion when standing still", true));

    private long inspectStart;
    private boolean keyWasDown;
    private ItemStack lastStack = ItemStack.EMPTY;
    private long equipStart;
    private float lastYaw = Float.NaN, lastPitch;
    private float lagYaw, lagPitch;
    private float shieldAmt;
    private long lastFrame;
    private float holdFull;

    public HandAnimations() {
        super("hand_animations", "Hand Animations", "Inspect, equip, sway, bow tension & shield", Category.IMMERSION, true, false);
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
            equipStart = System.nanoTime();
            inspectStart = 0;
        }
        lastStack = now.copy();

        if (inspect.isOn() && inspectKey.isBound() && mc.screen == null) {
            boolean down = GLFW.glfwGetKey(mc.getWindow().handle(), inspectKey.key()) == GLFW.GLFW_PRESS;
            if (down && !keyWasDown && !now.isEmpty()) {
                inspectStart = System.nanoTime();
            }
            keyWasDown = down;
        }
        if (p.isUsingItem() && p.getUseItem().getItem() instanceof BowItem && BowItem.getPowerForTime(p.getTicksUsingItem()) >= 1F) {
            holdFull = Math.min(1F, holdFull + 0.02F);
        } else {
            holdFull = 0F;
        }
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    public void apply(PoseStack pose, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) {
            return;
        }
        long nowNs = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016F : Mth.clamp((nowNs - lastFrame) / 1.0E9F, 0.0005F, 0.1F);
        lastFrame = nowNs;
        float k = intensity.floatValue() * ImmersionHooks.global();
        float side = p.getMainArm() == HumanoidArm.LEFT ? -1F : 1F;

        if (sway.isOn()) {
            float yaw = p.getViewYRot(pt);
            float pitch = p.getViewXRot(pt);
            if (Float.isNaN(lastYaw)) {
                lastYaw = yaw;
                lastPitch = pitch;
            }
            lagYaw += Mth.wrapDegrees(yaw - lastYaw);
            lagPitch += pitch - lastPitch;
            lastYaw = yaw;
            lastPitch = pitch;
            float decay = (float) Math.exp(-dt * 9F);
            lagYaw = Mth.clamp(lagYaw * decay, -25F, 25F);
            lagPitch = Mth.clamp(lagPitch * decay, -25F, 25F);
            float s = swayAmount.floatValue() * k;
            pose.translate(lagYaw * 0.0035F * s, -lagPitch * 0.0035F * s, 0);
            pose.mulPose(Axis.YP.rotationDegrees(-lagYaw * 0.15F * s));
        }

        if (breathing.isOn()) {
            double vx = p.getX() - p.xo, vz = p.getZ() - p.zo;
            if (vx * vx + vz * vz < 1.0E-4) {
                float t = (nowNs % 100_000_000_000L) / 1.0E9F;
                pose.translate(0, Mth.sin(t * 1.6F) * 0.006F * k, 0);
            }
        }

        if (equip.isOn() && equipStart != 0) {
            float t = (nowNs - equipStart) / 1.0E9F / 0.4F;
            if (t < 1F) {
                float d = (1F - ease(t)) * equipAmount.floatValue() * k;
                pose.translate(0, -0.12F * d, 0);
                pose.mulPose(Axis.XP.rotationDegrees(-25F * d));
            }
        }

        if (inspect.isOn() && inspectStart != 0) {
            float dur = inspectDuration.floatValue();
            float t = (nowNs - inspectStart) / 1.0E9F / dur;
            if (t >= 1F || p.isUsingItem() || p.swinging) {
                inspectStart = 0;
            } else {
                float e = ease(Math.min(1F, t * 5F)) * ease(Math.min(1F, (1F - t) * 5F)) * Math.min(1F, k);
                float turn = -40F * Mth.cos(t * Mth.PI);
                pose.translate(0.56F * side, -0.52F, -0.72F);
                pose.translate(-side * 0.12F * e, 0.08F * e, 0.08F * e);
                pose.mulPose(Axis.YP.rotationDegrees(side * e * turn));
                pose.mulPose(Axis.ZP.rotationDegrees(side * e * 14F));
                pose.mulPose(Axis.XP.rotationDegrees(-e * 12F));
                pose.translate(-0.56F * side, 0.52F, 0.72F);
            }
        }

        if (bowTension.isOn() && p.isUsingItem() && p.getUseItem().getItem() instanceof BowItem) {
            float power = BowItem.getPowerForTime(p.getTicksUsingItem());
            if (power > 0.1F) {
                float pull = ease(power) * k;
                pose.translate(0, 0, 0.04F * pull);
                float tremble = (power >= 1F ? 0.3F + holdFull : power > 0.5F ? (power - 0.5F) * 0.4F : 0F) * bowShake.floatValue() * k;
                if (tremble > 0) {
                    float t = (nowNs % 10_000_000_000L) / 1.0E9F;
                    pose.translate(Mth.sin(t * 47F) * 0.0025F * tremble, Mth.sin(t * 61F + 1.3F) * 0.0025F * tremble, 0);
                    pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 39F) * 0.4F * tremble));
                }
            }
        }

        if (shield.isOn()) {
            float target = p.isBlocking() ? 1F : 0F;
            float rate = target > shieldAmt ? 7F : 10F;
            shieldAmt += (target - shieldAmt) * (1F - (float) Math.exp(-dt * rate));
            if (shieldAmt > 0.01F) {
                // Overshoot curve gives the raise a bit of weight.
                float s = shieldAmt;
                float over = Mth.sin(s * Mth.PI) * 0.35F;
                float shieldSide = p.getUsedItemHand() == net.minecraft.world.InteractionHand.OFF_HAND ? -side : side;
                pose.translate(-0.04F * shieldSide * s * k, 0.03F * s * k - 0.02F * over * k, 0.03F * s * k);
                pose.mulPose(Axis.ZP.rotationDegrees(shieldSide * (4F * s + 3F * over) * k));
            }
        }
    }
}
