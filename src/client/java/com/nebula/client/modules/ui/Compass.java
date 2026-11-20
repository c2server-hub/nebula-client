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
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

import java.util.Locale;

/**
 * A glass compass strip at the top of the screen: cardinals, intercardinals and minor ticks glide
 * under a glowing centre notch as you turn, with your exact heading (and optionally the biome)
 * written underneath. The heading follows the game camera, so it stays butter-smooth even while
 * turning quickly.
 */
public final class Compass extends Module {
	public final NumberSetting width = add(new NumberSetting("Width", "Strip width", 180, 100, 320, 5, "px"));
	public final NumberSetting zoom = add(new NumberSetting("Zoom", "Pixels per degree", 1.7, 0.8, 4, 0.1));
	public final BoolSetting intercardinal = add(new BoolSetting("Intercardinals", "NE, SE, SW, NW markers", true));
	public final BoolSetting ticks = add(new BoolSetting("Ticks", "Minor marks every 15 degrees", true));
	public final BoolSetting showHeading = add(new BoolSetting("Heading", "Direction and degrees under the strip", true));
	public final BoolSetting showBiome = add(new BoolSetting("Biome", "Append the biome name", false));
	private final NumberSetting posX = add(HudElement.position("Compass X", 0.5));
	private final NumberSetting posY = add(HudElement.position("Compass Y", 0.015));

	public final HudElement element = new Element();
	private String biome = "";
	private int biomeCooldown;

	public Compass() {
		super("compass", "Compass", "Scrolling cardinal strip with heading and biome", Category.INTERFACE, true, false);
	}

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		if (biomeCooldown-- > 0 || mc.level == null || mc.player == null) {
			return;
		}
		biomeCooldown = 40; // biome rarely changes; refresh every two seconds
		BlockPos pos = mc.player.blockPosition();
		biome = mc.level.getBiome(pos).unwrapKey()
				.map(key -> prettify(key.identifier().getPath()))
				.orElse("");
	}

	private static String prettify(String path) {
		String[] words = path.replace('_', ' ').split(" ");
		StringBuilder sb = new StringBuilder();
		for (String word : words) {
			if (!word.isEmpty()) {
				sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
			}
		}
		return sb.toString().trim();
	}

	private final class Element extends HudElement {
		private static final float H = 22;

		Element() {
			super(Compass.this, "Compass", posX, posY);
		}

		@Override
		public float width() {
			return widthSetting();
		}

		private float widthSetting() {
			return Compass.this.width.floatValue();
		}

		@Override
		public float height() {
			return H + (showHeading.isOn() ? 11 : 0);
		}

		/** Centred horizontally on its anchor. */
		@Override
		public float left(int screenWidth) {
			float left = (float) (posX.get() * screenWidth) - width() / 2;
			return Math.clamp(left, 2, Math.max(2, screenWidth - width() - 2));
		}

		@Override
		public void render(GuiGraphics g, Theme t, float x, float y, boolean preview) {
			Minecraft mc = Minecraft.getInstance();
			float w = widthSetting();
			float pxPerDeg = zoom.floatValue();
			// The camera yaw gives the smoothest heading (it eases between ticks).
			// Vanilla yaw: 0 faces south (+Z), 180 north. Compass bearing = yaw + 180.
			int heading = preview ? 45 : Math.floorMod(Math.round(mc.gameRenderer.getMainCamera().yRot()) + 180, 360);
			float half = w / 2;

			Style.panel(g, t, x, y, w, H, Math.min(t.radius() + 2, 11), 1, ClientManager.get().modules().guiSettings.hudGlass.isOn());
			RenderUtils.scissor(g, x + 2, y, w - 4, H);

			// Marks every 15 degrees; cardinals and intercardinals get letters.
			for (int deg = 0; deg < 360; deg += 15) {
				int delta = wrapDeg(deg - heading);
				float off = delta * pxPerDeg;
				if (Math.abs(off) > half - 4) {
					continue;
				}
				float edgeFade = 1 - Math.clamp(Math.abs(off) / (half - 6), 0, 1);
				float mx = x + half + off;
				boolean cardinal = deg % 90 == 0;
				boolean inter = deg % 45 == 0 && !cardinal;
				if (cardinal) {
					String label = nameOf(deg);
					int c = deg == 0 ? 0xFFFF6B5E : t.text();
					float alpha = 0.45F + 0.55F * edgeFade;
					RenderUtils.centered(g, label, mx, y + 4, ColorUtil.withAlpha(c, alpha), NFont.DISPLAY, 0.95F);
					RoundedRenderer.rect(g, mx - 0.75F, y + 16, 1.5F, 4, 0.75F, ColorUtil.withAlpha(c, 0.8F * edgeFade + 0.1F));
				} else if (inter && intercardinal.isOn()) {
					String label = nameOf(deg);
					RenderUtils.centered(g, label, mx, y + 5, ColorUtil.withAlpha(t.textDim(), 0.4F + 0.5F * edgeFade), NFont.TEXT, 0.72F);
					RoundedRenderer.rect(g, mx - 0.6F, y + 16, 1.2F, 3, 0.6F, ColorUtil.withAlpha(t.textDim(), 0.5F * edgeFade + 0.1F));
				} else if (ticks.isOn()) {
					RoundedRenderer.rect(g, mx - 0.5F, y + 16, 1, 2.5F, 0.5F, ColorUtil.withAlpha(t.textDim(), 0.35F * edgeFade + 0.08F));
				}
			}
			g.disableScissor();

			// Centre notch: glowing accent pill marking where you look.
			Style.accentPill(g, t, x + half - 1, y + 2, 2, H - 4, 1, 1);
			GlowRenderer.glow(g, x + half - 3.5F, y + 3, 7, H - 6, 3.5F, 6, t.accent(), 0.7F * t.glow());

			if (showHeading.isOn()) {
				String facing = nameOf(round45(heading));
				String text = facing + " · " + heading + "°";
				if (showBiome.isOn() && !biome.isEmpty()) {
					text += " · " + biome;
				}
				RenderUtils.centered(g, text, x + half, y + H + 2, ColorUtil.withAlpha(t.textDim(), 0.9F), NFont.TEXT, 0.82F);
			}
		}

		private int wrapDeg(int delta) {
			return ((delta + 180) % 360 + 360) % 360 - 180;
		}

		private int round45(int deg) {
			return Math.round(deg / 45F) * 45 % 360;
		}

		private String nameOf(int deg) {
			return switch (Math.floorMod(deg, 360)) {
				case 0 -> "N";
				case 45 -> "NE";
				case 90 -> "E";
				case 135 -> "SE";
				case 180 -> "S";
				case 225 -> "SW";
				case 270 -> "W";
				case 315 -> "NW";
				default -> String.format(Locale.ROOT, "%d", Math.floorMod(deg, 360));
			};
		}
	}
}
