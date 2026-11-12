package com.nebula.client;

import com.nebula.client.core.ClientManager;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NebulaClient implements ClientModInitializer {
	public static final String MOD_ID = "nebula";
	public static final Logger LOGGER = LoggerFactory.getLogger("Nebula");

	@Override
	public void onInitializeClient() {
		ClientManager.init();
	}
}
