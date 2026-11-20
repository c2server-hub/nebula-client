package com.nebula.client.modules.immersion;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Space-aware reverb (cave / hall / outdoors), underwater muffling and occlusion behind walls (OpenAL EFX). */
public final class ImmersiveSound extends Module {
    public final BoolSetting reverb = add(new BoolSetting("Reverb", "Echo that depends on the space around you", true));
    public final NumberSetting reverbAmount = add(new NumberSetting("Reverb amount", "Reverb send level", 0.6, 0, 1.5, 0.05, "x"));
    public final BoolSetting muffleUnderwater = add(new BoolSetting("Underwater muffling", "Low-pass filter when your head is in water", true));
    public final NumberSetting muffleAmount = add(new NumberSetting("Muffle amount", "Underwater muffling strength", 1.0, 0, 1, 0.05));
    public final BoolSetting occlusion = add(new BoolSetting("Wall occlusion", "Sounds behind solid blocks are muffled", true));
    public final NumberSetting occlusionAmount = add(new NumberSetting("Occlusion amount", "Strength of wall muffling", 1.0, 0, 1.5, 0.05));

    private static final Vec3[] DIRS = buildDirs();
    private int counter;
    private float decay = 0.4F, gain = 0F;

    public ImmersiveSound() {
        super("immersive_sound", "Immersive Sound", "Reverb, underwater muffling and wall occlusion", Category.IMMERSION, true, false);
    }

    private static Vec3[] buildDirs() {
        Vec3[] d = new Vec3[14];
        int i = 0;
        d[i++] = new Vec3(0, 1, 0);
        d[i++] = new Vec3(0, -1, 0);
        for (int a = 0; a < 6; a++) {
            double ang = a * Math.PI / 3;
            d[i++] = new Vec3(Math.cos(ang), 0, Math.sin(ang));
        }
        for (int a = 0; a < 6; a++) {
            double ang = a * Math.PI / 3 + Math.PI / 6;
            d[i++] = new Vec3(Math.cos(ang) * 0.7, 0.7, Math.sin(ang) * 0.7).normalize();
        }
        return d;
    }

    @Override
    protected void onEnable() {
        SoundFx.active = true;
    }

    @Override
    protected void onDisable() {
        SoundFx.active = false;
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            return;
        }
        Vec3 eye = p.getEyePosition();
        SoundFx.lx = eye.x;
        SoundFx.ly = eye.y;
        SoundFx.lz = eye.z;
        SoundFx.reverbOn = reverb.isOn();
        SoundFx.muffleOn = muffleUnderwater.isOn();
        SoundFx.occlusionOn = occlusion.isOn();
        SoundFx.underwater = Env.underwater;
        SoundFx.underwaterAmount = muffleAmount.floatValue();
        SoundFx.occlusionAmount = occlusionAmount.floatValue();
        SoundFx.active = true;

        if (++counter % 5 == 0) {
            int hits = 0;
            double dist = 0;
            for (Vec3 d : DIRS) {
                BlockHitResult hit = mc.level.clip(new ClipContext(eye, eye.add(d.scale(28)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                if (hit.getType() != HitResult.Type.MISS) {
                    hits++;
                    dist += hit.getLocation().distanceTo(eye);
                }
            }
            float enclosure = hits / (float) DIRS.length;
            float size = hits == 0 ? 1F : (float) Math.min(1.0, (dist / hits) / 20.0);
            float targetDecay = 0.25F + enclosure * (0.5F + size * 5.5F);
            if (Env.sky) {
                targetDecay *= 0.55F;
            }
            float targetGain = (float) Math.pow(enclosure, 1.3) * (Env.sky ? 0.35F : 1F) * reverbAmount.floatValue() * 0.85F;
            if (Env.underwater) {
                targetDecay = 2.2F;
                targetGain = 0.6F * reverbAmount.floatValue();
            }
            decay += (targetDecay - decay) * 0.5F;
            gain += (targetGain - gain) * 0.5F;
            SoundFx.decay = decay;
            SoundFx.sendGain = gain;
        }
    }
}
