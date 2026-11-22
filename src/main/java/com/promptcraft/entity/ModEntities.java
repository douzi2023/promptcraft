package com.promptcraft.entity;

import com.promptcraft.PromptCraft;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntities {
    public static final EntityType<DynamicCreatureEntity> DYNAMIC_CREATURE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(PromptCraft.MOD_ID, "dynamic_creature"),
            EntityType.Builder.of(DynamicCreatureEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.8f)
                    .build("dynamic_creature")
    );

    public static void register() {
        FabricDefaultAttributeRegistry.register(DYNAMIC_CREATURE, DynamicCreatureEntity.createAttributes());
    }
}
