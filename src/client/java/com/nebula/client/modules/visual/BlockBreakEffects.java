package com.nebula.client.modules.visual;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.rendering.world.GlowBatch;
import com.nebula.client.rendering.world.SparkPool;
import com.nebula.client.rendering.world.WorldFrame;
import com.nebula.client.rendering.world.WorldRenderable;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Spark burst in the block's own colour plus an expanding ghost box when you break a block. */
public final class BlockBreakEffects extends Module implements WorldRenderable {
	private static final int MAX_GHOSTS = 8;

	public final NumberSetting count = add(new NumberSetting("Count", "Sparks per block", 20, 4, 48, 1));
	public final NumberSetting speed = add(new NumberSetting("Speed", "Burst speed", 3.5, 0.5, 8, 0.1, "m/s"));
	public final NumberSetting size = add(new NumberSetting("Size", "Spark size", 0.13, 0.03, 0.3, 0.005));
	public final BoolSetting ghostBox = add(new BoolSetting("Ghost box", "Expanding glowing box where the block was", true));
	public final BoolSetting blockColor = add(new BoolSetting("Block color", "Use the broken block's colour", true));
	private final ColorChoice color = new ColorChoice(add(ColorChoice.modeSetting()), add(new ColorSetting("Color", "Custom colour", 0xFF7AD7FF)));

	private final RandomSource random = RandomSource.create();
	private final SparkPool sparks = new SparkPool(320);
	private final long[] ghostBorn = new long[MAX_GHOSTS];
	private final int[] ghostX = new int[MAX_GHOSTS], ghostY = new int[MAX_GHOSTS], ghostZ = new int[MAX_GHOSTS], ghostColor = new int[MAX_GHOSTS];
	private int nextGhost;

	public BlockBreakEffects() {
		super("block_break", "Block Break FX", "Sparks and a ghost box when you break blocks", Category.VISUAL);
	}

	public void onBreak(ClientLevel level, BlockPos pos, BlockState state) {
		if (!isEnabled()) {
			return;
		}
		int mapColor = state.getMapColor(level, pos).col;
		float seconds = System.nanoTime() / 1e9F;
		int base = blockColor.isOn() && mapColor != 0 ? ColorUtil.brighten(0xFF000000 | mapColor, 0.2F) : color.color(0, seconds);
		HitEffects.burst(sparks, random, pos.getX() + 0.5, pos.getY() + 0.85, pos.getZ() + 0.5, count.intValue(), speed.floatValue(), 0.9F,
			size.floatValue(), i -> ColorUtil.mix(base, 0xFFFFFFFF, random.nextFloat() * 0.35F));
		ghostX[nextGhost] = pos.getX();
		ghostY[nextGhost] = pos.getY();
		ghostZ[nextGhost] = pos.getZ();
		ghostColor[nextGhost] = base;
		ghostBorn[nextGhost] = System.nanoTime();
		nextGhost = (nextGhost + 1) % MAX_GHOSTS;
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		sparks.render(f, batch, -7);
		if (!ghostBox.isOn()) {
			return;
		}
		long now = System.nanoTime();
		batch.begin(f);
		for (int g = 0; g < MAX_GHOSTS; g++) {
			if (ghostBorn[g] == 0) {
				continue;
			}
			float t = (now - ghostBorn[g]) / 1e9F / 0.6F;
			if (t >= 1) {
				continue;
			}
			float grow = 0.5F + 0.35F * Animation.easeOutCubic(t);
			float cx = f.rx(ghostX[g] + 0.5), cy = f.ry(ghostY[g] + 0.5), cz = f.rz(ghostZ[g] + 0.5);
			batch.box(cx - grow, cy - grow, cz - grow, cx + grow, cy + grow, cz + grow, ColorUtil.withAlpha(ghostColor[g], (1 - t) * (1 - t)));
		}
		batch.end(NebulaPipelines.BLOCK_GLOW, 2, 0.15F, f.seconds());
	}
}
