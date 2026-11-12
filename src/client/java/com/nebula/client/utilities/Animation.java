package com.nebula.client.utilities;

/**
 * Exponential approach towards a target, integrated with real elapsed time so the motion is
 * identical at 30 or 300 FPS. Each instance keeps its own clock.
 */
public final class Animation {
	private float value;
	private float target;
	private final float speed;
	private long last = -1;

	public Animation(float initial, float speed) {
		this.value = initial;
		this.target = initial;
		this.speed = speed;
	}

	public Animation(float speed) {
		this(0, speed);
	}

	public float update(float target) {
		this.target = target;
		return update();
	}

	public float update() {
		long now = System.nanoTime();
		float dt = last < 0 ? 0 : Math.min((now - last) / 1_000_000_000F, 0.1F);
		last = now;
		float k = 1 - (float) Math.exp(-speed * speedMultiplier * dt);
		value += (target - value) * k;
		if (Math.abs(target - value) < 0.0005F) {
			value = target;
		}
		return value;
	}

	public float get() {
		return value;
	}

	public void snap(float v) {
		value = v;
		target = v;
	}

	private static float speedMultiplier = 1;

	/** Global multiplier driven by GUI Settings → Animation speed. */
	public static void setSpeedMultiplier(float multiplier) {
		speedMultiplier = Math.max(0.05F, multiplier);
	}

	public static float easeOutCubic(float t) {
		float p = 1 - Math.clamp(t, 0, 1);
		return 1 - p * p * p;
	}

	public static float easeOutBack(float t) {
		t = Math.clamp(t, 0, 1);
		float c1 = 1.70158F;
		float c3 = c1 + 1;
		return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
	}
}
