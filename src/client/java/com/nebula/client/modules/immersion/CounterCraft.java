package com.nebula.client.modules.immersion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.settings.ActionSetting;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.KeybindSetting;
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.Animation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * CounterCraft knives: the CS knife models shipped inside the client itself (no separate resource
 * pack — rename a sword or an axe after one of the knives, e.g. "M9 Bayonet") are animated the
 * Counter-Strike way: weapon draw out of the holster, a full inspect spin, alternating slashes on
 * attack, a heavy stab on right-click and a CS-like guard stance with weapon sway and micro noise.
 */
public final class CounterCraft extends Module {
    public final NumberSetting intensity = add(new NumberSetting("Intensity", "Master strength of the knife animations", 1.0, 0, 2, 0.05, "x"));
    public final BoolSetting draw = add(new BoolSetting("Draw", "Knife is spun out of the holster when selected", true));
    public final NumberSetting drawDuration = add(new NumberSetting("Draw time", "Length of the weapon draw", 0.45, 0.25, 0.8, 0.05, "s"));
    public final BoolSetting inspect = add(new BoolSetting("Inspect", "Full CS-style inspect spin of the knife", true));
    public final KeybindSetting inspectKey = add(new KeybindSetting("Inspect key", "Key that plays the inspect animation", GLFW.GLFW_KEY_G));
    public final NumberSetting inspectDuration = add(new NumberSetting("Inspect time", "Length of the inspect animation", 2.6, 1.2, 5, 0.1, "s"));
    public final BoolSetting slash = add(new BoolSetting("Slash", "Diagonal slashes when attacking with the knife", true));
    public final ModeSetting slashStyle = add(new ModeSetting("Slash style", "Direction pattern of the slashes", "Alternate", "Alternate", "Left only", "Right only"));
    public final BoolSetting stab = add(new BoolSetting("Stab", "Heavy stab lunge when right-clicking the air", true));
    public final BoolSetting stance = add(new BoolSetting("CS stance", "Idle guard pose and micro sway of the knife", true));
    public final BoolSetting sway = add(new BoolSetting("Weapon sway", "Knife lags slightly behind the camera", true));
    public final NumberSetting swayAmount = add(new NumberSetting("Sway amount", "Weapon sway strength", 1.0, 0, 2, 0.05, "x"));
    public final BoolSetting sounds = add(new BoolSetting("Knife sounds", "Draw, inspect, slash & stab sounds", true));
    public final ActionSetting preview = add(new ActionSetting("Preview inspect", "Plays the inspect animation right now", this::triggerInspect));

    /** Exact display names understood by the embedded item definitions (assets/minecraft/items). */
    private static final List<String> KNIVES = List.of(
            "Bayonet", "Bayonet Gradient", "Bayonet Violet",
            "M9 Bayonet", "M9 Bayonet Gradient", "M9 Bayonet Violet",
            "Ursus", "Ursus Gem", "Ursus Violet",
            "Stilletto", "Dark Stilletto",
            "Guonith", "Guonith Au");

    private static final float SLASH_TIME = 0.28F;
    private static final float STAB_TIME = 0.5F;
    private static final float PIVOT_X = 0.56F, PIVOT_Y = -0.52F, PIVOT_Z = -0.72F;

    // ---------------------------------------------------------------------------------------------
    // Keyframe tracks: (time, value) pairs in [0..1] time, cosine-interpolated. Values are model
    // units for translations and degrees for rotations, always relative to the idle pose.
    // ---------------------------------------------------------------------------------------------
    private static final Track DRAW_Y = Track.of(0, -0.62F, 0.5F, -0.20F, 0.78F, 0.03F, 1, 0);
    private static final Track DRAW_PITCH = Track.of(0, -96F, 0.5F, -40F, 0.78F, 7F, 1, 0);
    private static final Track DRAW_YAW = Track.of(0, -34F, 0.6F, -10F, 1, 0);
    private static final Track DRAW_ROLL = Track.of(0, -22F, 0.6F, -3F, 1, 0);
    private static final Track DRAW_Z = Track.of(0, 0.12F, 1, 0);

