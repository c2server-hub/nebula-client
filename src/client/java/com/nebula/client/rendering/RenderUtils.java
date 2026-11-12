package com.nebula.client.rendering;

import com.nebula.client.NebulaClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Text with the bundled Inter fonts at sub-pixel positions, plus scissor helpers. */
public final class RenderUtils {
	public enum NFont {
		TEXT("text"),
		DISPLAY("display"),
		VANILLA(null);

		private final Style style;
		private final Map<String, Component> cache = new HashMap<>();

		NFont(String id) {
			this.style = id == null ? Style.EMPTY : Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath(NebulaClient.MOD_ID, id)));
		}

		/** Components are cached because GUI labels are mostly static and this runs every frame. */
		Component component(String text) {
			if (cache.size() > 2048) {
				cache.clear();
			}
			return cache.computeIfAbsent(text, t -> Component.literal(t).withStyle(style));
		}
	}

	private RenderUtils() {
	}

	public static Font font() {
		return Minecraft.getInstance().font;
	}

	public static void text(GuiGraphics g, String text, float x, float y, int color, NFont font) {
		text(g, text, x, y, color, font, 1);
	}

	public static void text(GuiGraphics g, String text, float x, float y, int color, NFont font, float scale) {
		float opacity = ShapeElement.globalOpacity();
		if (opacity < 1) {
			color = (Math.round((color >>> 24) * opacity) << 24) | (color & 0xFFFFFF);
		}
		if (text.isEmpty() || (color >>> 24) < 4) {
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		if (scale != 1) {
			g.pose().scale(scale, scale);
		}
		g.drawString(font(), font.component(text), 0, 0, color, false);
		g.pose().popMatrix();
	}

	public static void centered(GuiGraphics g, String text, float cx, float y, int color, NFont font, float scale) {
		text(g, text, cx - width(text, font) * scale / 2, y, color, font, scale);
	}

	public static float width(String text, NFont font) {
		return font().width(font.component(text));
	}

	public static float height() {
		return font().lineHeight;
	}

	/** Trims with an ellipsis so labels never overflow their component. */
	public static String ellipsize(String text, float maxWidth, NFont font) {
		if (width(text, font) <= maxWidth) {
			return text;
		}
		String s = text;
		while (s.length() > 1 && width(s + "…", font) > maxWidth) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "…";
	}

	public static void scissor(GuiGraphics g, float x, float y, float w, float h) {
		g.enableScissor((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + w), (int) Math.ceil(y + h));
	}
}
