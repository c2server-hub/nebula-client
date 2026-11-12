package com.nebula.client.gui;

import com.nebula.client.core.ClientManager;
import com.nebula.client.gui.hud.HudElement;
import com.nebula.client.gui.themes.Theme;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RenderUtils.NFont;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Drag HUD elements with the mouse; positions are saved as screen fractions. */
public final class HudEditorScreen extends Screen {
	private final @Nullable Screen parent;
	private @Nullable HudElement dragging;
	private float grabX, grabY;

	public HudEditorScreen(@Nullable Screen parent) {
		super(Component.literal("Nebula HUD Layout"));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		Theme t = Theme.current();
		RoundedRenderer.rect(g, 0, 0, width, height, 0, ColorUtil.withAlpha(t.background(), 0.35F));
		// Alignment guides.
		RoundedRenderer.rect(g, width / 2F - 0.5F, 0, 1, height, 0, ColorUtil.withAlpha(t.accent(), 0.15F));
		RoundedRenderer.rect(g, 0, height / 2F - 0.5F, width, 1, 0, ColorUtil.withAlpha(t.accent(), 0.15F));
		for (HudElement e : ClientManager.get().hud().elements()) {
			if (!e.isVisible() && !e.owner().isEnabled()) {
				continue;
			}
			float x = e.left(width), y = e.top(height);
			boolean hovered = mouseX >= x && mouseX < x + e.width() && mouseY >= y && mouseY < y + e.height();
			e.render(g, t, x, y, true);
			int outline = dragging == e ? t.accent() : hovered ? ColorUtil.withAlpha(t.accent(), 0.7F) : ColorUtil.withAlpha(0xFFFFFFFF, 0.25F);
			RoundedRenderer.outline(g, x - 3, y - 3, e.width() + 6, e.height() + 6, 6, 1, outline);
			if (hovered || dragging == e) {
				RenderUtils.text(g, e.name(), x, y - 13, t.text(), NFont.TEXT);
			}
		}
		String hint = "Drag elements to move them  ·  Esc to return";
		float hw = RenderUtils.width(hint, NFont.TEXT) + 20;
		RoundedRenderer.rect(g, width / 2F - hw / 2, height - 30, hw, 18, 9, ColorUtil.withAlpha(t.panel(), 0.85F), 1, t.border());
		RenderUtils.centered(g, hint, width / 2F, height - 25, t.textDim(), NFont.TEXT, 1);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != 0) {
			return false;
		}
		var elements = ClientManager.get().hud().elements();
		for (int i = elements.size() - 1; i >= 0; i--) {
			HudElement e = elements.get(i);
			if (!e.owner().isEnabled()) {
				continue;
			}
			float x = e.left(width), y = e.top(height);
			if (event.x() >= x && event.x() < x + e.width() && event.y() >= y && event.y() < y + e.height()) {
				dragging = e;
				grabX = (float) event.x() - x;
				grabY = (float) event.y() - y;
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging != null) {
			float x = (float) event.x() - grabX;
			float y = (float) event.y() - grabY;
			// Snap to the screen centre lines.
			if (Math.abs(x + dragging.width() / 2 - width / 2F) < 4) {
				x = width / 2F - dragging.width() / 2;
			}
			if (Math.abs(y + dragging.height() / 2 - height / 2F) < 4) {
				y = height / 2F - dragging.height() / 2;
			}
			dragging.moveTo(x, y, width, height);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = null;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		ClientManager.get().config().save();
		minecraft.setScreen(parent instanceof ClickGui ? new ClickGui() : parent);
	}
}
