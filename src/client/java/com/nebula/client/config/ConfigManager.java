package com.nebula.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nebula.client.NebulaClient;
import com.nebula.client.core.ModuleManager;
import com.nebula.client.modules.Module;
import com.nebula.client.settings.Setting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Persists every module state and setting as JSON. Writes are debounced and atomic (temp file +
 * move), unreadable files are quarantined instead of overwritten, and unknown/missing keys fall
 * back to defaults so older or hand-edited files keep loading.
 */
public final class ConfigManager {
	public static final int VERSION = 1;
	private static final long SAVE_DELAY_MS = 1500;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file;
	private final ModuleManager modules;
	private boolean dirty;
	private boolean loading;
	private long lastChange;
	private String lastStatus = "Not loaded";

	public ConfigManager(Path directory, ModuleManager modules) {
		this.file = directory.resolve("config.json");
		this.modules = modules;
	}

	public Path file() {
		return file;
	}

	public String lastStatus() {
		return lastStatus;
	}

	public void noteStatus(String status) {
		lastStatus = status;
	}

	public void markDirty() {
		if (loading) {
			return;
		}
		dirty = true;
		lastChange = System.currentTimeMillis();
	}

	/** Called every client tick; saves once changes have settled. */
	public void tick() {
		if (dirty && System.currentTimeMillis() - lastChange >= SAVE_DELAY_MS) {
			save();
		}
	}

	public void load() {
		if (!Files.exists(file)) {
			lastStatus = "Created default config";
			NebulaClient.LOGGER.info("[Nebula] No config found, writing defaults to {}", file);
			save();
			return;
		}
		JsonObject root;
		try {
			String text = Files.readString(file, StandardCharsets.UTF_8);
			JsonElement parsed = JsonParser.parseString(text);
			if (!parsed.isJsonObject()) {
				throw new IllegalStateException("Root is not a JSON object");
			}
			root = parsed.getAsJsonObject();
		} catch (Exception e) {
			quarantine(e);
			save();
			return;
		}
		loading = true;
		try {
			apply(root);
			lastStatus = "Loaded " + file.getFileName();
			NebulaClient.LOGGER.info("[Nebula] Config loaded from {}", file);
		} finally {
			loading = false;
		}
		dirty = false;
	}

	private void apply(JsonObject root) {
		JsonObject moduleObjects = root.has("modules") && root.get("modules").isJsonObject() ? root.getAsJsonObject("modules") : new JsonObject();
		for (Module module : modules.all()) {
			JsonElement element = moduleObjects.get(module.id());
			if (element == null || !element.isJsonObject()) {
				continue; // missing entry: keep defaults
			}
			JsonObject object = element.getAsJsonObject();
			JsonObject settings = object.has("settings") && object.get("settings").isJsonObject() ? object.getAsJsonObject("settings") : new JsonObject();
			for (Setting<?> setting : module.settings()) {
				JsonElement value = settings.get(setting.configKey());
				if (value != null && setting.isPersistent()) {
					try {
						setting.fromJson(value);
					} catch (RuntimeException e) {
						NebulaClient.LOGGER.warn("[Nebula] Ignoring invalid value for {}.{}: {}", module.id(), setting.configKey(), e.getMessage());
					}
				}
			}
			if (object.has("enabled") && object.get("enabled").isJsonPrimitive()) {
				module.setEnabled(object.get("enabled").getAsBoolean());
			}
		}
	}

	public void save() {
		JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		JsonObject moduleObjects = new JsonObject();
		for (Module module : modules.all()) {
			JsonObject object = new JsonObject();
			object.addProperty("enabled", module.isEnabled());
			JsonObject settings = new JsonObject();
			for (Setting<?> setting : module.settings()) {
				if (setting.isPersistent()) {
					settings.add(setting.configKey(), setting.toJson());
				}
			}
			object.add("settings", settings);
			moduleObjects.add(module.id(), object);
		}
		root.add("modules", moduleObjects);
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
			dirty = false;
			lastStatus = "Saved";
		} catch (IOException e) {
			lastStatus = "Save failed: " + e.getMessage();
			NebulaClient.LOGGER.error("[Nebula] Failed to save config {}", file, e);
		}
	}

	/** Restores every module and setting to defaults and writes the result. */
	public void resetAll() {
		loading = true;
		try {
			for (Module module : modules.all()) {
				module.resetSettings();
			}
		} finally {
			loading = false;
		}
		save();
		lastStatus = "Reset to defaults";
		NebulaClient.LOGGER.info("[Nebula] Configuration reset to defaults");
	}

	private void quarantine(Exception cause) {
		Path backup = file.resolveSibling("config.corrupt-" + System.currentTimeMillis() + ".json");
		try {
			Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
			NebulaClient.LOGGER.warn("[Nebula] Config was unreadable ({}); moved it to {} and restored defaults", cause.getMessage(), backup.getFileName());
			lastStatus = "Recovered from corrupt config";
		} catch (IOException e) {
			NebulaClient.LOGGER.error("[Nebula] Config unreadable and could not be backed up", e);
			lastStatus = "Config unreadable";
		}
	}
}
