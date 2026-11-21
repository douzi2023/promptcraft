package com.promptcraft.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class DynamicWandItem extends Item {

    public DynamicWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();

        int cooldown = tag.contains("cooldown") ? tag.getInt("cooldown") : 20;
        String spellType = tag.contains("spell_type") ? tag.getString("spell_type") : "lightning";
        float power = tag.contains("power") ? tag.getFloat("power") : 5.0f;

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        player.getCooldowns().addCooldown(this, cooldown);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.2f);

        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            Vec3 eyePos = player.getEyePosition();
            Vec3 lookVec = player.getLookAngle();
            Vec3 targetPos = eyePos.add(lookVec.scale(48.0));

            BlockHitResult blockHit = serverLevel.clip(new ClipContext(eyePos, targetPos,
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

            Vec3 hitVec = blockHit.getType() != HitResult.Type.MISS ? blockHit.getLocation() : targetPos;
            BlockPos targetBlockPos = BlockPos.containing(hitVec);

            switch (spellType.toLowerCase()) {
                case "lightning" -> {
                    LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
                    if (lightning != null) {
                        lightning.moveTo(hitVec);
                        serverLevel.addFreshEntity(lightning);
                    }
                }
                case "freeze" -> {
                    AABB box = new AABB(hitVec.add(-power, -power, -power), hitVec.add(power, power, power));
                    List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive());
                    for (LivingEntity target : targets) {
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 4));
                        target.setTicksFrozen(200);
                        serverLevel.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + 1, target.getZ(),
                                20, 0.5, 0.5, 0.5, 0.1);
                    }
                }
                case "levitate" -> {
                    AABB box = new AABB(hitVec.add(-power, -power, -power), hitVec.add(power, power, power));
                    List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive());
                    for (LivingEntity target : targets) {
                        target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 60, 2));
                        serverLevel.sendParticles(ParticleTypes.PORTAL, target.getX(), target.getY() + 1, target.getZ(),
                                25, 0.5, 0.5, 0.5, 0.2);
                    }
                }
                case "heal" -> {
                    player.heal(power * 2.0f);
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
                    serverLevel.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1, player.getZ(),
                            10, 0.4, 0.4, 0.4, 0.1);
                }
                default -> { // Arcane explosion
                    serverLevel.explode(player, hitVec.x, hitVec.y, hitVec.z, power, Level.ExplosionInteraction.MOB);
                    serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, hitVec.x, hitVec.y, hitVec.z,
                            40, 0.8, 0.8, 0.8, 0.15);
                }
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();

        if (tag.contains("spell_type")) {
            tooltipComponents.add(Component.literal("§d🔮 法术: §f" + tag.getString("spell_type")));
        }
        if (tag.contains("power")) {
            tooltipComponents.add(Component.literal("§b✨ 法力强度: §f" + tag.getFloat("power")));
        }
        if (tag.contains("cooldown")) {
            tooltipComponents.add(Component.literal("§9⏱ 冷却时间: §f" + tag.getInt("cooldown") + " ticks"));
        }
        tooltipComponents.add(Component.literal("§8[PromptCraft 魔法工坊]"));
    }
}
