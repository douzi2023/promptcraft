package com.promptcraft.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.promptcraft.config.PromptCraftConfig;
import com.promptcraft.tools.ToolRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class LLMClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("PromptCraft/LLM");
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public static CompletableFuture<String> processPlayerPrompt(ServerPlayer player, String userPrompt) {
        PromptCraftConfig config = PromptCraftConfig.get();

        if (config.apiKey == null || config.apiKey.isBlank()) {
            player.sendSystemMessage(Component.literal("§c[PromptCraft] API Key 未配置！请使用 /ai config key <你的API密钥> 设置，或设置环境变量 PROMPTCRAFT_API_KEY。"));
            return CompletableFuture.completedFuture("No API key configured");
        }

        player.sendSystemMessage(Component.literal("§b[PromptCraft] §7正在思考并生成解决方案..."));

        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonArray messages = new JsonArray();

                // 1. System Prompt
                JsonObject systemMsg = new JsonObject();
                systemMsg.addProperty("role", "system");
                String systemPrompt = getSystemPrompt(player, config);
                systemMsg.addProperty("content", systemPrompt);
                messages.add(systemMsg);

                // 2. User Prompt
                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", userPrompt);
                messages.add(userMsg);

                // Start tool loop (max 6 turns to allow self-correction and retries)
                int maxTurns = 6;
                for (int turn = 0; turn < maxTurns; turn++) {
                    JsonObject requestBody = new JsonObject();
                    requestBody.addProperty("model", config.model);
                    requestBody.addProperty("temperature", config.temperature);
                    requestBody.addProperty("max_tokens", config.maxTokens);
                    requestBody.add("messages", messages);
                    requestBody.add("tools", ToolRegistry.getToolDefinitions());
                    requestBody.addProperty("tool_choice", "auto");

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(config.apiUrl))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + config.apiKey.trim())
                            .timeout(Duration.ofSeconds(60))
                            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                            .build();

                    HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() != 200) {
                        String errMsg = "API 报错 (HTTP " + response.statusCode() + "): " + response.body();
                        LOGGER.error(errMsg);
                        player.sendSystemMessage(Component.literal("§c[PromptCraft] " + errMsg));
                        return errMsg;
                    }

                    JsonObject respJson = GSON.fromJson(response.body(), JsonObject.class);
                    JsonArray choices = respJson.getAsJsonArray("choices");
                    if (choices == null || choices.isEmpty()) {
                        return "No choices returned from LLM.";
                    }

                    JsonObject firstChoice = choices.get(0).getAsJsonObject();
                    JsonObject assistantMsg = firstChoice.getAsJsonObject("message");
                    messages.add(assistantMsg);

                    // Check for tool calls
                    if (assistantMsg.has("tool_calls") && !assistantMsg.get("tool_calls").isJsonNull()) {
                        JsonArray toolCalls = assistantMsg.getAsJsonArray("tool_calls");
                        for (JsonElement toolElement : toolCalls) {
                            JsonObject toolCall = toolElement.getAsJsonObject();
                            String toolCallId = toolCall.get("id").getAsString();
                            JsonObject functionObj = toolCall.getAsJsonObject("function");
                            String functionName = functionObj.get("name").getAsString();
                            String argsString = functionObj.get("arguments").getAsString();

                            LOGGER.info("Player {} triggered tool: {} with args: {}", player.getName().getString(), functionName, argsString);

                            // Execute tool safely on the server thread
                            CompletableFuture<String> toolFuture = new CompletableFuture<>();
                            player.server.execute(() -> {
                                try {
                                    String result = ToolRegistry.executeTool(player, functionName, argsString);
                                    toolFuture.complete(result);
                                } catch (Exception e) {
                                    LOGGER.error("Error executing tool " + functionName, e);
                                    toolFuture.complete("Error executing tool: " + e.getMessage());
                                }
                            });

                            String executionResult = toolFuture.join();

                            JsonObject toolResponseMsg = new JsonObject();
                            toolResponseMsg.addProperty("role", "tool");
                            toolResponseMsg.addProperty("tool_call_id", toolCallId);
                            toolResponseMsg.addProperty("content", executionResult);
                            messages.add(toolResponseMsg);
                        }
                        // Continue next turn to let LLM summarize or execute further
                    } else {
                        // Final reply reached
                        String finalReply = assistantMsg.has("content") && !assistantMsg.get("content").isJsonNull()
                                ? assistantMsg.get("content").getAsString() : "";
                        player.sendSystemMessage(Component.literal("§a[PromptCraft] §f" + finalReply));
                        return finalReply;
                    }
                }

                return "Finished multi-turn execution.";
            } catch (Exception e) {
                LOGGER.error("Failed to query LLM", e);
                player.sendSystemMessage(Component.literal("§c[PromptCraft] 通信异常: " + e.getMessage()));
                return "Error: " + e.getMessage();
            }
        });
    }

    private static String getSystemPrompt(ServerPlayer player, PromptCraftConfig config) {
        String base = """
            You are PromptCraft, an omnipotent in-game AI intelligence for Minecraft (Fabric 1.21.1).
            You can interact with the Minecraft world by calling tools to:
            1. Execute vanilla / server commands (building houses with /fill, summoning mobs, setting weather/time, etc.).
            2. Dynamically craft and give custom weapons (guns, fireball blasters, magic wands, explosive blades) with custom stats and behaviors.
            3. Setup and start live minigames with BossBars, waves of enemies, countdowns, and win/loss conditions (e.g. Zombie Fortress Defense).
            4. Inspect the player's surroundings and search loaded mod items for deep mod interoperability.
            5. Dynamically manufacture and spawn custom creatures / boss mobs / guardian companions (`create_custom_creature`) with custom scale (0.2x to 10x), combat styles (melee, laser beam, fireball, TNT barrage, kamikaze bomber, healer), particle auras, skins, and skills.

            Player Information:
            - Name: %s
            - Coordinates: [%.1f, %.1f, %.1f]
            - Current Dimension: %s
            - Game Mode: %s

            Guidelines:
            - Always prefer calling tools to actually make things happen in the game rather than just talking!
            - When asked to build something (e.g. house, castle, bridge), calculate realistic coordinates relative to the player (~ ~ ~) and invoke `execute_commands` or `build_structure`.
            - When asked to "make a gun mod" or "give me a weapon/wand", call `create_dynamic_weapon` immediately!
            - Choose the exact projectile_type matching the user's intent:
              * TNT launcher / grenade launcher / cannon: ALWAYS use projectile_type="tnt"
              * Fireball blaster / flame Gatling: use projectile_type="fireball" or "small_fireball"
              * Breeze wind gun: use projectile_type="wind_charge"
              * Wither skull launcher: use projectile_type="wither_skull"
              * Dragon breath gun: use projectile_type="dragon_fireball"
              * Laser / bullet / rifle: use projectile_type="raycast"
            - When asked to create/spawn a custom monster, giant, boss, pet, bodyguard, healer, minion, or puppet (e.g. '造一个烈焰巨人', '做一个会丢TNT的哥布林', '制造一个会给我加血的守护天使', '做一个自爆怪'):
              * Call `create_custom_creature`!
              * Set scale appropriately (e.g. 0.5 for dwarf/pet, 1.0 normal, 3.0-5.0 for giant/boss).
              * Set combat_style: 'melee', 'ranged_laser', 'ranged_fireball', 'ranged_tnt', 'kamikaze_bomber', 'healer_support', 'guardian'.
              * Set is_friendly=true for guardian companions, pets, and healers that fight for or assist the player; false for hostile monsters/bosses.
              * Add relevant skills like 'teleport_on_hurt', 'summon_minions', 'death_explosion', 'regeneration'.
            - When asked to "play a zombie siege game" or "start a minigame", call `create_minigame` to start the game with a BossBar and waves.
            - When asked to interact with other mods, call `inspect_mods` to discover available mod items and blocks.
            - Answer in the same language as the player (e.g., Chinese if asked in Chinese). Keep your final text concise and exciting!

            Automatic Error Self-Correction:
            - If `execute_commands` returns a failure report (e.g. "FAILED: /fill ... -> Game error: 'Unknown block type...'"), DO NOT give up or send an apology text!
            - Immediately analyze the Game error message, fix the command syntax or block/entity ID, and call `execute_commands` again.
            - The game silently intercepts errors so the player's chat is NOT spammed with red text while you correct yourself.
            """;

        String filled = String.format(base,
                player.getName().getString(),
                player.getX(), player.getY(), player.getZ(),
                player.level().dimension().location().toString(),
                player.gameMode.getGameModeForPlayer().getName()
        );

        if (config.customSystemPrompt != null && !config.customSystemPrompt.isBlank()) {
            filled += "\n\nAdditional Instructions:\n" + config.customSystemPrompt;
        }

        return filled;
    }
}
