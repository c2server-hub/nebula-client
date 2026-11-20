package com.nebula.client.modules.immersion;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.ScreenFx;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.settings.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Screen-space colour grading by time of day, dimension and biome, with smooth transitions. */
public final class ColorGrading extends Module {
    public final ModeSetting style = add(new ModeSetting("Style", "Look of the grading", "Natural", "Natural", "Cold Nether", "Filmic"));
    public final NumberSetting intensity = add(new NumberSetting("Intensity", "Strength of the grading", 0.4, 0, 1, 0.05));
    public final BoolSetting timeOfDay = add(new BoolSetting("Time of day", "Warm dawn / dusk, cool night", true));
    public final BoolSetting dimension = add(new BoolSetting("Dimension", "Nether and End tints", true));
    public final BoolSetting biome = add(new BoolSetting("Biome", "Swamp, desert and snowy tints", true));

    // time (ticks), mul rgb, lift rgb
    private static final float[][] KEYS = {
        {0, 1F, .90F, .80F, .05F, .02F, 0F},
        {2000, 1F, .97F, .93F, .02F, .01F, 0F},
        {6000, 1F, 1F, .98F, 0F, 0F, 0F},
        {10500, 1F, .97F, .92F, .015F, .008F, 0F},
        {12000, 1F, .86F, .80F, .06F, .02F, .02F},
        {13500, .88F, .86F, .98F, .01F, 0F, .03F},
        {18000, .80F, .86F, 1F, 0F, .005F, .025F},
        {22500, .86F, .86F, .98F, .01F, 0F, .02F},
        {24000, 1F, .90F, .80F, .05F, .02F, 0F},
    };

    private final float[] cur = {1, 1, 1, 0, 0, 0};
    private long last;

    public ColorGrading() {
        super("color_grading", "Color Grading", "Time of day, dimension and biome colour grading", Category.IMMERSION, true, false);
    }

    private void target(float[] out) {
        out[0] = out[1] = out[2] = 1F;
        out[3] = out[4] = out[5] = 0F;
        boolean filmic = style.is("Filmic");
        if (Env.overworld && timeOfDay.isOn()) {
            float t = Env.dayFraction * 24000F;
            for (int i = 0; i < KEYS.length - 1; i++) {
                if (t >= KEYS[i][0] && t <= KEYS[i + 1][0]) {
                    float f = (t - KEYS[i][0]) / (KEYS[i + 1][0] - KEYS[i][0]);
                    f = f * f * (3F - 2F * f);
                    for (int c = 0; c < 6; c++) {
                        out[c] = Mth.lerp(f, KEYS[i][c + 1], KEYS[i + 1][c + 1]);
                    }
                    break;
                }
            }
            // Underground the sky colour does not matter.
            float sky = Env.skyLight / 15F;
            for (int c = 0; c < 3; c++) {
                out[c] = Mth.lerp(sky, 1F, out[c]);
                out[c + 3] *= sky;
            }
        }
        if (dimension.isOn()) {
            if (Env.nether) {
                if (style.is("Cold Nether")) {
                    set(out, .82F, .9F, 1F, 0F, .01F, .03F);
                } else {
                    set(out, 1F, .86F, .76F, .04F, .01F, 0F);
                }
            } else if (Env.end) {
                set(out, .92F, .86F, 1F, .02F, 0F, .04F);
            }
        }
        if (biome.isOn() && Env.overworld) {
            if (Env.swamp) {
                mul(out, .93F, 1F, .88F);
            } else if (Env.desert) {
                mul(out, 1F, .96F, .88F);
            } else if (Env.snowy) {
                mul(out, .94F, .97F, 1F);
            }
        }
        if (filmic) {
            // Teal shadows / warm highlights approximation.
            mul(out, 1F, .98F, .95F);
            out[4] += .006F;
            out[5] += .012F;
        }
    }

    private static void set(float[] o, float r, float g, float b, float lr, float lg, float lb) {
        o[0] = r; o[1] = g; o[2] = b; o[3] = lr; o[4] = lg; o[5] = lb;
    }

    private static void mul(float[] o, float r, float g, float b) {
        o[0] *= r; o[1] *= g; o[2] *= b;
    }

    static int rgb(float r, float g, float b) {
        int ir = Mth.clamp((int) (r * 255F + 0.5F), 0, 255);
        int ig = Mth.clamp((int) (g * 255F + 0.5F), 0, 255);
        int ib = Mth.clamp((int) (b * 255F + 0.5F), 0, 255);
        return 0xFF000000 | ir << 16 | ig << 8 | ib;
    }

    public void render(GuiGraphics g) {
        long now = System.nanoTime();
        float dt = last == 0 ? 1F : Mth.clamp((now - last) / 1.0E9F, 0F, 0.2F);
        last = now;
        float[] t = new float[6];
        target(t);
        float a = 1F - (float) Math.exp(-dt / 1.5F);
        for (int c = 0; c < 6; c++) {
            cur[c] += (t[c] - cur[c]) * a;
        }
        float k = Mth.clamp(intensity.floatValue() * ImmersionHooks.global(), 0F, 1.5F);
        float r = Mth.lerp(k, 1F, cur[0]), gg = Mth.lerp(k, 1F, cur[1]), b = Mth.lerp(k, 1F, cur[2]);
        int w = g.guiWidth(), h = g.guiHeight();
        if (r < 0.995F || gg < 0.995F || b < 0.995F) {
            int c = rgb(r, gg, b);
            ScreenFx.multiply(g, 0, 0, w, h, c, c);
        }
        float lr = cur[3] * k, lg = cur[4] * k, lb = cur[5] * k;
        if (lr + lg + lb > 0.004F) {
            int c = rgb(lr, lg, lb);
            ScreenFx.add(g, 0, 0, w, h, 0, c);
        }
    }
}
