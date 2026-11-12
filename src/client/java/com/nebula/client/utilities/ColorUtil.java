package com.nebula.client.utilities;

public final class ColorUtil {
	private ColorUtil() {
	}

	public static int alpha(int c) {
		return c >>> 24;
	}

	public static int withAlpha(int c, int alpha) {
		return (Math.clamp(alpha, 0, 255) << 24) | (c & 0xFFFFFF);
	}

	public static int withAlpha(int c, float alpha) {
		return withAlpha(c, Math.round(alpha * 255));
	}

	/** Multiplies the existing alpha, used for fade animations. */
	public static int fade(int c, float factor) {
		return withAlpha(c, Math.round(alpha(c) * Math.clamp(factor, 0, 1)));
	}

	public static int mix(int a, int b, float t) {
		t = Math.clamp(t, 0, 1);
		int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
		int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
		return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
	}

	public static int brighten(int c, float amount) {
		return mix(c, c | 0xFFFFFF, amount);
	}

	/** h, s, v in [0, 1]. */
	public static int hsv(float h, float s, float v, int alpha) {
		int rgb = java.awt.Color.HSBtoRGB(h, s, v) & 0xFFFFFF;
		return (alpha << 24) | rgb;
	}

	public static float[] toHsv(int c) {
		return java.awt.Color.RGBtoHSB(c >> 16 & 255, c >> 8 & 255, c & 255, null);
	}

	public static float red(int c) {
		return (c >> 16 & 255) / 255F;
	}

	public static float green(int c) {
		return (c >> 8 & 255) / 255F;
	}

	public static float blue(int c) {
		return (c & 255) / 255F;
	}

	public static String hex(int c) {
		return String.format(java.util.Locale.ROOT, "#%06X", c & 0xFFFFFF);
	}
}
