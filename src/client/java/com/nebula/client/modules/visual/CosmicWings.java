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
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * Glowing energy wings attached to the back of players. The blades sweep with a flap animation
 * that reacts to movement: a slow idle sway on the ground, deep fast beats while jumping or
 * falling. Blade shape and energy flow live in wings.fsh.
 */
public final class CosmicWings extends Module implements WorldRenderable {
	private static final int BLADES = 3;

	public final ModeSetting targets = add(new ModeSetting("Show on", "Who gets wings", "Self", "Self", "Everyone", "Others"));
	public final NumberSetting range = add(new NumberSetting("Range", "How far other players are drawn", 32, 8, 64, 1, "m"))
			.visibleWhen(() -> !targets.is("Self"));
	public final NumberSetting span = add(new NumberSetting("Span", "Wing length", 1.3, 0.6, 2.2, 0.05, "m"));
	public final NumberSetting width = add(new NumberSetting("Width", "Blade width", 0.45, 0.25, 0.8, 0.01, "m"));
	public final NumberSetting height = add(new NumberSetting("Height", "Attachment height", 1.3, 1.0, 1.7, 0.05, "m"));
	public final NumberSetting flow = add(new NumberSetting("Flow", "Speed of the energy waves", 1.0, 0.2, 3, 0.05));
	public final NumberSetting edge = add(new NumberSetting("Edge glow", "Brightness of the blade edges", 0.9, 0, 2, 0.05));
	public final NumberSetting opacity = add(new NumberSetting("Opacity", "Overall brightness", 0.85, 0.1, 1, 0.05));
	public final BoolSetting embers = add(new BoolSetting("Embers", "Sparks trailing from the wing tips", true));
	public final BoolSetting firstPerson = add(new BoolSetting("First person", "Render your own wings in first person", false));
	private final ColorChoice color = new ColorChoice(add(ColorChoice.modeSetting()), add(new ColorSetting("Color", "Custom wing colour", 0xFF6FB4FF)));

	private final RandomSource random = RandomSource.create();
	private final SparkPool sparks = new SparkPool(96);
	private float phase;
	private long lastNanos;

	public CosmicWings() {
		super("cosmic_wings", "Cosmic Wings", "Animated energy wings on the back of players", Category.PLAYER);
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		float dt = Math.min(0.1F, (System.nanoTime() - lastNanos) / 1e9F);
		if (lastNanos == 0) {
			dt = 0;
		}
		lastNanos = System.nanoTime();
		boolean firstPersonView = mc.options.getCameraType().isFirstPerson();
		boolean everyone = targets.is("Everyone");
		boolean others = targets.is("Others") || everyone;
		double rangeSq = range.floatValue() * range.floatValue();

		boolean any = false;
		batch.begin(f);
		// Global flap clock: advances with a base beat plus the local player's air time.
		LocalPlayer self = mc.player;
		float selfVy = (float) (self.getY() - self.yo);
		float selfAir = Mth.clamp(0.25F + Math.abs(selfVy) * 1.7F, 0.25F, 1F);
		phase += dt * (2.0F + selfAir * 5.5F);
		for (Player p : mc.level.players()) {
			if (p.isSpectator() || p.isInvisible() || p.isDeadOrDying() || p.isSleeping() || p.isSwimming()) {
				continue;
			}
			boolean isSelf = p == mc.player;
			if (isSelf && firstPersonView && !firstPerson.isOn()) {
				continue;
			}
			if (!isSelf && !others) {
				continue;
			}
			if (!isSelf && p.distanceToSqr(mc.player) > rangeSq) {
				continue;
			}
			drawWings(f, batch, p, dt);
			any = true;
		}
		if (!any) {
			return;
		}
		batch.end(NebulaPipelines.WINGS, f.seconds(), flow.floatValue(), edge.floatValue());
		if (embers.isOn()) {
			sparks.render(f, batch, -1.2F);
		}
	}

	private void drawWings(WorldFrame f, GlowBatch batch, Player p, float dt) {
		// Interpolated body yaw so the wings follow the torso, not the head.
		float yaw = Mth.rotLerp(f.partialTick(), p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		float fx = -Mth.sin(yaw), fz = Mth.cos(yaw); // forward
		float rx = -Mth.cos(yaw), rz = -Mth.sin(yaw); // entity right
		double px = Mth.lerp(f.partialTick(), p.xo, p.getX());
		double py = Mth.lerp(f.partialTick(), p.yo, p.getY());
		double pz = Mth.lerp(f.partialTick(), p.zo, p.getZ());

		// Flap: idle sway on the ground, strong beats proportional to vertical speed.
		float vy = (float) (p.getY() - p.yo);
		float air = Mth.clamp(0.25F + Math.abs(vy) * 1.7F, 0.25F, 1F);
		float localPhase = phase + p.getId() * 0.71F;
		float flap = Mth.sin(localPhase) * 0.38F * air;
		float tilt = 0.55F + flap;

		float h = (float) p.getBbHeight() * (height.floatValue() / 1.8F);
		double rootX = px - fx * 0.22; // behind the chest
		double rootY = py + h;
		double rootZ = pz - fz * 0.22;
		// Feathers trail backwards and slightly downwards.
		float cx = -fx * 0.94F, cy = -0.34F, cz = -fz * 0.94F;

		float span = this.span.floatValue();
		float width = this.width.floatValue();
		float alpha = opacity.floatValue();
		for (int side = -1; side <= 1; side += 2) {
			for (int j = 0; j < BLADES; j++) {
				float a = tilt - j * 0.34F; // each blade sweeps a bit lower
				float dirX = rx * side * Mth.cos(a);
				float dirY = Mth.sin(a);
				float dirZ = rz * side * Mth.cos(a);
				float rootOff = 0.05F + j * 0.11F;
				float len = span * (0.82F - j * 0.13F);
				float chord = width * (0.5F - j * 0.08F);

				float bx = (float) rootX + dirX * rootOff;
				float by = (float) rootY + dirY * rootOff;
				float bz = (float) rootZ + dirZ * rootOff;
				float tipX = bx + dirX * len, tipY = by + dirY * len, tipZ = bz + dirZ * len;

				int c = ColorUtil.withAlpha(color.color((j + 1) / (float) BLADES, f.seconds()), alpha * (1 - j * 0.1F));
				batch.quad(
						f.rx(bx - cx * chord * 0.5F), f.ry(by - cy * chord * 0.5F), f.rz(bz - cz * chord * 0.5F),
						f.rx(bx + cx * chord * 0.5F), f.ry(by + cy * chord * 0.5F), f.rz(bz + cz * chord * 0.5F),
						f.rx(tipX + cx * chord * 0.28F), f.ry(tipY + cy * chord * 0.28F), f.rz(tipZ + cz * chord * 0.28F),
						f.rx(tipX - cx * chord * 0.28F), f.ry(tipY - cy * chord * 0.28F), f.rz(tipZ - cz * chord * 0.28F),
						c);

				// Sparks shed from the primary blade tip while beating hard.
				if (embers.isOn() && j == 0 && air > 0.6F && random.nextFloat() < 0.12F) {
					float jitter = 0.35F;
					sparks.emit(tipX, tipY, tipZ,
							-dirX * 0.8F + (random.nextFloat() - 0.5F) * jitter,
							0.3F + (random.nextFloat() - 0.5F) * jitter,
							-dirZ * 0.8F + (random.nextFloat() - 0.5F) * jitter,
							ColorUtil.withAlpha(color.color(random.nextFloat(), f.seconds()), 0.8F), 0.55F, 0.07F);
				}
			}
		}
	}
}
