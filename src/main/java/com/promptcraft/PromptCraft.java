package com.promptcraft;

import com.promptcraft.command.PromptCraftCommand;
import com.promptcraft.config.PromptCraftConfig;
import com.promptcraft.item.ModItems;
import com.promptcraft.minigame.MinigameManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PromptCraft implements ModInitializer {
    public static final String MOD_ID = "promptcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("PromptCraft is initializing...");

        // 1. Load Configuration
        PromptCraftConfig.load();

        // 2. Register Dynamic Items
        ModItems.register();

        // 3. Register Commands (/ai ...)
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            PromptCraftCommand.register(dispatcher);
        });

        // 4. Register Minigame Tick Loop & Server Stopping Hook
        ServerTickEvents.END_SERVER_TICK.register(MinigameManager::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(MinigameManager::stopAll);

        LOGGER.info("PromptCraft successfully initialized!");
    }
}
