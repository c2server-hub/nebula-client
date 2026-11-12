package com.nebula.client.core;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.modules.ui.ClickGuiModule;
import com.nebula.client.modules.ui.GuiSettings;
import com.nebula.client.modules.ui.ThemeEditor;
import com.nebula.client.modules.visual.CustomHud;
import com.nebula.client.modules.visual.MotionTrails;
import com.nebula.client.modules.visual.ParticleEffects;
import com.nebula.client.modules.visual.PlayerOrbit;
import com.nebula.client.modules.visual.TargetHud;
import com.nebula.client.modules.visual.WorldTimeDisplay;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ModuleManager {
	private final List<Module> modules = new ArrayList<>();
	public final PlayerOrbit playerOrbit = register(new PlayerOrbit());
	public final MotionTrails motionTrails = register(new MotionTrails());
	public final ParticleEffects particleEffects = register(new ParticleEffects());
	public final CustomHud customHud = register(new CustomHud());
	public final TargetHud targetHud = register(new TargetHud());
	public final WorldTimeDisplay worldTime = register(new WorldTimeDisplay());
	public final ClickGuiModule clickGui = register(new ClickGuiModule());
	public final ThemeEditor themeEditor = register(new ThemeEditor());
	public final GuiSettings guiSettings = register(new GuiSettings());

	private <M extends Module> M register(M module) {
		modules.add(module);
		return module;
	}

	public List<Module> all() {
		return Collections.unmodifiableList(modules);
	}

	public Module byId(String id) {
		for (Module m : modules) {
			if (m.id().equals(id)) {
				return m;
			}
		}
		return null;
	}

	public List<Module> search(Category category, String query) {
		String q = query.trim().toLowerCase(Locale.ROOT);
		List<Module> result = new ArrayList<>();
		for (Module m : modules) {
			boolean matchesCategory = category == null || m.category() == category;
			boolean matchesQuery = q.isEmpty()
				|| m.name().toLowerCase(Locale.ROOT).contains(q)
				|| m.description().toLowerCase(Locale.ROOT).contains(q);
			// While searching, look across every category.
			if ((q.isEmpty() ? matchesCategory : true) && matchesQuery) {
				result.add(m);
			}
		}
		return result;
	}

	public void onKeyPressed(int key) {
		// The GUI must stay reachable even if its key was unbound.
		int guiKey = clickGui.keybind().isBound() ? clickGui.keybind().key() : org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
		if (key == guiKey) {
			clickGui.open();
			return;
		}
		for (Module m : modules) {
			if (m != clickGui && !m.isAlwaysActive() && m.keybind().isBound() && m.keybind().key() == key) {
				m.toggle();
			}
		}
	}

	public void tick() {
		if (Minecraft.getInstance().player == null) {
			return;
		}
		for (Module m : modules) {
			if (m.isEnabled()) {
				m.onTick();
			}
		}
	}
}
