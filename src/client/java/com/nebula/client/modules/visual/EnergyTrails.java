package com.nebula.client.modules.visual;

import com.nebula.client.core.ClientManager;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.rendering.world.GlowBatch;
import com.nebula.client.rendering.world.WorldFrame;
import com.nebula.client.rendering.world.WorldRenderable;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

/**
 * Light ribbons on body anchors (back, hands, feet, weapon tip, elytra wing tips), colour by
 * speed or action, sword swing arcs, dash afterimages and violet ender pearl trails.
 */
public final class EnergyTrails extends Module implements WorldRenderable {
	private static final int CYAN = 0xFF35E8FF, GOLD = 0xFFFFD34D, ELYTRA = 0xFFC77DFF, WHITE = 0xFFFFFFFF, PURPLE = 0xFFB24DFF;
	private static final int[] ANCHORS = {
		Skeleton.BACK, Skeleton.L_HAND, Skeleton.R_HAND, Skeleton.L_FOOT, Skeleton.R_FOOT, Skeleton.WEAPON_TIP, Skeleton.L_WING, Skeleton.R_WING
	};
	private static final int MAX_GHOSTS = 12;
	private static final float GHOST_LIFE = 0.4F;
	private static final int MAX_ARCS = 6;
	private static final float ARC_LIFE = 0.34F;

	public final BoolSetting back = add(new BoolSetting("Back", "Ribbon from the back", true));
	public final BoolSetting hands = add(new BoolSetting("Hands", "Ribbons from both hands", true));
	public final BoolSetting feet = add(new BoolSetting("Feet", "Ribbons from both feet", false));
	public final BoolSetting weaponTip = add(new BoolSetting("Weapon tip", "Ribbon from the tip of the held item", true));
	public final NumberSetting duration = add(new NumberSetting("Length", "Trail lifetime", 0.45, 0.1, 2, 0.05, "s"));
	public final NumberSetting width = add(new NumberSetting("Width", "Ribbon width", 0.12, 0.03, 0.5, 0.01, "m"));
	public final ModeSetting colorBy = add(new ModeSetting("Color by", "Aura colour, speed gradient or current action", "Action", "Aura", "Speed", "Action"));
	public final ColorSetting slowColor = add(new ColorSetting("Slow color", "Colour at walking speed", 0xFF6B7CFF));
	public final ColorSetting fastColor = add(new ColorSetting("Fast color", "Colour at high speed", 0xFFFF4DD8));
	public final BoolSetting swingArc = add(new BoolSetting("Swing arc", "Light arc following each swing", true));
	public final BoolSetting afterimages = add(new BoolSetting("Afterimages", "Fading energy silhouettes behind you", true));
	public final ModeSetting afterTrigger = add(new ModeSetting("Afterimage trigger", "When afterimages are emitted", "Dash", "Dash", "Sprint", "Always"));
	public final BoolSetting pearls = add(new BoolSetting("Pearl trails", "Violet trail behind ender pearls", true));
	public final BoolSetting firstPerson = add(new BoolSetting("First person", "Show body trails in first person", false));

	private final Skeleton skeleton = new Skeleton();
	private final TrailHistory[] trails = new TrailHistory[ANCHORS.length];
	private final Map<Integer, TrailHistory> pearlTrails = new HashMap<>();
	private final Set<Integer> seen = new HashSet<>();

	private final double[][] ghost = new double[MAX_GHOSTS][Skeleton.COUNT * 3];
	private final long[] ghostBorn = new long[MAX_GHOSTS];
	private final int[] ghostColor = new int[MAX_GHOSTS];
	private int ghostNext;
	private long lastGhost;

	private final double[] arcX = new double[MAX_ARCS], arcY = new double[MAX_ARCS], arcZ = new double[MAX_ARCS];
	private final float[] arcYaw = new float[MAX_ARCS], arcPitch = new float[MAX_ARCS], arcTilt = new float[MAX_ARCS], arcDir = new float[MAX_ARCS];
	private final long[] arcBorn = new long[MAX_ARCS];
	private final int[] arcColor = new int[MAX_ARCS];
	private int arcNext;
	private boolean arcFlip;
	private boolean wasSwinging;

	private double speed, lastSpeed;
	private long dashUntil;

	public EnergyTrails() {
		super("energy_trails", "Energy Trails", "Anchor ribbons, swing arcs, afterimages, pearl trails", Category.VISUAL);
		for (int i = 0; i < trails.length; i++) {
			trails[i] = new TrailHistory(160);
		}
	}

	private static AuraGlow aura() {
		return ClientManager.get().modules().auraGlow;
	}

