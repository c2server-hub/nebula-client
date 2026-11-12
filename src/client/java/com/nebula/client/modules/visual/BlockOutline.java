package com.nebula.client.modules.visual;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.rendering.world.GlowBatch;
import com.nebula.client.rendering.world.WorldFrame;
import com.nebula.client.rendering.world.WorldRenderable;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Glowing selection box replacing the vanilla outline. The box glides from one block to the next
 * (frame-rate independent smoothing of all six bounds) and fades in and out.
 */
public final class BlockOutline extends Module implements WorldRenderable {
	public final NumberSetting lineWidth = add(new NumberSetting("Line width", "Edge thickness in pixels", 2, 0.5, 6, 0.25, "px"));
	public final NumberSetting fill = add(new NumberSetting("Fill", "Face fill opacity", 0.12, 0, 0.6, 0.01));
	public final NumberSetting smoothness = add(new NumberSetting("Smoothness", "How fast the box glides to the new block", 18, 4, 40, 1));
	public final BoolSetting blockColor = add(new BoolSetting("Block color", "Tint with the colour of the targeted block", false));
	private final ColorChoice color = new ColorChoice(add(ColorChoice.modeSetting()), add(new ColorSetting("Color", "Custom outline colour", 0xFF5AA0FF)));

	private final double[] current = new double[6];
	private final double[] target = new double[6];
	private float alpha;
	private int targetColor = 0xFFFFFFFF;
	private int shownColor = 0xFFFFFFFF;
	private long last;

	public BlockOutline() {
		super("block_outline", "Block Outline", "Animated glowing selection box that glides between blocks", Category.VISUAL, true, false);
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		Minecraft mc = Minecraft.getInstance();
		long now = System.nanoTime();
		float dt = last == 0 ? 0 : Math.min((now - last) / 1e9F, 0.1F);
		last = now;
		boolean visible = false;
		if (mc.level != null && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			BlockState state = mc.level.getBlockState(hit.getBlockPos());
			VoxelShape shape = state.getShape(mc.level, hit.getBlockPos());
			if (!shape.isEmpty()) {
				AABB box = shape.bounds().move(hit.getBlockPos()).inflate(0.004);
				target[0] = box.minX;
				target[1] = box.minY;
				target[2] = box.minZ;
				target[3] = box.maxX;
				target[4] = box.maxY;
				target[5] = box.maxZ;
				int mapColor = state.getMapColor(mc.level, hit.getBlockPos()).col;
				targetColor = blockColor.isOn() && mapColor != 0 ? ColorUtil.brighten(0xFF000000 | mapColor, 0.25F) : color.color(0, f.seconds());
				visible = true;
			}
		}
		if (visible && alpha < 0.02F) {
			System.arraycopy(target, 0, current, 0, 6); // appear in place instead of flying in from the last block
			shownColor = targetColor;
		}
		float k = 1 - (float) Math.exp(-smoothness.floatValue() * dt);
		for (int i = 0; i < 6; i++) {
			current[i] += (target[i] - current[i]) * k;
		}
		shownColor = ColorUtil.mix(shownColor, targetColor, k);
		alpha += ((visible ? 1 : 0) - alpha) * (1 - (float) Math.exp(-14 * dt));
		if (alpha < 0.01F || mc.options.hideGui) {
			return;
		}
		batch.begin(f);
		batch.box(f.rx(current[0]), f.ry(current[1]), f.rz(current[2]), f.rx(current[3]), f.ry(current[4]), f.rz(current[5]),
			ColorUtil.withAlpha(shownColor, alpha));
		batch.end(NebulaPipelines.BLOCK_GLOW, lineWidth.floatValue(), fill.floatValue(), f.seconds());
	}
}
