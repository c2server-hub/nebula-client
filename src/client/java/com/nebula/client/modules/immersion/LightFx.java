package com.nebula.client.modules.immersion;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.ScreenFx;
import com.nebula.client.rendering.effects.ImmersionPost;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Light bloom (real post pass) plus screen-space god rays / sun glare that respect occlusion. */
public final class LightFx extends Module {
    public final BoolSetting bloom = add(new BoolSetting("Bloom", "Bright areas softly glow", true));
    public final NumberSetting bloomStrength = add(new NumberSetting("Bloom strength", "Bloom intensity", 0.25, 0, 1.5, 0.05));
    public final NumberSetting bloomThreshold = add(new NumberSetting("Bloom threshold", "Only pixels brighter than this glow", 0.72, 0.3, 0.98, 0.01));
    public final NumberSetting bloomRadius = add(new NumberSetting("Bloom radius", "Spread of the glow", 5, 1, 12, 0.5));
    public final BoolSetting godRays = add(new BoolSetting("God rays", "Light shafts from the sun when it is visible", true));
    public final NumberSetting raysStrength = add(new NumberSetting("Rays strength", "God ray intensity", 0.4, 0, 1.5, 0.05));
    public final BoolSetting sunGlare = add(new BoolSetting("Sun glare", "Soft glare and lens ghosts", true));

    public LightFx() {
        super("light_fx", "Bloom & God Rays", "Light post-process bloom and screen-space god rays", Category.IMMERSION, true, false);
    }

    public void renderPost() {
        if (bloom.isOn() && !Env.underwater) {
            float s = bloomStrength.floatValue() * ImmersionHooks.global();
            ImmersionPost.INSTANCE.bloom(bloomThreshold.floatValue(), s, bloomRadius.floatValue());
        }
    }

    public void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !Env.overworld || Env.underwater || (!godRays.isOn() && !sunGlare.isOn())) {
            return;
        }
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float yaw = p.getViewYRot(pt) * Mth.DEG_TO_RAD;
        float pitch = p.getViewXRot(pt) * Mth.DEG_TO_RAD;
        Vec3 f = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        Vec3 up = right.cross(f);
        Vec3 sun = Env.sunDirection(Env.dayFraction);
        double dot = sun.dot(f);
        if (dot < 0.5) {
            return;
        }
        float facing = (float) Mth.clamp((dot - 0.55) / (0.98 - 0.55), 0, 1);
        facing = facing * facing * (3 - 2 * facing);
        float vis = Env.sunVisibility * (1F - Env.rain) * facing * ImmersionHooks.global();
        if (vis < 0.01F) {
            return;
        }
        int w = g.guiWidth(), h = g.guiHeight();
        float focal = (float) ((h / 2.0) / Math.tan(Math.toRadians(ImmersionHooks.lastFov) / 2.0));
        float sx = (float) (w / 2.0 + sun.dot(right) / dot * focal);
        float sy = (float) (h / 2.0 - sun.dot(up) / dot * focal);
        // Low sun = warmer light.
        float low = (float) Mth.clamp(1.0 - sun.y * 2.5, 0, 1);
        int tint = ColorGrading.rgb(1F, Mth.lerp(low, 0.95F, 0.78F), Mth.lerp(low, 0.85F, 0.55F)) & 0xFFFFFF;

        if (sunGlare.isOn()) {
            float r = h * 0.35F;
            ScreenFx.addSoft(g, sx - r, sy - r, r * 2, r * 2, r, r * 0.9F, alpha(vis * 0.22F) | tint, 1F);
            // Lens ghosts mirrored through the screen centre.
            float[] ghosts = {0.45F, -0.3F, -0.75F};
            float[] sizes = {0.05F, 0.03F, 0.08F};
            for (int i = 0; i < ghosts.length; i++) {
                float gx = w / 2F + (sx - w / 2F) * ghosts[i];
                float gy = h / 2F + (sy - h / 2F) * ghosts[i];
                float gr = h * sizes[i];
                ScreenFx.addSoft(g, gx - gr, gy - gr, gr * 2, gr * 2, gr, gr * 0.6F, alpha(vis * 0.06F) | tint, 1F);
            }
        }
        if (godRays.isOn()) {
            float k = raysStrength.floatValue();
            float t = (System.nanoTime() % 1_000_000_000_000L) / 1.0E9F;
            float len = h * 1.1F;
            for (int i = 0; i < 12; i++) {
                float ang = i * (Mth.TWO_PI / 12F) + Mth.sin(t * 0.05F + i) * 0.08F;
                float flicker = 0.6F + 0.4F * Mth.sin(t * 0.4F + i * 1.7F);
                float width = h * (0.035F + 0.02F * ((i * 7) % 3));
                int c0 = alpha(vis * k * 0.10F * flicker) | tint;
                int c1 = tint; // alpha 0 at the far end
                g.pose().pushMatrix();
                g.pose().translate(sx, sy);
                g.pose().rotate(ang);
                ScreenFx.addSoft(g, 0, -width / 2, len, width, width * 0.45F, 1F, c0, c1, c1, c0);
                g.pose().popMatrix();
            }
        }
    }

    private static int alpha(float a) {
        return Mth.clamp((int) (a * 255F), 0, 255) << 24;
    }
}
