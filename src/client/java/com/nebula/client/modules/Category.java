package com.nebula.client.modules;

import com.nebula.client.gui.components.CategoryTab;

/** Every module belongs to exactly one of these; the ClickGUI sidebar mirrors them in order. */
public enum Category {
	SKY("Sky", "Light, stars & atmosphere", CategoryTab.Icon.STAR),
	WORLD("World", "Ambience & environment", CategoryTab.Icon.GLOBE),
	PLAYER("Player", "Effects on your character", CategoryTab.Icon.PERSON),
	COMBAT("Combat", "Target feedback & impact", CategoryTab.Icon.SWORDS),
	INTERFACE("Interface", "HUD, GUI & controls", CategoryTab.Icon.GRID);

	private final String displayName;
	private final String subtitle;
	private final CategoryTab.Icon icon;

	Category(String displayName, String subtitle, CategoryTab.Icon icon) {
		this.displayName = displayName;
		this.subtitle = subtitle;
		this.icon = icon;
	}

	public String displayName() {
		return displayName;
	}

	public String subtitle() {
		return subtitle;
	}

	public CategoryTab.Icon icon() {
		return icon;
	}
}
