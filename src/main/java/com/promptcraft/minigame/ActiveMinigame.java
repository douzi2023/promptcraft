package com.promptcraft.minigame;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ActiveMinigame {
    private static final Logger LOGGER = LoggerFactory.getLogger("PromptCraft/Minigame");

    private final UUID gameId = UUID.randomUUID();
    private final String gameTitle;
    private final ServerPlayer player;
    private final ServerLevel level;
    private final List<WaveConfig> waves;
    private final ServerBossEvent bossEvent;

    private int currentWaveIndex = -1;
    private final List<UUID> aliveMobUuids = new ArrayList<>();
    private int waveTotalMobs = 0;
    private int intermissionCountdown = 100; // 5 seconds before wave 1 starts
    private boolean isFinished = false;

    public ActiveMinigame(String title, ServerPlayer player, List<WaveConfig> waves) {
        this.gameTitle = title;
        this.player = player;
        this.level = player.serverLevel();
        this.waves = waves;

        this.bossEvent = new ServerBossEvent(
                Component.literal("§6§l[ " + title + " - 准备阶段 ]"),
                BossEvent.BossBarColor.YELLOW,
                BossEvent.BossBarOverlay.PROGRESS
        );
        this.bossEvent.addPlayer(player);
        this.bossEvent.setVisible(true);

        player.sendSystemMessage(Component.literal("§a[PromptCraft] 小游戏 §e" + title + " §a已开启！战斗将在 5 秒后打响！"));
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    public UUID getGameId() {
        return gameId;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public boolean isFinished() {
        return isFinished;
    }

    public void tick() {
        if (isFinished) return;

        if (!player.isAlive()) {
            defeat("玩家已阵亡！");
            return;
        }

        if (intermissionCountdown > 0) {
            intermissionCountdown--;
            int secondsLeft = (intermissionCountdown / 20) + 1;
            bossEvent.setName(Component.literal("§e" + gameTitle + " - 下一波倒计时: " + secondsLeft + "s"));
            bossEvent.setProgress((float) intermissionCountdown / 100.0f);

            if (intermissionCountdown == 0) {
                startNextWave();
            }
            return;
        }

        // Active wave processing
        aliveMobUuids.removeIf(uuid -> {
            Entity entity = level.getEntity(uuid);
            return entity == null || !entity.isAlive();
        });

        int remaining = aliveMobUuids.size();
        if (waveTotalMobs > 0) {
            float progress = Math.max(0.0f, Math.min(1.0f, (float) remaining / (float) waveTotalMobs));
            bossEvent.setProgress(progress);
        }

        if (currentWaveIndex >= 0 && currentWaveIndex < waves.size()) {
            WaveConfig currentWave = waves.get(currentWaveIndex);
            bossEvent.setName(Component.literal(String.format("§c%s - 第 %d/%d 波: %s (剩余: %d/%d)",
                    gameTitle, (currentWaveIndex + 1), waves.size(), currentWave.waveTitle, remaining, waveTotalMobs)));
        }

        // Wave cleared
        if (remaining == 0) {
            if (currentWaveIndex + 1 >= waves.size()) {
                victory();
            } else {
                player.sendSystemMessage(Component.literal("§a[PromptCraft] 第 " + (currentWaveIndex + 1) + " 波防守成功！下一波即将到来..."));
                player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.2f);
                intermissionCountdown = 100; // 5s break
                bossEvent.setColor(BossEvent.BossBarColor.YELLOW);
            }
        }
    }

    private void startNextWave() {
        currentWaveIndex++;
        if (currentWaveIndex >= waves.size()) {
            victory();
            return;
        }

        WaveConfig wave = waves.get(currentWaveIndex);
        bossEvent.setColor(BossEvent.BossBarColor.RED);
        aliveMobUuids.clear();
        waveTotalMobs = 0;

        player.sendSystemMessage(Component.literal("§c§l[PromptCraft] 第 " + (currentWaveIndex + 1) + " 波来袭: " + wave.waveTitle + "！"));
        player.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.5f, 1.0f);

        Random random = new Random();
        BlockPos playerPos = player.blockPosition();

        for (WaveConfig.MobSpawnInfo info : wave.mobs) {
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.tryParse(info.entityId));
            if (entityType == null) {
                entityType = EntityType.ZOMBIE;
            }

            for (int i = 0; i < info.count; i++) {
                // Spawn in radius 15-25 around player
                double angle = random.nextDouble() * 2 * Math.PI;
                double distance = 14 + random.nextDouble() * 10;
                int x = (int) (playerPos.getX() + Math.cos(angle) * distance);
                int z = (int) (playerPos.getZ() + Math.sin(angle) * distance);
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

                Entity entity = entityType.create(level);
                if (entity != null) {
                    entity.moveTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0f, 0.0f);

                    if (entity instanceof LivingEntity living) {
                        if (info.healthMultiplier > 1.0f && living.getAttributes().hasAttribute(Attributes.MAX_HEALTH)) {
                            double newMax = living.getMaxHealth() * info.healthMultiplier;
                            living.getAttribute(Attributes.MAX_HEALTH).setBaseValue(newMax);
                            living.setHealth((float) newMax);
                        }

                        // Equip gear if specified
                        equipMob(living, info);
                    }

                    level.addFreshEntity(entity);
                    aliveMobUuids.add(entity.getUUID());
                    waveTotalMobs++;
                }
            }
        }
    }

    private void equipMob(LivingEntity living, WaveConfig.MobSpawnInfo info) {
        if ("iron".equalsIgnoreCase(info.armorSet)) {
            living.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            living.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            living.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            living.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        } else if ("diamond".equalsIgnoreCase(info.armorSet)) {
            living.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            living.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
            living.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
            living.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        }

        if (!info.mainHandItem.isBlank()) {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(info.mainHandItem));
            if (item != null) {
                living.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
            }
        }
    }

    public void stop(String reason) {
        if (isFinished) return;
        isFinished = true;
        cleanup();
        player.sendSystemMessage(Component.literal("§e[PromptCraft] 小游戏已停止: " + reason));
    }

    private void victory() {
        isFinished = true;
        bossEvent.setColor(BossEvent.BossBarColor.GREEN);
        bossEvent.setName(Component.literal("§a§l🏆 " + gameTitle + " - 全胜通关！"));
        bossEvent.setProgress(1.0f);

        player.sendSystemMessage(Component.literal("§6§l========================================"));
        player.sendSystemMessage(Component.literal("§a§l恭喜通关！你成功抵挡了所有攻城波次！"));
        player.sendSystemMessage(Component.literal("§6§l========================================"));
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);

        // Give reward
        player.giveExperiencePoints(500);
        player.getInventory().add(new ItemStack(Items.DIAMOND, 5));

        cleanup();
    }

    private void defeat(String reason) {
        isFinished = true;
        bossEvent.setColor(BossEvent.BossBarColor.RED);
        bossEvent.setName(Component.literal("§4§l💀 " + gameTitle + " - 战败 (" + reason + ")"));
        player.sendSystemMessage(Component.literal("§c[PromptCraft] 游戏失败: " + reason));
        cleanup();
    }

    private void cleanup() {
        bossEvent.removeAllPlayers();
        bossEvent.setVisible(false);

        // Remove leftover spawned wave mobs
        for (UUID uuid : aliveMobUuids) {
            Entity entity = level.getEntity(uuid);
            if (entity != null && entity.isAlive()) {
                entity.discard();
            }
        }
        aliveMobUuids.clear();
    }
}
