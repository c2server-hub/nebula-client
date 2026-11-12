package com.nebula.client.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

/** One choice out of a fixed list, edited with a dropdown. */
public final class ModeSetting extends Setting<String> {
	private final List<String> modes;

	public ModeSetting(String name, String description, String defaultValue, String... modes) {
		super(name, description, defaultValue);
		this.modes = List.of(modes);
		if (!this.modes.contains(defaultValue)) {
			throw new IllegalArgumentException("Default mode " + defaultValue + " not in " + this.modes);
		}
	}

	public List<String> modes() {
		return modes;
	}

	public boolean is(String mode) {
		return value.equals(mode);
	}

	public int index() {
		return modes.indexOf(value);
	}

	@Override
	protected String sanitize(String candidate) {
		return modes.contains(candidate) ? candidate : null;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
			set(json.getAsString());
		}
	}
}
