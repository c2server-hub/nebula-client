package com.nebula.client.modules.immersion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

/**
 * OpenAL EFX layer: one shared reverb slot + one low-pass filter per source.
 * All AL calls happen on the sound thread (from {@code Channel.play}); the environment is
 * measured on the main thread and handed over through volatile fields.
 */
public final class SoundFx {
    // Written by ImmersiveSound on the main thread.
    static volatile boolean active;
    static volatile boolean reverbOn, muffleOn, occlusionOn;
    static volatile float decay = 0.5F, sendGain, underwaterAmount = 1F, occlusionAmount = 1F;
    static volatile boolean underwater;
    static volatile double lx, ly, lz;

    // Sound-thread state.
    private static long context;
    private static boolean efx;
    private static int slot, effect;
    private static float appliedDecay = -1, appliedGain = -1;
    private static boolean broken;

    private SoundFx() {
    }

    public static int onPlay(int source, int filter, Vec3 pos, boolean relative) {
        if (broken) {
            return filter;
        }
        try {
            if (!ensure()) {
                return filter;
            }
            if (!active) {
                AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, EXTEfx.AL_EFFECTSLOT_NULL, 0, EXTEfx.AL_FILTER_NULL);
                AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
                return filter;
            }
            updateEffect();
            AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, reverbOn ? slot : EXTEfx.AL_EFFECTSLOT_NULL, 0, EXTEfx.AL_FILTER_NULL);

            float hf = 1F;
            float gain = 1F;
            if (muffleOn && underwater) {
                hf *= 1F - 0.9F * underwaterAmount;
                gain *= 1F - 0.35F * underwaterAmount;
            }
            if (occlusionOn && !relative && pos != null) {
                int occ = occlusion(pos);
                if (occ > 0) {
                    hf *= 1F - Math.min(0.9F, occ * 0.3F * occlusionAmount);
                    gain *= 1F - Math.min(0.55F, occ * 0.14F * occlusionAmount);
                }
            }
            hf = Math.max(0.02F, Math.min(1F, hf));
            gain = Math.max(0.05F, Math.min(1F, gain));
            if (hf < 0.999F || gain < 0.999F) {
                if (filter == 0) {
                    filter = EXTEfx.alGenFilters();
                    EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
                }
                EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, gain);
                EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, hf);
                AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filter);
            } else {
                AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
            }
        } catch (Throwable t) {
            broken = true; // driver without EFX or unexpected AL error: silently disable.
        }
        return filter;
    }

    public static void release(int filter) {
        if (filter != 0 && efx && !broken) {
            try {
                EXTEfx.alDeleteFilters(filter);
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean ensure() {
        long ctx = ALC10.alcGetCurrentContext();
        if (ctx == 0L) {
            return false;
        }
        if (ctx != context) {
            // New OpenAL context (sound engine reload): ids from the old context are gone.
            context = ctx;
            long device = ALC10.alcGetContextsDevice(ctx);
            efx = ALC10.alcIsExtensionPresent(device, "ALC_EXT_EFX");
            if (efx) {
                slot = EXTEfx.alGenAuxiliaryEffectSlots();
                effect = EXTEfx.alGenEffects();
                EXTEfx.alEffecti(effect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_REVERB);
                appliedDecay = -1;
                appliedGain = -1;
            }
        }
        return efx;
    }

    private static void updateEffect() {
        float d = Math.max(0.1F, Math.min(8F, decay));
        float gn = Math.max(0F, Math.min(1F, sendGain));
        if (Math.abs(d - appliedDecay) < 0.04F && Math.abs(gn - appliedGain) < 0.015F) {
            return;
        }
        float size = Math.min(1F, d / 5F);
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DECAY_TIME, d);
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DENSITY, 0.6F + 0.4F * size);
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DIFFUSION, 0.85F);
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_GAIN, 0.32F);
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_GAINHF, underwater ? 0.25F : 0.8F);
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_REFLECTIONS_GAIN, 0.4F + 0.8F * size);
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_REFLECTIONS_DELAY, 0.005F + 0.04F * size);
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_LATE_REVERB_GAIN, 0.8F + 1.2F * size);
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_LATE_REVERB_DELAY, 0.01F + 0.05F * size);
        EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, effect);
        EXTEfx.alAuxiliaryEffectSlotf(slot, EXTEfx.AL_EFFECTSLOT_GAIN, gn);
        appliedDecay = d;
        appliedGain = gn;
    }

    /** Counts sound-blocking blocks between the listener and the source (max 4). */
    private static int occlusion(Vec3 pos) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return 0;
        }
        double dx = pos.x - lx;
        double dy = pos.y - ly;
        double dz = pos.z - lz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.5 || len > 48) {
            return 0;
        }
        double end = len - 0.9; // skip the block that emits the sound
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        long last = Long.MIN_VALUE;
        int count = 0;
        for (double s = 0.4; s < end; s += 0.4) {
            double t = s / len;
            m.set(lx + dx * t, ly + dy * t, lz + dz * t);
            long key = m.asLong();
            if (key == last) {
                continue;
            }
            last = key;
            if (level.getBlockState(m).canOcclude()) {
                if (++count >= 4) {
                    break;
                }
            }
        }
        return count;
    }
}
