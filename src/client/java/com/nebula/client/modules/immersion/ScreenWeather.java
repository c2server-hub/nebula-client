package com.nebula.client.modules.immersion;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.BlurRenderer;
import com.nebula.client.rendering.Gradient;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

import java.util.Random;

/** Rain drops on the lens, drips when leaving water, and soft biome / post-rain / deep-cave haze. */
public final class ScreenWeather extends Module {
    public final BoolSetting rainDrops = add(new BoolSetting("Rain drops", "Drops hit the screen when it rains on you", true));
    public final NumberSetting dropAmount = add(new NumberSetting("Drop amount", "How many drops land on the screen", 0.6, 0, 2, 0.05, "x"));
    public final BoolSetting refractive = add(new BoolSetting("Refractive drops", "Drops bend the image like real water (glass shader)", true));
    public final BoolSetting exitDrips = add(new BoolSetting("Water exit drips", "Water runs down the screen after surfacing", true));
    public final BoolSetting postRainMist = add(new BoolSetting("Mist after rain", "Light haze for a while after the rain stops", true));
    public final BoolSetting biomeHaze = add(new BoolSetting("Biome haze", "Swamp, snowy and deep cave haze with smooth transitions", true));
    public final NumberSetting hazeAmount = add(new NumberSetting("Haze amount", "Haze density", 1.0, 0, 2, 0.05, "x"));

    private static final int MAX = 64;
    private final float[] x = new float[MAX], y = new float[MAX], r = new float[MAX], vy = new float[MAX], life = new float[MAX], age = new float[MAX];
    private final boolean[] alive = new boolean[MAX];
    private final Random random = new Random();
    private float spawnAcc;
    private long last;
    private long lastExitHandled;
    private float hazeAlpha;
    private int hazeColor = 0xFFB8C0C8;

    public ScreenWeather() {
        super("screen_weather", "Screen Weather", "Rain drops on screen, water drips and biome haze", Category.IMMERSION, true, false);
    }

    private void spawn(int w, int h, float sizeMul, boolean drip) {
        for (int i = 0; i < MAX; i++) {
            if (!alive[i]) {
                alive[i] = true;
                x[i] = random.nextFloat() * w;
                y[i] = drip ? random.nextFloat() * h * 0.6F : random.nextFloat() * h;
                r[i] = (1.5F + random.nextFloat() * 3.5F) * sizeMul;
                vy[i] = random.nextFloat() < 0.35F ? 8F + random.nextFloat() * 40F : 0F;
                life[i] = drip ? 1.2F + random.nextFloat() * 1.5F : 1.5F + random.nextFloat() * 2.5F;
                age[i] = 0;
                return;
            }
        }
    }

    public void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) {
            return;
        }
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016F : Mth.clamp((now - last) / 1.0E9F, 0F, 0.1F);
        last = now;
        int w = g.guiWidth(), h = g.guiHeight();
        float k = ImmersionHooks.global();

        renderHaze(g, w, h, dt, k);

        if (Env.underwater) {
            java.util.Arrays.fill(alive, false);
            return;
        }
        if (rainDrops.isOn() && Env.rainingHere && !Env.snowy) {
            float lookUp = Mth.clamp(-p.getXRot() / 90F, 0F, 1F);
            float rate = 6F * dropAmount.floatValue() * Env.rain * (0.4F + 1.6F * lookUp) * k;
            spawnAcc += rate * dt;
            while (spawnAcc >= 1F) {
                spawnAcc -= 1F;
                spawn(w, h, 1F, false);
            }
        }
        if (exitDrips.isOn() && Env.exitedWaterAt != lastExitHandled && Env.secondsSince(Env.exitedWaterAt) < 0.3F) {
            lastExitHandled = Env.exitedWaterAt;
            int n = (int) (28 * Math.min(1.5F, k));
            for (int i = 0; i < n; i++) {
                spawn(w, h, 1.2F, true);
            }
        }
        if (exitDrips.isOn()) {
            float s = Env.secondsSince(Env.exitedWaterAt);
            if (s < 1.6F) {
                float a = (1F - s / 1.6F) * 0.18F * k;
                RoundedRenderer.gradient(g, 0, 0, w, h, 0, Gradient.vertical(ColorUtil.withAlpha(0xFF9FC4D8, a), ColorUtil.withAlpha(0xFF9FC4D8, a * 0.4F)));
            }
        }

        for (int i = 0; i < MAX; i++) {
            if (!alive[i]) {
                continue;
            }
            age[i] += dt;
            if (age[i] >= life[i]) {
                alive[i] = false;
                continue;
            }
            if (vy[i] > 0) {
                y[i] += vy[i] * dt;
                x[i] += Mth.sin(age[i] * 3F + i) * 3F * dt;
            }
            // Shrink at the end instead of fading (a fading glass shape pops).
            float shrink = Mth.clamp((life[i] - age[i]) / 0.5F, 0F, 1F);
            float rr = r[i] * shrink;
            if (rr < 0.4F) {
                continue;
            }
            float hgt = vy[i] > 0 ? rr * 1.35F : rr;
            if (refractive.isOn()) {
                BlurRenderer.glass(g, x[i] - rr, y[i] - hgt, rr * 2, hgt * 2, rr, 0x10FFFFFF, 0.5F, 0x30FFFFFF, 1.6F);
            } else {
                RoundedRenderer.rect(g, x[i] - rr, y[i] - hgt, rr * 2, hgt * 2, rr, 0x22C8DCEB, 0.5F, 0x40FFFFFF);
            }
            RoundedRenderer.circle(g, x[i] - rr * 0.35F, y[i] - hgt * 0.45F, Math.max(0.4F, rr * 0.22F), 0x70FFFFFF);
        }
    }

    private void renderHaze(GuiGraphics g, int w, int h, float dt, float k) {
        float target = 0F;
        int color = hazeColor;
        if (Env.overworld) {
            if (postRainMist.isOn()) {
                float since = Env.secondsSince(Env.rainEndedAt);
                if (since < 90F && Env.sky) {
                    target = Math.max(target, 0.10F * (1F - since / 90F));
                    color = 0xFFC9D2DA;
                }
                if (Env.rainingHere) {
                    target = Math.max(target, 0.05F * Env.rain);
                    color = 0xFFB4BEC8;
                }
            }
            if (biomeHaze.isOn()) {
                if (Env.swamp && Env.sky) {
                    target = Math.max(target, 0.08F);
                    color = 0xFFA9B5A0;
                } else if (Env.snowy && Env.rainingHere) {
                    target = Math.max(target, 0.08F);
                    color = 0xFFEEF3F8;
                } else if (Env.eyeY < 0 && Env.skyLight == 0) {
                    target = Math.max(target, 0.06F);
                    color = 0xFF1A2230;
                }
            }
        }
        target *= hazeAmount.floatValue() * k;
        float a = 1F - (float) Math.exp(-dt / 3F);
        hazeAlpha += (target - hazeAlpha) * a;
        hazeColor = ColorUtil.mix(hazeColor, color, a);
        if (hazeAlpha > 0.004F) {
            RoundedRenderer.gradient(g, 0, 0, w, h, 0, Gradient.vertical(
                ColorUtil.withAlpha(hazeColor, hazeAlpha * 0.6F),
                ColorUtil.withAlpha(hazeColor, hazeAlpha),
                ColorUtil.withAlpha(hazeColor, hazeAlpha * 0.5F)));
        }
    }
}
