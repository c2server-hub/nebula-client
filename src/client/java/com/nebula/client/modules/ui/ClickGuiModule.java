package com.nebula.client.modules.ui;

import com.nebula.client.gui.ClickGui;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class ClickGuiModule extends Module {
	public ClickGuiModule() {
		super("click_gui", "ClickGUI", "Opens this interface; change its key below", Category.INTERFACE, true, true);
		keybind.set(GLFW.GLFW_KEY_RIGHT_SHIFT);
	}

	@Override
	public void resetSettings() {
		super.resetSettings();
		keybind.set(GLFW.GLFW_KEY_RIGHT_SHIFT);
	}

	public void open() {
		Minecraft mc = Minecraft.getInstance();
		if (!(mc.screen instanceof ClickGui)) {
			mc.setScreen(new ClickGui());
		}
	}
}
