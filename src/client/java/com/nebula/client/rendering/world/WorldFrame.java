package com.nebula.client.rendering.world;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;

/** Everything a world effect needs for one frame; positions passed to renderers are relative to {@code camera}. */
public record WorldFrame(RenderTarget target, Matrix4fc view, Vec3 camera, Quaternionfc cameraRotation, float partialTick, float seconds) {
	public float rx(double x) {
		return (float) (x - camera.x);
	}

	public float ry(double y) {
		return (float) (y - camera.y);
	}

	public float rz(double z) {
		return (float) (z - camera.z);
	}
}
