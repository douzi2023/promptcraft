package com.promptcraft.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PromptCraftClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("PromptCraft/Client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("PromptCraft client initialized.");
    }
}
