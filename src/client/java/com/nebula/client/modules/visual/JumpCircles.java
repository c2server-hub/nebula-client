package com.nebula.client.modules.visual;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.rendering.world.GlowBatch;
import com.nebula.client.rendering.world.WorldFrame;
import com.nebula.client.rendering.world.WorldRenderable;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Expanding glowing shock rings on the ground when you jump or land. */
public final class JumpCircles extends Module implements WorldRenderable {
	private static final int MAX = 24;
	private static final float RING_POS = 0.82F;

	public final ModeSetting trigger = add(new ModeSetting("Trigger", "When rings appear", "Both", "Jump", "Land", "Both"));
	public final NumberSetting radius = add(new NumberSetting("Radius", "Final ring radius", 1.3, 0.4, 3, 0.05, "m"));
	public final NumberSetting duration = add(new NumberSetting("Duration", "Lifetime of a ring", 0.9, 0.3, 2.5, 0.05, "s"));
	public final NumberSetting thickness = add(new NumberSetting("Thickness", "Ring thickness", 0.06, 0.015, 0.15, 0.005));
	public final NumberSetting fill = add(new NumberSetting("Fill", "Inner disc glow", 0.35, 0, 1, 0.05));
	public final NumberSetting echoes = add(new NumberSetting("Echoes", "Delayed rings per impact", 1, 0, 3, 1));
	private final ColorChoice color = new ColorChoice(add(ColorChoice.modeSetting()), add(new ColorSetting("Color", "Custom ring colour", 0xFF5AA0FF)));

	private final double[] x = new double[MAX], y = new double[MAX], z = new double[MAX];
	private final long[] born = new long[MAX];
	private final float[] hueOffset = new float[MAX];
	private int next;
	private boolean wasOnGround = true;

	public JumpCircles() {
		super("jump_circles", "Jump Circles", "Glowing shock rings when you jump and land", Category.VISUAL);
	}

	@Override
	public void onTick() {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) {
			return;
		}
		boolean onGround = p.onGround();
		boolean jumped = wasOnGround && !onGround && p.getDeltaMovement().y > 0.1;
		boolean landed = !wasOnGround && onGround;
		if ((jumped && !trigger.is("Land")) || (landed && !trigger.is("Jump"))) {
			spawn(p.getX(), landed ? p.getY() : p.yo, p.getZ());
		}
		wasOnGround = onGround;
	}

	private void spawn(double px, double py, double pz) {
		x[next] = px;
		y[next] = py + 0.03;
		z[next] = pz;
		born[next] = System.nanoTime();
		hueOffset[next] = (next * 0.13F) % 1F;
		next = (next + 1) % MAX;
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		long now = System.nanoTime();
		float life = duration.floatValue();
		int echoCount = echoes.intValue();
		batch.begin(f);
		for (int i = 0; i < MAX; i++) {
			if (born[i] == 0) {
				continue;
			}
			float age = (now - born[i]) / 1e9F;
			for (int e = 0; e <= echoCount; e++) {
				float t = (age - e * 0.12F) / life;
				if (t < 0 || t > 1) {
					continue;
				}
				float r = 0.15F + (radius.floatValue() - 0.15F) * Animation.easeOutCubic(t);
				float fade = (float) Math.pow(1 - t, 1.4) * (e == 0 ? 1 : 0.55F);
				int c = ColorUtil.withAlpha(color.color(hueOffset[i] + e * 0.25F, f.seconds()), fade);
				batch.flat(f.rx(x[i]), f.ry(y[i]), f.rz(z[i]), r / RING_POS, c);
			}
		}
		batch.end(NebulaPipelines.GLOW_RING, RING_POS, thickness.floatValue(), fill.floatValue());
	}
}
