package com.nebula.client.modules.visual;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Approximate world-space joints of the local player model, rebuilt from the vanilla humanoid
 * animation formulas (walk cycle, attack swing, body yaw). Used as anchor points for trails and
 * afterimage silhouettes without hooking the entity renderer.
 */
final class Skeleton {
	static final int HEAD = 0, NECK = 1, PELVIS = 2, L_SHOULDER = 3, R_SHOULDER = 4, L_HAND = 5, R_HAND = 6, L_HIP = 7, R_HIP = 8,
		L_FOOT = 9, R_FOOT = 10, BACK = 11, WEAPON_TIP = 12, L_WING = 13, R_WING = 14;
	static final int COUNT = 15;
	/** Bones drawn for afterimage silhouettes. */
	static final int[][] BONES = {
		{NECK, PELVIS}, {L_SHOULDER, R_SHOULDER}, {L_SHOULDER, L_HAND}, {R_SHOULDER, R_HAND},
		{L_HIP, R_HIP}, {L_HIP, L_FOOT}, {R_HIP, R_FOOT}
	};

	final double[] x = new double[COUNT], y = new double[COUNT], z = new double[COUNT];
	double baseX, baseY, baseZ;
	float height;
	boolean flying;
	boolean holdingItem;

	private double bx, by, bz;
	private float fx, fy, fz, ux, uy, uz, rx, rz, s;

	void update(LocalPlayer p, float pt) {
		bx = Mth.lerp(pt, p.xo, p.getX());
		by = Mth.lerp(pt, p.yo, p.getY());
		bz = Mth.lerp(pt, p.zo, p.getZ());
		baseX = bx;
		baseY = by;
		baseZ = bz;
		height = p.getBbHeight();
		s = p.getScale();
		flying = p.isFallFlying() || p.isVisuallySwimming();
		holdingItem = !p.getMainHandItem().isEmpty();
		float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		rx = -Mth.cos(yaw);
		rz = -Mth.sin(yaw);
		if (flying) {
			updateFlying(p, pt, yaw);
			return;
		}
		fx = -Mth.sin(yaw);
		fy = 0;
		fz = Mth.cos(yaw);
		ux = 0;
		uy = 1;
		uz = 0;

		float pos = p.walkAnimation.position(pt);
		float spd = Math.min(1F, p.walkAnimation.speed(pt));
		float arm = Mth.cos(pos * 0.6662F) * spd * 0.9F;
		float leg = Mth.cos(pos * 0.6662F) * spd * 0.7F;
		float swing = Mth.sin(p.getAttackAnim(pt) * Mth.PI) * 1.9F;
		boolean rightMain = p.getMainArm() == HumanoidArm.RIGHT;
		float rArm = -arm + (rightMain ? swing : 0);
		float lArm = arm + (rightMain ? 0 : swing);
		float crouch = p.isCrouching() ? -0.25F : 0;

		local(HEAD, 0, 1.64F + crouch, 0.02F);
		local(NECK, 0, 1.41F + crouch, 0);
		local(PELVIS, 0, 0.72F, 0);
		local(R_SHOULDER, 0.35F, 1.29F + crouch, 0);
		local(L_SHOULDER, -0.35F, 1.29F + crouch, 0);
		limb(R_HAND, R_SHOULDER, rArm, 0.6F);
		limb(L_HAND, L_SHOULDER, lArm, 0.6F);
		local(R_HIP, 0.12F, 0.70F, 0);
		local(L_HIP, -0.12F, 0.70F, 0);
		limb(R_FOOT, R_HIP, leg, 0.66F);
		limb(L_FOOT, L_HIP, -leg, 0.66F);
		local(BACK, 0, 1.05F + crouch * 0.5F, -0.2F);
		local(R_WING, 0.5F, 1.25F + crouch, -0.25F);
		local(L_WING, -0.5F, 1.25F + crouch, -0.25F);

		// Held item points forward out of the fist, perpendicular to the arm.
		int hand = rightMain ? R_HAND : L_HAND;
		float a = rightMain ? rArm : lArm;
		float len = 0.7F * s;
		x[WEAPON_TIP] = x[hand] + (fx * Mth.cos(a) + ux * Mth.sin(a)) * len;
		y[WEAPON_TIP] = y[hand] + (fy * Mth.cos(a) + uy * Mth.sin(a)) * len;
		z[WEAPON_TIP] = z[hand] + (fz * Mth.cos(a) + uz * Mth.sin(a)) * len;
	}

	/** Elytra / swimming: the body lies along the view direction, so joints are spread along it. */
	private void updateFlying(LocalPlayer p, float pt, float yaw) {
		float pitch = p.getViewXRot(pt) * Mth.DEG_TO_RAD;
		fx = -Mth.sin(yaw) * Mth.cos(pitch);
		fy = -Mth.sin(pitch);
		fz = Mth.cos(yaw) * Mth.cos(pitch);
		ux = -Mth.sin(yaw) * Mth.sin(pitch);
		uy = Mth.cos(pitch);
		uz = Mth.cos(yaw) * Mth.sin(pitch);
		by += height * 0.5;
		localF(HEAD, 0, 0.05F, 0.75F);
		localF(NECK, 0, 0, 0.5F);
		localF(PELVIS, 0, 0, -0.2F);
		localF(R_SHOULDER, 0.35F, 0, 0.4F);
		localF(L_SHOULDER, -0.35F, 0, 0.4F);
		localF(R_HAND, 0.4F, -0.05F, -0.15F);
		localF(L_HAND, -0.4F, -0.05F, -0.15F);
		localF(R_HIP, 0.12F, 0, -0.22F);
		localF(L_HIP, -0.12F, 0, -0.22F);
		localF(R_FOOT, 0.12F, 0, -0.9F);
		localF(L_FOOT, -0.12F, 0, -0.9F);
		localF(BACK, 0, 0.18F, 0.1F);
		localF(R_WING, 0.95F, 0.1F, -0.1F);
		localF(L_WING, -0.95F, 0.1F, -0.1F);
		localF(WEAPON_TIP, 0.45F, 0, -0.3F);
	}

	private void local(int i, float side, float up, float fwd) {
		x[i] = bx + (rx * side + fx * fwd) * s;
		y[i] = by + up * s;
		z[i] = bz + (rz * side + fz * fwd) * s;
	}

	private void localF(int i, float side, float up, float fwd) {
		x[i] = bx + (rx * side + ux * up + fx * fwd) * s;
		y[i] = by + (uy * up + fy * fwd) * s;
		z[i] = bz + (rz * side + uz * up + fz * fwd) * s;
	}

	/** Joint hanging from {@code from}, rotated forward by {@code angle} around the side axis. */
	private void limb(int i, int from, float angle, float length) {
		float l = length * s;
		x[i] = x[from] + fx * Mth.sin(angle) * l;
		y[i] = y[from] - Mth.cos(angle) * l;
		z[i] = z[from] + fz * Mth.sin(angle) * l;
	}

	/** Height of a joint relative to the feet, normalised to [0, 1] (0 = feet, 1 = head). */
	float heightFraction(int i) {
		return Mth.clamp((float) ((y[i] - baseY) / Math.max(0.3F, height)), 0, 1);
	}
}
