package com.promptcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.promptcraft.config.PromptCraftConfig;
import com.promptcraft.llm.LLMClient;
import com.promptcraft.minigame.MinigameManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class PromptCraftCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("ai")
                // 1. /ai stopgame
                .then(Commands.literal("stopgame")
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        MinigameManager.stopGameForPlayer(player, "玩家手动中止");
                        player.sendSystemMessage(Component.literal("§a[PromptCraft] 已中止当前进行的小游戏。"));
                        return 1;
                    })
                )
                // 2. /ai config ...
                .then(Commands.literal("config")
                    .then(Commands.literal("key")
                        .then(Commands.argument("apiKey", StringArgumentType.string())
                            .executes(context -> {
                                String key = StringArgumentType.getString(context, "apiKey");
                                PromptCraftConfig.get().apiKey = key;
                                PromptCraftConfig.save();
                                context.getSource().sendSuccess(() -> Component.literal("§a[PromptCraft] API Key 已成功保存！"), false);
                                return 1;
                            })
                        )
                    )
                    .then(Commands.literal("url")
                        .then(Commands.argument("apiUrl", StringArgumentType.greedyString())
                            .executes(context -> {
                                String url = StringArgumentType.getString(context, "apiUrl");
                                PromptCraftConfig.get().apiUrl = url;
                                PromptCraftConfig.save();
                                context.getSource().sendSuccess(() -> Component.literal("§a[PromptCraft] API URL 已更新为: " + url), false);
                                return 1;
                            })
                        )
                    )
                    .then(Commands.literal("model")
                        .then(Commands.argument("modelName", StringArgumentType.string())
                            .executes(context -> {
                                String model = StringArgumentType.getString(context, "modelName");
                                PromptCraftConfig.get().model = model;
                                PromptCraftConfig.save();
                                context.getSource().sendSuccess(() -> Component.literal("§a[PromptCraft] 模型已切换为: " + model), false);
                                return 1;
                            })
                        )
                    )
                    .executes(context -> {
                        PromptCraftConfig cfg = PromptCraftConfig.get();
                        String maskedKey = (cfg.apiKey == null || cfg.apiKey.isBlank())
                                ? "§c未设置" : "§a已设置 (" + cfg.apiKey.substring(0, Math.min(6, cfg.apiKey.length())) + "...)";
                        context.getSource().sendSuccess(() -> Component.literal(String.format("""
                                §6§l--- PromptCraft 配置 ---
                                §7API Key: %s
                                §7API URL: §b%s
                                §7Model: §e%s
                                §7Safe Mode: %s
                                """, maskedKey, cfg.apiUrl, cfg.model, (cfg.safeMode ? "§a开启" : "§c关闭"))), false);
                        return 1;
                    })
                )
                // 3. /ai <prompt...>
                .then(Commands.argument("prompt", StringArgumentType.greedyString())
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        String prompt = StringArgumentType.getString(context, "prompt");
                        player.sendSystemMessage(Component.literal("§e[你] §f" + prompt));
                        LLMClient.processPlayerPrompt(player, prompt);
                        return 1;
                    })
                )
        );
    }
}
