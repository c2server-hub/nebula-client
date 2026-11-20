package com.nebula.client.modules.immersion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Shared, tick-sampled view of the player's surroundings used by every immersion module. */
public final class Env {
    public static boolean valid;
    public static boolean overworld, nether, end;
    public static boolean swamp, forest, cherry, jungle, desert, snowy;
    public static boolean sky, underwater, rainingHere, thunder, waterNearby, night;
    public static int skyLight, blockLight, light, waterDepth;
    public static float rain, dayFraction, eyeY;
    public static float sunVisibility;
    public static long exitedWaterAt, rainEndedAt;

    private static boolean wasUnderwater;
    private static float lastRain;
    private static int counter;
    private static float sunTarget;

    private Env() {
    }

    public static void update(Minecraft mc) {
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null) {
            valid = false;
            return;
        }
        valid = true;
        counter++;
        Vec3 eyeVec = p.getEyePosition();
        BlockPos eye = BlockPos.containing(eyeVec);
        eyeY = (float) eyeVec.y;

        overworld = level.dimension() == Level.OVERWORLD;
        nether = level.dimension() == Level.NETHER;
        end = level.dimension() == Level.END;

        skyLight = level.getBrightness(LightLayer.SKY, eye);
        blockLight = level.getBrightness(LightLayer.BLOCK, eye);
        light = level.getMaxLocalRawBrightness(eye);
        sky = level.canSeeSky(eye);

        long time = level.getDayTime() % 24000L;
        if (time < 0) {
            time += 24000L;
        }
        dayFraction = time / 24000F;
        night = time >= 13000L && time <= 23000L;

        float r = level.getRainLevel(1F);
        if (lastRain > 0.2F && r <= 0.2F) {
            rainEndedAt = System.nanoTime();
        }
        lastRain = r;
        rain = r;
        thunder = level.getThunderLevel(1F) > 0.5F;
        rainingHere = r > 0.05F && level.isRainingAt(eye.above());

        underwater = p.isUnderWater();
        if (wasUnderwater && !underwater) {
            exitedWaterAt = System.nanoTime();
        }
        wasUnderwater = underwater;
        if (underwater) {
            BlockPos.MutableBlockPos m = eye.mutable();
            int depth = 0;
            while (depth < 40 && level.getFluidState(m).is(FluidTags.WATER)) {
                depth++;
                m.move(0, 1, 0);
            }
            waterDepth = depth;
        } else {
            waterDepth = 0;
        }

        if (counter % 10 == 0) {
            Holder<Biome> b = level.getBiome(eye);
            swamp = b.is(Biomes.SWAMP) || b.is(Biomes.MANGROVE_SWAMP);
            forest = b.is(BiomeTags.IS_FOREST) || b.is(BiomeTags.IS_TAIGA) || swamp;
            cherry = b.is(Biomes.CHERRY_GROVE);
            jungle = b.is(BiomeTags.IS_JUNGLE);
            desert = b.is(Biomes.DESERT) || b.is(BiomeTags.IS_BADLANDS);
            snowy = b.is(Biomes.SNOWY_PLAINS) || b.is(Biomes.SNOWY_TAIGA) || b.is(Biomes.ICE_SPIKES)
                || b.is(Biomes.FROZEN_PEAKS) || b.is(Biomes.SNOWY_SLOPES) || b.is(Biomes.GROVE)
                || b.is(Biomes.JAGGED_PEAKS) || b.is(Biomes.SNOWY_BEACH) || b.is(Biomes.FROZEN_RIVER)
                || b.is(Biomes.FROZEN_OCEAN);
            waterNearby = scanWater(level, p.blockPosition());
        }

        if (counter % 5 == 0) {
            sunTarget = overworld && !night ? sunOcclusion(level, p, eyeVec) : 0F;
        }
        sunVisibility += (sunTarget - sunVisibility) * 0.25F;
    }

    private static boolean scanWater(ClientLevel level, BlockPos feet) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -6; dx <= 6; dx += 3) {
            for (int dz = -6; dz <= 6; dz += 3) {
                for (int dy = -4; dy <= 1; dy++) {
                    m.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
                    if (level.getFluidState(m).is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static float sunOcclusion(ClientLevel level, LocalPlayer p, Vec3 eye) {
        Vec3 dir = sunDirection(dayFraction);
        if (dir.y < -0.05) {
            return 0F;
        }
        int clear = 0;
        double[][] jitter = {{0, 0}, {0.03, 0}, {-0.03, 0}, {0, 0.03}, {0, -0.03}};
        for (double[] j : jitter) {
            Vec3 d = dir.add(0, j[0], j[1]).normalize();
            HitResult hit = level.clip(new ClipContext(eye, eye.add(d.scale(128)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
            if (hit.getType() == HitResult.Type.MISS) {
                clear++;
            }
        }
        return clear / (float) jitter.length;
    }

    /** Vanilla celestial angle formula. Sun moves in the X/Y plane (rises east, sets west). */
    public static Vec3 sunDirection(float dayFraction) {
        float f = Mth.frac(dayFraction - 0.25F);
        float g = 0.5F - (float) Math.cos(f * Math.PI) / 2F;
        float angle = (f * 2F + g) / 3F;
        double a = angle * Math.PI * 2;
        return new Vec3(-Math.sin(a), Math.cos(a), 0);
    }

    public static float secondsSince(long nanos) {
        return nanos == 0 ? Float.MAX_VALUE : (System.nanoTime() - nanos) / 1.0E9F;
    }
}
