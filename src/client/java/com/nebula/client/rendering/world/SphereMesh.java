package com.nebula.client.rendering.world;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit icosphere uploaded once to static GPU buffers. Subdividing an icosahedron gives evenly
 * sized triangles (no pole pinching like a UV sphere), so the fresnel rim stays smooth.
 */
public final class SphereMesh implements AutoCloseable {
	private final int subdivisions;
	private @Nullable GpuBuffer vertices;
	private @Nullable GpuBuffer indices;
	private int indexCount;

	public SphereMesh(int subdivisions) {
		this.subdivisions = subdivisions;
	}

	public GpuBuffer vertices() {
		ensureUploaded();
		return vertices;
	}

	public GpuBuffer indices() {
		ensureUploaded();
		return indices;
	}

	public int indexCount() {
		ensureUploaded();
		return indexCount;
	}

	private void ensureUploaded() {
		if (vertices != null) {
			return;
		}
		List<float[]> points = new ArrayList<>();
		float t = (float) ((1 + Math.sqrt(5)) / 2);
		float[][] base = {
			{-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0},
			{0, -1, t}, {0, 1, t}, {0, -1, -t}, {0, 1, -t},
			{t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1}
		};
		for (float[] p : base) {
			points.add(normalize(p));
		}
		int[] faces = {
			0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11,
			1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8,
			3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9,
			4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1
		};
		for (int s = 0; s < subdivisions; s++) {
			Map<Long, Integer> midpoints = new HashMap<>();
			int[] next = new int[faces.length * 4];
			int n = 0;
			for (int i = 0; i < faces.length; i += 3) {
				int a = faces[i], b = faces[i + 1], c = faces[i + 2];
				int ab = midpoint(points, midpoints, a, b);
				int bc = midpoint(points, midpoints, b, c);
				int ca = midpoint(points, midpoints, c, a);
				int[] tri = {a, ab, ca, b, bc, ab, c, ca, bc, ab, bc, ca};
				System.arraycopy(tri, 0, next, n, tri.length);
				n += tri.length;
			}
			faces = next;
		}
		ByteBuffer vb = MemoryUtil.memAlloc(points.size() * 12);
		ByteBuffer ib = MemoryUtil.memAlloc(faces.length * 4);
		try {
			for (float[] p : points) {
				vb.putFloat(p[0]).putFloat(p[1]).putFloat(p[2]);
			}
			for (int index : faces) {
				ib.putInt(index);
			}
			vb.flip();
			ib.flip();
			vertices = RenderSystem.getDevice().createBuffer(() -> "Nebula sphere vertices", GpuBuffer.USAGE_VERTEX, vb);
			indices = RenderSystem.getDevice().createBuffer(() -> "Nebula sphere indices", GpuBuffer.USAGE_INDEX, ib);
			indexCount = faces.length;
		} finally {
			MemoryUtil.memFree(vb);
			MemoryUtil.memFree(ib);
		}
	}

	private static int midpoint(List<float[]> points, Map<Long, Integer> cache, int a, int b) {
		long key = a < b ? ((long) a << 32) | b : ((long) b << 32) | a;
		Integer existing = cache.get(key);
		if (existing != null) {
			return existing;
		}
		float[] pa = points.get(a), pb = points.get(b);
		points.add(normalize(new float[]{(pa[0] + pb[0]) / 2, (pa[1] + pb[1]) / 2, (pa[2] + pb[2]) / 2}));
		cache.put(key, points.size() - 1);
		return points.size() - 1;
	}

	private static float[] normalize(float[] p) {
		float len = (float) Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
		return new float[]{p[0] / len, p[1] / len, p[2] / len};
	}

	@Override
	public void close() {
		if (vertices != null) {
			vertices.close();
			vertices = null;
		}
		if (indices != null) {
			indices.close();
			indices = null;
		}
	}
}