	@Override
	public void onTick() {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) {
			return;
		}
		Vec3 d = p.position().subtract(p.xo, p.yo, p.zo);
		speed = d.length() * 20;
		long now = System.nanoTime();
		if ((speed > 6 && speed > lastSpeed * 1.6 + 0.5) || p.isAutoSpinAttack()) {
			dashUntil = now + 350_000_000L;
		}
		lastSpeed = speed;
		if (p.swinging && !wasSwinging && swingArc.isOn()) {
			spawnArc(p, now);
		}
		wasSwinging = p.swinging;
	}

	private int actionColor(LocalPlayer p, float h, float t, long now) {
		if (colorBy.is("Speed")) {
			return ColorUtil.mix(slowColor.argb(), fastColor.argb(), Mth.clamp((float) (speed - 3) / 9F, 0, 1));
		}
		int base = aura().color(h, t);
		if (colorBy.is("Aura")) {
			return base;
		}
		if (now < dashUntil) {
			return ColorUtil.mix(base, WHITE, 0.75F);
		}
		if (p.isFallFlying()) {
			return ELYTRA;
		}
		if (!p.onGround() && !p.isInWater()) {
			return ColorUtil.mix(base, GOLD, 0.7F);
		}
		if (p.isSprinting()) {
			return ColorUtil.mix(base, CYAN, 0.8F);
		}
		return base;
	}

	private boolean anchorEnabled(int idx) {
		boolean fly = skeleton.flying;
		return switch (idx) {
			case Skeleton.BACK -> back.isOn() && !fly;
			case Skeleton.L_HAND, Skeleton.R_HAND -> hands.isOn() && !fly;
			case Skeleton.L_FOOT, Skeleton.R_FOOT -> feet.isOn();
			case Skeleton.WEAPON_TIP -> weaponTip.isOn() && skeleton.holdingItem && !fly;
			case Skeleton.L_WING, Skeleton.R_WING -> fly && (back.isOn() || hands.isOn());
			default -> false;
		};
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			return;
		}
		long now = System.nanoTime();
		float t = f.seconds();
		boolean fp = mc.options.getCameraType().isFirstPerson();
		boolean showBody = (!fp || firstPerson.isOn()) && !p.isInvisible() && !p.isDeadOrDying();
		float dur = duration.floatValue();
		float w = width.floatValue() * 0.5F;
		float alphaMul = Math.min(1, aura().intensity.floatValue());

		batch.begin(f);
		if (showBody) {
			skeleton.update(p, f.partialTick());
			for (int i = 0; i < ANCHORS.length; i++) {
				int idx = ANCHORS[i];
				if (!anchorEnabled(idx)) {
					trails[i].clear();
					continue;
				}
				int c = actionColor(p, skeleton.heightFraction(idx), t, now);
				double x = skeleton.x[idx], y = skeleton.y[idx], z = skeleton.z[idx];
				trails[i].push(x, y, z, c, now);
				trails[i].render(f, batch, now, dur, w * (idx == Skeleton.WEAPON_TIP ? 0.8F : 1), x, y, z, c, alphaMul);
			}
			if (afterimages.isOn()) {
				boolean active = switch (afterTrigger.get()) {
					case "Sprint" -> p.isSprinting() || now < dashUntil;
					case "Always" -> speed > 1.5;
					default -> now < dashUntil;
				};
				if (active && now - lastGhost > 60_000_000L) {
					snapshotGhost(actionColor(p, 0.6F, t, now), now);
				}
			}
		} else {
			for (TrailHistory trail : trails) {
				trail.clear();
			}
		}
		renderGhosts(f, batch, now);
		renderArcs(f, batch, now);
		if (pearls.isOn()) {
			renderPearls(mc, f, batch, now);
		} else {
			pearlTrails.clear();
		}
		batch.end(NebulaPipelines.GLOW_RIBBON, 1, 0, 0);
	}

	private void snapshotGhost(int color, long now) {
		int g = ghostNext;
		ghostNext = (ghostNext + 1) % MAX_GHOSTS;
		for (int j = 0; j < Skeleton.COUNT; j++) {
			ghost[g][j * 3] = skeleton.x[j];
			ghost[g][j * 3 + 1] = skeleton.y[j];
			ghost[g][j * 3 + 2] = skeleton.z[j];
		}
		ghostBorn[g] = now;
		ghostColor[g] = color;
		lastGhost = now;
	}

	private void renderGhosts(WorldFrame f, GlowBatch batch, long now) {
		for (int g = 0; g < MAX_GHOSTS; g++) {
			if (ghostBorn[g] == 0) {
				continue;
			}
			float tt = (now - ghostBorn[g]) / 1e9F / GHOST_LIFE;
			if (tt >= 1) {
				ghostBorn[g] = 0;
				continue;
			}
			int c = ColorUtil.withAlpha(ghostColor[g], (1 - tt) * (1 - tt) * 0.5F);
			double[] s = ghost[g];
			for (int[] bone : Skeleton.BONES) {
				int a = bone[0] * 3, b = bone[1] * 3;
				float bw = bone[0] == Skeleton.NECK ? 0.13F : 0.06F;
				TrailHistory.segment(batch, f.rx(s[a]), f.ry(s[a + 1]), f.rz(s[a + 2]), f.rx(s[b]), f.ry(s[b + 1]), f.rz(s[b + 2]), bw, bw, c, c);
			}
			int h = Skeleton.HEAD * 3;
			float hx = f.rx(s[h]), hy = f.ry(s[h + 1]), hz = f.rz(s[h + 2]);
			TrailHistory.segment(batch, hx, hy - 0.12F, hz, hx, hy + 0.12F, hz, 0.15F, 0.15F, c, c);
		}
	}

	private void spawnArc(LocalPlayer p, long now) {
		int i = arcNext;
		arcNext = (arcNext + 1) % MAX_ARCS;
		Vec3 eye = p.getEyePosition();
		arcX[i] = eye.x;
		arcY[i] = eye.y - 0.25;
		arcZ[i] = eye.z;
		arcYaw[i] = p.getYRot() * Mth.DEG_TO_RAD;
		arcPitch[i] = p.getXRot() * Mth.DEG_TO_RAD;
		arcFlip = !arcFlip;
		arcTilt[i] = (arcFlip ? 1 : -1) * 0.45F;
		arcDir[i] = p.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
		arcColor[i] = actionColor(p, 0.7F, now / 1e9F, now);
		arcBorn[i] = now;
	}

	private void renderArcs(WorldFrame f, GlowBatch batch, long now) {
		final int segments = 14;
		final float radius = 1.15F, sweep = 1.25F;
		for (int i = 0; i < MAX_ARCS; i++) {
			if (arcBorn[i] == 0) {
				continue;
			}
			float tt = (now - arcBorn[i]) / 1e9F / ARC_LIFE;
			if (tt >= 1) {
				arcBorn[i] = 0;
				continue;
			}
			float head = Animation.easeOutCubic(Math.min(1, tt / 0.6F));
			float tail = Mth.clamp(head - 0.55F * (1 - Math.max(0, (tt - 0.5F) / 0.5F)), 0, head);
			if (head - tail < 1e-3F) {
				continue;
			}
			float yaw = arcYaw[i], pitch = arcPitch[i], tilt = arcTilt[i];
			float fx = -Mth.sin(yaw) * Mth.cos(pitch), fy = -Mth.sin(pitch), fz = Mth.cos(yaw) * Mth.cos(pitch);
			float ux = -Mth.sin(yaw) * Mth.sin(pitch), uy = Mth.cos(pitch), uz = Mth.cos(yaw) * Mth.sin(pitch);
			float rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
			float ct = Mth.cos(tilt), st = Mth.sin(tilt);
			float sx = (rx * ct + ux * st) * arcDir[i], sy = (uy * st) * arcDir[i], sz = (rz * ct + uz * st) * arcDir[i];
			float fade = (float) Math.pow(1 - tt, 1.2);
			float px = 0, py = 0, pz = 0, pw = 0;
			int pc = 0;
			for (int k = 0; k <= segments; k++) {
				float s = k / (float) segments;
				float u = tail + (head - tail) * s;
				float theta = sweep - 2 * sweep * u;
				float cx = f.rx(arcX[i] + (fx * Mth.cos(theta) + sx * Mth.sin(theta)) * radius);
				float cy = f.ry(arcY[i] + (fy * Mth.cos(theta) + sy * Mth.sin(theta)) * radius);
				float cz = f.rz(arcZ[i] + (fz * Mth.cos(theta) + sz * Mth.sin(theta)) * radius);
				float cw = 0.02F + 0.14F * s;
				int cc = ColorUtil.withAlpha(arcColor[i], s * fade);
				if (k > 0) {
					TrailHistory.segment(batch, px, py, pz, cx, cy, cz, pw, cw, pc, cc);
				}
				px = cx;
				py = cy;
				pz = cz;
				pw = cw;
				pc = cc;
			}
		}
	}

	private void renderPearls(Minecraft mc, WorldFrame f, GlowBatch batch, long now) {
		seen.clear();
		float pt = f.partialTick();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e.getType() != EntityType.ENDER_PEARL) {
				continue;
			}
			int id = e.getId();
			seen.add(id);
			TrailHistory trail = pearlTrails.computeIfAbsent(id, k -> new TrailHistory(240));
			double x = Mth.lerp(pt, e.xo, e.getX());
			double y = Mth.lerp(pt, e.yo, e.getY()) + e.getBbHeight() * 0.5;
			double z = Mth.lerp(pt, e.zo, e.getZ());
			trail.push(x, y, z, PURPLE, now);
			trail.render(f, batch, now, 1.2F, 0.09F, x, y, z, PURPLE, 1);
		}
		Iterator<Map.Entry<Integer, TrailHistory>> it = pearlTrails.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, TrailHistory> entry = it.next();
			if (seen.contains(entry.getKey())) {
				continue;
			}
			if (entry.getValue().expired(now, 1.2F)) {
				it.remove();
			} else {
				entry.getValue().renderDetached(f, batch, now, 1.2F, 0.09F, 1);
			}
		}
	}
}
