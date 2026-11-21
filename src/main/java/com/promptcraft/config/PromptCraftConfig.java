package com.promptcraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class PromptCraftConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("PromptCraft/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "promptcraft.json");

    private static PromptCraftConfig instance;

    public String apiKey = "";
    public String apiUrl = "https://api.deepseek.com/chat/completions";
    public String model = "deepseek-chat";
    public double temperature = 0.7;
    public int maxTokens = 4096;
    public boolean safeMode = true;
    public String customSystemPrompt = "";

    public static PromptCraftConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                instance = GSON.fromJson(reader, PromptCraftConfig.class);
                if (instance == null) {
                    instance = new PromptCraftConfig();
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load promptcraft.json config, falling back to defaults", e);
                instance = new PromptCraftConfig();
            }
        } else {
            instance = new PromptCraftConfig();
            save();
        }

        // Environment variable override if empty
        if (instance.apiKey == null || instance.apiKey.isBlank()) {
            String envKey = System.getenv("PROMPTCRAFT_API_KEY");
            if (envKey == null || envKey.isBlank()) {
                envKey = System.getenv("OPENAI_API_KEY");
            }
            if (envKey != null && !envKey.isBlank()) {
                instance.apiKey = envKey;
            }
        }
    }

    public static void save() {
        try {
            if (!CONFIG_FILE.getParentFile().exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(get(), writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save promptcraft.json config", e);
        }
    }
}
