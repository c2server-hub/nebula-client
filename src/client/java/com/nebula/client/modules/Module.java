package com.nebula.client.modules;

import com.nebula.client.settings.KeybindSetting;
import com.nebula.client.settings.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
	private final String id;
	private final String name;
	private final String description;
	private final Category category;
	private final boolean alwaysActive;
	private final boolean enabledByDefault;
	private final List<Setting<?>> settings = new ArrayList<>();
	protected final KeybindSetting keybind;
	private boolean enabled;

	protected Module(String id, String name, String description, Category category, boolean enabledByDefault, boolean alwaysActive) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
		this.alwaysActive = alwaysActive;
		this.enabledByDefault = enabledByDefault || alwaysActive;
		this.keybind = add(new KeybindSetting("Keybind", "Key that toggles this module in-game", KeybindSetting.NONE));
		this.enabled = this.enabledByDefault;
	}

	protected Module(String id, String name, String description, Category category) {
		this(id, name, description, category, false, false);
	}

	protected final <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	public Category category() {
		return category;
	}

	public List<Setting<?>> settings() {
		return Collections.unmodifiableList(settings);
	}

	public KeybindSetting keybind() {
		return keybind;
	}

	/** Configuration-only modules (theme, GUI settings) cannot be switched off. */
	public boolean isAlwaysActive() {
		return alwaysActive;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public boolean isEnabledByDefault() {
		return enabledByDefault;
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	public void setEnabled(boolean enabled) {
		if (alwaysActive) {
			enabled = true;
		}
		if (this.enabled == enabled) {
			return;
		}
		this.enabled = enabled;
		if (enabled) {
			onEnable();
		} else {
			onDisable();
		}
		ModuleEvents.changed(this);
	}

	public void resetSettings() {
		for (Setting<?> setting : settings) {
			setting.reset();
		}
		setEnabled(enabledByDefault);
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	/** Client tick (20 Hz), only while enabled and in a world. */
	public void onTick() {
	}
}
