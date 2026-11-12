package com.nebula.client.rendering.world;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.nebula.client.core.ClientManager;
import com.nebula.client.core.ModuleManager;
import com.nebula.client.rendering.NebulaPipelines;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Hooks the 3D effects into the end of the main world pass, after terrain, entities and translucency. */
public final class WorldEffects implements AutoCloseable {
	private final OrbRenderer orbs = new OrbRenderer();
	private final TrailRenderer trails = new TrailRenderer();
	private final OrbFrame frame = new OrbFrame();
	private final Matrix4f view = new Matrix4f();

	public void register() {
		WorldRenderEvents.END_MAIN.register(this::render);
	}

	private void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !ClientManager.isReady()) {
			return;
		}
		NebulaPipelines.verifyOnce();
		ModuleManager modules = ClientManager.get().modules();
		Vec3 camera = context.worldState().cameraRenderState.pos;
		// During the main pass the model-view stack holds exactly the camera rotation; positions are camera-relative.
		view.set(RenderSystem.getModelViewMatrix());
		RenderTarget target = mc.getMainRenderTarget();
		float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

		if (modules.motionTrails.isEnabled()) {
			modules.motionTrails.render(trails, target, view, camera, partialTick);
		}
		if (modules.playerOrbit.isEnabled() && modules.playerOrbit.compute(frame, camera, partialTick)) {
			orbs.render(frame, target, view);
		}
	}

	@Override
	public void close() {
		orbs.close();
		trails.close();
	}
}
