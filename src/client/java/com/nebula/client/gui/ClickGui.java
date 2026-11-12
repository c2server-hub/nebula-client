package com.nebula.client.gui;

import com.nebula.client.core.ClientManager;
import com.nebula.client.gui.components.CategoryTab;
import com.nebula.client.gui.components.SearchBar;
import com.nebula.client.gui.panels.ModuleListPanel;
import com.nebula.client.gui.panels.SettingsPanel;
import com.nebula.client.gui.panels.SidebarPanel;
import com.nebula.client.gui.themes.Theme;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.modules.ui.GuiSettings;
import com.nebula.client.rendering.GlowRenderer;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RenderUtils.NFont;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.rendering.ShapeElement;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** The Nebula ClickGUI: one floating glass window with sidebar, module list and settings panel. */
public final class ClickGui extends Screen {
	private static final float WIDTH = 620;
	private static final float HEIGHT = 370;
	private static final float SIDEBAR = 140;
	private static final float LIST = 232;

	// Remembered across openings, like a real application window.
	private static Category lastCategory = Category.VISUAL;
	private static Module lastSelected;

	private final Animation open = new Animation(0, 11);
	private final SearchBar search = new SearchBar();
	private final ModuleListPanel moduleList = new ModuleListPanel(this::select);
	private final SettingsPanel settings = new SettingsPanel();
	private final CategoryTab visualTab;
	private final CategoryTab interfaceTab;
	private final CategoryTab resetTab;
	private final SidebarPanel sidebar;
	private Category category = lastCategory;
	private boolean closing;
	private long resetArmedAt;
	private float scale = 1, wx, wy;

	public ClickGui() {
		super(Component.literal("Nebula"));
		visualTab = new CategoryTab("Visual", CategoryTab.Icon.ORB, () -> setCategory(Category.VISUAL));
		interfaceTab = new CategoryTab("Interface", CategoryTab.Icon.GRID, () -> setCategory(Category.INTERFACE));
		CategoryTab themeTab = new CategoryTab("Theme", CategoryTab.Icon.PALETTE, () -> {
			setCategory(Category.INTERFACE);
			select(ClientManager.get().modules().themeEditor);
		});
		CategoryTab layoutTab = new CategoryTab("HUD Layout", CategoryTab.Icon.LAYOUT, () -> minecraft.setScreen(new HudEditorScreen(this)));
		CategoryTab saveTab = new CategoryTab("Save config", CategoryTab.Icon.SAVE, () -> ClientManager.get().config().save());
		resetTab = new CategoryTab("Reset all", CategoryTab.Icon.GRID, this::reset);
		sidebar = new SidebarPanel(List.of(visualTab, interfaceTab), List.of(themeTab, layoutTab), List.of(saveTab, resetTab));
		settings.show(lastSelected);
	}

	private void reset() {
		long now = System.currentTimeMillis();
		if (now - resetArmedAt < 3000) {
			ClientManager.get().config().resetAll();
			resetArmedAt = 0;
		} else {
			resetArmedAt = now;
			ClientManager.get().config().noteStatus("Click Reset again to confirm");
		}
	}

	private void setCategory(Category c) {
		category = c;
		lastCategory = c;
	}

	private void select(Module module) {
		lastSelected = module;
		settings.show(module);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		// The window draws its own blurred backdrop; vanilla's menu blur would blur twice.
	}

	private double localX(double mx) {
		return (mx - width / 2.0) / scale + width / 2.0;
	}

	private double localY(double my) {
		return (my - height / 2.0) / scale + height / 2.0;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		GuiSettings gs = ClientManager.get().modules().guiSettings;
		float o = open.update(closing ? 0 : 1);
		if (closing && o < 0.02F) {
			minecraft.setScreen(null);
			return;
		}
		float ease = Animation.easeOutCubic(o);
		Theme t = Theme.current();
		float fit = Math.min(1, Math.min((width - 16) / WIDTH, (height - 16) / HEIGHT));
		scale = gs.scale.floatValue() * fit * (0.94F + 0.06F * ease);
		wx = (width - WIDTH) / 2F;
		wy = (height - HEIGHT) / 2F;

		ShapeElement.setGlobalOpacity(ease);
		try {
			RoundedRenderer.rect(g, 0, 0, width, height, 0, ColorUtil.withAlpha(t.background(), 0.45F));
			g.pose().pushMatrix();
			g.pose().translate(width / 2F, height / 2F);
			g.pose().scale(scale, scale);
			g.pose().translate(-width / 2F, -height / 2F);
			double mx = localX(mouseX), my = localY(mouseY);
			renderWindow(g, t, mx, my);
			g.pose().popMatrix();
		} finally {
			ShapeElement.setGlobalOpacity(1);
		}
	}

