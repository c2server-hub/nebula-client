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
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(NebulaClient.MOD_ID, "hud"), this::render);
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