    private static final Track INSP_Y = Track.of(0, 0, 0.14F, 0.14F, 0.72F, 0.14F, 0.88F, -0.012F, 1, 0);
    private static final Track INSP_Z = Track.of(0, 0, 0.14F, 0.09F, 0.72F, 0.09F, 1, 0);
    private static final Track INSP_X = Track.of(0, 0, 0.14F, 0.045F, 0.72F, 0.045F, 1, 0);
    private static final Track INSP_SPIN = Track.of(0, 0, 0.18F, 0, 0.5F, 180F, 0.84F, 360F, 1, 360F);
    private static final Track INSP_ROLL = Track.of(0, 0, 0.16F, 20F, 0.5F, 24F, 0.84F, -8F, 1, 0);
    private static final Track INSP_PITCH = Track.of(0, 0, 0.16F, -13F, 0.5F, -9F, 0.84F, 5F, 1, 0);

    private static final Track ATTACK_ENV = Track.of(0, 0, 0.2F, 1F, 0.6F, 0.35F, 1, 0);

    private static final Track STAB_Z = Track.of(0, 0, 0.18F, 0.10F, 0.30F, -0.44F, 0.42F, -0.36F, 1, 0);
    private static final Track STAB_Y = Track.of(0, 0, 0.18F, 0.02F, 0.30F, -0.03F, 1, 0);
    private static final Track STAB_PITCH = Track.of(0, 0, 0.18F, 11F, 0.30F, -15F, 0.5F, -6F, 1, 0);
    private static final Track STAB_YAW = Track.of(0, 0, 0.18F, -6F, 0.30F, 4F, 1, 0);

    // Sound designs (vanilla events, pitched like CS weapon foley).
    private static final SoundCue CUE_DRAW = new SoundCue(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.45F, 1.35F);
    private static final SoundCue CUE_INSPECT = new SoundCue(SoundEvents.BOOK_PAGE_TURN, 0.5F, 1.2F);
    private static final SoundCue CUE_SLASH = new SoundCue(SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 1.25F);
    private static final SoundCue CUE_STAB = new SoundCue(SoundEvents.PLAYER_ATTACK_STRONG, 0.7F, 0.95F);

    // Tick-sampled state.
    private String knifeName;
    private ItemStack lastStack = ItemStack.EMPTY;
    private boolean wasSwinging;
    private boolean useWasDown;
    private boolean inspectWasDown;
    private int slashDir = 1;

    // Animation clocks (nanoseconds; 0 = idle).
    private long drawStart, inspectStart, slashStart, stabStart;
    private boolean stabSoundPlayed;
    private long lastFrame;
    private float lastYaw = Float.NaN, lastPitch, lagYaw, lagPitch;
    private final Animation stanceAmt = new Animation(6F);

    public CounterCraft() {
        super("countercraft", "CounterCraft Knives", "CS knives with draw, inspect, slash & stab animations", Category.IMMERSION, true, false);
    }

