package com.promptcraft.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class DynamicCreatureEntity extends PathfinderMob {

    private static final EntityDataAccessor<String> SKIN_TYPE =
            SynchedEntityData.defineId(DynamicCreatureEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> PARTICLE_TYPE =
            SynchedEntityData.defineId(DynamicCreatureEntity.class, EntityDataSerializers.STRING);

    private String combatStyle = "melee";
    private boolean isFriendly = false;
    private UUID ownerUuid = null;
    private final Set<String> skills = new HashSet<>();

    private int attackCooldown = 0;
    private int minionCooldown = 0;
    private int healCooldown = 0;
    private int kamikazeFuse = -1;

    public DynamicCreatureEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.SCALE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN_TYPE, "zombie");
        builder.define(PARTICLE_TYPE, "flame");
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new DynamicCombatGoal(this));
        this.goalSelector.addGoal(2, new DynamicSupportGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new DynamicTargetGoal(this));
    }

    public String getSkinType() {
        return this.entityData.get(SKIN_TYPE);
    }

    public void setSkinType(String skinType) {
        this.entityData.set(SKIN_TYPE, skinType != null ? skinType : "zombie");
    }

    public String getParticleType() {
        return this.entityData.get(PARTICLE_TYPE);
    }

    public void setParticleType(String particleType) {
        this.entityData.set(PARTICLE_TYPE, particleType != null ? particleType : "");
    }

    public String getCombatStyle() {
        return combatStyle;
    }

    public void setCombatStyle(String style) {
        this.combatStyle = style != null ? style.toLowerCase() : "melee";
    }

    public boolean isFriendly() {
        return isFriendly;
    }

    public void setFriendly(boolean friendly) {
        this.isFriendly = friendly;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public Set<String> getSkills() {
        return skills;
    }

    public void addSkill(String skill) {
        if (skill != null && !skill.isBlank()) {
            this.skills.add(skill.toLowerCase());
        }
    }

    public ResourceLocation getSkinTexture() {
        String skin = getSkinType().toLowerCase();
        return switch (skin) {
            case "skeleton" -> ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/skeleton/skeleton.png");
            case "wither_skeleton" -> ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/skeleton/wither_skeleton.png");
            case "piglin" -> ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/piglin/piglin.png");
            case "iron_golem" -> ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/iron_golem/iron_golem.png");
            case "enderman" -> ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/enderman/enderman.png");
            case "steve" -> ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png");
            default -> ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/zombie/zombie.png");
        };
    }

    @Override
    public void tick() {
        super.tick();

        Level currentLevel = this.level();

        // 1. Client particle aura
        if (currentLevel.isClientSide()) {
            String pType = getParticleType().toLowerCase();
            if (!pType.isEmpty() && random.nextFloat() < 0.6f) {
                double px = getX() + (random.nextDouble() - 0.5) * getBbWidth() * 1.2;
                double py = getY() + random.nextDouble() * getBbHeight();
                double pz = getZ() + (random.nextDouble() - 0.5) * getBbWidth() * 1.2;

                switch (pType) {
                    case "flame" -> currentLevel.addParticle(ParticleTypes.FLAME, px, py, pz, 0, 0.02, 0);
                    case "soul_fire" -> currentLevel.addParticle(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 0, 0.02, 0);
                    case "portal" -> currentLevel.addParticle(ParticleTypes.PORTAL, px, py, pz, (random.nextDouble() - 0.5) * 0.2, (random.nextDouble() - 0.5) * 0.2, (random.nextDouble() - 0.5) * 0.2);
                    case "dragon_breath" -> currentLevel.addParticle(ParticleTypes.DRAGON_BREATH, px, py, pz, 0, 0.01, 0);
                    case "enchant" -> currentLevel.addParticle(ParticleTypes.ENCHANT, px, py, pz, (random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.5);
                    case "heart" -> currentLevel.addParticle(ParticleTypes.HEART, px, py, pz, 0, 0.02, 0);
                    case "smoke" -> currentLevel.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, py, pz, 0, 0.03, 0);
                }
            }
        }

        // 2. Server side logic
        if (!currentLevel.isClientSide() && currentLevel instanceof ServerLevel serverLevel) {
            // Regeneration skill
            if (skills.contains("regeneration") && tickCount % 35 == 0 && getHealth() < getMaxHealth()) {
                heal(2.0f);
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + getBbHeight() / 2, getZ(),
                        4, 0.3, 0.3, 0.3, 0.05);
            }

            // Summon minions skill
            if (skills.contains("summon_minions") && getTarget() != null && getTarget().isAlive()) {
                if (minionCooldown > 0) minionCooldown--;
                if (minionCooldown <= 0) {
                    minionCooldown = 160; // Every 8 seconds
                    for (int i = 0; i < 2; i++) {
                        EntityType<?> minionType = isFriendly ? EntityType.WOLF : EntityType.ZOMBIE;
                        Entity minion = minionType.create(serverLevel);
                        if (minion != null) {
                            double mx = getX() + (random.nextDouble() - 0.5) * 4.0;
                            double mz = getZ() + (random.nextDouble() - 0.5) * 4.0;
                            minion.moveTo(mx, getY(), mz, random.nextFloat() * 360, 0);
                            serverLevel.addFreshEntity(minion);
                            serverLevel.sendParticles(ParticleTypes.POOF, mx, getY() + 0.5, mz, 10, 0.2, 0.2, 0.2, 0.05);
                        }
                    }
                    serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.0f, 1.2f);
                }
            }

            // Kamikaze fuse ticking
            if (kamikazeFuse >= 0) {
                kamikazeFuse--;
                serverLevel.sendParticles(ParticleTypes.SMOKE, getX(), getY() + getBbHeight(), getZ(), 3, 0.1, 0.1, 0.1, 0.02);
                if (kamikazeFuse <= 0) {
                    serverLevel.explode(this, getX(), getY() + getBbHeight() / 2, getZ(), 3.5f, Level.ExplosionInteraction.MOB);
                    this.discard();
                }
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (result && !this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
            // Teleport on hurt skill
            if (skills.contains("teleport_on_hurt") && random.nextFloat() < 0.45f) {
                double targetX = getX() + (random.nextDouble() - 0.5) * 16.0;
                double targetZ = getZ() + (random.nextDouble() - 0.5) * 16.0;
                double targetY = getY() + (random.nextDouble() - 0.5) * 6.0;
                teleportTo(targetX, targetY, targetZ);
                serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.0f, 1.0f);
                serverLevel.sendParticles(ParticleTypes.PORTAL, getX(), getY() + getBbHeight() / 2, getZ(), 30, 0.5, 0.5, 0.5, 0.2);
            }
        }
        return result;
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);
        if (!this.level().isClientSide() && skills.contains("death_explosion")) {
            this.level().explode(this, getX(), getY() + getBbHeight() / 2, getZ(), 3.0f, Level.ExplosionInteraction.MOB);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("skin_type", getSkinType());
        tag.putString("particle_type", getParticleType());
        tag.putString("combat_style", combatStyle);
        tag.putBoolean("is_friendly", isFriendly);
        if (ownerUuid != null) {
            tag.putUUID("owner_uuid", ownerUuid);
        }
        ListTag skillsList = new ListTag();
        for (String s : skills) {
            skillsList.add(StringTag.valueOf(s));
        }
        tag.put("skills", skillsList);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("skin_type")) setSkinType(tag.getString("skin_type"));
        if (tag.contains("particle_type")) setParticleType(tag.getString("particle_type"));
        if (tag.contains("combat_style")) setCombatStyle(tag.getString("combat_style"));
        if (tag.contains("is_friendly")) setFriendly(tag.getBoolean("is_friendly"));
        if (tag.hasUUID("owner_uuid")) setOwnerUuid(tag.getUUID("owner_uuid"));

        skills.clear();
        if (tag.contains("skills", Tag.TAG_LIST)) {
            ListTag list = tag.getList("skills", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                skills.add(list.getString(i));
            }
        }
    }

    // --- Dynamic AI Goals ---

    private static class DynamicCombatGoal extends Goal {
        private final DynamicCreatureEntity mob;
        private int attackTimer = 0;

        public DynamicCombatGoal(DynamicCreatureEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = mob.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null || !target.isAlive()) return;

            mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
            double distSq = mob.distanceToSqr(target);
            String style = mob.getCombatStyle();

            switch (style) {
                case "kamikaze_bomber" -> {
                    mob.getNavigation().moveTo(target, 1.4);
                    if (distSq <= 9.0 && mob.kamikazeFuse < 0) { // within 3 blocks
                        mob.kamikazeFuse = 25; // 1.25s countdown
                        mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                                SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 1.2f, 1.0f);
                    }
                }
                case "ranged_laser" -> {
                    double dist = Math.sqrt(distSq);
                    if (dist > 14.0) mob.getNavigation().moveTo(target, 1.1);
                    else if (dist < 6.0) mob.getNavigation().moveTo(target, -1.0);
                    else mob.getNavigation().stop();

                    attackTimer++;
                    if (attackTimer >= 22) {
                        attackTimer = 0;
                        if (mob.level() instanceof ServerLevel serverLevel) {
                            Vec3 eye = mob.getEyePosition();
                            Vec3 targetEye = target.getEyePosition();
                            Vec3 dir = targetEye.subtract(eye);
                            double steps = dir.length() / 0.5;
                            for (int i = 0; i < (int) steps; i++) {
                                Vec3 pt = eye.add(dir.scale((double) i / steps));
                                serverLevel.sendParticles(ParticleTypes.END_ROD, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
                            }
                            serverLevel.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                                    SoundEvents.GUARDIAN_ATTACK, SoundSource.HOSTILE, 1.0f, 1.6f);
                            float dmg = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
                            target.hurt(mob.damageSources().mobAttack(mob), dmg);
                        }
                    }
                }
                case "ranged_fireball" -> {
                    double dist = Math.sqrt(distSq);
                    if (dist > 15.0) mob.getNavigation().moveTo(target, 1.0);
                    else if (dist < 7.0) mob.getNavigation().moveTo(target, -1.0);
                    else mob.getNavigation().stop();

                    attackTimer++;
                    if (attackTimer >= 35) {
                        attackTimer = 0;
                        if (mob.level() instanceof ServerLevel serverLevel) {
                            Vec3 eye = mob.getEyePosition();
                            Vec3 dir = target.getEyePosition().subtract(eye).normalize();
                            SmallFireball ball = new SmallFireball(serverLevel, eye.x, eye.y, eye.z, dir);
                            ball.setOwner(mob);
                            serverLevel.addFreshEntity(ball);
                            serverLevel.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                                    SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0f, 1.0f);
                        }
                    }
                }
                case "ranged_tnt" -> {
                    double dist = Math.sqrt(distSq);
                    if (dist > 14.0) mob.getNavigation().moveTo(target, 1.0);
                    else mob.getNavigation().stop();

                    attackTimer++;
                    if (attackTimer >= 50) {
                        attackTimer = 0;
                        if (mob.level() instanceof ServerLevel serverLevel) {
                            Vec3 eye = mob.getEyePosition();
                            Vec3 dir = target.getEyePosition().subtract(eye).normalize();
                            PrimedTnt tnt = new PrimedTnt(serverLevel, eye.x + dir.x, eye.y + dir.y, eye.z + dir.z, mob);
                            tnt.setDeltaMovement(dir.scale(1.1));
                            tnt.setFuse(40);
                            serverLevel.addFreshEntity(tnt);
                            serverLevel.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                                    SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 1.0f, 1.0f);
                        }
                    }
                }
                default -> { // Melee / Guardian
                    mob.getNavigation().moveTo(target, 1.25);
                    double reachSq = (mob.getBbWidth() * 2.0f * mob.getBbWidth() * 2.0f + target.getBbWidth());
                    attackTimer++;
                    if (distSq <= reachSq && attackTimer >= 15) {
                        attackTimer = 0;
                        mob.doHurtTarget(target);
                        mob.swing(InteractionHand.MAIN_HAND);
                    }
                }
            }
        }
    }

    private static class DynamicSupportGoal extends Goal {
        private final DynamicCreatureEntity mob;
        private int healTimer = 0;

        public DynamicSupportGoal(DynamicCreatureEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return mob.isFriendly() && mob.getOwnerUuid() != null;
        }

        @Override
        public void tick() {
            if (mob.level() instanceof ServerLevel serverLevel) {
                ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(mob.getOwnerUuid());
                if (owner == null || !owner.isAlive()) return;

                double dist = mob.distanceTo(owner);
                if (dist > 5.0) {
                    mob.getNavigation().moveTo(owner, 1.2);
                }

                if (mob.getCombatStyle().equals("healer_support") || mob.getSkills().contains("healer")) {
                    healTimer++;
                    if (healTimer >= 35 && owner.getHealth() < owner.getMaxHealth() && dist < 10.0) {
                        healTimer = 0;
                        owner.heal(4.0f);
                        serverLevel.sendParticles(ParticleTypes.HEART, owner.getX(), owner.getY() + 1.2, owner.getZ(), 8, 0.4, 0.4, 0.4, 0.1);
                        serverLevel.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.5f);
                    }
                }
            }
        }
    }

    private static class DynamicTargetGoal extends TargetGoal {
        private final DynamicCreatureEntity mob;

        public DynamicTargetGoal(DynamicCreatureEntity mob) {
            super(mob, true);
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (mob.getTarget() != null && mob.getTarget().isAlive()) return false;

            if (mob.isFriendly()) {
                // Target nearby hostile monsters
                AABB box = mob.getBoundingBox().inflate(20.0);
                List<Monster> hostiles = mob.level().getEntitiesOfClass(Monster.class, box, e -> e.isAlive() && e.getId() != mob.getId());
                if (!hostiles.isEmpty()) {
                    mob.setTarget(hostiles.get(0));
                    return true;
                }
            } else {
                // Target nearby players
                Player nearest = mob.level().getNearestPlayer(mob, 20.0);
                if (nearest != null && nearest.isAlive() && !nearest.isCreative() && !nearest.isSpectator()) {
                    mob.setTarget(nearest);
                    return true;
                }
            }
            return false;
        }
    }
}
