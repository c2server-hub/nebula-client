package com.nebula.client.rendering.world;

/** Per-frame orb data in camera-relative world space, preallocated to avoid per-frame garbage. */
public final class OrbFrame {
	public static final int MAX = 16;
	public final float[] x = new float[MAX];
	public final float[] y = new float[MAX];
	public final float[] z = new float[MAX];
	public final float[] radius = new float[MAX];
	public final int[] color = new int[MAX];
	public int count;
	public float intensity;
	public float haloScale;
	public float haloIntensity;
	public boolean bloom;
	public float bloomStrength;
	public float bloomRadius;
	public float time;
}
