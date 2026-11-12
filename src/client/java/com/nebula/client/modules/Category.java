package com.nebula.client.modules;

public enum Category {
	VISUAL("Visual", "World & player effects"),
	INTERFACE("Interface", "HUD, theme & GUI");

	private final String displayName;
	private final String subtitle;

	Category(String displayName, String subtitle) {
		this.displayName = displayName;
		this.subtitle = subtitle;
	}

	public String displayName() {
		return displayName;
	}

	public String subtitle() {
		return subtitle;
	}
}
