package com.nebula.client.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class NumberSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double step;
	private final String unit;

	public NumberSetting(String name, String description, double defaultValue, double min, double max, double step, String unit) {
		super(name, description, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step;
		this.unit = unit;
	}

	public NumberSetting(String name, String description, double defaultValue, double min, double max, double step) {
		this(name, description, defaultValue, min, max, step, "");
	}

	public float floatValue() {
		return value.floatValue();
	}

	public int intValue() {
		return (int) Math.round(value);
	}

	public double min() {
		return min;
	}

	public double max() {
		return max;
	}

	public double step() {
		return step;
	}

	/** Normalised position of the value in [0, 1], used by sliders. */
	public double fraction() {
		return (value - min) / (max - min);
	}

	public void setFraction(double fraction) {
		set(min + Math.clamp(fraction, 0, 1) * (max - min));
	}

	public String display() {
		boolean integral = step >= 1;
		String number = integral ? Long.toString(Math.round(value)) : String.format(java.util.Locale.ROOT, step >= 0.1 ? "%.1f" : "%.2f", value);
		return number + unit;
	}

	@Override
	protected Double sanitize(Double candidate) {
		if (candidate == null || candidate.isNaN() || candidate.isInfinite()) {
			return null;
		}
		double snapped = step > 0 ? Math.round((candidate - min) / step) * step + min : candidate;
		return Math.clamp(snapped, min, max);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			set(json.getAsDouble());
		}
	}
}
