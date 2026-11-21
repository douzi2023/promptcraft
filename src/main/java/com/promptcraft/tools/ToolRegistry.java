package com.promptcraft.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.promptcraft.config.PromptCraftConfig;
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
import net.minecraft.world.entity.LivingEntity;
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
                        .addStringProperty("weapon_type", "Type of weapon: 'gun' (shoots bullets/fireballs/arrows) or 'wand' (casts spells like lightning/freeze/explosion/levitate)", true)
                        .addStringProperty("name", "Display name of the weapon, e.g. '§6烈焰火球加特林'", true)
                        .addArrayProperty("lore", "string", "List of lore/description lines for the weapon tooltip", false)
                        .addStringProperty("projectile_type", "For gun: 'raycast' (bullet), 'fireball', 'small_fireball', 'arrow', 'snowball'", false)
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
        CommandSourceStack source = player.createCommandSourceStack().withPermission(4); // Run with elevated permissions

        boolean safeMode = PromptCraftConfig.get().safeMode;
        List<String> forbidden = List.of("stop", "ban", "ban-ip", "pardon", "op", "deop", "whitelist");

        for (JsonElement elem : cmds) {
            String cmd = elem.getAsString().trim();
            if (cmd.startsWith("/")) cmd = cmd.substring(1);

            String baseCmd = cmd.split(" ")[0].toLowerCase();
            if (safeMode && forbidden.contains(baseCmd)) {
                results.add("Blocked dangerous command in Safe Mode: " + cmd);
                continue;
            }

            try {
                player.server.getCommands().performPrefixedCommand(source, cmd);
                results.add("Successfully executed: /" + cmd);
            } catch (Exception e) {
                results.add("Failed executing /" + cmd + ": " + e.getMessage());
            }
        }

        return String.join("\n", results);
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

        // Give to player
        if (player.getMainHandItem().isEmpty()) {
            player.setItemInHand(player.getUsedItemHand(), stack);
        } else {
            player.getInventory().add(stack);
        }

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
