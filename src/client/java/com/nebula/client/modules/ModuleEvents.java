package com.nebula.client.modules;

import java.util.function.Consumer;

/** Notifies listeners (config auto-save, HUD module list animation) when a module is toggled. */
public final class ModuleEvents {
	private static Consumer<Module> listener = m -> {
	};

	private ModuleEvents() {
	}

	public static void setListener(Consumer<Module> l) {
		listener = l;
	}

	static void changed(Module module) {
		listener.accept(module);
	}
}
