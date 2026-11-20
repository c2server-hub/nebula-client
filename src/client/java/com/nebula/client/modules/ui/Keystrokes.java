package com.nebula.client.modules.ui;

import com.nebula.client.core.ClientManager;
import com.nebula.client.gui.Style;
import com.nebula.client.gui.hud.HudElement;
import com.nebula.client.gui.themes.Theme;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.GlowRenderer;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RenderUtils.NFont;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * The classic keystrokes widget, rebuilt in glass: WASD, both mouse buttons with a live CPS
 * counter, the space bar and an optional sneak bar. Pressed keys light up with the theme accent
 * and a tiny springy scale, and the whole panel scales with the HUD setting.
 */
public final class Keystrokes extends Module {
	public final BoolSetting showMouse = add(new BoolSetting("Mouse buttons", "LMB/RMB keys with CPS", true));
	public final BoolSetting showCps = add(new BoolSetting("CPS counter", "Clicks per second inside the buttons", true));
	public final BoolSetting showSpace = add(new BoolSetting("Space bar", "Jump key strip", true));
	public final BoolSetting showSneak = add(new BoolSetting("Sneak bar", "Shift key strip", false));
	public final BoolSetting glow = add(new BoolSetting("Accent glow", "Glow behind pressed keys", true));
	public final NumberSetting scale = add(new NumberSetting("Scale", "Widget size", 1.0, 0.6, 1.6, 0.05, "x"));
	private final NumberSetting posX = add(HudElement.position("Keystrokes X", 0.005));
	private final NumberSetting posY = add(HudElement.position("Keystrokes Y", 0.4));

	public final HudElement element = new Element();

	public Keystrokes() {
		super("keystrokes", "Keystrokes", "Glass WASD, mouse and CPS display", Category.INTERFACE, true, false);
	}

	private final class Element extends HudElement {
		private static final float KEY = 22, GAP = 4, MOUSE_W = 35, SPACE_H = 12;

		private final Animation wKey = new Animation(24);
		private final Animation aKey = new Animation(24);
		private final Animation sKey = new Animation(24);
		private final Animation dKey = new Animation(24);
		private final Animation lmb = new Animation(24);
		private final Animation rmb = new Animation(24);
		private final Animation space = new Animation(24);
		private final Animation sneak = new Animation(24);
		private final Queue<Long> leftClicks = new ArrayDeque<>();
		private final Queue<Long> rightClicks = new ArrayDeque<>();
		private boolean wasLeft, wasRight;

		Element() {
			super(Keystrokes.this, "Keystrokes", posX, posY);
		}

		@Override
		public float width() {
			return (3 * KEY + 2 * GAP) * scale.floatValue();
		}

		@Override
		public float height() {
			float h = 2 * KEY + GAP + SPACE_H;
			if (showMouse.isOn()) {
				h += KEY + GAP;
			}
			if (showSneak.isOn()) {
				h += SPACE_H + GAP;
			}
			return h * scale.floatValue();
		}

		private void pollMouse() {
			Minecraft mc = Minecraft.getInstance();
			long now = System.currentTimeMillis();
			boolean left = mc.mouseHandler != null && mc.mouseHandler.isLeftPressed();
			boolean right = mc.mouseHandler != null && mc.mouseHandler.isRightPressed();
			if (left && !wasLeft) {
				leftClicks.add(now);
			}
			if (right && !wasRight) {
				rightClicks.add(now);
			}
			wasLeft = left;
			wasRight = right;
			while (!leftClicks.isEmpty() && now - leftClicks.peek() > 1000) {
				leftClicks.poll();
			}
			while (!rightClicks.isEmpty() && now - rightClicks.peek() > 1000) {
				rightClicks.poll();
			}
		}

		private int cps(Queue<Long> clicks) {
			return clicks.size();
		}

