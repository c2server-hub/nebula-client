package com.nebula.client.modules.visual;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.GlowRenderer;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.rendering.world.GlowBatch;
import com.nebula.client.rendering.world.WorldFrame;
import com.nebula.client.rendering.world.WorldRenderable;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Living wisps curling around active portals: violet spirits spiral up the face of nether portals,
 * teal motes hover above end portals, and standing close bathes your screen edges in a faint
 * portal-coloured glow. Portal blocks are scanned once per second in a small cube around you.
 */
public final class PortalFx extends Module implements WorldRenderable {
	private static final int MAX_PORTALS = 6;
	private static final float TINT_RANGE = 4F;
	private static final float FAR = 4000;

	public final NumberSetting scanRange = add(new NumberSetting("Scan range", "How far portals are searched", 10, 4, 16, 1, "m"));
	public final NumberSetting wisps = add(new NumberSetting("Wisps", "Spirits per portal", 6, 2, 12, 1));
	public final NumberSetting size = add(new NumberSetting("Size", "Wisp and aura size", 1.0, 0.5, 2, 0.05, "x"));
	public final NumberSetting brightness = add(new NumberSetting("Brightness", "Glow strength", 1.0, 0.2, 2, 0.05));
	public final BoolSetting endPortals = add(new BoolSetting("End portals", "Teal shimmer above end portals", true));
	public final BoolSetting baseAura = add(new BoolSetting("Base aura", "Glowing pad under the portal", true));
	public final BoolSetting ambientTint = add(new BoolSetting("Ambient tint", "Faint edge glow when standing in a portal", true));
	private final ColorChoice color = new ColorChoice(add(ColorChoice.modeSetting()), add(new ColorSetting("Color", "Custom wisp colour", 0xFFB45CFF)));

	// Anchors found by the scan; one per portal panel, not per block.
	private final double[] px = new double[MAX_PORTALS], py = new double[MAX_PORTALS], pz = new double[MAX_PORTALS];
	private final double[] axx = new double[MAX_PORTALS], axz = new double[MAX_PORTALS]; // along the portal plane
	private final double[] ppx = new double[MAX_PORTALS], ppz = new double[MAX_PORTALS]; // perpendicular
	private final boolean[] isEnd = new boolean[MAX_PORTALS];
	private int portals;
	private float nearestDist = Float.MAX_VALUE;
	private int scanCooldown = -1;

