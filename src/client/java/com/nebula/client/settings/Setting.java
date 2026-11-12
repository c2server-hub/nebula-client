package com.nebula.client.settings;

import com.google.gson.JsonElement;

import java.util.function.BooleanSupplier;

/** A named, persisted, observable value owned by a module. */
public abstract class Setting<T> {
	/** Installed by the config manager so every change schedules an auto-save. */
	private static Runnable changeHook = () -> {
	};

	private final String name;
	private final String description;
	private final T defaultValue;
	protected T value;
	private BooleanSupplier visibility = () -> true;
	private Runnable onChange = () -> {
	};

	protected Setting(String name, String description, T defaultValue) {
		this.name = name;
		this.description = description;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	public static void setChangeHook(Runnable hook) {
		changeHook = hook;
	}

	public String name() {
		return name;
	}

	/** Stable key used in the config file. */
	public String configKey() {
		return name.toLowerCase().replace(' ', '_');
	}

	public String description() {
		return description;
	}

	public T get() {
		return value;
	}

	public T defaultValue() {
		return defaultValue;
	}

	public void set(T newValue) {
		T sanitized = sanitize(newValue);
		if (sanitized == null || sanitized.equals(value)) {
			return;
		}
		value = sanitized;
		onChange.run();
		changeHook.run();
	}

	public void reset() {
		set(defaultValue);
	}

	public boolean isVisible() {
		return visibility.getAsBoolean();
	}

	public boolean isPersistent() {
		return true;
	}

	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S visibleWhen(BooleanSupplier supplier) {
		this.visibility = supplier;
		return (S) this;
	}

	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S onChange(Runnable callback) {
		this.onChange = callback;
		return (S) this;
	}

	protected T sanitize(T candidate) {
		return candidate;
	}

	public abstract JsonElement toJson();

	/** Applies a stored value; implementations must tolerate malformed input and keep the current value. */
	public abstract void fromJson(JsonElement json);
}
