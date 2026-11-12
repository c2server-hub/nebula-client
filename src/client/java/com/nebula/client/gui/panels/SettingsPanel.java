package com.nebula.client.gui.panels;

import com.nebula.client.gui.components.KeybindEditor;
import com.nebula.client.gui.components.Panel;
import com.nebula.client.gui.components.SettingComponent;
import com.nebula.client.gui.components.Toggle;
import com.nebula.client.gui.components.UiComponent;
import com.nebula.client.gui.themes.Theme;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RenderUtils.NFont;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.settings.Setting;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Header with the selected module, then every visible setting as an interactive row. */
public final class SettingsPanel extends Panel {
	private static final float HEADER = 46;
	private final List<SettingComponent<?>> all = new ArrayList<>();
	private final List<UiComponent> visible = new ArrayList<>();
	private final Animation swap = new Animation(12);
	private final Animation headerToggle = new Animation(14);
	private @Nullable Module module;
	private float bodyY, bodyH;

	public SettingsPanel() {
		this.spacing = 3;
		this.padding = 8;
	}

	public @Nullable Module module() {
		return module;
	}

	public void show(@Nullable Module module) {
		if (module == this.module) {
			return;
		}
		this.module = module;
		all.clear();
		if (module != null) {
			for (Setting<?> s : module.settings()) {
				all.add(SettingComponent.create(s));
			}
			headerToggle.snap(module.isEnabled() ? 1 : 0);
		}
		swap.snap(0);
		resetScroll();
	}

	public boolean isCapturingKeys() {
		for (SettingComponent<?> c : all) {
			if (c instanceof KeybindEditor k && k.isListening()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void render(GuiGraphics g, Theme t, double mx, double my) {
		if (module == null) {
			RenderUtils.centered(g, "Select a module", x + w / 2, y + h / 2 - 10, t.textDim(), NFont.TEXT, 1);
			RenderUtils.centered(g, "Right-click a card to edit it", x + w / 2, y + h / 2 + 3, ColorUtil.withAlpha(t.textDim(), 0.7F), NFont.TEXT, 0.9F);
			return;
		}
		float a = Animation.easeOutCubic(swap.update(1));
		float slide = (1 - a) * 10;
		float hx = x + 8 + slide;
		RenderUtils.text(g, module.name(), hx, y + 8, ColorUtil.fade(t.text(), a), NFont.DISPLAY);
		RenderUtils.text(g, RenderUtils.ellipsize(module.description(), w - 56, NFont.TEXT), hx, y + 25, ColorUtil.fade(t.textDim(), a), NFont.TEXT);
		if (!module.isAlwaysActive()) {
			float hv = mx >= x + w - 36 && mx < x + w - 6 && my >= y + 6 && my < y + 22 ? 1 : 0;
			Toggle.drawSwitch(g, t, x + w - 34, y + 9, headerToggle.update(module.isEnabled() ? 1 : 0), hv);
		}
		RoundedRenderer.rect(g, x + 8, y + HEADER - 4, w - 16, 1, 0, t.border());

		visible.clear();
		for (SettingComponent<?> c : all) {
			if (c.setting().isVisible()) {
				visible.add(c);
			}
		}
		float savedY = y, savedH = h;
		bodyY = y + HEADER;
		bodyH = h - HEADER;
		y = bodyY;
		h = bodyH;
		layoutAndRender(g, t, mx, my, visible);
		y = savedY;
		h = savedH;
	}

	private boolean inBody(double mx, double my) {
		return mx >= x && mx < x + w && my >= bodyY && my < bodyY + bodyH;
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (module == null) {
			return false;
		}
		if (!module.isAlwaysActive() && button == 0 && mx >= x + w - 36 && mx < x + w - 6 && my >= y + 6 && my < y + 22) {
			module.toggle();
			return true;
		}
		return clickChildren(visible, mx, my, button, inBody(mx, my));
	}

	@Override
	public void mouseReleased(double mx, double my, int button) {
		for (UiComponent c : visible) {
			c.mouseReleased(mx, my, button);
		}
	}

	@Override
	public void mouseDragged(double mx, double my) {
		for (UiComponent c : visible) {
			c.mouseDragged(mx, my);
		}
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		for (UiComponent c : visible) {
			if (c.keyPressed(key, modifiers)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double amount) {
		if (!inBody(mx, my)) {
			return false;
		}
		float savedY = y, savedH = h;
		y = bodyY;
		h = bodyH;
		boolean r = super.mouseScrolled(mx, my, amount);
		y = savedY;
		h = savedH;
		return r;
	}
}
