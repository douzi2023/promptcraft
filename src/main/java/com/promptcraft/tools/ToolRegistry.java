package com.promptcraft.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.promptcraft.config.PromptCraftConfig;
import com.promptcraft.entity.DynamicCreatureEntity;
import com.promptcraft.entity.ModEntities;
import com.promptcraft.item.ModItems;
import com.promptcraft.minigame.ActiveMinigame;
import com.promptcraft.minigame.MinigameManager;
import com.promptcraft.minigame.WaveConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ToolRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("PromptCraft/Tools");
    private static final Gson GSON = new Gson();

    public static JsonArray getToolDefinitions() {
        JsonArray tools = new JsonArray();

        // 1. execute_commands
        tools.add(createTool(
                "execute_commands",
                "Execute one or multiple Minecraft server/vanilla commands (e.g. /fill, /setblock, /summon, /weather, /time, /effect, /give).",
                new JsonObjectBuilder()
                        .addArrayProperty("commands", "string", "List of Minecraft commands without leading slash, e.g. ['fill ~-3 ~ ~-3 ~3 ~3 ~3 oak_planks hollow', 'setblock ~ ~1 ~ torch']", true)
                        .build()
        ));

        // 2. create_dynamic_weapon
        tools.add(createTool(
                "create_dynamic_weapon",
                "Manufacture a custom dynamic weapon (gun or magic wand) with custom stats and projectile effects, putting it immediately into the player's hands.",
                new JsonObjectBuilder()
                        .addStringProperty("weapon_type", "Type of weapon: 'gun' (shoots bullets/tnt/fireballs/arrows) or 'wand' (casts spells like lightning/freeze/explosion/levitate)", true)
                        .addStringProperty("name", "Display name of the weapon, e.g. '§6烈焰火球加特林' or '§cTNT发射炮'", true)
                        .addArrayProperty("lore", "string", "List of lore/description lines for the weapon tooltip", false)
                        .addStringProperty("projectile_type", "For gun: 'tnt' (shoots actual primed TNT blocks!), 'fireball' (large ghast fireball), 'small_fireball' (blaze), 'wind_charge' (breeze blast), 'wither_skull', 'dragon_fireball', 'arrow', 'snowball', 'raycast' (instant laser)", false)
                        .addStringProperty("spell_type", "For wand: 'lightning', 'freeze', 'levitate', 'heal', 'explosion'", false)
                        .addNumberProperty("damage", "Attack damage for the weapon", false)
                        .addNumberProperty("power", "Spell power or explosion power", false)
                        .addNumberProperty("cooldown", "Cooldown in ticks between uses (20 ticks = 1 sec)", false)
                        .addNumberProperty("recoil", "Recoil push factor for guns (e.g. 0.2)", false)
                        .build()
        ));

        // 3. create_minigame
        tools.add(createTool(
                "create_minigame",
                "Create and immediately launch a live minigame (such as Zombie Siege / Tower Defense) with a BossBar, wave countdowns, and monsters attacking the player.",
                new JsonObjectBuilder()
                        .addStringProperty("title", "Title of the minigame, e.g. '僵尸要塞攻防战'", true)
                        .addArrayProperty("waves", "object", "List of waves with waveTitle and mobs array: [{waveTitle: 'Wave 1', mobs: [{entityId: 'minecraft:zombie', count: 8, armorSet: 'iron', mainHandItem: 'minecraft:iron_sword'}]}]", true)
                        .build()
        ));

        // 4. inspect_surroundings
        tools.add(createTool(
                "inspect_surroundings",
                "Inspect the player's current surroundings: coordinates, biome, time, weather, nearby blocks and nearby living entities.",
                new JsonObjectBuilder().build()
        ));

        // 5. inspect_mods
        tools.add(createTool(
                "inspect_mods",
                "Query all installed mods and search for registered items, blocks, and entities by keyword or namespace (for mod interoperability).",
                new JsonObjectBuilder()
                        .addStringProperty("keyword", "Keyword or namespace to search (e.g. 'create', 'sword', 'gun', 'iron', 'magic', 'energy')", true)
                        .build()
        ));

        // 6. create_custom_creature
        tools.add(createTool(
                "create_custom_creature",
                "Create and spawn a fully custom dynamic puppet creature with arbitrary scale, health, combat styles (melee, ranged laser, fireball, TNT barrage, kamikaze bomber, healer), auras, skins, and equipment.",
                new JsonObjectBuilder()
                        .addStringProperty("name", "Display name of the creature (e.g. '§6地狱泰坦巨人' or '§a守护天使')", true)
                        .addNumberProperty("scale", "Physical size scale of the entity from 0.2 to 10.0 (e.g. 0.5 for dwarf/pet, 1.0 normal, 3.5 for giant boss). Changes hitbox, reach, and model natively!", false)
                        .addNumberProperty("max_health", "Maximum health points (default 60.0)", false)
                        .addNumberProperty("attack_damage", "Base attack damage (default 10.0)", false)
                        .addNumberProperty("movement_speed", "Movement speed (0.1 slow to 0.6 very fast, default 0.32)", false)
                        .addStringProperty("combat_style", "Combat AI behavior: 'melee' (close combat), 'ranged_laser' (fires raycast beams), 'ranged_fireball' (shoots fireballs), 'ranged_tnt' (hurls primed TNT explosives), 'kamikaze_bomber' (charges at targets and self-destructs), 'healer_support' (follows owner and casts healing auras), 'guardian' (defends player)", false)
                        .addBooleanProperty("is_friendly", "Whether the creature is a friendly companion protecting the player (true) or an enemy monster attacking the player (false)", false)
                        .addStringProperty("skin_type", "Base model skin texture: 'zombie', 'skeleton', 'wither_skeleton', 'piglin', 'iron_golem', 'enderman', 'steve'", false)
                        .addStringProperty("particle", "Particle aura around creature: 'flame', 'soul_fire', 'portal', 'dragon_breath', 'enchant', 'heart', 'smoke'", false)
                        .addStringProperty("head_block", "Block or helmet equipped on head, e.g. 'minecraft:carved_pumpkin', 'minecraft:diamond_helmet', 'minecraft:tnt', 'minecraft:dragon_head'", false)
                        .addStringProperty("main_hand_item", "Weapon or item held in main hand, e.g. 'minecraft:netherite_sword', 'minecraft:blaze_rod', 'minecraft:bow'", false)
                        .addArrayProperty("skills", "string", "Special skill tags: 'teleport_on_hurt' (teleports when struck), 'summon_minions' (spawns wolves/zombies), 'death_explosion' (explodes on dying), 'regeneration' (heals over time)", false)
                        .addNumberProperty("spawn_count", "Number of creatures to summon (1 to 10, default 1)", false)
                        .build()
        ));

        return tools;
    }

    public static String executeTool(ServerPlayer player, String toolName, String argumentsJson) {
        try {
            JsonObject args = GSON.fromJson(argumentsJson, JsonObject.class);
            if (args == null) args = new JsonObject();

            return switch (toolName) {
                case "execute_commands" -> executeCommands(player, args);
                case "create_dynamic_weapon" -> createDynamicWeapon(player, args);
                case "create_minigame" -> createMinigame(player, args);
                case "inspect_surroundings" -> inspectSurroundings(player);
                case "inspect_mods" -> inspectMods(args);
                case "create_custom_creature" -> createCustomCreature(player, args);
                default -> "Unknown tool: " + toolName;
            };
        } catch (Exception e) {
            LOGGER.error("Failed to execute tool: " + toolName, e);
            return "Error executing tool: " + e.getMessage();
        }
    }

    private static String executeCommands(ServerPlayer player, JsonObject args) {
        if (!args.has("commands")) {
            return "Missing 'commands' array.";
        }

        JsonArray cmds = args.getAsJsonArray("commands");
        List<String> results = new ArrayList<>();
        boolean anyError = false;

        boolean safeMode = PromptCraftConfig.get().safeMode;
        List<String> forbidden = List.of("stop", "ban", "ban-ip", "pardon", "op", "deop", "whitelist");

        for (JsonElement elem : cmds) {
            String cmd = elem.getAsString().trim();
            if (cmd.startsWith("/")) cmd = cmd.substring(1);

            String baseCmd = cmd.split(" ")[0].toLowerCase();
            if (safeMode && forbidden.contains(baseCmd)) {
                results.add("Blocked dangerous command in Safe Mode: /" + cmd);
                continue;
            }

            CommandCaptureSource capture = new CommandCaptureSource();
            boolean[] successHolder = new boolean[]{true};

            CommandSourceStack source = player.createCommandSourceStack()
                    .withPermission(4) // Run with elevated permissions
                    .withSource(capture) // Intercept failure and success output silently
                    .withCallback((success, result) -> successHolder[0] = success);

            try {
                player.server.getCommands().performPrefixedCommand(source, cmd);

                String engineOutput = String.join("; ", capture.messages);
                if (!successHolder[0]) {
                    anyError = true;
                    results.add(String.format("FAILED: /%s -> Game error: '%s'. Please fix syntax/arguments/block ID and retry.", cmd, engineOutput.isEmpty() ? "Unknown command failure" : engineOutput));
                } else {
                    results.add(String.format("SUCCESS: /%s%s", cmd, engineOutput.isEmpty() ? "" : " (" + engineOutput + ")"));
                }
            } catch (Exception e) {
                anyError = true;
                results.add(String.format("EXCEPTION: /%s -> Error: %s", cmd, e.getMessage()));
            }
        }

        if (anyError) {
            player.sendSystemMessage(Component.literal("§e[PromptCraft] §7检测到指令异常，正在自动捕获报错并交由 AI 现场重写纠错..."));
        }

        return String.join("\n", results);
    }

    public static class CommandCaptureSource implements net.minecraft.commands.CommandSource {
        public final List<String> messages = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }

    private static String createDynamicWeapon(ServerPlayer player, JsonObject args) {
        String weaponType = args.has("weapon_type") ? args.get("weapon_type").getAsString() : "gun";
        String name = args.has("name") ? args.get("name").getAsString() : "§b动态特制武器";

        ItemStack stack;
        CompoundTag customTag = new CompoundTag();

        if ("wand".equalsIgnoreCase(weaponType)) {
            stack = new ItemStack(ModItems.DYNAMIC_WAND);
            customTag.putString("spell_type", args.has("spell_type") ? args.get("spell_type").getAsString() : "lightning");
            customTag.putFloat("power", args.has("power") ? args.get("power").getAsFloat() : 5.0f);
            customTag.putInt("cooldown", args.has("cooldown") ? args.get("cooldown").getAsInt() : 20);
        } else {
            stack = new ItemStack(ModItems.DYNAMIC_GUN);
            customTag.putString("projectile_type", args.has("projectile_type") ? args.get("projectile_type").getAsString() : "raycast");
            customTag.putFloat("damage", args.has("damage") ? args.get("damage").getAsFloat() : 15.0f);
            customTag.putFloat("speed", args.has("speed") ? args.get("speed").getAsFloat() : 3.0f);
            customTag.putInt("cooldown", args.has("cooldown") ? args.get("cooldown").getAsInt() : 6);
            customTag.putFloat("recoil", args.has("recoil") ? args.get("recoil").getAsFloat() : 0.2f);
            if (args.has("power")) {
                customTag.putFloat("explosion_power", args.get("power").getAsFloat());
            }
        }

        // Set Custom Data
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customTag));

        // Set Custom Name
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));

        // Set Lore
        if (args.has("lore")) {
            List<Component> loreLines = new ArrayList<>();
            for (JsonElement l : args.getAsJsonArray("lore")) {
                loreLines.add(Component.literal(l.getAsString()));
            }
            stack.set(DataComponents.LORE, new ItemLore(loreLines));
        }

        // Add enchantment glint to distinguish custom created weapons
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

        // Equip directly to player's main hand (safely move old item to inventory)
        ItemStack oldMainHand = player.getMainHandItem();
        if (!oldMainHand.isEmpty()) {
            player.getInventory().add(oldMainHand.copy());
        }
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        player.sendSystemMessage(Component.literal("§a[PromptCraft] 已将特制武器 §e" + name + " §a直接装备到你的主手！（原手持物品已收纳至背包）"));

        // Play feedback sound and particle
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1.0f, 1.2f);
        level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(),
                30, 0.5, 0.5, 0.5, 0.2);

        return "Successfully created weapon [" + name + "] and placed into player inventory!";
    }

    private static String createMinigame(ServerPlayer player, JsonObject args) {
        String title = args.has("title") ? args.get("title").getAsString() : "自定义小游戏";
        List<WaveConfig> waves = new ArrayList<>();

        if (args.has("waves")) {
            JsonArray waveArray = args.getAsJsonArray("waves");
            for (int i = 0; i < waveArray.size(); i++) {
                JsonObject waveObj = waveArray.get(i).getAsJsonObject();
                WaveConfig wave = new WaveConfig();
                wave.waveNumber = i + 1;
                wave.waveTitle = waveObj.has("waveTitle") ? waveObj.get("waveTitle").getAsString() : "第 " + (i + 1) + " 波";

                if (waveObj.has("mobs")) {
                    for (JsonElement mobElem : waveObj.getAsJsonArray("mobs")) {
                        JsonObject mobObj = mobElem.getAsJsonObject();
                        WaveConfig.MobSpawnInfo info = new WaveConfig.MobSpawnInfo();
                        info.entityId = mobObj.has("entityId") ? mobObj.get("entityId").getAsString() : "minecraft:zombie";
                        info.count = mobObj.has("count") ? mobObj.get("count").getAsInt() : 5;
                        info.healthMultiplier = mobObj.has("healthMultiplier") ? mobObj.get("healthMultiplier").getAsFloat() : 1.0f;
                        info.armorSet = mobObj.has("armorSet") ? mobObj.get("armorSet").getAsString() : "";
                        info.mainHandItem = mobObj.has("mainHandItem") ? mobObj.get("mainHandItem").getAsString() : "";
                        wave.mobs.add(info);
                    }
                }
                waves.add(wave);
            }
        }

        if (waves.isEmpty()) {
            // Default 2 waves
            WaveConfig w1 = new WaveConfig();
            w1.waveTitle = "先头僵尸小队";
            WaveConfig.MobSpawnInfo info1 = new WaveConfig.MobSpawnInfo();
            info1.entityId = "minecraft:zombie";
            info1.count = 6;
            w1.mobs.add(info1);
            waves.add(w1);
        }

        ActiveMinigame game = new ActiveMinigame(title, player, waves);
        MinigameManager.startMinigame(game);

        return "Minigame '" + title + "' launched successfully with " + waves.size() + " waves!";
    }

    private static String inspectSurroundings(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();

        // Count nearby living entities
        AABB box = new AABB(pos).inflate(24.0);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive());
        List<String> entityNames = nearby.stream()
                .map(e -> e.getType().getDescription().getString() + " (距离: " + (int) e.distanceTo(player) + "格)")
                .limit(10)
                .toList();

        return String.format("""
                Surroundings Report:
                - Dimension: %s
                - Coordinates: [X: %d, Y: %d, Z: %d]
                - Biome: %s
                - Time of Day: %d ticks (%s)
                - Weather: %s
                - Nearby Mobs (%d total): %s
                """,
                level.dimension().location(),
                pos.getX(), pos.getY(), pos.getZ(),
                level.getBiome(pos).unwrapKey().map(k -> k.location().toString()).orElse("unknown"),
                level.getDayTime() % 24000, (level.isDay() ? "白天" : "黑夜"),
                (level.isRaining() ? (level.isThundering() ? "雷雨" : "下雨") : "晴天"),
                nearby.size(), String.join(", ", entityNames)
        );
    }

    private static String inspectMods(JsonObject args) {
        String keyword = args.has("keyword") ? args.get("keyword").getAsString().toLowerCase() : "";
        List<String> matches = new ArrayList<>();

        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            String fullId = id.toString();
            if (keyword.isEmpty() || fullId.toLowerCase().contains(keyword)) {
                matches.add(fullId);
                if (matches.size() >= 30) break; // Limit to 30 items
            }
        }

        return "Found " + matches.size() + " matching registered items:\n" + String.join(", ", matches);
    }

    private static String createCustomCreature(ServerPlayer player, JsonObject args) {
        ServerLevel level = player.serverLevel();
        String name = args.has("name") ? args.get("name").getAsString() : "§b神秘创造生物";
        double scale = args.has("scale") ? Math.max(0.2, Math.min(10.0, args.get("scale").getAsDouble())) : 1.0;
        double maxHealth = args.has("max_health") ? Math.max(1.0, args.get("max_health").getAsDouble()) : 60.0;
        double attackDamage = args.has("attack_damage") ? Math.max(0.0, args.get("attack_damage").getAsDouble()) : 10.0;
        double speed = args.has("movement_speed") ? Math.max(0.05, Math.min(1.0, args.get("movement_speed").getAsDouble())) : 0.32;
        String combatStyle = args.has("combat_style") ? args.get("combat_style").getAsString() : "melee";
        boolean isFriendly = args.has("is_friendly") && args.get("is_friendly").getAsBoolean();
        String skinType = args.has("skin_type") ? args.get("skin_type").getAsString() : "zombie";
        String particle = args.has("particle") ? args.get("particle").getAsString() : "flame";
        String headItem = args.has("head_block") ? args.get("head_block").getAsString() : "";
        String mainHandItem = args.has("main_hand_item") ? args.get("main_hand_item").getAsString() : "";
        int count = args.has("spawn_count") ? Math.max(1, Math.min(10, args.get("spawn_count").getAsInt())) : 1;

        List<String> skillsList = new ArrayList<>();
        if (args.has("skills") && args.get("skills").isJsonArray()) {
            for (JsonElement e : args.getAsJsonArray("skills")) {
                skillsList.add(e.getAsString().toLowerCase());
            }
        }

        Vec3 look = player.getLookAngle();
        double spawnX = player.getX() + look.x * 3.0;
        double spawnY = player.getY();
        double spawnZ = player.getZ() + look.z * 3.0;

        for (int i = 0; i < count; i++) {
            DynamicCreatureEntity entity = new DynamicCreatureEntity(ModEntities.DYNAMIC_CREATURE, level);
            double offsetX = (count > 1) ? (player.getRandom().nextDouble() - 0.5) * 4.0 : 0.0;
            double offsetZ = (count > 1) ? (player.getRandom().nextDouble() - 0.5) * 4.0 : 0.0;
            entity.moveTo(spawnX + offsetX, spawnY, spawnZ + offsetZ, player.getYRot() + 180.0f, 0.0f);

            entity.setCustomName(Component.literal(name));
            entity.setCustomNameVisible(true);

            // Attributes
            var scaleAttr = entity.getAttribute(Attributes.SCALE);
            if (scaleAttr != null) scaleAttr.setBaseValue(scale);

            var healthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.setBaseValue(maxHealth);
                entity.setHealth((float) maxHealth);
            }

            var dmgAttr = entity.getAttribute(Attributes.ATTACK_DAMAGE);
            if (dmgAttr != null) dmgAttr.setBaseValue(attackDamage);

            var spdAttr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (spdAttr != null) spdAttr.setBaseValue(speed);

            // Behavior & Aesthetics
            entity.setCombatStyle(combatStyle);
            entity.setFriendly(isFriendly);
            if (isFriendly) {
                entity.setOwnerUuid(player.getUUID());
            }
            entity.setSkinType(skinType);
            entity.setParticleType(particle);

            for (String s : skillsList) {
                entity.addSkill(s);
            }

            // Equip items
            if (!headItem.isEmpty()) {
                BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(headItem)).ifPresent(item -> {
                    entity.setItemSlot(EquipmentSlot.HEAD, new ItemStack(item));
                });
            }
            if (!mainHandItem.isEmpty()) {
                BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(mainHandItem)).ifPresent(item -> {
                    entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
                });
            }

            level.addFreshEntity(entity);

            // Spawn visual effect
            level.sendParticles(ParticleTypes.EXPLOSION, entity.getX(), entity.getY() + 1.0, entity.getZ(), 3, 0.5, 0.5, 0.5, 0.0);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.PLAYERS, 1.0f, 1.2f);
        }

        player.sendSystemMessage(Component.literal(String.format(
                "§a[PromptCraft] §f已成功召唤出 %d 只自定义生物: §e%s §7(体型: %.1fx, 生命: %.0f, 攻击: %.0f, 风格: %s, 阵营: %s)",
                count, name, scale, maxHealth, attackDamage, combatStyle, isFriendly ? "友好卫士" : "敌对怪物"
        )));

        return String.format("Successfully spawned %d custom creature(s) named '%s' [Scale: %.1f, Health: %.0f, Combat: %s, Friendly: %b]",
                count, name, scale, maxHealth, combatStyle, isFriendly);
    }

    private static JsonObject createTool(String name, String description, JsonObject parameters) {
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        JsonObject fn = new JsonObject();
        fn.addProperty("name", name);
        fn.addProperty("description", description);
        fn.add("parameters", parameters);
        tool.add("function", fn);
        return tool;
    }

    private static class JsonObjectBuilder {
        private final JsonObject properties = new JsonObject();
        private final JsonArray required = new JsonArray();

        public JsonObjectBuilder addStringProperty(String name, String description, boolean isRequired) {
            JsonObject prop = new JsonObject();
            prop.addProperty("type", "string");
            prop.addProperty("description", description);
            properties.add(name, prop);
            if (isRequired) required.add(name);
            return this;
        }

        public JsonObjectBuilder addNumberProperty(String name, String description, boolean isRequired) {
            JsonObject prop = new JsonObject();
            prop.addProperty("type", "number");
            prop.addProperty("description", description);
            properties.add(name, prop);
            if (isRequired) required.add(name);
            return this;
        }

        public JsonObjectBuilder addBooleanProperty(String name, String description, boolean isRequired) {
            JsonObject prop = new JsonObject();
            prop.addProperty("type", "boolean");
            prop.addProperty("description", description);
            properties.add(name, prop);
            if (isRequired) required.add(name);
            return this;
        }

        public JsonObjectBuilder addArrayProperty(String name, String itemType, String description, boolean isRequired) {
            JsonObject prop = new JsonObject();
            prop.addProperty("type", "array");
            prop.addProperty("description", description);
            JsonObject items = new JsonObject();
            items.addProperty("type", itemType);
            prop.add("items", items);
            properties.add(name, prop);
            if (isRequired) required.add(name);
            return this;
        }

        public JsonObject build() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.add("required", required);
            return schema;
        }
    }
}
