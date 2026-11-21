package com.promptcraft.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class DynamicGunItem extends Item {

    public DynamicGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();

        int cooldown = tag.contains("cooldown") ? tag.getInt("cooldown") : 10;
        float damage = tag.contains("damage") ? tag.getFloat("damage") : 12.0f;
        float speed = tag.contains("speed") ? tag.getFloat("speed") : 3.0f;
        float recoil = tag.contains("recoil") ? tag.getFloat("recoil") : 0.2f;
        float explosionPower = tag.contains("explosion_power") ? tag.getFloat("explosion_power") : 0.0f;
        String projectileType = tag.contains("projectile_type") ? tag.getString("projectile_type") : "raycast";

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        player.getCooldowns().addCooldown(this, cooldown);

        // Sound effect
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.6f);

        // Recoil
        if (recoil > 0) {
            Vec3 look = player.getLookAngle();
            player.push(-look.x * recoil, -look.y * (recoil * 0.5f), -look.z * recoil);
        }

        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            Vec3 eyePos = player.getEyePosition();
            Vec3 lookVec = player.getLookAngle();

            switch (projectileType.toLowerCase()) {
                case "tnt", "primed_tnt" -> {
                    PrimedTnt tnt = new PrimedTnt(serverLevel, eyePos.x + lookVec.x * 1.5, eyePos.y + lookVec.y * 1.5, eyePos.z + lookVec.z * 1.5, player);
                    tnt.setDeltaMovement(lookVec.scale(speed > 0 ? speed * 0.6 : 1.4));
                    int fuse = tag.contains("fuse") ? tag.getInt("fuse") : 40; // 2 seconds fuse
                    tnt.setFuse(fuse);
                    serverLevel.addFreshEntity(tnt);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 1.0f, 1.0f);
                }
                case "wind_charge" -> {
                    WindCharge windCharge = new WindCharge(player, serverLevel, eyePos.x + lookVec.x * 1.2, eyePos.y + lookVec.y * 1.2, eyePos.z + lookVec.z * 1.2);
                    windCharge.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, speed * 1.5f, 1.0f);
                    serverLevel.addFreshEntity(windCharge);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.WIND_CHARGE_THROW, SoundSource.PLAYERS, 1.0f, 1.0f);
                }
                case "wither_skull" -> {
                    WitherSkull skull = new WitherSkull(serverLevel, player, lookVec.normalize());
                    skull.setPos(eyePos.x + lookVec.x * 1.5, eyePos.y + lookVec.y * 1.5, eyePos.z + lookVec.z * 1.5);
                    serverLevel.addFreshEntity(skull);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 1.0f, 1.0f);
                }
                case "dragon_fireball" -> {
                    DragonFireball dragonFireball = new DragonFireball(serverLevel, player, lookVec.normalize());
                    dragonFireball.setPos(eyePos.x + lookVec.x * 1.5, eyePos.y + lookVec.y * 1.5, eyePos.z + lookVec.z * 1.5);
                    serverLevel.addFreshEntity(dragonFireball);
                }
                case "fireball" -> {
                    LargeFireball fireball = new LargeFireball(serverLevel, player, lookVec.normalize(), (int) (explosionPower > 0 ? explosionPower : 2));
                    fireball.setPos(eyePos.x + lookVec.x * 1.5, eyePos.y + lookVec.y * 1.5, eyePos.z + lookVec.z * 1.5);
                    serverLevel.addFreshEntity(fireball);
                }
                case "small_fireball" -> {
                    SmallFireball smallFireball = new SmallFireball(serverLevel, eyePos.x + lookVec.x * 1.5, eyePos.y + lookVec.y * 1.5, eyePos.z + lookVec.z * 1.5, lookVec.normalize());
                    smallFireball.setOwner(player);
                    serverLevel.addFreshEntity(smallFireball);
                }
                case "arrow" -> {
                    Arrow arrow = new Arrow(serverLevel, player, stack, null);
                    arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, speed * 2.0f, 1.0f);
                    arrow.setBaseDamage(damage / 2.0);
                    serverLevel.addFreshEntity(arrow);
                }
                case "snowball" -> {
                    Snowball snowball = new Snowball(serverLevel, player);
                    snowball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, speed * 1.5f, 1.0f);
                    serverLevel.addFreshEntity(snowball);
                }
                default -> { // Raycast bullet (instant hit with particle tracer)
                    double maxDistance = 64.0;
                    Vec3 endPos = eyePos.add(lookVec.scale(maxDistance));

                    // Particle trail
                    double step = 0.5;
                    int count = (int) (maxDistance / step);
                    for (int i = 0; i < count; i++) {
                        Vec3 particlePoint = eyePos.add(lookVec.scale(i * step));
                        serverLevel.sendParticles(ParticleTypes.CRIT,
                                particlePoint.x, particlePoint.y, particlePoint.z,
                                1, 0, 0, 0, 0.0);
                    }

                    // Check entity hit
                    AABB searchBox = new AABB(eyePos, endPos).inflate(1.0);
                    List<Entity> entities = serverLevel.getEntities(player, searchBox,
                            e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator());

                    Entity hitEntity = null;
                    double closestDist = Double.MAX_VALUE;

                    for (Entity entity : entities) {
                        AABB entityBox = entity.getBoundingBox().inflate(0.3);
                        var hitOpt = entityBox.clip(eyePos, endPos);
                        if (hitOpt.isPresent()) {
                            double dist = eyePos.distanceTo(hitOpt.get());
                            if (dist < closestDist) {
                                closestDist = dist;
                                hitEntity = entity;
                            }
                        }
                    }

                    if (hitEntity != null) {
                        DamageSource damageSource = serverLevel.damageSources().playerAttack(player);
                        hitEntity.hurt(damageSource, damage);
                        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                                hitEntity.getX(), hitEntity.getY() + hitEntity.getBbHeight() / 2.0, hitEntity.getZ(),
                                3, 0.2, 0.2, 0.2, 0.05);

                        if (explosionPower > 0) {
                            serverLevel.explode(player, hitEntity.getX(), hitEntity.getY(), hitEntity.getZ(),
                                    explosionPower, Level.ExplosionInteraction.MOB);
                        }
                    }
                }
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();

        if (tag.contains("projectile_type")) {
            String pType = tag.getString("projectile_type");
            String displayType = switch (pType.toLowerCase()) {
                case "tnt", "primed_tnt" -> "§c🧨 点燃的 TNT 实体炸药";
                case "wind_charge" -> "§f🌪 旋风弹 (Wind Charge)";
                case "wither_skull" -> "§8💀 凋灵之首 (Wither Skull)";
                case "dragon_fireball" -> "§5🐉 龙息火球 (Dragon Fireball)";
                case "fireball" -> "§6🔥 恶魂火球 (Large Fireball)";
                case "small_fireball" -> "§e⚡ 烈焰火焰弹 (Small Fireball)";
                case "arrow" -> "§7🏹 强化箭矢 (Arrow)";
                case "snowball" -> "§f❄ 冰雪弹 (Snowball)";
                default -> "§b🎯 瞬发穿透激光 (Raycast Bullet)";
            };
            tooltipComponents.add(Component.literal("§e⚡ 弹药类型: " + displayType));
        }

        if (tag.contains("damage")) {
            tooltipComponents.add(Component.literal("§c⚔ 伤害: §f" + tag.getFloat("damage")));
        }
        if (tag.contains("cooldown")) {
            tooltipComponents.add(Component.literal("§9⏱ 射击间隔: §f" + tag.getInt("cooldown") + " ticks"));
        }
        if (tag.contains("recoil")) {
            tooltipComponents.add(Component.literal("§7💨 后坐力: §f" + tag.getFloat("recoil")));
        }
        if (tag.contains("explosion_power") && tag.getFloat("explosion_power") > 0) {
            tooltipComponents.add(Component.literal("§4💥 爆炸威力: §f" + tag.getFloat("explosion_power")));
        }
        tooltipComponents.add(Component.literal("§8[PromptCraft 动态生成]"));
    }
}
