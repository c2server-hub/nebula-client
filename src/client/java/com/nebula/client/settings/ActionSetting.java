package com.nebula.client.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** A button inside the settings panel; not persisted. */
public final class ActionSetting extends Setting<Integer> {
	private final Runnable action;

	public ActionSetting(String name, String description, Runnable action) {
		super(name, description, 0);
		this.action = action;
	}

	public void run() {
		action.run();
	}

	@Override
	public boolean isPersistent() {
		return false;
	}

	@Override
	public JsonElement toJson() {
		return JsonNull.INSTANCE;
	}

	@Override
	public void fromJson(JsonElement json) {
	}
}
