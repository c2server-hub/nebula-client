package com.nebula.client.modules.immersion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.NumberSetting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Head bob, strafe sway, landing spring, rotation inertia, smooth sneak, trauma-based shake and eased FOV. */
public final class ImmersiveCamera extends Module {
    public final NumberSetting intensity = add(new NumberSetting("Intensity", "Master strength of every camera effect", 1.0, 0, 2, 0.05, "x"));

    public final BoolSetting bob = add(new BoolSetting("Head bob", "Speed-based head bob (walk / sprint)", true));
    public final NumberSetting bobAmount = add(new NumberSetting("Bob amount", "Head bob strength", 0.6, 0, 2, 0.05, "x"));
    public final NumberSetting sprintBob = add(new NumberSetting("Sprint bob", "Extra bob while sprinting", 1.25, 1, 2, 0.05, "x"));
    public final BoolSetting replaceBob = add(new BoolSetting("Replace vanilla bob", "Disable the vanilla view bobbing and use this one", true));

    public final BoolSetting sway = add(new BoolSetting("Strafe sway", "Camera rolls slightly when strafing / turning", true));
    public final NumberSetting swayAmount = add(new NumberSetting("Sway amount", "Roll strength", 0.6, 0, 2, 0.05, "x"));

    public final BoolSetting landing = add(new BoolSetting("Landing impact", "Camera dips when you land after a fall", true));
    public final NumberSetting landingAmount = add(new NumberSetting("Landing amount", "Landing dip strength", 0.7, 0, 2, 0.05, "x"));

    public final BoolSetting inertia = add(new BoolSetting("Rotation inertia", "View lags slightly behind the mouse", false));
    public final NumberSetting inertiaAmount = add(new NumberSetting("Inertia amount", "Rotation smoothing", 0.35, 0.05, 1, 0.05));

    public final BoolSetting smoothSneak = add(new BoolSetting("Smooth sneak", "Camera lowers progressively when sneaking", true));
    public final NumberSetting sneakSpeed = add(new NumberSetting("Sneak speed", "Higher = faster crouch transition", 6, 2, 14, 0.5));

    public final BoolSetting shake = add(new BoolSetting("Camera shake", "Shake on explosions, damage and nearby tremors", true));
    public final NumberSetting shakeAmount = add(new NumberSetting("Shake amount", "Shake strength", 0.6, 0, 2, 0.05, "x"));
    public final BoolSetting shakeDamage = add(new BoolSetting("Shake on damage", "Short kick when you get hurt", true));
    public final BoolSetting shakeExplosions = add(new BoolSetting("Shake on explosions", "Distance-based explosion shake", true));
    public final BoolSetting shakeNearby = add(new BoolSetting("Nearby tremors", "Warden, lit TNT, swelling creepers", true));

    public final BoolSetting dynamicFov = add(new BoolSetting("Dynamic FOV", "Softer, eased FOV changes (sprint, speed, bow)", true));
    public final NumberSetting fovStrength = add(new NumberSetting("FOV strength", "Fraction of the vanilla FOV change kept", 0.5, 0, 1, 0.05));
    public final NumberSetting fovSmoothing = add(new NumberSetting("FOV easing", "Higher = faster FOV transitions", 6, 1, 20, 0.5));

    // Tick state
    private float phase, phaseO, amp, ampO;
    private float roll, rollO, lastYaw;
    private float landPos, landPosO, landVel;
    private float trauma;
    private float lastVy;
    private int lastHurt;
    private float lastHealth = -1;
    private int scan;
    private float rumble;

    // Frame state
    private long lastFrame;
    private float eyeFast = -1, eyeSlow = -1;
    private float smoothYaw = Float.NaN, smoothPitch;
    private float fovCurrent = -1;

    public ImmersiveCamera() {
        super("immersive_camera", "Immersive Camera", "Head bob, sway, inertia, smooth sneak, shake & eased FOV", Category.IMMERSION, true, false);
    }

    public boolean replacesVanillaBob() {
        return bob.isOn() && replaceBob.isOn();
    }

    private float k() {
        return intensity.floatValue() * ImmersionHooks.global();
    }

    @Override
    protected void onDisable() {
        trauma = 0;
        smoothYaw = Float.NaN;
        eyeFast = eyeSlow = -1;
        fovCurrent = -1;
    }

