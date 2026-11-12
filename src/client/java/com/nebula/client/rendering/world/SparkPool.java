package com.nebula.client.rendering.world;

import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.utilities.ColorUtil;

/**
 * Fixed-size pool of ballistic glowing sparks. Positions are evaluated analytically from the
 * spawn time, so motion is identical at any frame rate and nothing is integrated per frame.
 */
public final class SparkPool {
	private final int max;
	private final double[] x, y, z;
	private final float[] vx, vy, vz, life, size;
	private final int[] color;
	private final long[] born;
	private int next;

	public SparkPool(int max) {
		this.max = max;
		x = new double[max];
		y = new double[max];
		z = new double[max];
		vx = new float[max];
		vy = new float[max];
		vz = new float[max];
		life = new float[max];
		size = new float[max];
		color = new int[max];
		born = new long[max];
	}

	public void emit(double px, double py, double pz, float dx, float dy, float dz, int argb, float lifetime, float sparkSize) {
		int s = next;
		next = (next + 1) % max;
		x[s] = px;
		y[s] = py;
		z[s] = pz;
		vx[s] = dx;
		vy[s] = dy;
		vz[s] = dz;
		color[s] = argb;
		life[s] = lifetime;
		size[s] = sparkSize;
		born[s] = System.nanoTime();
	}

	/** Head plus two trailing samples along the trajectory read as a motion-blurred streak. */
	public void render(WorldFrame f, GlowBatch batch, float gravity) {
		long now = System.nanoTime();
		batch.begin(f);
		for (int s = 0; s < max; s++) {
			if (born[s] == 0) {
				continue;
			}
			float age = (now - born[s]) / 1e9F;
			float t = age / life[s];
			if (t >= 1) {
				born[s] = 0;
				continue;
			}
			for (int k = 0; k < 3; k++) {
				float ta = Math.max(0, age - k * 0.018F);
				float drag = 1 - Math.min(0.6F, ta * 0.8F);
				double px = x[s] + vx[s] * ta * drag;
				double py = y[s] + vy[s] * ta * drag + 0.5 * gravity * ta * ta;
				double pz = z[s] + vz[s] * ta * drag;
				float fade = (1 - t) * (k == 0 ? 1 : 0.5F / k);
				batch.billboard(f.rx(px), f.ry(py), f.rz(pz), size[s] * (1 - 0.5F * t) * (k == 0 ? 1 : 0.7F), ColorUtil.withAlpha(color[s], fade));
			}
		}
		batch.end(NebulaPipelines.GLOW_SPARK, 0, 0, 0);
	}
}
