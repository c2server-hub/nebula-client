package com.nebula.client.rendering.world;

import com.nebula.client.core.ClientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Projects the sun (or moon) into GUI pixel coordinates once per frame at the end of the main
 * world pass, where the 3D view and projection matrices are still bound. Screen-space effects
 * like the Lens Flare module then read the result while rendering the HUD.
 */
public final class SunTracker {
	public static final SunTracker INSTANCE = new SunTracker();

	/** Sun (or moon) position in GUI pixels; only meaningful while {@link #strength} > 0. */
	public float screenX, screenY;
	/** 0..1 blend of every visibility factor: facing, elevation, rain, sky light. */
	public float strength;
	/** True when the flare source is the moon rather than the sun. */
	public boolean moon;

	private final Vector3f dir = new Vector3f();
	private final Vector3f viewDir = new Vector3f();
	private final Vector4f clip = new Vector4f();

	private SunTracker() {
	}

	public void update(WorldFrame frame, Minecraft mc) {
		strength = 0;
		var level = mc.level;
		LocalPlayer player = mc.player;
		if (level == null || player == null || !level.dimensionType().hasSkyLight()) {
			return;
		}
		// Exact vanilla sun direction (see celestial.fsh): angle = (fraction - 0.25) * 2pi.
		float fraction = Math.floorMod(level.getDayTime(), 24000L) / 24000F;
		float ang = (fraction - 0.25F) * Mth.TWO_PI;
		dir.set(-Mth.sin(ang), Mth.cos(ang), 0);
		moon = dir.y < -0.03F;
		if (moon) {
			dir.negate();
		}

		// View-space direction: the model-view stack at this point holds only the camera rotation.
		viewDir.set(dir);
		frame.view().transformDirection(viewDir);
		if (viewDir.z > -0.05F) {
			return; // Behind the camera.
		}

		// Project a far point along the ray with the live projection matrix.
		Matrix4fc proj = mc.gameRenderer.getProjectionMatrix(frame.partialTick());
		proj.transform(clip.set(viewDir.x * 100, viewDir.y * 100, viewDir.z * 100, 1));
		if (Math.abs(clip.w) < 1e-4F) {
			return;
		}
		screenX = (clip.x / clip.w * 0.5F + 0.5F) * mc.getWindow().getGuiScaledWidth();
		screenY = (0.5F - clip.y / clip.w * 0.5F) * mc.getWindow().getGuiScaledHeight();

		// Fade in as the source moves towards the screen centre, once it clears the horizon,
		// with weather and (optionally) being under cover dimming the flare further.
		float facing = Math.clamp((-viewDir.z - 0.15F) / 0.5F, 0, 1);
		float elevation = Math.clamp((dir.y + 0.08F) / 0.25F, 0, 1);
		float weather = 1 - level.getRainLevel(frame.partialTick()) * 0.9F;
		float cover = 1;
		if (ClientManager.isReady() && ClientManager.get().modules().lensFlare.hideInCaves.isOn()) {
			BlockPos pos = player.blockPosition();
			cover = Math.clamp(level.getBrightness(LightLayer.SKY, pos) / 15F * 1.7F, 0, 1);
		}
		strength = facing * elevation * weather * cover * (moon ? 0.45F : 1);
	}
}
