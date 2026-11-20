package com.nebula.client.modules.visual;

import com.nebula.client.rendering.world.GlowBatch;
import com.nebula.client.rendering.world.WorldFrame;
import com.nebula.client.utilities.ColorUtil;

/**
 * Ring buffer of timestamped positions rendered as a camera-facing ribbon whose width and alpha
 * decrease with age. Sampled per frame (capped at ~90 Hz) so it stays smooth at any FPS.
 */
final class TrailHistory {
	private static final long MIN_STEP_NANOS = 11_000_000L;

	private final int cap;
	private final double[] x, y, z;
	private final long[] time;
	private final int[] color;
	private int head = -1;
	private int size;

	TrailHistory(int cap) {
		this.cap = cap;
		x = new double[cap];
		y = new double[cap];
		z = new double[cap];
		time = new long[cap];
		color = new int[cap];
	}

	void push(double px, double py, double pz, int argb, long now) {
		if (size > 0 && now - time[head] < MIN_STEP_NANOS) {
			return;
		}
		head = (head + 1) % cap;
		x[head] = px;
		y[head] = py;
		z[head] = pz;
		time[head] = now;
		color[head] = argb;
		size = Math.min(cap, size + 1);
	}

	void clear() {
		size = 0;
		head = -1;
	}

	boolean expired(long now, float duration) {
		return size == 0 || (now - time[head]) / 1e9F > duration;
	}

	/** Ribbon attached to a live point (the anchor's current position this frame). */
	void render(WorldFrame f, GlowBatch b, long now, float duration, float width, double lx, double ly, double lz, int liveColor, float alphaMul) {
		draw(f, b, now, duration, width, f.rx(lx), f.ry(ly), f.rz(lz), liveColor, 0, 0, alphaMul);
	}

	/** Ribbon that starts at its newest sample (the source no longer exists). */
	void renderDetached(WorldFrame f, GlowBatch b, long now, float duration, float width, float alphaMul) {
		if (size == 0) {
			return;
		}
		float age = (now - time[head]) / 1e9F;
		draw(f, b, now, duration, width, f.rx(x[head]), f.ry(y[head]), f.rz(z[head]), color[head], age, 1, alphaMul);
	}

	private void draw(WorldFrame f, GlowBatch b, long now, float duration, float width, float px, float py, float pz, int pColor, float pAge,
					  int skip, float alphaMul) {
		for (int k = skip; k < size; k++) {
			int i = ((head - k) % cap + cap) % cap;
			float age = (now - time[i]) / 1e9F;
			if (age >= duration || pAge >= duration) {
				break;
			}
			float cx = f.rx(x[i]), cy = f.ry(y[i]), cz = f.rz(z[i]);
			float t0 = pAge / duration, t1 = age / duration;
			segment(b, px, py, pz, cx, cy, cz, width * (1 - 0.75F * t0), width * (1 - 0.75F * t1),
				ColorUtil.withAlpha(pColor, (float) Math.pow(1 - t0, 1.5) * alphaMul),
				ColorUtil.withAlpha(color[i], (float) Math.pow(1 - t1, 1.5) * alphaMul));
			px = cx;
			py = cy;
			pz = cz;
			pColor = color[i];
			pAge = age;
		}
	}

	/**
	 * One camera-facing ribbon segment between two camera-relative points. The camera sits at the
	 * origin, so the side vector is the cross product of the segment and the view ray.
	 */
	static void segment(GlowBatch b, float x0, float y0, float z0, float x1, float y1, float z1, float w0, float w1, int c0, int c1) {
		float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
		float mx = (x0 + x1) * 0.5F, my = (y0 + y1) * 0.5F, mz = (z0 + z1) * 0.5F;
		float sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
		float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (len < 1e-7F) {
			return;
		}
		sx /= len;
		sy /= len;
		sz /= len;
		b.quadGradient(
			x0 - sx * w0, y0 - sy * w0, z0 - sz * w0,
			x0 + sx * w0, y0 + sy * w0, z0 + sz * w0,
			x1 + sx * w1, y1 + sy * w1, z1 + sz * w1,
			x1 - sx * w1, y1 - sy * w1, z1 - sz * w1,
			c0, c1);
	}
}
