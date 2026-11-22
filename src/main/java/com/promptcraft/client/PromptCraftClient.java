package com.promptcraft.client;

import com.promptcraft.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PromptCraftClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("PromptCraft/Client");

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.DYNAMIC_CREATURE, DynamicCreatureRenderer::new);
        LOGGER.info("PromptCraft client initialized (DynamicCreatureRenderer registered).");
    }
}
