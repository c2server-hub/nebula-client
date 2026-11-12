package com.nebula.client.modules.visual;

import com.nebula.client.core.ClientManager;
import com.nebula.client.gui.Style;
import com.nebula.client.gui.hud.HudElement;
import com.nebula.client.gui.themes.Theme;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.GlowRenderer;
import com.nebula.client.rendering.Gradient;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RenderUtils.NFont;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.Locale;

/** Shows the in-game (world) time, not the real-world clock. */
public final class WorldTimeDisplay extends Module {
	public final BoolSetting twelveHour = add(new BoolSetting("12-hour clock", "Use AM/PM instead of 24h", false));
	public final BoolSetting showDay = add(new BoolSetting("Show day", "Display the day counter", true));
	public final BoolSetting showArc = add(new BoolSetting("Sun arc", "Draw the sun/moon position", true));
	private final NumberSetting posX = add(HudElement.position("Time X", 0.995));
	private final NumberSetting posY = add(HudElement.position("Time Y", 0.99));

	public final HudElement element = new Element();

	public WorldTimeDisplay() {
		super("world_time", "World Time", "Displays the Minecraft world clock", Category.VISUAL, true, false);
	}

	/** Minecraft day starts at 06:00 (tick 0) and lasts 24000 ticks. */
	static String format(long dayTime, boolean twelve) {
		long t = Math.floorMod(dayTime, 24000L);
		int hours = (int) ((t / 1000 + 6) % 24);
		int minutes = (int) (t % 1000 * 60 / 1000);
		if (!twelve) {
			return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
		}
		int h12 = hours % 12 == 0 ? 12 : hours % 12;
		return String.format(Locale.ROOT, "%d:%02d %s", h12, minutes, hours < 12 ? "AM" : "PM");
	}

	static String phase(long dayTime) {
		long t = Math.floorMod(dayTime, 24000L);
		if (t < 1000) return "Sunrise";
		if (t < 6000) return "Morning";
		if (t < 9000) return "Noon";
		if (t < 12000) return "Afternoon";
		if (t < 13800) return "Sunset";
		return "Night";
	}

	private final class Element extends HudElement {
		Element() {
			super(WorldTimeDisplay.this, "World Time", posX, posY);
		}

		@Override
		public float width() {
			return showArc.isOn() ? 118 : 86;
		}

		@Override
		public float height() {
			return 34;
		}

		@Override
		public void render(GuiGraphics g, Theme t, float x, float y, boolean preview) {
			Minecraft mc = Minecraft.getInstance();
			long dayTime = mc.level != null ? mc.level.getDayTime() : 6000;
			float w = width(), h = height();
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			Style.panel(g, t, 0, 0, w, h, Math.min(t.radius(), 12), 1, ClientManager.get().modules().guiSettings.hudGlass.isOn());
			float tx = 9;
			if (showArc.isOn()) {
				float cx = 20, cy = 24, r = 12;
				float progress = Math.floorMod(dayTime, 24000L) / 24000F;
				boolean night = progress > 0.54F && progress < 0.98F;
				// Sun travels across the upper half by day, moon by night.
				float local = night ? (progress - 0.54F) / 0.44F : ((progress + 0.02F) % 1F) / 0.56F;
				float angle = Mth.PI * (1 - Math.clamp(local, 0, 1));
				RoundedRenderer.outline(g, cx - r, cy - r, r * 2, r * 2, r, 1, ColorUtil.withAlpha(t.textDim(), 0.35F));
				RoundedRenderer.rect(g, cx - r - 2, cy, r * 2 + 4, 0.75F, 0, ColorUtil.withAlpha(t.textDim(), 0.5F));
				float bx = cx + Mth.cos(angle) * r;
				float by = cy - Mth.sin(angle) * r;
				int body = night ? 0xFFDDE6FF : 0xFFFFC94D;
				GlowRenderer.glow(g, bx - 3, by - 3, 6, 6, 3, 6, body, 0.9F * t.glow());
				RoundedRenderer.circle(g, bx, by, 3, body);
				tx = 40;
			}
			RenderUtils.text(g, format(dayTime, twelveHour.isOn()), tx, 6, t.text(), NFont.DISPLAY);
			long day = Math.floorDiv(dayTime, 24000L) + 1;
			String sub = showDay.isOn() ? "Day " + day + " · " + phase(dayTime) : phase(dayTime);
			RenderUtils.text(g, RenderUtils.ellipsize(sub, w - tx - 6, NFont.TEXT), tx, 21, t.textDim(), NFont.TEXT);
			RoundedRenderer.gradient(g, w - 4, 8, 1.5F, h - 16, 0.75F, Gradient.vertical(t.accent(), t.accent2()));
			g.pose().popMatrix();
		}
	}
}
