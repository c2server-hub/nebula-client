package com.nebula.client.modules.immersion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nebula.client.core.ClientManager;
import com.nebula.client.core.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

/** Single entry point for mixins / HUD layers. Every call is null-safe and never throws into vanilla code. */
public final class ImmersionHooks {
    public static boolean renderingHand;
    public static float lastFov = 70F;

    private ImmersionHooks() {
    }

    private static ModuleManager m() {
        return ClientManager.isReady() ? ClientManager.get().modules() : null;
    }

    /** Master multiplier from the preset module. */
    public static float global() {
        ModuleManager m = m();
        return m == null ? 1F : m.immersionPresets.globalIntensity();
    }

    public static boolean cancelVanillaBob() {
        ModuleManager m = m();
        return m != null && m.immersiveCamera.isEnabled() && m.immersiveCamera.replacesVanillaBob();
    }

    public static void applyView(PoseStack pose, float pt) {
        ModuleManager m = m();
        if (m == null || Minecraft.getInstance().player == null) {
            return;
        }
        try {
            if (renderingHand) {
                if (m.immersiveCamera.isEnabled()) {
                    m.immersiveCamera.applyHand(pose, pt);
                }
                if (m.counterCraft.isEnabled() && m.counterCraft.isHoldingKnife()) {
                    m.counterCraft.apply(pose, pt);
                } else if (m.handAnimations.isEnabled()) {
                    m.handAnimations.apply(pose, pt);
                }
            } else if (m.immersiveCamera.isEnabled()) {
                m.immersiveCamera.applyWorld(pose, pt);
            }
        } catch (RuntimeException ignored) {
            // Never break the frame because of a cosmetic effect.
        }
    }

    public static float modifyFov(float fov) {
        ModuleManager m = m();
        float out = fov;
        if (m != null && m.immersiveCamera.isEnabled()) {
            out = m.immersiveCamera.modifyFov(fov);
        }
        lastFov = out;
        return out;
    }

    public static void onExplosion(Vec3 center) {
        ModuleManager m = m();
        if (m != null && m.immersiveCamera.isEnabled() && center != null) {
            m.immersiveCamera.onExplosion(center);
        }
    }

    /** Real post-process layers on the finished 3D frame, right before the GUI is drawn. */
    public static void beforeGui() {
        ModuleManager m = m();
        Minecraft mc = Minecraft.getInstance();
        if (m == null || mc.player == null || mc.level == null) {
            return;
        }
        try {
            if (m.eyeAdaptation.isEnabled()) {
                m.eyeAdaptation.renderPost();
            }
            if (m.lightFx.isEnabled()) {
                m.lightFx.renderPost();
            }
        } catch (RuntimeException ignored) {
        }
    }

    /** GUI-space screen layers, drawn under the vignette and the vanilla HUD. */
    public static void renderScreenLayers(GuiGraphics g) {
        ModuleManager m = m();
        Minecraft mc = Minecraft.getInstance();
        if (m == null || mc.player == null || mc.level == null || !Env.valid) {
            return;
        }
        if (m.colorGrading.isEnabled()) {
            m.colorGrading.render(g);
        }
        if (m.eyeAdaptation.isEnabled()) {
            m.eyeAdaptation.render(g);
        }
        if (m.lightFx.isEnabled()) {
            m.lightFx.render(g);
        }
        if (m.screenWeather.isEnabled()) {
            m.screenWeather.render(g);
        }
    }
}
