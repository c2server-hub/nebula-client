package com.nebula.client.rendering.world;

/** A module that draws glow geometry in the world, called once per frame while enabled. */
public interface WorldRenderable {
	void renderWorld(WorldFrame frame, GlowBatch batch);
}
