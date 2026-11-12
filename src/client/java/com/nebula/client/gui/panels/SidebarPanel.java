package com.nebula.client.gui.panels;

import com.nebula.client.core.ClientManager;
import com.nebula.client.gui.Style;
import com.nebula.client.gui.components.CategoryTab;
import com.nebula.client.gui.components.UiComponent;
import com.nebula.client.gui.themes.Theme;
import com.nebula.client.rendering.GlowRenderer;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RenderUtils.NFont;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Logo, category navigation, workspace shortcuts and configuration actions. */
public final class SidebarPanel extends UiComponent {
	private final List<CategoryTab> navigation;
	private final List<CategoryTab> workspace;
	private final List<CategoryTab> config;

	public SidebarPanel(List<CategoryTab> navigation, List<CategoryTab> workspace, List<CategoryTab> config) {
		this.navigation = navigation;
		this.workspace = workspace;
		this.config = config;
	}

	@Override
	public void render(GuiGraphics g, Theme t, double mx, double my) {
		// Logo: glowing orb + wordmark.
		float lx = x + 14, ly = y + 16;
		GlowRenderer.glow(g, lx, ly, 16, 16, 8, 10, t.accent2(), 0.9F * t.glow());
		RoundedRenderer.gradient(g, lx, ly, 16, 16, 8, Style.accentGradient(t, 1));
		RoundedRenderer.circle(g, lx + 5.5F, ly + 5.5F, 2.5F, 0xBBFFFFFF);
		RenderUtils.text(g, "NEBULA", lx + 22, ly + 2, t.text(), NFont.DISPLAY);
		RenderUtils.text(g, "client", lx + 22 + RenderUtils.width("NEBULA", NFont.DISPLAY) + 4, ly + 5, t.textDim(), NFont.TEXT, 0.85F);

		float cy = y + 50;
		cy = section(g, t, "MODULES", navigation, cy, mx, my);
		section(g, t, "WORKSPACE", workspace, cy + 6, mx, my);

		float by = y + h - 10 - config.size() * 26 - 14;
		String status = ClientManager.get().config().lastStatus();
		RenderUtils.text(g, RenderUtils.ellipsize(status, w - 24, NFont.TEXT), x + 12, by, ColorUtil.withAlpha(t.textDim(), 0.8F), NFont.TEXT, 0.8F);
		by += 12;
		for (CategoryTab tab : config) {
			tab.setBounds(x + 8, by, w - 16, 24);
			tab.render(g, t, mx, my);
			by += 26;
		}
	}

	private float section(GuiGraphics g, Theme t, String title, List<CategoryTab> tabs, float cy, double mx, double my) {
		RenderUtils.text(g, title, x + 12, cy, ColorUtil.withAlpha(t.textDim(), 0.7F), NFont.TEXT, 0.75F);
		cy += 11;
		for (CategoryTab tab : tabs) {
			tab.setBounds(x + 8, cy, w - 16, 24);
			tab.render(g, t, mx, my);
			cy += 26;
		}
		return cy;
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		for (List<CategoryTab> group : List.of(navigation, workspace, config)) {
			for (CategoryTab tab : group) {
				if (tab.mouseClicked(mx, my, button)) {
					return true;
				}
			}
		}
		return false;
	}
}
