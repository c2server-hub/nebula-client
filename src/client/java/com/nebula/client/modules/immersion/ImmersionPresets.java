package com.nebula.client.modules.immersion;

import com.nebula.client.core.ClientManager;
import com.nebula.client.core.ModuleManager;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.settings.ActionSetting;
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.settings.NumberSetting;

/** One-click presets and a global multiplier for every immersion effect. */
public final class ImmersionPresets extends Module {
    public final ModeSetting preset = add(new ModeSetting("Preset", "Overall style of the immersion modules", "Subtle", "Cinematic", "Subtle", "Vanilla+"));
    public final ActionSetting apply = add(new ActionSetting("Apply preset", "Writes the preset values into every immersion module", this::applyPreset));
    public final NumberSetting global = add(new NumberSetting("Global intensity", "Multiplies every camera / hand / screen effect", 1.0, 0, 1.5, 0.05, "x"));

    public ImmersionPresets() {
        super("immersion_presets", "Immersion Presets", "Cinematic / Subtle / Vanilla+ and global intensity", Category.IMMERSION, true, true);
    }

    public float globalIntensity() {
        return global.floatValue();
    }

    public void applyPreset() {
        if (!ClientManager.isReady()) {
            return;
        }
        ModuleManager m = ClientManager.get().modules();
        ImmersiveCamera c = m.immersiveCamera;
        HandAnimations h = m.handAnimations;
        switch (preset.get()) {
            case "Cinematic" -> {
                c.setEnabled(true);
                c.bob.set(true); c.replaceBob.set(true); c.bobAmount.set(1.3);
                c.sway.set(true); c.swayAmount.set(1.4);
                c.landing.set(true); c.landingAmount.set(1.4);
                c.inertia.set(true); c.inertiaAmount.set(0.45);
                c.smoothSneak.set(true); c.sneakSpeed.set(5.0);
                c.shake.set(true); c.shakeAmount.set(1.3);
                c.dynamicFov.set(true); c.fovStrength.set(0.8); c.fovSmoothing.set(5.0);
                h.setEnabled(true); h.inspect.set(true); h.equip.set(true); h.sway.set(true); h.swayAmount.set(1.3);
                h.bowTension.set(true); h.shield.set(true); h.breathing.set(true);
                m.colorGrading.setEnabled(true); m.colorGrading.intensity.set(0.8);
                m.eyeAdaptation.setEnabled(true); m.eyeAdaptation.strength.set(0.8); m.eyeAdaptation.distortion.set(true);
                m.screenWeather.setEnabled(true); m.screenWeather.dropAmount.set(1.2); m.screenWeather.postRainMist.set(true); m.screenWeather.biomeHaze.set(true);
                m.lightFx.setEnabled(true); m.lightFx.bloom.set(true); m.lightFx.bloomStrength.set(0.45); m.lightFx.godRays.set(true); m.lightFx.raysStrength.set(0.8);
                m.immersiveSound.setEnabled(true); m.immersiveSound.reverbAmount.set(1.0);
                m.diegeticHud.setEnabled(true);
                m.atmosphereParticles.setEnabled(true); m.atmosphereParticles.density.set(70.0);
                global.set(1.0);
            }
            case "Vanilla+" -> {
                c.setEnabled(true);
                c.bob.set(false); c.replaceBob.set(false);
                c.sway.set(true); c.swayAmount.set(0.3);
                c.landing.set(true); c.landingAmount.set(0.4);
                c.inertia.set(false);
                c.smoothSneak.set(true); c.sneakSpeed.set(8.0);
                c.shake.set(true); c.shakeAmount.set(0.4);
                c.dynamicFov.set(true); c.fovStrength.set(0.9); c.fovSmoothing.set(8.0);
                h.setEnabled(true); h.inspect.set(true); h.equip.set(true); h.sway.set(true); h.swayAmount.set(0.5);
                h.bowTension.set(true); h.shield.set(true); h.breathing.set(false);
                m.colorGrading.setEnabled(false);
                m.eyeAdaptation.setEnabled(true); m.eyeAdaptation.strength.set(0.35); m.eyeAdaptation.distortion.set(false);
                m.screenWeather.setEnabled(true); m.screenWeather.dropAmount.set(0.5); m.screenWeather.postRainMist.set(false); m.screenWeather.biomeHaze.set(false);
                m.lightFx.setEnabled(false);
                m.immersiveSound.setEnabled(true); m.immersiveSound.reverbAmount.set(0.5);
                m.diegeticHud.setEnabled(false);
                m.atmosphereParticles.setEnabled(true); m.atmosphereParticles.density.set(30.0);
                global.set(1.0);
            }
            default -> { // Subtle
                c.setEnabled(true);
                c.bob.set(true); c.replaceBob.set(true); c.bobAmount.set(0.6);
                c.sway.set(true); c.swayAmount.set(0.6);
                c.landing.set(true); c.landingAmount.set(0.7);
                c.inertia.set(false);
                c.smoothSneak.set(true); c.sneakSpeed.set(6.0);
                c.shake.set(true); c.shakeAmount.set(0.6);
                c.dynamicFov.set(true); c.fovStrength.set(0.5); c.fovSmoothing.set(6.0);
                h.setEnabled(true); h.inspect.set(true); h.equip.set(true); h.sway.set(true); h.swayAmount.set(1.0);
                h.bowTension.set(true); h.shield.set(true); h.breathing.set(true);
                m.colorGrading.setEnabled(true); m.colorGrading.intensity.set(0.4);
                m.eyeAdaptation.setEnabled(true); m.eyeAdaptation.strength.set(0.5); m.eyeAdaptation.distortion.set(true);
                m.screenWeather.setEnabled(true); m.screenWeather.dropAmount.set(0.6); m.screenWeather.postRainMist.set(true); m.screenWeather.biomeHaze.set(true);
                m.lightFx.setEnabled(true); m.lightFx.bloom.set(true); m.lightFx.bloomStrength.set(0.25); m.lightFx.godRays.set(true); m.lightFx.raysStrength.set(0.4);
                m.immersiveSound.setEnabled(true); m.immersiveSound.reverbAmount.set(0.6);
                m.diegeticHud.setEnabled(true);
                m.atmosphereParticles.setEnabled(true); m.atmosphereParticles.density.set(45.0);
                global.set(1.0);
            }
        }
    }
}