		@Override
		public void render(GuiGraphics g, Theme t, float x, float y, boolean preview) {
			Minecraft mc = Minecraft.getInstance();
			pollMouse();
			boolean mouse = showMouse.isOn();
			boolean spaceOn = showSpace.isOn();
			boolean sneakOn = showSneak.isOn();

			float wOn = preview ? 0 : (mc.options.keyUp.isDown() ? 1 : 0);
			float aOn = preview ? 0 : (mc.options.keyLeft.isDown() ? 1 : 0);
			float sOn = preview ? 0 : (mc.options.keyDown.isDown() ? 1 : 0);
			float dOn = preview ? 0 : (mc.options.keyRight.isDown() ? 1 : 0);
			float lOn = preview ? 0 : (mc.mouseHandler != null && mc.mouseHandler.isLeftPressed() ? 1 : 0);
			float rOn = preview ? 0 : (mc.mouseHandler != null && mc.mouseHandler.isRightPressed() ? 1 : 0);
			float spOn = preview ? 0 : (mc.options.keyJump.isDown() ? 1 : 0);
			float snOn = preview ? 0 : (mc.options.keyShift.isDown() ? 1 : 0);

			g.pose().pushMatrix();
			g.pose().translate(x, y);
			float s = scale.floatValue();
			g.pose().scale(s, s);
			float totalW = width();

			// Row 1: W centred.
			drawKey(g, t, (totalW - KEY) / 2, 0, KEY, KEY, "W", wKey.update(wOn), NFont.DISPLAY);
			// Row 2: A S D.
			float row2 = KEY + GAP;
			drawKey(g, t, 0, row2, KEY, KEY, "A", aKey.update(aOn), NFont.DISPLAY);
			drawKey(g, t, KEY + GAP, row2, KEY, KEY, "S", sKey.update(sOn), NFont.DISPLAY);
			drawKey(g, t, 2 * (KEY + GAP), row2, KEY, KEY, "D", dKey.update(dOn), NFont.DISPLAY);

			if (mouse) {
				float row3 = row2 + KEY + GAP;
				drawMouseKey(g, t, 0, row3, "LMB", lmb.update(lOn), cps(leftClicks));
				drawMouseKey(g, t, MOUSE_W + GAP, row3, "RMB", rmb.update(rOn), cps(rightClicks));
			}

			if (spaceOn) {
				float rowY = row2 + KEY + GAP + (mouse ? KEY + GAP : 0);
				drawBar(g, t, 0, rowY, totalW, SPACE_H, space.update(spOn));
				if (sneakOn) {
					drawBar(g, t, 0, rowY + SPACE_H + GAP, totalW, SPACE_H, sneak.update(snOn));
				}
			}
			g.pose().popMatrix();
		}

		/** One glass key: accent gradient + glow + spring scale when pressed. */
		private void drawKey(GuiGraphics g, Theme t, float x, float y, float w, float h, String label, float pressed, NFont font) {
			boolean down = pressed > 0.5F;
			float scaleK = 1 - 0.05F * pressed;
			g.pose().pushMatrix();
			g.pose().translate(x + w / 2, y + h / 2);
			g.pose().scale(scaleK, scaleK);
			g.pose().translate(-w / 2, -h / 2);
			if (glow.isOn() && pressed > 0.02F) {
				GlowRenderer.glow(g, 0, 0, w, h, 6, 7, t.accent(), 0.55F * pressed * t.glow());
			}
			int fill = ColorUtil.mix(ColorUtil.withAlpha(t.panel(), 0.62F), t.accent(), 0.82F * pressed);
			RoundedRenderer.rect(g, 0, 0, w, h, 6, fill, 1, ColorUtil.mix(t.border(), ColorUtil.withAlpha(t.accent(), 0.9F), pressed));
			if (pressed > 0.02F) {
				RoundedRenderer.gradient(g, 1, 1, w - 2, Math.max(3, h * 0.3F), 5, Style.accentGradient(t, 0.22F * pressed));
			}
			int fg = ColorUtil.mix(t.textDim(), 0xFFFFFFFF, Math.max(0.35F, pressed));
			RenderUtils.centered(g, label, w / 2, h / 2 - RenderUtils.height() / 2 - 0.5F, fg, font, 0.9F);
			g.pose().popMatrix();
		}

		private void drawMouseKey(GuiGraphics g, Theme t, float x, float y, String label, float pressed, int clicks) {
			drawKey(g, t, x, y, MOUSE_W, KEY, label, pressed, NFont.TEXT);
			if (showCps.isOn()) {
				String cps = clicks + " cps";
				int fg = ColorUtil.mix(t.textDim(), t.accent(), pressed * 0.6F);
				RenderUtils.centered(g, cps, x + MOUSE_W / 2, y + KEY - 8.5F, fg, NFont.TEXT, 0.66F);
			}
		}

		private void drawBar(GuiGraphics g, Theme t, float x, float y, float w, float h, float pressed) {
			if (glow.isOn() && pressed > 0.02F) {
				GlowRenderer.glow(g, x, y, w, h, 5, 7, t.accent(), 0.5F * pressed * t.glow());
			}
			int fill = ColorUtil.mix(ColorUtil.withAlpha(t.panel(), 0.62F), t.accent(), 0.82F * pressed);
			RoundedRenderer.rect(g, x, y, w, h, h / 2, fill, 1, ColorUtil.mix(t.border(), ColorUtil.withAlpha(t.accent(), 0.9F), pressed));
			if (pressed > 0.02F) {
				RoundedRenderer.gradient(g, x + 1.5F, y + 1, w - 3, Math.max(2, h * 0.4F), h / 2, Style.accentGradient(t, 0.25F * pressed));
			}
		}
	}
}
