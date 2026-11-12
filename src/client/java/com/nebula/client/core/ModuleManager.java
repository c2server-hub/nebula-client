package com.nebula.client.core;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.modules.ui.ClickGuiModule;
import com.nebula.client.modules.ui.GuiSettings;
import com.nebula.client.modules.ui.Notifications;
import com.nebula.client.modules.ui.ThemeEditor;
import com.nebula.client.modules.visual.AmbientParticles;
import com.nebula.client.modules.visual.Aurora;
import com.nebula.client.modules.visual.BlockBreakEffects;
import com.nebula.client.modules.visual.BlockOutline;
import com.nebula.client.modules.visual.CustomCrosshair;
import com.nebula.client.modules.visual.CustomSky;
import com.nebula.client.modules.visual.Vignette;
import com.nebula.client.modules.visual.CustomHud;
import com.nebula.client.modules.visual.Halo;
import com.nebula.client.modules.visual.HitEffects;
import com.nebula.client.modules.visual.JumpCircles;
import com.nebula.client.modules.visual.TargetEsp;
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
	// Sky modules first: world effects render in registration order and the sky must sit underneath.
	public final CustomSky customSky = register(new CustomSky());
	public final Aurora aurora = register(new Aurora());
	public final PlayerOrbit playerOrbit = register(new PlayerOrbit());
	public final MotionTrails motionTrails = register(new MotionTrails());
	public final ParticleEffects particleEffects = register(new ParticleEffects());
	public final AmbientParticles ambientParticles = register(new AmbientParticles());
	public final BlockOutline blockOutline = register(new BlockOutline());
	public final BlockBreakEffects blockBreak = register(new BlockBreakEffects());
	public final JumpCircles jumpCircles = register(new JumpCircles());
	public final Halo halo = register(new Halo());
	public final TargetEsp targetEsp = register(new TargetEsp());
	public final HitEffects hitEffects = register(new HitEffects());
	public final CustomCrosshair crosshair = register(new CustomCrosshair());
	public final Vignette vignette = register(new Vignette());
	public final CustomHud customHud = register(new CustomHud());
	public final TargetHud targetHud = register(new TargetHud());
	public final WorldTimeDisplay worldTime = register(new WorldTimeDisplay());
	public final ClickGuiModule clickGui = register(new ClickGuiModule());
	public final Notifications notifications = register(new Notifications());
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

	/** Returns true when the key opened the ClickGUI (the event is then consumed). */
	public boolean onKeyPressed(int key) {
		// The GUI must stay reachable even if its key was unbound.
		int guiKey = clickGui.keybind().isBound() ? clickGui.keybind().key() : org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
		if (key == guiKey) {
			clickGui.open();
			return true;
		}
		for (Module m : modules) {
			if (m != clickGui && !m.isAlwaysActive() && m.keybind().isBound() && m.keybind().key() == key) {
				m.toggle();
			}
		}
		return false;
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
