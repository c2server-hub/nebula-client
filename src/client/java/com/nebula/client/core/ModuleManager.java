package com.nebula.client.core;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.modules.ui.ArmorHud;
import com.nebula.client.modules.ui.ClickGuiModule;
import com.nebula.client.modules.ui.Compass;
import com.nebula.client.modules.ui.GuiSettings;
import com.nebula.client.modules.ui.ItemUse;
import com.nebula.client.modules.ui.Keystrokes;
import com.nebula.client.modules.ui.Notifications;
import com.nebula.client.modules.ui.PotionHud;
import com.nebula.client.modules.ui.ThemeEditor;
import com.nebula.client.modules.ui.Zoom;
import com.nebula.client.modules.visual.AmbientParticles;
import com.nebula.client.modules.visual.Aurora;
import com.nebula.client.modules.visual.Bloom;
import com.nebula.client.modules.visual.BlockBreakEffects;
import com.nebula.client.modules.visual.BlockOutline;
import com.nebula.client.modules.visual.ComboCounter;
import com.nebula.client.modules.visual.CustomCrosshair;
import com.nebula.client.modules.visual.Celestial;
import com.nebula.client.modules.visual.CosmicWings;
import com.nebula.client.modules.visual.CustomSky;
import com.nebula.client.modules.visual.DamageNumbers;
import com.nebula.client.modules.visual.ItemGlow;
import com.nebula.client.modules.visual.Vignette;
import com.nebula.client.modules.visual.CustomHud;
import com.nebula.client.modules.visual.DeathFx;
import com.nebula.client.modules.visual.EnergyShield;
import com.nebula.client.modules.visual.Halo;
import com.nebula.client.modules.visual.HitEffects;
import com.nebula.client.modules.visual.HurtEffects;
import com.nebula.client.modules.visual.JumpCircles;
import com.nebula.client.modules.visual.LensFlare;
import com.nebula.client.modules.visual.TargetEsp;
import com.nebula.client.modules.visual.MotionTrails;
import com.nebula.client.modules.visual.Companion;
import com.nebula.client.modules.visual.ParticleEffects;
import com.nebula.client.modules.visual.PortalFx;
import com.nebula.client.modules.visual.ProjectileTrails;
import com.nebula.client.modules.visual.PlayerOrbit;
import com.nebula.client.modules.visual.RuneCircle;
import com.nebula.client.modules.visual.ShootingStars;
import com.nebula.client.modules.visual.SlashFx;
import com.nebula.client.modules.visual.SoulSteps;
import com.nebula.client.modules.visual.SprintFx;
import com.nebula.client.modules.visual.TargetHud;
import com.nebula.client.modules.visual.VoidFog;
import com.nebula.client.modules.visual.WorldTimeDisplay;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ModuleManager {
	private final List<Module> modules = new ArrayList<>();

	// Sky: the sky passes render in registration order and must sit underneath everything else.
	public final CustomSky customSky = register(new CustomSky());
	public final Aurora aurora = register(new Aurora());
	public final Celestial celestial = register(new Celestial());
	public final ShootingStars shootingStars = register(new ShootingStars());
	public final LensFlare lensFlare = register(new LensFlare());
	public final Bloom bloom = register(new Bloom());

	// World ambience.
	public final AmbientParticles ambientParticles = register(new AmbientParticles());
	public final VoidFog voidFog = register(new VoidFog());
	public final BlockOutline blockOutline = register(new BlockOutline());
	public final BlockBreakEffects blockBreak = register(new BlockBreakEffects());
	public final ItemGlow itemGlow = register(new ItemGlow());
	public final PortalFx portalFx = register(new PortalFx());
	public final ProjectileTrails projectileTrails = register(new ProjectileTrails());

	// Player effects.
	public final PlayerOrbit playerOrbit = register(new PlayerOrbit());
	public final MotionTrails motionTrails = register(new MotionTrails());
	public final CosmicWings cosmicWings = register(new CosmicWings());
	public final Halo halo = register(new Halo());
	public final EnergyShield energyShield = register(new EnergyShield());
	public final ParticleEffects particleEffects = register(new ParticleEffects());
	public final JumpCircles jumpCircles = register(new JumpCircles());
	public final Companion companion = register(new Companion());
	public final SoulSteps soulSteps = register(new SoulSteps());
	public final SprintFx sprintFx = register(new SprintFx());
	public final RuneCircle runeCircle = register(new RuneCircle());

	// Combat feedback.
	public final TargetEsp targetEsp = register(new TargetEsp());
	public final TargetHud targetHud = register(new TargetHud());
	public final HitEffects hitEffects = register(new HitEffects());
	public final HurtEffects hurtEffects = register(new HurtEffects());
	public final DeathFx deathFx = register(new DeathFx());
	public final SlashFx slashFx = register(new SlashFx());
	public final DamageNumbers damageNumbers = register(new DamageNumbers());
	public final ComboCounter comboCounter = register(new ComboCounter());

	// Interface: HUD, GUI and controls.
	public final CustomCrosshair crosshair = register(new CustomCrosshair());
	public final Keystrokes keystrokes = register(new Keystrokes());
	public final PotionHud potionHud = register(new PotionHud());
	public final ArmorHud armorHud = register(new ArmorHud());
	public final Vignette vignette = register(new Vignette());
	public final WorldTimeDisplay worldTime = register(new WorldTimeDisplay());
	public final CustomHud customHud = register(new CustomHud());
	public final Zoom zoom = register(new Zoom());
	public final Compass compass = register(new Compass());
	public final ItemUse itemUse = register(new ItemUse());
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

	/** Modules of one category in registration order; used by the module list panel. */
	public List<Module> ofCategory(Category category) {
		List<Module> result = new ArrayList<>();
		for (Module m : modules) {
			if (m.category() == category) {
				result.add(m);
			}
		}
		return result;
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
