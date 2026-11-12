package com.nebula.client.gui.panels;

import com.nebula.client.core.ClientManager;
import com.nebula.client.gui.components.ModuleButton;
import com.nebula.client.gui.components.Panel;
import com.nebula.client.gui.themes.Theme;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RenderUtils.NFont;
import net.minecraft.client.gui.GuiGraphics;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Filtered, scrollable list of module cards. */
public final class ModuleListPanel extends Panel {
	private final Map<Module, ModuleButton> buttons = new HashMap<>();
	private final List<ModuleButton> visible = new ArrayList<>();
	private final Consumer<Module> onSelect;
	private @Nullable Category category;
	private String query = "";
	private boolean dirty = true;

	public ModuleListPanel(Consumer<Module> onSelect) {
		this.onSelect = onSelect;
		this.spacing = 6;
	}

	public void filter(@Nullable Category category, String query) {
		if (category != this.category || !query.equals(this.query)) {
			this.category = category;
			this.query = query;
			dirty = true;
			resetScroll();
		}
	}

	public List<ModuleButton> visibleButtons() {
		return visible;
	}

	public void setSelected(@Nullable Module module) {
		for (ModuleButton b : buttons.values()) {
			b.setSelected(b.module() == module);
		}
	}

	@Override
	public void render(GuiGraphics g, Theme t, double mx, double my) {
		if (dirty) {
			visible.clear();
			for (Module m : ClientManager.get().modules().search(category, query)) {
				// Fresh buttons on every filter change replay the slide-in animation.
				ModuleButton b = new ModuleButton(m, onSelect);
				buttons.put(m, b);
				visible.add(b);
			}
			dirty = false;
		}
		if (visible.isEmpty()) {
			RenderUtils.centered(g, "No module matches \"" + query + "\"", x + w / 2, y + 24, t.textDim(), NFont.TEXT, 1);
			return;
		}
		layoutAndRender(g, t, mx, my, visible);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		return clickChildren(visible, mx, my, button, isHovered(mx, my));
	}
}