	private void renderWindow(GuiGraphics g, Theme t, double mx, double my) {
		float r = Math.max(8, t.radius() + 6);
		GlowRenderer.glow(g, wx, wy, WIDTH, HEIGHT, r, 40, t.accent(), 0.22F * t.glow());
		Style.panel(g, t, wx, wy, WIDTH, HEIGHT, r, 1, true);
		// Sidebar sits on a slightly darker layer with a hairline divider.
		RoundedRenderer.rect(g, wx + 1, wy + 1, SIDEBAR, HEIGHT - 2, r - 1, ColorUtil.withAlpha(t.background(), 0.35F));
		RoundedRenderer.rect(g, wx + SIDEBAR, wy + 12, 1, HEIGHT - 24, 0, t.border());

		visualTab.setSelected(category == Category.VISUAL);
		interfaceTab.setSelected(category == Category.INTERFACE);
		resetTab.setSelected(System.currentTimeMillis() - resetArmedAt < 3000);
		sidebar.setBounds(wx, wy, SIDEBAR, HEIGHT);
		sidebar.render(g, t, mx, my);

		float cx = wx + SIDEBAR + 14;
		boolean searching = !search.query().isBlank();
		String title = searching ? "Search" : category.displayName();
		String subtitle = searching ? "Results across all categories" : category.subtitle();
		RenderUtils.text(g, title, cx, wy + 14, t.text(), NFont.DISPLAY);
		RenderUtils.text(g, subtitle, cx, wy + 30, t.textDim(), NFont.TEXT);
		search.setBounds(wx + WIDTH - 194, wy + 14, 180, 20);
		search.render(g, t, mx, my);

		moduleList.filter(category, search.query());
		moduleList.setSelected(settings.module());
		moduleList.setBounds(cx - 6, wy + 48, LIST, HEIGHT - 58);
		moduleList.render(g, t, mx, my);

		float sx = cx + LIST + 6;
		float sw = wx + WIDTH - 10 - sx;
		RoundedRenderer.rect(g, sx, wy + 48, sw, HEIGHT - 58, Math.min(t.radius(), 12), ColorUtil.withAlpha(t.panel(), 0.45F), 1, t.border());
		settings.setBounds(sx, wy + 48, sw, HEIGHT - 58);
		settings.render(g, t, mx, my);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = localX(event.x()), my = localY(event.y());
		int button = event.button();
		boolean handled = search.mouseClicked(mx, my, button);
		handled |= !handled && sidebar.mouseClicked(mx, my, button);
		handled |= !handled && moduleList.mouseClicked(mx, my, button);
		// Settings always receive the click so open editors can cancel themselves.
		boolean settingsHandled = settings.mouseClicked(handled ? -1e4 : mx, handled ? -1e4 : my, button);
		return handled || settingsHandled;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		settings.mouseReleased(localX(event.x()), localY(event.y()), event.button());
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		settings.mouseDragged(localX(event.x()), localY(event.y()));
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		double mx = localX(x), my = localY(y);
		return moduleList.mouseScrolled(mx, my, vertical) || settings.mouseScrolled(mx, my, vertical);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (settings.isCapturingKeys()) {
			return settings.keyPressed(key, event.modifiers());
		}
		if (search.keyPressed(key, event.modifiers())) {
			return true;
		}
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			if (search.isFocused()) {
				search.setFocused(false);
				return true;
			}
			onClose();
			return true;
		}
		if (key == ClientManager.get().modules().clickGui.keybind().key() && !search.isFocused()) {
			onClose();
			return true;
		}
		return settings.keyPressed(key, event.modifiers());
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (settings.isCapturingKeys()) {
			return true;
		}
		return search.charTyped(event.codepoint());
	}

	@Override
	public void onClose() {
		// Play the closing animation; the screen removes itself once fully faded.
		closing = true;
		search.setFocused(false);
	}

	@Override
	public void removed() {
		ClientManager.get().config().save();
	}
}
