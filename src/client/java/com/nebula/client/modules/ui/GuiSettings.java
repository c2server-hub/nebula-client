package com.nebula.client.modules.ui;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.BlurRenderer;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.Animation;

public final class GuiSettings extends Module {
	public final NumberSetting scale = add(new NumberSetting("Scale", "Size of the ClickGUI", 1.0, 0.6, 1.5, 0.05, "x"));
	public final NumberSetting animationSpeed = add(new NumberSetting("Animation speed", "Multiplier for every transition", 1.0, 0.25, 3.0, 0.05, "x"))
		.onChange(this::sync);
	public final NumberSetting opacity = add(new NumberSetting("Opacity", "Panel tint opacity over the blurred backdrop", 0.72, 0.2, 1.0, 0.01));
	public final NumberSetting radius = add(new NumberSetting("Radius", "Corner radius of components", 10, 0, 20, 1, "px"));
	public final NumberSetting borderWidth = add(new NumberSetting("Border width", "Thickness of panel borders", 1, 0, 3, 0.25, "px"));
	public final BoolSetting blur = add(new BoolSetting("Background blur", "Gaussian-blurred liquid glass panels", true));
	public final NumberSetting blurRadius = add(new NumberSetting("Blur radius", "Gaussian kernel radius", 6, 1, 16, 0.5))
		.visibleWhen(blur::isOn).onChange(this::sync);
	public final NumberSetting refraction = add(new NumberSetting("Refraction", "Edge lensing of glass panels", 0.6, 0, 1.5, 0.05))
		.visibleWhen(blur::isOn);
	public final NumberSetting glow = add(new NumberSetting("Glow", "Intensity of accent glows", 0.8, 0, 2, 0.05));
	public final BoolSetting hudGlass = add(new BoolSetting("HUD glass", "Blur behind HUD panels (costs a blur pass per frame)", true));

	public GuiSettings() {
		super("gui_settings", "GUI Settings", "Scale, animation, transparency and geometry of the interface", Category.INTERFACE, true, true);
		sync();
	}

	private void sync() {
		Animation.setSpeedMultiplier(animationSpeed.floatValue());
		BlurRenderer.INSTANCE.configure(blurRadius.floatValue(), 2);
	}
}
