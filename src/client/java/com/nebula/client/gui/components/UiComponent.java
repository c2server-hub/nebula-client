package com.nebula.client.gui.components;

import com.nebula.client.gui.themes.Theme;
import com.nebula.client.utilities.Animation;
import net.minecraft.client.gui.GuiGraphics;

/** Base of every ClickGUI widget: bounds, hover animation and input hooks (coordinates are GUI-local). */
public abstract class UiComponent {
	public float x, y, w, h;
	protected final Animation hover = new Animation(18);

	public void setBounds(float x, float y, float w, float h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
	}

	public boolean isHovered(double mx, double my) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	protected float updateHover(double mx, double my) {
		return hover.update(isHovered(mx, my) ? 1 : 0);
	}

	public abstract void render(GuiGraphics g, Theme t, double mx, double my);

	public boolean mouseClicked(double mx, double my, int button) {
		return false;
	}

	public void mouseReleased(double mx, double my, int button) {
	}

	public void mouseDragged(double mx, double my) {
	}

	public boolean keyPressed(int key, int modifiers) {
		return false;
	}

	public boolean charTyped(int codepoint) {
		return false;
	}

	/** Height this component wants in a vertical layout (may animate). */
	public float preferredHeight() {
		return h;
	}
}
