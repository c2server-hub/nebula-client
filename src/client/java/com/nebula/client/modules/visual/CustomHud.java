package com.nebula.client.modules.visual;

import com.nebula.client.core.ClientManager;
import com.nebula.client.gui.HudEditorScreen;
import com.nebula.client.gui.Style;
import com.nebula.client.gui.hud.HudElement;
import com.nebula.client.gui.themes.Theme;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.GlowRenderer;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RenderUtils.NFont;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.settings.ActionSetting;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CustomHud extends Module {
	public final BoolSetting watermark = add(new BoolSetting("Watermark", "Nebula logo with FPS", true));
	public final BoolSetting info = add(new BoolSetting("Info panel", "Coordinates, speed and facing", true));
	public final BoolSetting moduleList = add(new BoolSetting("Module list", "Animated list of active modules", true));
	public final BoolSetting glow = add(new BoolSetting("Accent glow", "Soft accent halo behind HUD panels", true));
	public final NumberSetting scale = add(new NumberSetting("HUD scale", "Size of HUD elements", 1.0, 0.6, 1.6, 0.05, "x"));
	private final NumberSetting watermarkX = add(HudElement.position("Watermark X", 0.005));
	private final NumberSetting watermarkY = add(HudElement.position("Watermark Y", 0.01));
	private final NumberSetting infoX = add(HudElement.position("Info X", 0.005));
	private final NumberSetting infoY = add(HudElement.position("Info Y", 0.66));
	private final NumberSetting listX = add(HudElement.position("List X", 0.995));
	private final NumberSetting listY = add(HudElement.position("List Y", 0.01));

	public final List<HudElement> elements = new ArrayList<>();

	public CustomHud() {
		super("custom_hud", "Custom HUD", "Watermark, info panel and module list", Category.VISUAL, true, false);
		add(new ActionSetting("Edit layout", "Drag HUD elements to reposition them", () -> Minecraft.getInstance().setScreen(new HudEditorScreen(Minecraft.getInstance().screen))));
		elements.add(new Watermark());
		elements.add(new Info());
		elements.add(new ModuleList());
	}

	private float s() {
		return scale.floatValue();
	}

	private final class Watermark extends HudElement {
		Watermark() {
			super(CustomHud.this, "Watermark", watermarkX, watermarkY);
		}

		private String fps() {
			return Minecraft.getInstance().getFps() + " fps";
		}

		@Override
		public float width() {
			return (16 + 4 + RenderUtils.width("NEBULA", NFont.DISPLAY) + 10 + RenderUtils.width(fps(), NFont.TEXT) + 14 + 12) * s();
		}

		@Override
		public float height() {
			return 22 * s();
		}

		@Override
		public boolean isVisible() {
			return super.isVisible() && watermark.isOn();
		}

		@Override
		public void render(GuiGraphics g, Theme t, float x, float y, boolean preview) {
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.pose().scale(s(), s());
			float w = width() / s(), h = 22;
			if (glow.isOn()) {
				GlowRenderer.glow(g, 0, 0, w, h, h / 2, 10, t.accent(), 0.35F * t.glow());
			}
			Style.panel(g, t, 0, 0, w, h, h / 2, 1, ClientManager.get().modules().guiSettings.hudGlass.isOn());
			// Logo: a tiny glowing orb.
			GlowRenderer.glow(g, 7, 5, 12, 12, 6, 6, t.accent2(), 0.9F * t.glow());
			RoundedRenderer.gradient(g, 7, 5, 12, 12, 6, Style.accentGradient(t, 1));
			RoundedRenderer.circle(g, 11, 9, 2, 0xCCFFFFFF);
			float tx = 24;
			RenderUtils.text(g, "NEBULA", tx, 5.5F, t.text(), NFont.DISPLAY);
			tx += RenderUtils.width("NEBULA", NFont.DISPLAY) + 8;
			RoundedRenderer.rect(g, tx, 6, 1, 10, 0, t.border());
			RenderUtils.text(g, fps(), tx + 7, 7.5F, t.textDim(), NFont.TEXT);
			g.pose().popMatrix();
		}
	}

	private final class Info extends HudElement {
		private final String[] lines = new String[3];

		Info() {
			super(CustomHud.this, "Info", infoX, infoY);
		}

		private void update() {
			LocalPlayer p = Minecraft.getInstance().player;
			if (p == null) {
				lines[0] = "XYZ  0, 64, 0";
				lines[1] = "Speed  0.0 b/s";
				lines[2] = "Facing  North";
				return;
			}
			lines[0] = String.format(Locale.ROOT, "XYZ  %.0f, %.0f, %.0f", p.getX(), p.getY(), p.getZ());
			double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
			double bps = Math.sqrt(dx * dx + dz * dz) * 20;
			lines[1] = String.format(Locale.ROOT, "Speed  %.1f b/s", bps);
			String dir = p.getDirection().getName();
			lines[2] = "Facing  " + Character.toUpperCase(dir.charAt(0)) + dir.substring(1);
		}

		@Override
		public float width() {
			update();
			float w = 0;
			for (String l : lines) {
				w = Math.max(w, RenderUtils.width(l, NFont.TEXT));
			}
			return (w + 20) * s();
		}

		@Override
		public float height() {
			return (lines.length * 11 + 10) * s();
		}

		@Override
		public boolean isVisible() {
			return super.isVisible() && info.isOn();
		}

		@Override
		public void render(GuiGraphics g, Theme t, float x, float y, boolean preview) {
			float w = width() / s();
			float h = height() / s();
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.pose().scale(s(), s());
			Style.panel(g, t, 0, 0, w, h, Math.min(t.radius(), 10), 1, ClientManager.get().modules().guiSettings.hudGlass.isOn());
			RoundedRenderer.gradient(g, 0, 6, 2.5F, h - 12, 1.25F, com.nebula.client.rendering.Gradient.vertical(t.accent(), t.accent2()));
			for (int i = 0; i < lines.length; i++) {
				String line = lines[i];
				int split = line.indexOf("  ");
				String label = line.substring(0, split);
				RenderUtils.text(g, label, 10, 6 + i * 11, t.textDim(), NFont.TEXT);
				RenderUtils.text(g, line.substring(split + 2), 10 + RenderUtils.width(label, NFont.TEXT) + 5, 6 + i * 11, t.text(), NFont.TEXT);
			}
			g.pose().popMatrix();
		}
	}

	private final class ModuleList extends HudElement {
		private final Map<Module, Animation> slide = new HashMap<>();
		private final List<Module> visible = new ArrayList<>();

		ModuleList() {
			super(CustomHud.this, "Module list", listX, listY);
		}

		private void collect(boolean preview) {
			visible.clear();
			for (Module m : ClientManager.get().modules().all()) {
				if (m.isAlwaysActive() || m == CustomHud.this) {
					continue;
				}
				float target = m.isEnabled() || preview ? 1 : 0;
				Animation a = slide.computeIfAbsent(m, k -> new Animation(target, 14));
				if (a.update(target) > 0.01F) {
					visible.add(m);
				}
			}
			visible.sort((a, b) -> Float.compare(RenderUtils.width(b.name(), NFont.TEXT), RenderUtils.width(a.name(), NFont.TEXT)));
		}

		@Override
		public float width() {
			float w = 60;
			for (Module m : visible) {
				w = Math.max(w, RenderUtils.width(m.name(), NFont.TEXT) + 16);
			}
			return w * s();
		}

		@Override
		public float height() {
			float h = 0;
			for (Module m : visible) {
				h += 14 * slide.get(m).get();
			}
			return Math.max(14, h) * s();
		}

		@Override
		public boolean isVisible() {
			return super.isVisible() && moduleList.isOn();
		}

		@Override
		public void render(GuiGraphics g, Theme t, float x, float y, boolean preview) {
			collect(preview);
			boolean right = anchoredRight();
			float totalW = width() / s();
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.pose().scale(s(), s());
			float cy = 0;
			int n = visible.size();
			for (int i = 0; i < n; i++) {
				Module m = visible.get(i);
				float a = slide.get(m).get();
				float eased = Animation.easeOutCubic(a);
				float w = RenderUtils.width(m.name(), NFont.TEXT) + 16;
				float offset = (1 - eased) * (w + 10) * (right ? 1 : -1);
				float bx = (right ? totalW - w : 0) + offset;
				int accent = t.accentMix(n <= 1 ? 0 : (float) i / (n - 1));
				RoundedRenderer.rect(g, bx, cy, w, 13, 4, ColorUtil.withAlpha(t.panel(), 0.78F * eased));
				RoundedRenderer.rect(g, right ? bx + w - 2 : bx, cy + 2.5F, 2, 8, 1, ColorUtil.fade(accent, eased));
				if (glow.isOn()) {
					GlowRenderer.glow(g, right ? bx + w - 2 : bx, cy + 2.5F, 2, 8, 1, 5, accent, 0.5F * t.glow() * eased);
				}
				RenderUtils.text(g, m.name(), bx + (right ? 6 : 8), cy + 3, ColorUtil.fade(t.text(), eased), NFont.TEXT);
				cy += 14 * a;
			}
			g.pose().popMatrix();
		}
	}
}
