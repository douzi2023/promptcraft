package com.promptcraft.item;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public class ModItems {
    public static final String MOD_ID = "promptcraft";

    public static final Item DYNAMIC_GUN = new DynamicGunItem(new Item.Properties()
            .stacksTo(1)
            .rarity(Rarity.EPIC)
            .durability(1000));

    public static final Item DYNAMIC_WAND = new DynamicWandItem(new Item.Properties()
            .stacksTo(1)
            .rarity(Rarity.RARE)
            .durability(500));

    public static void register() {
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(MOD_ID, "dynamic_gun"), DYNAMIC_GUN);
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(MOD_ID, "dynamic_wand"), DYNAMIC_WAND);

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> {
            entries.accept(DYNAMIC_GUN);
            entries.accept(DYNAMIC_WAND);
        });
    }
}
