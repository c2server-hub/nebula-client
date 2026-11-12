package com.nebula.client.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Notifies listeners (config auto-save, notifications) when a module is toggled. */
public final class ModuleEvents {
	private static final List<Consumer<Module>> LISTENERS = new ArrayList<>();

	private ModuleEvents() {
	}

	public static void addListener(Consumer<Module> l) {
		LISTENERS.add(l);
	}

	static void changed(Module module) {
		for (Consumer<Module> l : LISTENERS) {
			l.accept(module);
		}
	}
}
