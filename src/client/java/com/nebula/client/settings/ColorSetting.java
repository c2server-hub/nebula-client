package com.nebula.client.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

/** ARGB colour stored as {@code #AARRGGBB}. */
public final class ColorSetting extends Setting<Integer> {
	private final boolean allowAlpha;

	public ColorSetting(String name, String description, int defaultArgb, boolean allowAlpha) {
		super(name, description, defaultArgb);
		this.allowAlpha = allowAlpha;
	}

	public ColorSetting(String name, String description, int defaultArgb) {
		this(name, description, defaultArgb, false);
	}

	public int argb() {
		return value;
	}

	public boolean allowsAlpha() {
		return allowAlpha;
	}

	@Override
	protected Integer sanitize(Integer candidate) {
		if (candidate == null) {
			return null;
		}
		return allowAlpha ? candidate : candidate | 0xFF000000;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(String.format(Locale.ROOT, "#%08X", value));
	}

	@Override
	public void fromJson(JsonElement json) {
		if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
			return;
		}
		String s = json.getAsString().trim();
		if (s.startsWith("#")) {
			s = s.substring(1);
		}
		try {
			long parsed = Long.parseLong(s, 16);
			set((int) (s.length() <= 6 ? parsed | 0xFF000000L : parsed));
		} catch (NumberFormatException ignored) {
			// keep the current value for malformed colours
		}
	}
}
