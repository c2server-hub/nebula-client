package com.nebula.client.gui.hud;

import com.nebula.client.NebulaClient;
import com.nebula.client.gui.HudEditorScreen;
import com.nebula.client.gui.themes.Theme;
import com.nebula.client.core.ClientManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HudManager {
	private final List<HudElement> elements = new ArrayList<>();

	public void add(HudElement element) {
		elements.add(element);
	}

	public List<HudElement> elements() {
		return Collections.unmodifiableList(elements);
	}

	public void register() {
		// Underneath every vanilla HUD layer, so the hotbar and chat are not darkened.
		HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath(NebulaClient.MOD_ID, "vignette"), (g, delta) -> {
			if (ClientManager.isReady() && ClientManager.get().modules().vignette.isEnabled()) {
				ClientManager.get().modules().vignette.render(g);
			}
		});
		HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath(NebulaClient.MOD_ID, "hurt_flash"), (g, delta) -> {
			if (ClientManager.isReady() && ClientManager.get().modules().hurtEffects.isEnabled()) {
				ClientManager.get().modules().hurtEffects.renderFlash(g);
			}
		});
		// Order among the addFirst layers: flare first, then hurt flash, void fog, vignette on top.
		HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath(NebulaClient.MOD_ID, "lens_flare"), (g, delta) -> {
			if (ClientManager.isReady() && ClientManager.get().modules().lensFlare.isEnabled()) {
				ClientManager.get().modules().lensFlare.render(g);
			}
		});
		HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath(NebulaClient.MOD_ID, "portal_tint"), (g, delta) -> {
			if (ClientManager.isReady() && ClientManager.get().modules().portalFx.isEnabled()) {
				ClientManager.get().modules().portalFx.renderHud(g);
			}
		});
		HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath(NebulaClient.MOD_ID, "void_fog"), (g, delta) -> {
			if (ClientManager.isReady() && ClientManager.get().modules().voidFog.isEnabled()) {
				ClientManager.get().modules().voidFog.render(g);
			}
		});
		// Full-screen speed lines sit under every HUD panel.
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(NebulaClient.MOD_ID, "sprint_fx"), (g, delta) -> {
			if (ClientManager.isReady() && ClientManager.get().modules().sprintFx.isEnabled()) {
				ClientManager.get().modules().sprintFx.render(g);
			}
		});
		// Damage numbers are anchored in the world; they draw under panels but above the streaks.
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(NebulaClient.MOD_ID, "damage_numbers"), (g, delta) -> {
			if (ClientManager.isReady() && ClientManager.get().modules().damageNumbers.isEnabled()) {
				ClientManager.get().modules().damageNumbers.renderHud(g, Theme.current());
			}
		});
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(NebulaClient.MOD_ID, "hud"), this::render);
		// The use-progress ring wraps the crosshair, so it draws above everything else.
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(NebulaClient.MOD_ID, "item_use"), (g, delta) -> {
			if (ClientManager.isReady() && ClientManager.get().modules().itemUse.isEnabled()) {
				ClientManager.get().modules().itemUse.render(g, Theme.current());
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (g, delta) -> {
			if (!ClientManager.isReady() || !ClientManager.get().modules().crosshair.render(g)) {
				vanilla.render(g, delta);
			}
		});
	}

	private void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || mc.screen instanceof HudEditorScreen || mc.player == null) {
			return;
		}
		Theme theme = Theme.current();
		int sw = g.guiWidth();
		int sh = g.guiHeight();
		for (HudElement e : elements) {
			if (e.isVisible()) {
				e.render(g, theme, e.left(sw), e.top(sh), false);
			}
		}
		var notifications = ClientManager.get().modules().notifications;
		if (notifications.isEnabled()) {
			notifications.render(g, theme, elements);
		}
	}
}
