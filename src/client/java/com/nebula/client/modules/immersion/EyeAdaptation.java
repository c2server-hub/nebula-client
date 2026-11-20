package com.nebula.client.modules.immersion;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.ScreenFx;
import com.nebula.client.rendering.effects.ImmersionPost;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;

/** Eyes that get used to darkness / brightness, deeper water getting darker, caustics and real underwater refraction. */
public final class EyeAdaptation extends Module {
    public final NumberSetting strength = add(new NumberSetting("Strength", "Strength of the adaptation", 0.5, 0, 1.5, 0.05, "x"));
    public final NumberSetting darkTime = add(new NumberSetting("Dark adaptation", "Time for the eye to get used to darkness", 4, 0.5, 12, 0.5, "s"));
    public final NumberSetting brightTime = add(new NumberSetting("Bright adaptation", "Time to recover from glare", 1.2, 0.2, 6, 0.1, "s"));
    public final BoolSetting nightBoost = add(new BoolSetting("Dark vision", "Slightly lifts shadows once adapted to the dark", true));
    public final BoolSetting depthDarkening = add(new BoolSetting("Water depth", "Water gets darker and bluer the deeper you go", true));
    public final BoolSetting caustics = add(new BoolSetting("Caustics", "Moving light patterns in shallow water", true));
    public final BoolSetting distortion = add(new BoolSetting("Underwater distortion", "Wavy refraction post-process under water", true));
    public final NumberSetting distortionAmount = add(new NumberSetting("Distortion amount", "Underwater refraction strength", 1.0, 0, 2, 0.05, "x"));

    private float adapted = -1;
    private float depthSmooth;
    private long last;
    private final float[] cx = new float[12], cy = new float[12], cs = new float[12], cp = new float[12];
    private boolean causticsInit;

    public EyeAdaptation() {
        super("eye_adaptation", "Eye & Water", "Eye adaptation, depth darkening, caustics & underwater distortion", Category.IMMERSION, true, false);
    }

    private float perceived() {
        float sky = Env.skyLight / 15F;
        if (Env.overworld) {
            float day = Env.night ? 0.25F : 1F;
            sky *= day * (1F - Env.rain * 0.3F);
        } else {
            sky = Env.end ? 0.5F : 0.4F;
        }
        return Math.max(sky, Env.blockLight / 15F);
    }

    public void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016F : Mth.clamp((now - last) / 1.0E9F, 0F, 0.2F);
        last = now;
        float l = perceived();
        if (adapted < 0) {
            adapted = l;
        }
        float time = l < adapted ? darkTime.floatValue() : brightTime.floatValue();
        adapted += (l - adapted) * (1F - (float) Math.exp(-dt * 3F / time));
        float k = strength.floatValue() * ImmersionHooks.global();
        int w = g.guiWidth(), h = g.guiHeight();
        boolean nv = mc.player != null && mc.player.hasEffect(MobEffects.NIGHT_VISION);

        if (!nv && k > 0) {
            float diff = adapted - l;
            if (diff > 0.02F) {
                // Just walked into darkness: still adapted to bright light.
                float a = Math.min(0.75F, diff * 1.1F * k);
                int c = ColorGrading.rgb(1F - a, 1F - a, 1F - a * 0.9F);
                ScreenFx.multiply(g, 0, 0, w, h, c, c);
            } else if (diff < -0.02F) {
                // Just stepped into the light: short glare.
                float a = Math.min(0.5F, -diff * 0.8F * k);
                int c = ColorGrading.rgb(a, a, a * 0.92F);
                ScreenFx.add(g, 0, 0, w, h, 0, c);
            }
            if (nightBoost.isOn() && adapted < 0.3F && Math.abs(diff) < 0.1F && !Env.underwater) {
                float lift = (0.3F - adapted) * 0.07F * k;
                int c = ColorGrading.rgb(lift * 0.8F, lift * 0.9F, lift * 1.1F);
                ScreenFx.add(g, 0, 0, w, h, 0, c);
            }
        }

        float depthTarget = Env.underwater ? Env.waterDepth : 0F;
        depthSmooth += (depthTarget - depthSmooth) * (1F - (float) Math.exp(-dt * 1.5F));
        if (Env.underwater && depthDarkening.isOn()) {
            float d = Mth.clamp(depthSmooth / 28F, 0F, 1F) * Math.min(1F, k * 1.4F);
            int top = ColorGrading.rgb(1F - .45F * d, 1F - .35F * d, 1F - .2F * d);
            int bottom = ColorGrading.rgb(1F - .6F * d, 1F - .48F * d, 1F - .3F * d);
            ScreenFx.multiply(g, 0, 0, w, h, top, bottom);
        }
        if (Env.underwater && caustics.isOn() && depthSmooth < 10F && Env.skyLight > 6) {
            renderCaustics(g, w, h, (1F - depthSmooth / 10F) * Math.min(1F, k * 1.4F) * (Env.night ? 0.3F : 1F));
        }
    }

    private void renderCaustics(GuiGraphics g, int w, int h, float amount) {
        if (!causticsInit) {
            java.util.Random r = new java.util.Random(42);
            for (int i = 0; i < cx.length; i++) {
                cx[i] = r.nextFloat();
                cy[i] = r.nextFloat();
                cs[i] = 0.6F + r.nextFloat() * 0.8F;
                cp[i] = r.nextFloat() * 10F;
            }
            causticsInit = true;
        }
        float t = (System.nanoTime() % 1_000_000_000_000L) / 1.0E9F;
        for (int i = 0; i < cx.length; i++) {
            float x = (cx[i] + Mth.sin(t * 0.13F * cs[i] + cp[i]) * 0.08F + t * 0.006F * cs[i]) % 1F * w;
            float y = (cy[i] + Mth.cos(t * 0.11F * cs[i] + cp[i]) * 0.08F) % 1F * h;
            float pulse = 0.5F + 0.5F * Mth.sin(t * 1.3F * cs[i] + cp[i]);
            float size = (60F + 50F * cs[i]) * (0.8F + 0.4F * pulse);
            int a = (int) (Mth.clamp(amount * 0.10F * pulse, 0F, 1F) * 255F);
            if (a > 1) {
                ScreenFx.addSoft(g, x - size / 2, y - size * 0.3F, size, size * 0.6F, size * 0.3F, size * 0.25F, (a << 24) | 0xBFEFFF, 1F);
            }
        }
    }

    public void renderPost() {
        if (Env.underwater && distortion.isOn()) {
            float s = distortionAmount.floatValue() * Math.min(1F, ImmersionHooks.global());
            ImmersionPost.INSTANCE.underwater((System.nanoTime() % 1_000_000_000_000L) / 1.0E9F, s);
        }
    }
}
