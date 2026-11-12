package com.nebula.client.rendering;

/** Colour stops spread evenly along one axis, or a four-corner mesh gradient. */
public record Gradient(Direction direction, int[] colors) {
	public enum Direction { HORIZONTAL, VERTICAL, CORNERS }

	public static Gradient horizontal(int... colors) {
		return new Gradient(Direction.HORIZONTAL, colors);
	}

	public static Gradient vertical(int... colors) {
		return new Gradient(Direction.VERTICAL, colors);
	}

	/** Top-left, top-right, bottom-right, bottom-left. */
	public static Gradient corners(int tl, int tr, int br, int bl) {
		return new Gradient(Direction.CORNERS, new int[]{tl, tr, br, bl});
	}

	public static Gradient solid(int color) {
		return new Gradient(Direction.HORIZONTAL, new int[]{color});
	}
}