    @Override
    public void onTick() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            return;
        }
        double dx = p.getX() - p.xo;
        double dz = p.getZ() - p.zo;
        float spd = (float) Math.sqrt(dx * dx + dz * dz);
        boolean grounded = p.onGround() && !p.isSwimming() && !p.isFallFlying() && !p.getAbilities().flying && !p.isPassenger();

        phaseO = phase;
        phase += spd * 2.6F;
        ampO = amp;
        float targetAmp = grounded ? Math.min(spd / 0.2F, 1.5F) * (p.isSprinting() ? sprintBob.floatValue() : 1F) : 0F;
        amp += (targetAmp - amp) * 0.3F;

        float yaw = p.getYRot();
        float turn = Mth.clamp(Mth.wrapDegrees(yaw - lastYaw), -30, 30);
        lastYaw = yaw;
        float yr = yaw * Mth.DEG_TO_RAD;
        float strafe = (float) (dx * -Mth.cos(yr) + dz * -Mth.sin(yr));
        rollO = roll;
        float rollTarget = (strafe * 18F + turn * 0.04F) * swayAmount.floatValue();
        roll += (Mth.clamp(rollTarget, -4F, 4F) - roll) * 0.25F;

        // Landing spring
        float vy = (float) p.getDeltaMovement().y;
        landPosO = landPos;
        if (p.onGround() && lastVy < -0.25F && !p.isInWater()) {
            float impulse = Mth.clamp((-lastVy - 0.2F) * 1.4F, 0F, 1.2F);
            landVel += impulse * 0.35F;
        }
        lastVy = vy;
        landVel += -landPos * 0.32F;
        landVel *= 0.68F;
        landPos += landVel;

        // Shake sources
        trauma = Math.max(0F, trauma - 0.03F);
        if (shakeDamage.isOn()) {
            float h = p.getHealth();
            if (p.hurtTime > lastHurt && p.hurtTime > 0) {
                float lost = lastHealth < 0 ? 2F : Math.max(0F, lastHealth - h);
                addTrauma(0.22F + Math.min(0.4F, lost * 0.05F));
            }
            lastHealth = h;
        }
        lastHurt = p.hurtTime;
        if (shakeNearby.isOn() && ++scan % 4 == 0) {
            rumble = 0F;
            AABB box = p.getBoundingBox().inflate(28);
            for (Warden w : p.level().getEntitiesOfClass(Warden.class, box)) {
                float f = 1F - (float) w.distanceTo(p) / 28F;
                if (f <= 0) {
                    continue;
                }
                float base = w.hasPose(Pose.ROARING) ? 0.12F : (w.hasPose(Pose.EMERGING) || w.hasPose(Pose.DIGGING)) ? 0.08F
                    : w.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4 ? 0.025F : 0F;
                rumble = Math.max(rumble, base * f * f);
            }
            for (PrimedTnt t : p.level().getEntitiesOfClass(PrimedTnt.class, p.getBoundingBox().inflate(10))) {
                if (t.getFuse() < 30) {
                    rumble = Math.max(rumble, 0.02F * (1F - (float) t.distanceTo(p) / 10F));
                }
            }
            for (Creeper c : p.level().getEntitiesOfClass(Creeper.class, p.getBoundingBox().inflate(8))) {
                if (c.getSwelling(1F) > 0.3F) {
                    rumble = Math.max(rumble, 0.015F * (1F - (float) c.distanceTo(p) / 8F));
                }
            }
        }
        if (rumble > 0) {
            trauma = Math.min(1F, Math.max(trauma, Math.min(0.6F, trauma + rumble)));
        }
    }

    public void addTrauma(float t) {
        if (shake.isOn()) {
            trauma = Math.min(1F, trauma + t);
        }
    }

    public void onExplosion(Vec3 center) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !shakeExplosions.isOn()) {
            return;
        }
        float f = 1F - (float) p.getEyePosition().distanceTo(center) / 32F;
        if (f > 0) {
            addTrauma(0.9F * f * f + 0.1F * f);
        }
    }

    private float frameDt() {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016F : Mth.clamp((now - lastFrame) / 1.0E9F, 0.0005F, 0.1F);
        lastFrame = now;
        return dt;
    }

    private static float noise(float t, float seed) {
        return (Mth.sin(t * 1.0F + seed) * 0.5F + Mth.sin(t * 2.3F + seed * 1.7F) * 0.3F + Mth.sin(t * 4.7F + seed * 2.9F) * 0.2F);
    }

    /** World pass: called right after vanilla hurt tilt, before the camera rotation is applied. */
    public void applyWorld(PoseStack pose, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        float dt = frameDt();
        float k = k();
        boolean firstPerson = mc.options.getCameraType() == CameraType.FIRST_PERSON;

        float tx = 0, ty = 0, rollDeg = 0, pitchOff = 0, yawOff = 0;
        if (firstPerson) {
            if (bob.isOn()) {
                float ph = Mth.lerp(pt, phaseO, phase);
                float a = Mth.lerp(pt, ampO, amp) * bobAmount.floatValue() * k;
                tx += Mth.sin(ph) * a * 0.022F;
                ty -= Math.abs(Mth.cos(ph)) * a * 0.035F;
                rollDeg += Mth.sin(ph) * a * 0.9F;
                pitchOff += Math.abs(Mth.cos(ph - 0.2F)) * a * 0.8F;
            }
            if (sway.isOn()) {
                rollDeg += Mth.lerp(pt, rollO, roll) * k;
            }
            if (landing.isOn()) {
                float lp = Mth.lerp(pt, landPosO, landPos) * landingAmount.floatValue() * k;
                ty += lp * 0.18F;
                pitchOff += lp * 6F;
            }
            if (smoothSneak.isOn() && p != null) {
                float target = p.getEyeHeight();
                if (eyeFast < 0 || Math.abs(eyeFast - target) > 0.9F) {
                    eyeFast = eyeSlow = target;
                }
                eyeFast += (target - eyeFast) * (1F - (float) Math.exp(-dt * 13.8F));
                eyeSlow += (target - eyeSlow) * (1F - (float) Math.exp(-dt * sneakSpeed.floatValue()));
                // Camera is at vanilla height (fast); move it to the slow height.
                ty -= (eyeSlow - eyeFast);
            }
            if (inertia.isOn() && p != null) {
                float yaw = p.getViewYRot(pt);
                float pitch = p.getViewXRot(pt);
                if (Float.isNaN(smoothYaw) || Math.abs(Mth.wrapDegrees(yaw - smoothYaw)) > 45F) {
                    smoothYaw = yaw;
                    smoothPitch = pitch;
                }
                float rate = Mth.lerp(Mth.clamp(inertiaAmount.floatValue() * k, 0F, 1F), 40F, 6F);
                float a = 1F - (float) Math.exp(-dt * rate);
                smoothYaw += Mth.wrapDegrees(yaw - smoothYaw) * a;
                smoothPitch += (pitch - smoothPitch) * a;
                yawOff = Mth.clamp(Mth.wrapDegrees(smoothYaw - yaw), -8F, 8F);
                pitchOff += Mth.clamp(smoothPitch - pitch, -8F, 8F);
            } else {
                smoothYaw = Float.NaN;
            }
        }

        float shakeYaw = 0, shakePitch = 0, shakeRoll = 0;
        if (shake.isOn() && trauma > 0.001F) {
            float s = trauma * trauma * shakeAmount.floatValue() * k;
            float t = (System.nanoTime() % 1_000_000_000_000L) / 1.0E9F * 18F;
            shakeYaw = noise(t, 1.3F) * 3F * s;
            shakePitch = noise(t, 7.1F) * 3F * s;
            shakeRoll = noise(t, 3.7F) * 4.5F * s;
        }

        if (tx != 0 || ty != 0) {
            pose.translate(tx, ty, 0);
        }
        rollDeg += shakeRoll;
        if (rollDeg != 0) {
            pose.mulPose(Axis.ZP.rotationDegrees(rollDeg));
        }
        float pitchExtra = Mth.clamp(pitchOff + shakePitch, -12F, 12F);
        float yawExtra = yawOff + shakeYaw;
        if (yawExtra != 0 && p != null) {
            // Equivalent to re-rotating the camera: Rx(p + dp) * Ry(dy) * Rx(-p)
            float pitch = p.getViewXRot(pt);
            pose.mulPose(Axis.XP.rotationDegrees(pitch + pitchExtra));
            pose.mulPose(Axis.YP.rotationDegrees(yawExtra));
            pose.mulPose(Axis.XP.rotationDegrees(-pitch));
        } else if (pitchExtra != 0) {
            pose.mulPose(Axis.XP.rotationDegrees(pitchExtra));
        }
    }

    /** Hand pass: a little of the camera motion so the arm follows the head. */
    public void applyHand(PoseStack pose, float pt) {
        float k = k();
        if (replacesVanillaBob() && Minecraft.getInstance().options.bobView().get()) {
            float ph = Mth.lerp(pt, phaseO, phase);
            float a = Mth.lerp(pt, ampO, amp) * bobAmount.floatValue() * k;
            pose.translate(Mth.sin(ph) * a * 0.012F, -Math.abs(Mth.cos(ph)) * a * 0.02F, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(ph) * a * 0.6F));
        }
        if (shake.isOn() && trauma > 0.001F) {
            float s = trauma * trauma * shakeAmount.floatValue() * k * 0.35F;
            float t = (System.nanoTime() % 1_000_000_000_000L) / 1.0E9F * 18F;
            pose.mulPose(Axis.XP.rotationDegrees(noise(t, 5.3F) * 3F * s));
            pose.mulPose(Axis.YP.rotationDegrees(noise(t, 9.1F) * 3F * s));
        }
    }

    public float modifyFov(float vanilla) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (!dynamicFov.isOn() || p == null) {
            fovCurrent = -1;
            return vanilla;
        }
        float base = mc.options.fov().get().floatValue();
        if (p.isScoping() || vanilla < base * 0.6F) {
            fovCurrent = vanilla;
            return vanilla;
        }
        float target = base + (vanilla - base) * fovStrength.floatValue();
        long now = System.nanoTime();
        if (fovCurrent < 0) {
            fovCurrent = target;
        }
        float dt = fovLast == 0 ? 0.016F : Mth.clamp((now - fovLast) / 1.0E9F, 0F, 0.1F);
        fovLast = now;
        fovCurrent += (target - fovCurrent) * (1F - (float) Math.exp(-dt * fovSmoothing.floatValue()));
        return fovCurrent;
    }

    private long fovLast;
}
