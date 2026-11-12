package com.nebula.client.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** A GLFW key code, {@link #NONE} when unbound. */
public final class KeybindSetting extends Setting<Integer> {
	public static final int NONE = GLFW.GLFW_KEY_UNKNOWN;

	public KeybindSetting(String name, String description, int defaultKey) {
		super(name, description, defaultKey);
	}

	public int key() {
		return value;
	}

	public boolean isBound() {
		return value != NONE;
	}

	public String display() {
		if (!isBound()) {
			return "None";
		}
		String name = InputConstants.Type.KEYSYM.getOrCreate(value).getDisplayName().getString();
		return name.length() == 1 ? name.toUpperCase(Locale.ROOT) : name;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			int key = json.getAsInt();
			if (key == NONE || (key >= GLFW.GLFW_KEY_SPACE && key <= GLFW.GLFW_KEY_LAST)) {
				set(key);
			}
		}
	}
}
