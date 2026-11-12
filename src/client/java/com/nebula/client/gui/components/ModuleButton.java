package com.nebula.client.gui.components;

import com.nebula.client.gui.themes.Theme;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.GlowRenderer;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RenderUtils.NFont;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;

/**
 * Module card. Left click toggles the module, right click (or the chevron) opens its settings.
 * Configuration-only modules open their settings on any click.
 */
public final class ModuleButton extends UiComponent {
	public static final float HEIGHT = 38;
	private final Module module;
	private final Consumer<Module> onSelect;
	private final Animation enabled;
	private final Animation selected = new Animation(14);
	private final Animation appear = new Animation(0, 12);
	private boolean isSelected;

	public ModuleButton(Module module, Consumer<Module> onSelect) {
		this.module = module;
		this.onSelect = onSelect;
		this.enabled = new Animation(module.isEnabled() ? 1 : 0, 16);
		this.h = HEIGHT;
	}

	public Module module() {
		return module;
	}

	public void setSelected(boolean selected) {
		isSelected = selected;
	}

	@Override
	public void render(GuiGraphics g, Theme t, double mx, double my) {
		float hv = updateHover(mx, my);
		float on = enabled.update(module.isEnabled() ? 1 : 0);
		float sel = selected.update(isSelected ? 1 : 0);
		float ap = Animation.easeOutCubic(appear.update(1));
		float ox = (1 - ap) * 12;
		float lx = x + ox;

		int base = ColorUtil.mix(t.panel(), 0xFFFFFFFF, 0.05F + 0.04F * hv);
		int border = ColorUtil.mix(t.border(), ColorUtil.withAlpha(t.accent(), 0.85F), sel);
		if (sel > 0.01F) {
			GlowRenderer.glow(g, lx, y, w, h, 10, 10, t.accent(), 0.35F * sel * t.glow());
		}
		RoundedRenderer.rect(g, lx, y, w, h, Math.min(t.radius(), 12), ColorUtil.withAlpha(base, (0.55F + 0.2F * hv) * ap), 1, ColorUtil.fade(border, ap));

		// Status dot.
		int dot = ColorUtil.mix(ColorUtil.withAlpha(t.textDim(), 0.5F), t.accent(), on);
		if (on > 0.01F) {
			GlowRenderer.glow(g, lx + 10, y + 11, 5, 5, 2.5F, 5, t.accent(), 0.8F * on * t.glow());
		}
		RoundedRenderer.circle(g, lx + 12.5F, y + 13.5F, 2.5F, dot);

		float textW = w - 64;
		RenderUtils.text(g, RenderUtils.ellipsize(module.name(), textW, NFont.TEXT), lx + 21, y + 9.5F, ColorUtil.fade(t.text(), ap), NFont.TEXT);
		RenderUtils.text(g, RenderUtils.ellipsize(module.description(), w - 30, NFont.TEXT), lx + 11, y + 23, ColorUtil.fade(t.textDim(), ap), NFont.TEXT);

		if (module.isAlwaysActive()) {
			String tag = "Always on";
			float tw = RenderUtils.width(tag, NFont.TEXT) * 0.8F + 10;
			RoundedRenderer.rect(g, lx + w - tw - 22, y + 7, tw, 11, 5.5F, ColorUtil.withAlpha(t.accent(), 0.18F));
			RenderUtils.text(g, tag, lx + w - tw - 17, y + 9.5F, t.accent(), NFont.TEXT, 0.8F);
		} else {
			Toggle.drawSwitch(g, t, lx + w - 48, y + 7, on, hv);
		}
		// Settings chevron.
		float cx = lx + w - 12, cy = y + 12.5F;
		int cc = ColorUtil.mix(t.textDim(), t.accent(), Math.max(sel, hv * 0.5F));
		RoundedRenderer.rect(g, cx - 2, cy - 3, 1.3F, 1.3F, 0.65F, cc);
		RoundedRenderer.rect(g, cx - 2, cy - 0.65F, 1.3F, 1.3F, 0.65F, cc);
		RoundedRenderer.rect(g, cx - 2, cy + 1.7F, 1.3F, 1.3F, 0.65F, cc);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (!isHovered(mx, my)) {
			return false;
		}
		boolean chevron = mx >= x + w - 20 && my <= y + 22;
		if (button == 1 || chevron || module.isAlwaysActive()) {
			onSelect.accept(module);
		} else if (button == 0) {
			module.toggle();
		}
		return true;
	}
}
