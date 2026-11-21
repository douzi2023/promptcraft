package com.promptcraft.minigame;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class MinigameManager {
    private static final List<ActiveMinigame> ACTIVE_GAMES = new ArrayList<>();

    public static void startMinigame(ActiveMinigame game) {
        // Stop any existing game for this player
        stopGameForPlayer(game.getPlayer(), "开启了新的小游戏");
        ACTIVE_GAMES.add(game);
    }

    public static void tick(MinecraftServer server) {
        Iterator<ActiveMinigame> it = ACTIVE_GAMES.iterator();
        while (it.hasNext()) {
            ActiveMinigame game = it.next();
            if (game.isFinished()) {
                it.remove();
            } else {
                game.tick();
            }
        }
    }

    public static void stopGameForPlayer(ServerPlayer player, String reason) {
        UUID playerId = player.getUUID();
        for (ActiveMinigame game : ACTIVE_GAMES) {
            if (game.getPlayer().getUUID().equals(playerId)) {
                game.stop(reason);
            }
        }
        ACTIVE_GAMES.removeIf(ActiveMinigame::isFinished);
    }

    public static void stopAll(MinecraftServer server) {
        for (ActiveMinigame game : ACTIVE_GAMES) {
            game.stop("服务器重置/管理员手动终止");
        }
        ACTIVE_GAMES.clear();
    }
}