	public PortalFx() {
		super("portal_fx", "Portal FX", "Spiralling wisps around nether and end portals", Category.WORLD, true, false);
	}

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		var p = mc.player;
		var level = mc.level;
		if (p == null || level == null) {
			portals = 0;
			nearestDist = Float.MAX_VALUE;
			return;
		}
		if (scanCooldown > 0) {
			scanCooldown--;
			return;
		}
		scanCooldown = 20; // one scan per second is plenty
		portals = 0;
		nearestDist = Float.MAX_VALUE;
		int r = scanRange.intValue();
		BlockPos base = p.blockPosition();
		int v = Math.min(r, 12);
		for (int dy = -v; dy <= v && portals < MAX_PORTALS; dy++) {
			for (int dx = -r; dx <= r && portals < MAX_PORTALS; dx++) {
				for (int dz = -r; dz <= r && portals < MAX_PORTALS; dz++) {
					BlockPos pos = base.offset(dx, dy, dz);
					BlockState state = level.getBlockState(pos);
					boolean nether = state.is(Blocks.NETHER_PORTAL);
					boolean end = endPortals.isOn() && state.is(Blocks.END_PORTAL);
					if (!nether && !end) {
						continue;
					}
					// Keep one anchor per panel: the bottom block, and the first column along the plane.
					BlockState below = level.getBlockState(pos.below());
					if (below.is(Blocks.NETHER_PORTAL) || below.is(Blocks.END_PORTAL)) {
						continue;
					}
					Direction along = nether ? (state.getValue(NetherPortalBlock.AXIS) == Direction.Axis.X ? Direction.EAST : Direction.SOUTH) : Direction.EAST;
					BlockState neighbour = level.getBlockState(pos.relative(along.getOpposite()));
					if (neighbour.is(Blocks.NETHER_PORTAL) || neighbour.is(Blocks.END_PORTAL)) {
						continue;
					}
					float dist = (float) Math.sqrt(p.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
					nearestDist = Math.min(nearestDist, dist);
					int i = portals++;
					px[i] = pos.getX() + 0.5;
					py[i] = pos.getY();
					pz[i] = pos.getZ() + 0.5;
					axx[i] = along.getStepX();
					axz[i] = along.getStepZ();
					ppx[i] = along.getStepZ();
					ppz[i] = along.getStepX();
					isEnd[i] = end;
				}
			}
		}
		// Track the closest portal even when the anchor list is already full.
		if (portals >= MAX_PORTALS) {
			nearestDist = Math.min(nearestDist, (float) Math.sqrt(p.distanceToSqr(px[0], py[0], pz[0])));
		}
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		if (portals == 0) {
			return;
		}
		float seconds = System.nanoTime() / 1e9F;
		float s = size.floatValue();
		float b = brightness.floatValue();
		if (baseAura.isOn()) {
			batch.begin(f);
			for (int i = 0; i < portals; i++) {
				float pulse = 0.75F + 0.25F * Mth.sin(seconds * 1.6F + i * 2.4F);
				int c = isEnd[i] ? 0xFF5CE8B8 : color.color(0.5F, seconds);
				batch.flat(f.rx(px[i]), f.ry(py[i] + 0.06), f.rz(pz[i]), 1.0F * s, ColorUtil.withAlpha(c, 0.22F * b * pulse));
			}
			batch.end(NebulaPipelines.GLOW_RING, 0.85F, 0.4F, 0.3F);
		}
		int n = wisps.intValue();
		batch.begin(f);
		for (int i = 0; i < portals; i++) {
			int c = isEnd[i] ? 0xFF5CE8B8 : color.color(0.5F, seconds);
			for (int j = 0; j < n; j++) {
				float phase = j * 0.618F + i * 0.37F; // golden-ratio spread keeps wisps off each other
				float cycle = 2.6F + (j % 3) * 0.55F;
				float t = ((seconds / cycle + phase) % 1F + 1F) % 1F;
				float fade = Mth.sin(t * Mth.PI);
				if (isEnd[i]) {
					// End portals are flat: motes hover and sway just above the surface.
					float sway = Mth.sin(t * 9F + phase * 12F) * 0.28F;
					float swayCos = Mth.cos(t * 5F + phase * 7F) * 0.3F * s;
					float x = (float) (px[i] + axx[i] * sway * s + ppx[i] * swayCos);
					float z = (float) (pz[i] + axz[i] * sway * s + ppz[i] * swayCos);
					float y = (float) (py[i] + 0.12F + t * 0.55F);
					batch.billboard(f.rx(x), f.ry(y), f.rz(z), 0.055F * s * (1 + 0.3F * fade), ColorUtil.withAlpha(c, 0.8F * fade * b));
				} else {
					// Nether portals: a tightening spiral rising along the panel.
					float ang = phase * Mth.TWO_PI + t * 7.5F;
					float along = Mth.cos(ang) * 0.55F;
					float perp = Mth.sin(ang) * 0.16F;
					float radiusK = 1 - t * 0.7F;
					float x = (float) (px[i] + (axx[i] * along + ppx[i] * perp) * s * radiusK);
					float z = (float) (pz[i] + (axz[i] * along + ppz[i] * perp) * s * radiusK);
					float y = (float) (py[i] + 0.1F + t * 1.9F);
					batch.billboard(f.rx(x), f.ry(y), f.rz(z), 0.06F * s * (1 + 0.35F * fade), ColorUtil.withAlpha(c, 0.85F * fade * b));
				}
			}
		}
		batch.end(NebulaPipelines.GLOW_SPARK, 0, 0, 0);
	}

	/** Faint portal tint on the screen edges when you stand close; registered under the HUD. */
	public void renderHud(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		if (!ambientTint.isOn() || mc.options.hideGui || mc.player == null || nearestDist >= TINT_RANGE) {
			return;
		}
		float k = 1 - nearestDist / TINT_RANGE;
		float breathe = 0.8F + 0.2F * Mth.sin(System.nanoTime() / 1e9F * 2.2F);
		float alpha = 0.22F * k * breathe;
		int c = ColorUtil.withAlpha(color.color(0.5F, System.nanoTime() / 1e9F), alpha);
		int w = g.guiWidth(), h = g.guiHeight();
		GlowRenderer.softRect(g, -FAR, -FAR, FAR, h + 2 * FAR, 110, c, 2);
		GlowRenderer.softRect(g, w, -FAR, FAR, h + 2 * FAR, 110, c, 2);
		GlowRenderer.softRect(g, 0, -FAR, w, FAR, 110, c, 2);
		GlowRenderer.softRect(g, 0, h, w, FAR, 110, c, 2);
	}
}
