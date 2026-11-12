package com.nebula.client.core;

import com.nebula.client.NebulaClient;
import com.nebula.client.config.ConfigManager;
import com.nebula.client.gui.hud.HudManager;
import com.nebula.client.modules.ModuleEvents;
import com.nebula.client.rendering.world.WorldEffects;
import com.nebula.client.settings.Setting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;

/** Owns every subsystem and wires them to Fabric events. */
public final class ClientManager {
	private static ClientManager instance;

	private final ModuleManager modules;
	private final ConfigManager config;
	private final HudManager hud = new HudManager();
	private final WorldEffects worldEffects = new WorldEffects();
	private final TargetTracker targets = new TargetTracker();

	private ClientManager() {
		this.modules = new ModuleManager();
		this.config = new ConfigManager(FabricLoader.getInstance().getConfigDir().resolve(NebulaClient.MOD_ID), modules);
	}

	public static void init() {
		if (instance != null) {
			return;
		}
		ClientManager m = new ClientManager();
		instance = m;
		Setting.setChangeHook(m.config::markDirty);
		ModuleEvents.addListener(module -> m.config.markDirty());
		ModuleEvents.addListener(m.modules.notifications::moduleToggled);
		m.targets.register();
		m.targets.onAttack(m.modules.hitEffects::onHit);
		ClientPlayerBlockBreakEvents.AFTER.register((level, player, pos, state) -> m.modules.blockBreak.onBreak(level, pos, state));
		WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> !m.modules.blockOutline.isEnabled());
		m.config.load();

		m.hud.add(m.modules.customHud.elements.get(0));
		m.hud.add(m.modules.customHud.elements.get(1));
		m.hud.add(m.modules.customHud.elements.get(2));
		m.hud.add(m.modules.targetHud.element);
		m.hud.add(m.modules.worldTime.element);
		m.hud.register();
		m.worldEffects.register();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			m.modules.tick();
			m.config.tick();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			m.config.save();
			m.worldEffects.close();
			com.nebula.client.rendering.BlurRenderer.INSTANCE.close();
		});
		NebulaClient.LOGGER.info("[Nebula] Initialised {} modules", m.modules.all().size());
	}

	public static boolean isReady() {
		return instance != null;
	}

	public static ClientManager get() {
		if (instance == null) {
			throw new IllegalStateException("Nebula is not initialised");
		}
		return instance;
	}

	public ModuleManager modules() {
		return modules;
	}

	public ConfigManager config() {
		return config;
	}

	public HudManager hud() {
		return hud;
	}

	public TargetTracker targets() {
		return targets;
	}
}
