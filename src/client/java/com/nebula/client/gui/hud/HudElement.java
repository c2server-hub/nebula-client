package com.nebula.client.gui.hud;

import com.nebula.client.gui.themes.Theme;
import com.nebula.client.modules.Module;
import com.nebula.client.settings.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A draggable HUD widget. Its position is stored as a screen fraction (so it survives resolution
 * and GUI-scale changes); elements on the right half anchor by their right edge.
 */
public abstract class HudElement {
	private final String name;
	private final Module owner;
	private final NumberSetting x;
	private final NumberSetting y;

	protected HudElement(Module owner, String name, NumberSetting x, NumberSetting y) {
		this.owner = owner;
		this.name = name;
		this.x = x;
		this.y = y;
	}

	public String name() {
		return name;
	}

	public Module owner() {
		return owner;
	}

	public abstract float width();

	public abstract float height();

	/** Draws at the resolved position. {@code preview} is true in the layout editor (render placeholder data). */
	public abstract void render(GuiGraphics g, Theme theme, float left, float top, boolean preview);

	public boolean isVisible() {
		return owner.isEnabled();
	}

	public boolean anchoredRight() {
		return x.get() > 0.5;
	}

	public float left(int screenWidth) {
		float anchor = (float) (x.get() * screenWidth);
		float left = anchoredRight() ? anchor - width() : anchor;
		return Math.clamp(left, 2, Math.max(2, screenWidth - width() - 2));
	}

	public float top(int screenHeight) {
		float anchor = (float) (y.get() * screenHeight);
		float top = y.get() > 0.5 ? anchor - height() : anchor;
		return Math.clamp(top, 2, Math.max(2, screenHeight - height() - 2));
	}

	/** Stores a dragged position, re-deriving the anchor side from where the element now sits. */
	public void moveTo(float left, float top, int screenWidth, int screenHeight) {
		float cx = left + width() / 2;
		float cy = top + height() / 2;
		x.set((double) (cx > screenWidth / 2F ? (left + width()) / screenWidth : left / screenWidth));
		y.set((double) (cy > screenHeight / 2F ? (top + height()) / screenHeight : top / screenHeight));
	}

	public static NumberSetting position(String name, double value) {
		return new NumberSetting(name, "HUD position", value, 0, 1, 0.0001).visibleWhen(() -> false);
	}
}
