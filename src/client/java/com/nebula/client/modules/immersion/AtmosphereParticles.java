package com.nebula.client.modules.immersion;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.rendering.world.GlowBatch;
import com.nebula.client.rendering.world.WorldFrame;
import com.nebula.client.rendering.world.WorldRenderable;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;

import java.util.Random;

/** Context-driven ambient particles: fireflies in forests at night, falling leaves, dust in light, mist over water. */
public final class AtmosphereParticles extends Module implements WorldRenderable {
    private static final int MAX = 160;
    private static final int FIREFLY = 0, LEAF = 1, DUST = 2, MIST = 3;

    public final NumberSetting density = add(new NumberSetting("Density", "Maximum number of particles", 45, 0, MAX, 5));
    public final NumberSetting radius = add(new NumberSetting("Radius", "Area around you", 12, 4, 24, 1, "m"));
    public final NumberSetting brightness = add(new NumberSetting("Brightness", "Particle brightness", 1.0, 0.2, 2, 0.05));
    public final BoolSetting fireflies = add(new BoolSetting("Fireflies", "Night, forests and swamps", true));
    public final BoolSetting leaves = add(new BoolSetting("Falling leaves", "Forest / cherry grove leaves", true));
    public final BoolSetting dust = add(new BoolSetting("Dust motes", "Dust floating in lit rooms and caves", true));
    public final BoolSetting mist = add(new BoolSetting("Water mist", "Low mist drifting above water", true));

    private final double[] bx = new double[MAX], by = new double[MAX], bz = new double[MAX];
    private final float[] life = new float[MAX], phase = new float[MAX], tone = new float[MAX];
    private final int[] kind = new int[MAX];
    private final long[] born = new long[MAX];
    private final Random random = new Random();

    public AtmosphereParticles() {
        super("atmosphere_particles", "Atmosphere Particles", "Fireflies, leaves, dust and mist that follow biome & time", Category.IMMERSION, true, false);
    }

    @Override
    protected void onDisable() {
        java.util.Arrays.fill(born, 0);
    }

    private int pickKind() {
        float wf = fireflies.isOn() && Env.overworld && Env.night && Env.sky && (Env.forest || Env.swamp || Env.jungle) ? 1F : 0F;
        float wl = leaves.isOn() && Env.overworld && Env.sky && (Env.forest || Env.cherry || Env.jungle) && !Env.rainingHere ? 1F : 0F;
        float wd = 0F;
        if (dust.isOn()) {
            if (!Env.sky && Env.light >= 4) {
                wd = 1F;
            } else if (Env.forest && !Env.night) {
                wd = 0.3F;
            }
        }
        float wm = mist.isOn() && Env.waterNearby && Env.overworld && (Env.night || Env.dayFraction < 0.08F || Env.dayFraction > 0.95F || Env.rain > 0.1F || Env.swamp) ? 0.8F : 0F;
        float sum = wf + wl + wd + wm;
        if (sum <= 0F) {
            return -1;
        }
        float r = random.nextFloat() * sum;
        if ((r -= wf) < 0) return FIREFLY;
        if ((r -= wl) < 0) return LEAF;
        if ((r -= wd) < 0) return DUST;
        return MIST;
    }

    private void spawn(int i, LocalPlayer p, ClientLevel level, boolean anywhereInLife) {
        int k = pickKind();
        kind[i] = k;
        if (k < 0) {
            born[i] = System.nanoTime();
            life[i] = 1F + random.nextFloat();
            return;
        }
        float r = radius.floatValue();
        float a = random.nextFloat() * Mth.TWO_PI;
        float d = r * Mth.sqrt(random.nextFloat());
        bx[i] = p.getX() + Mth.cos(a) * d;
        bz[i] = p.getZ() + Mth.sin(a) * d;
        switch (k) {
            case LEAF -> by[i] = p.getY() + 4 + random.nextFloat() * 6;
            case MIST -> {
                by[i] = waterSurface(level, bx[i], p.getY(), bz[i]);
                if (Double.isNaN(by[i])) {
                    kind[i] = -1;
                }
            }
            case DUST -> by[i] = p.getY() + random.nextFloat() * 3;
            default -> by[i] = p.getY() - 0.5 + random.nextFloat() * 4;
        }
        phase[i] = random.nextFloat() * 100;
        tone[i] = random.nextFloat();
        life[i] = switch (k) {
            case LEAF -> 6 + random.nextFloat() * 5;
            case MIST -> 8 + random.nextFloat() * 8;
            default -> 5 + random.nextFloat() * 7;
        };
        long ageOffset = anywhereInLife ? (long) (random.nextFloat() * life[i] * 0.8F * 1e9) : 0;
        born[i] = System.nanoTime() - ageOffset;
    }