    // ---------------------------------------------------------------------------------------------
    // Knife detection — mirrors the embedded items/*.json select on the custom name component.
    // ---------------------------------------------------------------------------------------------
    private static String knifeId(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.has(DataComponents.CUSTOM_NAME)) {
            return null;
        }
        String name = stack.getHoverName().getString();
        return KNIVES.contains(name) ? name : null;
    }

    /** True while the main hand item renders as one of the embedded knives (sampled each tick). */
    public boolean isHoldingKnife() {
        return knifeName != null;
    }

    public void triggerInspect() {
        if (knifeName != null) {
            inspectStart = System.nanoTime();
            play(CUE_INSPECT);
        }
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) {
            reset();
            return;
        }

        ItemStack now = p.getMainHandItem();
        String nowKnife = knifeId(now);
        if (!ItemStack.isSameItem(now, lastStack) || !java.util.Objects.equals(nowKnife, knifeName)) {
            // Freshly selected knife: play the weapon draw.
            inspectStart = 0;
            slashStart = 0;
            stabStart = 0;
            drawStart = nowKnife != null ? System.nanoTime() : 0;
            if (nowKnife != null && draw.isOn()) {
                play(CUE_DRAW);
            }
        }
        knifeName = nowKnife;
        lastStack = now.copy();

        if (inspect.isOn() && inspectKey.isBound() && mc.screen == null) {
            boolean down = GLFW.glfwGetKey(mc.getWindow().handle(), inspectKey.key()) == GLFW.GLFW_PRESS;
            if (down && !inspectWasDown && knifeName != null && !p.isUsingItem() && !p.swinging) {
                triggerInspect();
            }
            inspectWasDown = down;
        }

        if (slash.isOn() && knifeName != null) {
            boolean swinging = p.swinging && p.swingingArm == InteractionHand.MAIN_HAND;
            if (swinging && !wasSwinging) {
                slashStart = System.nanoTime();
                if (slashStyle.is("Alternate")) {
                    slashDir = -slashDir;
                } else {
                    slashDir = slashStyle.is("Left only") ? 1 : -1;
                }
                play(CUE_SLASH);
            }
            wasSwinging = swinging;
        } else {
            wasSwinging = p.swinging;
        }

        if (stab.isOn() && knifeName != null && mc.screen == null && !p.isUsingItem()
                && (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.ENTITY)) {
            boolean down = mc.options.keyUse.isDown();
            if (down && !useWasDown) {
                stabStart = System.nanoTime();
                stabSoundPlayed = false;
                inspectStart = 0;
            }
            useWasDown = down;
        } else {
            useWasDown = mc.options.keyUse.isDown();
        }
    }

    private void reset() {
        knifeName = null;
        inspectStart = 0;
        slashStart = 0;
        stabStart = 0;
        drawStart = 0;
    }

    // ---------------------------------------------------------------------------------------------
    // Rendering: contributions of every active animation, applied around the vanilla item anchor.
    // ---------------------------------------------------------------------------------------------
    public void apply(PoseStack pose, float pt) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || knifeName == null) {
            stanceAmt.update(0);
            return;
        }
        long nowNs = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016F : Mth.clamp((nowNs - lastFrame) / 1.0E9F, 0.0005F, 0.1F);
        lastFrame = nowNs;
        float k = intensity.floatValue() * ImmersionHooks.global();
        float side = p.getMainArm() == HumanoidArm.LEFT ? -1F : 1F;

        float tx = 0, ty = 0, tz = 0;
        float yaw = 0, pitch = 0, roll = 0;
        float spin = 0, spinTilt = 0; // dedicated tilted-axis inspect spin

        if (sway.isOn()) {
            float viewYaw = p.getViewYRot(pt);
            float viewPitch = p.getViewXRot(pt);
            if (Float.isNaN(lastYaw)) {
                lastYaw = viewYaw;
                lastPitch = viewPitch;
            }
            lagYaw += Mth.wrapDegrees(viewYaw - lastYaw);
            lagPitch += viewPitch - lastPitch;
            lastYaw = viewYaw;
            lastPitch = viewPitch;
            float decay = (float) Math.exp(-dt * 11F); // snappier than the generic hand module
            lagYaw = Mth.clamp(lagYaw * decay, -30F, 30F);
            lagPitch = Mth.clamp(lagPitch * decay, -30F, 30F);
            float s = swayAmount.floatValue() * k;
            tx += lagYaw * 0.0038F * s;
            ty += -lagPitch * 0.0038F * s;
            yaw += -lagYaw * 0.16F * s;
        }

        if (stance.isOn()) {
            float stance = stanceAmt.update(1F);
            if (stance > 0.001F) {
                // CS guard hold: the blade is raised into view and canted like a ready stance.
                yaw += -8F * stance;
                pitch += 6F * stance;
                ty += 0.09F * stance;
                tz += 0.02F * stance;
                double vx = p.getX() - p.xo, vz = p.getZ() - p.zo;
                float moving = Mth.clamp((float) Math.sqrt(vx * vx + vz * vz) * 20F, 0F, 1F);
                float micro = stance * (1F - 0.6F * moving);
                float t = (nowNs % 4_000_000_000L) / 1.0E9F;
                yaw += Mth.sin(t * 0.9F) * 0.9F * micro;
                roll += Mth.sin(t * 1.6F + 0.7F) * 0.7F * micro;
                pitch += Mth.sin(t * 1.2F + 0.3F) * 0.5F * micro;
                ty += Mth.sin(t * 1.6F) * 0.004F * micro;
            }
        } else {
            stanceAmt.update(0F);
        }

        if (drawStart != 0) {
            float t = (nowNs - drawStart) / 1.0E9F / drawDuration.floatValue();
            if (t >= 1F) {
                drawStart = 0;
            } else {
                ty += DRAW_Y.at(t);
                tz += DRAW_Z.at(t);
                yaw += DRAW_YAW.at(t);
                pitch += DRAW_PITCH.at(t);
                roll += DRAW_ROLL.at(t);
            }
        }

        if (inspectStart != 0) {
            float t = (nowNs - inspectStart) / 1.0E9F / inspectDuration.floatValue();
            if (t >= 1F || p.isUsingItem() || p.swinging) {
                inspectStart = 0;
            } else {
                ty += INSP_Y.at(t);
                tz += INSP_Z.at(t);
                tx += INSP_X.at(t) * side;
                roll += INSP_ROLL.at(t);
                pitch += INSP_PITCH.at(t);
                spin = INSP_SPIN.at(t);
                spinTilt = 28F;
                // Organic wobble while the blade is held up.
                float hold = Math.min(1F, Math.min(t / 0.2F, (1F - t) / 0.2F));
                float wt = (nowNs % 1_000_000_000L) / 1.0E9F;
                roll += Mth.sin(wt * 9F) * 1.5F * hold;
                yaw += Mth.sin(wt * 7F + 1.2F) * 1.2F * hold;
                // Blade tip dips slightly twice per revolution, like a real inspect flip.
                pitch += Mth.sin(Mth.DEG_TO_RAD * spin * 2F) * 4F;
            }
        }

        if (slashStart != 0) {
            float t = (nowNs - slashStart) / 1.0E9F / SLASH_TIME;
            if (t >= 1F) {
                slashStart = 0;
            } else {
                float env = ATTACK_ENV.at(t);
                float d = slashDir;
                yaw += 19F * d * env;
                roll += 11F * d * env;
                pitch += -7F * env;
                tz += -0.16F * env;
                tx += -0.04F * d * env;
                ty += 0.03F * env;
            }
        }

        if (stabStart != 0) {
            float t = (nowNs - stabStart) / 1.0E9F / STAB_TIME;
            if (t >= 1F || p.isUsingItem()) {
                stabStart = 0;
            } else {
                tz += STAB_Z.at(t);
                ty += STAB_Y.at(t);
                pitch += STAB_PITCH.at(t);
                yaw += STAB_YAW.at(t);
                if (!stabSoundPlayed && t >= 0.2F) {
                    stabSoundPlayed = true;
                    play(CUE_STAB);
                }
            }
        }

        // Apply everything around the vanilla first-person item anchor, so rotations pivot on
        // the grip instead of throwing the whole view model across the screen.
        pose.translate(PIVOT_X * side, PIVOT_Y, PIVOT_Z);
        pose.translate(tx * k, ty * k, tz * k);
        if (spin != 0) {
            pose.mulPose(Axis.ZP.rotationDegrees(spinTilt * side));
            pose.mulPose(Axis.YP.rotationDegrees(spin * side));
            pose.mulPose(Axis.ZP.rotationDegrees(-spinTilt * side));
        }
        if (yaw != 0) {
            pose.mulPose(Axis.YP.rotationDegrees(yaw * k));
        }
        if (pitch != 0) {
            pose.mulPose(Axis.XP.rotationDegrees(pitch * k));
        }
        if (roll != 0) {
            pose.mulPose(Axis.ZP.rotationDegrees(roll * k));
        }
        pose.translate(-PIVOT_X * side, -PIVOT_Y, -PIVOT_Z);
    }

    private void play(SoundCue cue) {
        if (!sounds.isOn()) {
            return;
        }
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            return;
        }
        try {
            p.playSound(cue.event(), cue.volume(), cue.pitch() * (0.92F + p.getRandom().nextFloat() * 0.16F));
        } catch (RuntimeException ignored) {
            // Sound is cosmetic; never break the frame over it.
        }
    }

    private record SoundCue(net.minecraft.sounds.SoundEvent event, float volume, float pitch) {
    }

    // ---------------------------------------------------------------------------------------------
    // Piecewise keyframe track with smoothstep interpolation between the keys.
    // ---------------------------------------------------------------------------------------------
    private static final class Track {
        private final float[] time, value;

        private Track(float[] flat) {
            int n = flat.length / 2;
            this.time = new float[n];
            this.value = new float[n];
            for (int i = 0; i < n; i++) {
                time[i] = flat[i * 2];
                value[i] = flat[i * 2 + 1];
                if (i > 0 && time[i] <= time[i - 1]) {
                    throw new IllegalArgumentException("Track keys must be strictly increasing");
                }
            }
        }

        static Track of(float... flat) {
            return new Track(flat);
        }

        float at(float t) {
            if (t <= time[0]) {
                return value[0];
            }
            for (int i = 1; i < time.length; i++) {
                if (t <= time[i]) {
                    float f = (t - time[i - 1]) / (time[i] - time[i - 1]);
                    f = f * f * (3F - 2F * f); // smoothstep: no robotic linear motion
                    return value[i - 1] + (value[i] - value[i - 1]) * f;
                }
            }
            return value[value.length - 1];
        }
    }
}
