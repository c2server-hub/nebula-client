package com.nebula.client.modules.ui;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.settings.NumberSetting;
import org.lwjgl.glfw.GLFW;

/**
 * Smooth cinematic zoom, toggled by its keybind (default C). The FOV multiplier eases in and out
 * with an exponential filter and is applied by GameRendererMixin at the very source of the
 * vanilla projection, so terrain, entities and hand all zoom together.
 */
public final class Zoom extends Module {
	public final NumberSetting factor = add(new NumberSetting("Zoom factor", "How far you zoom in", 4, 2, 10, 0.5, "x"));
	public final NumberSetting smoothness = add(new NumberSetting("Smoothness", "Transition speed", 9, 2, 20, 1));

	private float current = 1;
	private long lastNanos;

	public Zoom() {
		super("zoom", "Zoom", "Smooth cinematic zoom on a key", Category.INTERFACE);
		keybind.set(GLFW.GLFW_KEY_C);
	}

	@Override
	public void resetSettings() {
		super.resetSettings();
		keybind.set(GLFW.GLFW_KEY_C);
	}

	@Override
	protected void onDisable() {
		// Let the animation ease back out instead of snapping.
	}

	/**
 	* Current FOV multiplier in ]0, 1]; animated with a real-time exponential filter so the
 	* speed setting works identically at any frame rate. Called by GameRendererMixin.
 	*/
	public float multiplier() {
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0 : Math.min((now - lastNanos) / 1e9F, 0.1F);
		lastNanos = now;
		float target = isEnabled() ? 1F / factor.floatValue() : 1F;
		float k = 1 - (float) Math.exp(-smoothness.floatValue() * dt);
		current += (target - current) * k;
		if (Math.abs(target - current) < 0.002F) {
			current = target;
		}
		return Math.max(0.03F, current);
	}
}