    private static double waterSurface(ClientLevel level, double x, double y, double z) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(x), bz = Mth.floor(z), base = Mth.floor(y);
        for (int dy = 2; dy >= -6; dy--) {
            m.set(bx, base + dy, bz);
            if (level.getFluidState(m).is(FluidTags.WATER)) {
                m.move(0, 1, 0);
                if (level.getFluidState(m).isEmpty() && level.getBlockState(m).isAir()) {
                    return base + dy + 1.15;
                }
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    @Override
    public void renderWorld(WorldFrame f, GlowBatch batch) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null || !Env.valid) {
            return;
        }
        long now = System.nanoTime();
        int n = Math.min(MAX, (int) (density.intValue() * Math.min(1.5F, ImmersionHooks.global())));
        float r = radius.floatValue();
        float bright = brightness.floatValue();
        batch.begin(f);
        for (int i = 0; i < n; i++) {
            if (born[i] == 0) {
                spawn(i, p, level, true);
            }
            float age = (now - born[i]) / 1e9F;
            double dx = bx[i] - p.getX(), dz = bz[i] - p.getZ();
            if (age > life[i] || dx * dx + dz * dz > r * r * 1.6) {
                spawn(i, p, level, false);
                age = 0;
            }
            if (kind[i] < 0) {
                continue;
            }
            float ph = phase[i];
            double x = bx[i], y = by[i], z = bz[i];
            float fade = Mth.clamp(Math.min(age / 1.2F, (life[i] - age) / 1.5F), 0F, 1F);
            float s;
            int c;
            switch (kind[i]) {
                case FIREFLY -> {
                    x += Mth.sin(age * 0.7F + ph) * 0.6F;
                    y += Mth.sin(age * 0.9F + ph * 2) * 0.35F;
                    z += Mth.cos(age * 0.6F + ph) * 0.6F;
                    float blink = Math.max(0F, Mth.sin(age * 1.8F + ph * 3));
                    s = 0.06F;
                    c = ColorUtil.withAlpha(ColorUtil.mix(0xFFB8FF5A, 0xFFFFE066, tone[i]), fade * blink * 0.9F * Math.min(1F, bright));
                }
                case LEAF -> {
                    y -= age * 0.55F;
                    x += Mth.sin(age * 1.3F + ph) * 0.6F + age * 0.15F;
                    z += Mth.cos(age * 1.1F + ph) * 0.4F;
                    s = 0.07F;
                    int col = Env.cherry ? ColorUtil.mix(0xFFFFB7D5, 0xFFFF8FBF, tone[i])
                        : ColorUtil.mix(0xFF5E8F2A, 0xFFC9A23A, tone[i]);
                    c = ColorUtil.withAlpha(col, fade * 0.55F * Math.min(1F, bright) * (Env.night ? 0.35F : 1F));
                }
                case DUST -> {
                    x += Mth.sin(age * 0.2F + ph) * 0.3F;
                    y += Mth.sin(age * 0.15F + ph) * 0.2F + age * 0.02F;
                    z += Mth.cos(age * 0.25F + ph) * 0.3F;
                    s = 0.025F;
                    float lit = Mth.clamp(Env.light / 15F, 0.2F, 1F);
                    c = ColorUtil.withAlpha(0xFFFFF1D6, fade * 0.45F * lit * Math.min(1F, bright));
                }
                default -> { // MIST
                    x += age * 0.12F + Mth.sin(age * 0.3F + ph) * 0.4F;
                    z += Mth.cos(age * 0.25F + ph) * 0.4F;
                    s = 0.9F + tone[i] * 0.8F;
                    c = ColorUtil.withAlpha(Env.night ? 0xFF8090A8 : 0xFFDCE4EC, fade * 0.05F * Math.min(1F, bright));
                }
            }
            batch.billboard(f.rx(x), f.ry(y), f.rz(z), s, c);
        }
        batch.end(NebulaPipelines.GLOW_SPARK, 0, 0, 0);
    }
}
