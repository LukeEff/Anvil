package net.minecraft.world.entity;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import io.github.lukeeff.event.EventManager;
import io.github.lukeeff.event.events.EffectAddedEvent;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddMobPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEquippedItemPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.EntityDamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.Brain.Provider;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.FrostWalkerEnchantment;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.scores.PlayerTeam;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;

public abstract class LivingEntity extends Entity {
    private static final UUID SPEED_MODIFIER_SPRINTING_UUID = UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D");
    private static final UUID SPEED_MODIFIER_SOUL_SPEED_UUID = UUID.fromString("87f46a96-686f-4796-b035-22e16ee9e038");
    private static final AttributeModifier SPEED_MODIFIER_SPRINTING;
    protected static final EntityDataAccessor<Byte> DATA_LIVING_ENTITY_FLAGS;
    private static final EntityDataAccessor<Float> DATA_HEALTH_ID;
    private static final EntityDataAccessor<Integer> DATA_EFFECT_COLOR_ID;
    private static final EntityDataAccessor<Boolean> DATA_EFFECT_AMBIENCE_ID;
    private static final EntityDataAccessor<Integer> DATA_ARROW_COUNT_ID;
    private static final EntityDataAccessor<Integer> DATA_STINGER_COUNT_ID;
    private static final EntityDataAccessor<Optional<BlockPos>> SLEEPING_POS_ID;
    protected static final EntityDimensions SLEEPING_DIMENSIONS;
    private final AttributeMap attributes;
    private final CombatTracker combatTracker = new CombatTracker(this);
    private final Map<MobEffect, MobEffectInstance> activeEffects = Maps.newHashMap();
    private final NonNullList<ItemStack> lastHandItemStacks;
    private final NonNullList<ItemStack> lastArmorItemStacks;
    public boolean swinging;
    public InteractionHand swingingArm;
    public int swingTime;
    public int removeArrowTime;
    public int removeStingerTime;
    public int hurtTime;
    public int hurtDuration;
    public float hurtDir;
    public int deathTime;
    public float oAttackAnim;
    public float attackAnim;
    protected int attackStrengthTicker;
    public float animationSpeedOld;
    public float animationSpeed;
    public float animationPosition;
    public final int invulnerableDuration;
    public final float timeOffs;
    public final float rotA;
    public float yBodyRot;
    public float yBodyRotO;
    public float yHeadRot;
    public float yHeadRotO;
    public float flyingSpeed;
    @Nullable
    protected Player lastHurtByPlayer;
    protected int lastHurtByPlayerTime;
    protected boolean dead;
    protected int noActionTime;
    protected float oRun;
    protected float run;
    protected float animStep;
    protected float animStepO;
    protected float rotOffs;
    protected int deathScore;
    protected float lastHurt;
    protected boolean jumping;
    public float xxa;
    public float yya;
    public float zza;
    protected int lerpSteps;
    protected double lerpX;
    protected double lerpY;
    protected double lerpZ;
    protected double lerpYRot;
    protected double lerpXRot;
    protected double lyHeadRot;
    protected int lerpHeadSteps;
    private boolean effectsDirty;
    @Nullable
    private LivingEntity lastHurtByMob;
    private int lastHurtByMobTimestamp;
    private LivingEntity lastHurtMob;
    private int lastHurtMobTimestamp;
    private float speed;
    private int noJumpDelay;
    private float absorptionAmount;
    protected ItemStack useItem;
    protected int useItemRemaining;
    protected int fallFlyTicks;
    private BlockPos lastPos;
    private Optional<BlockPos> lastClimbablePos;
    private DamageSource lastDamageSource;
    private long lastDamageStamp;
    protected int autoSpinAttackTicks;
    private float swimAmount;
    private float swimAmountO;
    protected Brain<?> brain;

    protected LivingEntity(EntityType<? extends LivingEntity> var1, Level var2) {
        super(var1, var2);
        this.lastHandItemStacks = NonNullList.withSize(2, ItemStack.EMPTY);
        this.lastArmorItemStacks = NonNullList.withSize(4, ItemStack.EMPTY);
        this.invulnerableDuration = 20;
        this.flyingSpeed = 0.02F;
        this.effectsDirty = true;
        this.useItem = ItemStack.EMPTY;
        this.lastClimbablePos = Optional.empty();
        this.attributes = new AttributeMap(DefaultAttributes.getSupplier(var1));
        this.setHealth(this.getMaxHealth());
        this.blocksBuilding = true;
        this.rotA = (float)((Math.random() + 1.0D) * 0.009999999776482582D);
        this.reapplyPosition();
        this.timeOffs = (float)Math.random() * 12398.0F;
        this.yRot = (float)(Math.random() * 6.2831854820251465D);
        this.yHeadRot = this.yRot;
        this.maxUpStep = 0.6F;
        NbtOps var3 = NbtOps.INSTANCE;
        this.brain = this.makeBrain(new Dynamic(var3, var3.createMap(ImmutableMap.of(var3.createString("memories"), var3.emptyMap()))));
    }

    public Brain<?> getBrain() {
        return this.brain;
    }

    protected Provider<?> brainProvider() {
        return Brain.provider(ImmutableList.of(), ImmutableList.of());
    }

    protected Brain<?> makeBrain(Dynamic<?> var1) {
        return this.brainProvider().makeBrain(var1);
    }

    public void kill() {
        this.hurt(DamageSource.OUT_OF_WORLD, 3.4028235E38F);
    }

    public boolean canAttackType(EntityType<?> var1) {
        return true;
    }

    protected void defineSynchedData() {
        this.entityData.define(DATA_LIVING_ENTITY_FLAGS, (byte)0);
        this.entityData.define(DATA_EFFECT_COLOR_ID, 0);
        this.entityData.define(DATA_EFFECT_AMBIENCE_ID, false);
        this.entityData.define(DATA_ARROW_COUNT_ID, 0);
        this.entityData.define(DATA_STINGER_COUNT_ID, 0);
        this.entityData.define(DATA_HEALTH_ID, 1.0F);
        this.entityData.define(SLEEPING_POS_ID, Optional.empty());
    }

    public static Builder createLivingAttributes() {
        return AttributeSupplier.builder().add(Attributes.MAX_HEALTH).add(Attributes.KNOCKBACK_RESISTANCE).add(Attributes.MOVEMENT_SPEED).add(Attributes.ARMOR).add(Attributes.ARMOR_TOUGHNESS);
    }

    protected void checkFallDamage(double var1, boolean var3, BlockState var4, BlockPos var5) {
        if (!this.isInWater()) {
            this.updateInWaterStateAndDoWaterCurrentPushing();
        }

        if (!this.level.isClientSide && var3 && this.fallDistance > 0.0F) {
            this.removeSoulSpeed();
            this.tryAddSoulSpeed();
        }

        if (!this.level.isClientSide && this.fallDistance > 3.0F && var3) {
            float var6 = (float)Mth.ceil(this.fallDistance - 3.0F);
            if (!var4.isAir()) {
                double var7 = Math.min(0.2F + var6 / 15.0F, 2.5D);
                int var9 = (int)(150.0D * var7);
                ((ServerLevel)this.level).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, var4), this.getX(), this.getY(), this.getZ(), var9, 0.0D, 0.0D, 0.0D, 0.15000000596046448D);
            }
        }

        super.checkFallDamage(var1, var3, var4, var5);
    }

    public boolean canBreatheUnderwater() {
        return this.getMobType() == MobType.UNDEAD;
    }

    public void baseTick() {
        this.oAttackAnim = this.attackAnim;
        if (this.firstTick) {
            this.getSleepingPos().ifPresent(this::setPosToBed);
        }

        if (this.canSpawnSoulSpeedParticle()) {
            this.spawnSoulSpeedParticle();
        }

        super.baseTick();
        this.level.getProfiler().push("livingEntityBaseTick");
        boolean var1 = this instanceof Player;
        if (this.isAlive()) {
            if (this.isInWall()) {
                this.hurt(DamageSource.IN_WALL, 1.0F);
            } else if (var1 && !this.level.getWorldBorder().isWithinBounds(this.getBoundingBox())) {
                double var2 = this.level.getWorldBorder().getDistanceToBorder(this) + this.level.getWorldBorder().getDamageSafeZone();
                if (var2 < 0.0D) {
                    double var4 = this.level.getWorldBorder().getDamagePerBlock();
                    if (var4 > 0.0D) {
                        this.hurt(DamageSource.IN_WALL, (float)Math.max(1, Mth.floor(-var2 * var4)));
                    }
                }
            }
        }

        if (this.fireImmune() || this.level.isClientSide) {
            this.clearFire();
        }

        boolean var11 = var1 && ((Player)this).abilities.invulnerable;
        if (this.isAlive()) {
            if (this.isUnderLiquid(FluidTags.WATER) && !this.level.getBlockState(new BlockPos(this.getX(), this.getEyeY(), this.getZ())).is(Blocks.BUBBLE_COLUMN)) {
                if (!this.canBreatheUnderwater() && !MobEffectUtil.hasWaterBreathing(this) && !var11) {
                    this.setAirSupply(this.decreaseAirSupply(this.getAirSupply()));
                    if (this.getAirSupply() == -20) {
                        this.setAirSupply(0);
                        Vec3 var3 = this.getDeltaMovement();

                        for(int var13 = 0; var13 < 8; ++var13) {
                            double var5 = this.random.nextDouble() - this.random.nextDouble();
                            double var7 = this.random.nextDouble() - this.random.nextDouble();
                            double var9 = this.random.nextDouble() - this.random.nextDouble();
                            this.level.addParticle(ParticleTypes.BUBBLE, this.getX() + var5, this.getY() + var7, this.getZ() + var9, var3.x, var3.y, var3.z);
                        }

                        this.hurt(DamageSource.DROWN, 2.0F);
                    }
                }

                if (!this.level.isClientSide && this.isPassenger() && this.getVehicle() != null && !this.getVehicle().rideableUnderWater()) {
                    this.stopRiding();
                }
            } else if (this.getAirSupply() < this.getMaxAirSupply()) {
                this.setAirSupply(this.increaseAirSupply(this.getAirSupply()));
            }

            if (!this.level.isClientSide) {
                BlockPos var12 = this.blockPosition();
                if (!Objects.equal(this.lastPos, var12)) {
                    this.lastPos = var12;
                    this.onChangedBlock(var12);
                }
            }
        }

        if (this.isAlive() && this.isInWaterRainOrBubble()) {
            this.clearFire();
        }

        if (this.hurtTime > 0) {
            --this.hurtTime;
        }

        if (this.invulnerableTime > 0 && !(this instanceof ServerPlayer)) {
            --this.invulnerableTime;
        }

        if (this.isDeadOrDying()) {
            this.tickDeath();
        }

        if (this.lastHurtByPlayerTime > 0) {
            --this.lastHurtByPlayerTime;
        } else {
            this.lastHurtByPlayer = null;
        }

        if (this.lastHurtMob != null && !this.lastHurtMob.isAlive()) {
            this.lastHurtMob = null;
        }

        if (this.lastHurtByMob != null) {
            if (!this.lastHurtByMob.isAlive()) {
                this.setLastHurtByMob(null);
            } else if (this.tickCount - this.lastHurtByMobTimestamp > 100) {
                this.setLastHurtByMob(null);
            }
        }

        this.tickEffects();
        this.animStepO = this.animStep;
        this.yBodyRotO = this.yBodyRot;
        this.yHeadRotO = this.yHeadRot;
        this.yRotO = this.yRot;
        this.xRotO = this.xRot;
        this.level.getProfiler().pop();
    }

    public boolean canSpawnSoulSpeedParticle() {
        return this.tickCount % 5 == 0 && this.getDeltaMovement().x != 0.0D && this.getDeltaMovement().z != 0.0D && !this.isSpectator() && EnchantmentHelper.hasSoulSpeed(this) && this.onSoulSpeedBlock();
    }

    protected void spawnSoulSpeedParticle() {
        Vec3 var1 = this.getDeltaMovement();
        this.level.addParticle(ParticleTypes.SOUL, this.getX() + (this.random.nextDouble() - 0.5D) * (double)this.getBbWidth(), this.getY() + 0.1D, this.getZ() + (this.random.nextDouble() - 0.5D) * (double)this.getBbWidth(), var1.x * -0.2D, 0.1D, var1.z * -0.2D);
        float var2 = this.random.nextFloat() * 0.4F + this.random.nextFloat() > 0.9F ? 0.6F : 0.0F;
        this.playSound(SoundEvents.SOUL_ESCAPE, var2, 0.6F + this.random.nextFloat() * 0.4F);
    }

    protected boolean onSoulSpeedBlock() {
        return this.getBlockStateOn().is(BlockTags.SOUL_SPEED_BLOCKS);
    }

    protected float getBlockSpeedFactor() {
        return this.onSoulSpeedBlock() && EnchantmentHelper.getEnchantmentLevel(Enchantments.SOUL_SPEED, this) > 0 ? 1.0F : super.getBlockSpeedFactor();
    }

    protected boolean shouldRemoveSoulSpeed(BlockState var1) {
        return !var1.isAir() || this.isFallFlying();
    }

    protected void removeSoulSpeed() {
        AttributeInstance var1 = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (var1 != null) {
            if (var1.getModifier(SPEED_MODIFIER_SOUL_SPEED_UUID) != null) {
                var1.removeModifier(SPEED_MODIFIER_SOUL_SPEED_UUID);
            }

        }
    }

    protected void tryAddSoulSpeed() {
        if (!this.getBlockStateOn().isAir()) {
            int var1 = EnchantmentHelper.getEnchantmentLevel(Enchantments.SOUL_SPEED, this);
            if (var1 > 0 && this.onSoulSpeedBlock()) {
                AttributeInstance var2 = this.getAttribute(Attributes.MOVEMENT_SPEED);
                if (var2 == null) {
                    return;
                }

                var2.addTransientModifier(new AttributeModifier(SPEED_MODIFIER_SOUL_SPEED_UUID, "Soul speed boost", 0.03F * (1.0F + (float)var1 * 0.35F), Operation.ADDITION));
                if (this.getRandom().nextFloat() < 0.04F) {
                    ItemStack var3 = this.getItemBySlot(EquipmentSlot.FEET);
                    var3.hurtAndBreak(1, this, (var0) -> {
                        var0.broadcastBreakEvent(EquipmentSlot.FEET);
                    });
                }
            }
        }

    }

    protected void onChangedBlock(BlockPos var1) {
        int var2 = EnchantmentHelper.getEnchantmentLevel(Enchantments.FROST_WALKER, this);
        if (var2 > 0) {
            FrostWalkerEnchantment.onEntityMoved(this, this.level, var1, var2);
        }

        if (this.shouldRemoveSoulSpeed(this.getBlockStateOn())) {
            this.removeSoulSpeed();
        }

        this.tryAddSoulSpeed();
    }

    public boolean isBaby() {
        return false;
    }

    public float getScale() {
        return this.isBaby() ? 0.5F : 1.0F;
    }

    public boolean rideableUnderWater() {
        return false;
    }

    protected void tickDeath() {
        ++this.deathTime;
        if (this.deathTime == 20) {
            this.remove();

            for(int var1 = 0; var1 < 20; ++var1) {
                double var2 = this.random.nextGaussian() * 0.02D;
                double var4 = this.random.nextGaussian() * 0.02D;
                double var6 = this.random.nextGaussian() * 0.02D;
                this.level.addParticle(ParticleTypes.POOF, this.getRandomX(1.0D), this.getRandomY(), this.getRandomZ(1.0D), var2, var4, var6);
            }
        }

    }

    protected boolean shouldDropExperience() {
        return !this.isBaby();
    }

    protected boolean shouldDropLoot() {
        return !this.isBaby();
    }

    protected int decreaseAirSupply(int var1) {
        int var2 = EnchantmentHelper.getRespiration(this);
        return var2 > 0 && this.random.nextInt(var2 + 1) > 0 ? var1 : var1 - 1;
    }

    protected int increaseAirSupply(int var1) {
        return Math.min(var1 + 4, this.getMaxAirSupply());
    }

    protected int getExperienceReward(Player var1) {
        return 0;
    }

    protected boolean isAlwaysExperienceDropper() {
        return false;
    }

    public Random getRandom() {
        return this.random;
    }

    @Nullable
    public LivingEntity getLastHurtByMob() {
        return this.lastHurtByMob;
    }

    public int getLastHurtByMobTimestamp() {
        return this.lastHurtByMobTimestamp;
    }

    public void setLastHurtByPlayer(@Nullable Player var1) {
        this.lastHurtByPlayer = var1;
        this.lastHurtByPlayerTime = this.tickCount;
    }

    public void setLastHurtByMob(@Nullable LivingEntity var1) {
        this.lastHurtByMob = var1;
        this.lastHurtByMobTimestamp = this.tickCount;
    }

    @Nullable
    public LivingEntity getLastHurtMob() {
        return this.lastHurtMob;
    }

    public int getLastHurtMobTimestamp() {
        return this.lastHurtMobTimestamp;
    }

    public void setLastHurtMob(Entity var1) {
        if (var1 instanceof LivingEntity) {
            this.lastHurtMob = (LivingEntity)var1;
        } else {
            this.lastHurtMob = null;
        }

        this.lastHurtMobTimestamp = this.tickCount;
    }

    public int getNoActionTime() {
        return this.noActionTime;
    }

    public void setNoActionTime(int var1) {
        this.noActionTime = var1;
    }

    protected void playEquipSound(ItemStack var1) {
        if (!var1.isEmpty()) {
            SoundEvent var2 = SoundEvents.ARMOR_EQUIP_GENERIC;
            Item var3 = var1.getItem();
            if (var3 instanceof ArmorItem) {
                var2 = ((ArmorItem)var3).getMaterial().getEquipSound();
            } else if (var3 == Items.ELYTRA) {
                var2 = SoundEvents.ARMOR_EQUIP_ELYTRA;
            }

            this.playSound(var2, 1.0F, 1.0F);
        }
    }

    public void addAdditionalSaveData(CompoundTag compoundTag) {
        compoundTag.putFloat("Health", this.getHealth());
        compoundTag.putShort("HurtTime", (short)this.hurtTime);
        compoundTag.putInt("HurtByTimestamp", this.lastHurtByMobTimestamp);
        compoundTag.putShort("DeathTime", (short)this.deathTime);
        compoundTag.putFloat("AbsorptionAmount", this.getAbsorptionAmount());
        compoundTag.put("Attributes", this.getAttributes().save());
        if (!this.activeEffects.isEmpty()) {
            ListTag var2 = new ListTag();
            Iterator var3 = this.activeEffects.values().iterator();

            while(var3.hasNext()) {
                MobEffectInstance var4 = (MobEffectInstance)var3.next();
                var2.add(var4.save(new CompoundTag()));
            }

            compoundTag.put("ActiveEffects", var2);
        }

        compoundTag.putBoolean("FallFlying", this.isFallFlying());
        this.getSleepingPos().ifPresent((blockPos) -> {
            compoundTag.putInt("SleepingX", blockPos.getX());
            compoundTag.putInt("SleepingY", blockPos.getY());
            compoundTag.putInt("SleepingZ", blockPos.getZ());
        });
        DataResult var5 = this.brain.serializeStart(NbtOps.INSTANCE);
        Logger var10001 = LOGGER;
        var10001.getClass();
        var5.resultOrPartial(var10001::error).ifPresent(tag -> {
            compoundTag.put("Brain", (net.minecraft.nbt.Tag) tag);
        });
    }

    public void readAdditionalSaveData(CompoundTag var1) {
        this.setAbsorptionAmount(var1.getFloat("AbsorptionAmount"));
        if (var1.contains("Attributes", 9) && this.level != null && !this.level.isClientSide) {
            this.getAttributes().load(var1.getList("Attributes", 10));
        }

        if (var1.contains("ActiveEffects", 9)) {
            ListTag var2 = var1.getList("ActiveEffects", 10);

            for(int var3 = 0; var3 < var2.size(); ++var3) {
                CompoundTag var4 = var2.getCompound(var3);
                MobEffectInstance var5 = MobEffectInstance.load(var4);
                if (var5 != null) {
                    this.activeEffects.put(var5.getEffect(), var5);
                }
            }
        }

        if (var1.contains("Health", 99)) {
            this.setHealth(var1.getFloat("Health"));
        }

        this.hurtTime = var1.getShort("HurtTime");
        this.deathTime = var1.getShort("DeathTime");
        this.lastHurtByMobTimestamp = var1.getInt("HurtByTimestamp");
        if (var1.contains("Team", 8)) {
            String var6 = var1.getString("Team");
            PlayerTeam var8 = this.level.getScoreboard().getPlayerTeam(var6);
            boolean var9 = var8 != null && this.level.getScoreboard().addPlayerToTeam(this.getStringUUID(), var8);
            if (!var9) {
                LOGGER.warn("Unable to add mob to team \"{}\" (that team probably doesn't exist)", var6);
            }
        }

        if (var1.getBoolean("FallFlying")) {
            this.setSharedFlag(7, true);
        }

        if (var1.contains("SleepingX", 99) && var1.contains("SleepingY", 99) && var1.contains("SleepingZ", 99)) {
            BlockPos var7 = new BlockPos(var1.getInt("SleepingX"), var1.getInt("SleepingY"), var1.getInt("SleepingZ"));
            this.setSleepingPos(var7);
            this.entityData.set(DATA_POSE, Pose.SLEEPING);
            if (!this.firstTick) {
                this.setPosToBed(var7);
            }
        }

        if (var1.contains("Brain", 10)) {
            this.brain = this.makeBrain(new Dynamic(NbtOps.INSTANCE, var1.get("Brain")));
        }

    }

    protected void tickEffects() {
        Iterator var1 = this.activeEffects.keySet().iterator();

        try {
            while(var1.hasNext()) {
                MobEffect var2 = (MobEffect)var1.next();
                MobEffectInstance var3 = this.activeEffects.get(var2);
                if (!var3.tick(this, () -> {
                    this.onEffectUpdated(var3, true);
                })) {
                    if (!this.level.isClientSide) {
                        var1.remove();
                        this.onEffectRemoved(var3);
                    }
                } else if (var3.getDuration() % 600 == 0) {
                    this.onEffectUpdated(var3, false);
                }
            }
        } catch (ConcurrentModificationException var11) {
        }

        if (this.effectsDirty) {
            if (!this.level.isClientSide) {
                this.updateInvisibilityStatus();
            }

            this.effectsDirty = false;
        }

        int var12 = this.entityData.get(DATA_EFFECT_COLOR_ID);
        boolean var13 = this.entityData.get(DATA_EFFECT_AMBIENCE_ID);
        if (var12 > 0) {
            boolean var4;
            if (this.isInvisible()) {
                var4 = this.random.nextInt(15) == 0;
            } else {
                var4 = this.random.nextBoolean();
            }

            if (var13) {
                var4 &= this.random.nextInt(5) == 0;
            }

            if (var4 && var12 > 0) {
                double var5 = (double)(var12 >> 16 & 255) / 255.0D;
                double var7 = (double)(var12 >> 8 & 255) / 255.0D;
                double var9 = (double)(var12 >> 0 & 255) / 255.0D;
                this.level.addParticle(var13 ? ParticleTypes.AMBIENT_ENTITY_EFFECT : ParticleTypes.ENTITY_EFFECT, this.getRandomX(0.5D), this.getRandomY(), this.getRandomZ(0.5D), var5, var7, var9);
            }
        }

    }

    protected void updateInvisibilityStatus() {
        if (this.activeEffects.isEmpty()) {
            this.removeEffectParticles();
            this.setInvisible(false);
        } else {
            Collection var1 = this.activeEffects.values();
            this.entityData.set(DATA_EFFECT_AMBIENCE_ID, areAllEffectsAmbient(var1));
            this.entityData.set(DATA_EFFECT_COLOR_ID, PotionUtils.getColor(var1));
            this.setInvisible(this.hasEffect(MobEffects.INVISIBILITY));
        }

    }

    public double getVisibilityPercent(@Nullable Entity var1) {
        double var2 = 1.0D;
        if (this.isDiscrete()) {
            var2 *= 0.8D;
        }

        if (this.isInvisible()) {
            float var4 = this.getArmorCoverPercentage();
            if (var4 < 0.1F) {
                var4 = 0.1F;
            }

            var2 *= 0.7D * (double)var4;
        }

        if (var1 != null) {
            ItemStack var7 = this.getItemBySlot(EquipmentSlot.HEAD);
            Item var5 = var7.getItem();
            EntityType var6 = var1.getType();
            if (var6 == EntityType.SKELETON && var5 == Items.SKELETON_SKULL || var6 == EntityType.ZOMBIE && var5 == Items.ZOMBIE_HEAD || var6 == EntityType.CREEPER && var5 == Items.CREEPER_HEAD) {
                var2 *= 0.5D;
            }
        }

        return var2;
    }

    public boolean canAttack(LivingEntity var1) {
        return true;
    }

    public boolean canAttack(LivingEntity var1, TargetingConditions var2) {
        return var2.test(this, var1);
    }

    public static boolean areAllEffectsAmbient(Collection<MobEffectInstance> var0) {
        Iterator var1 = var0.iterator();

        MobEffectInstance var2;
        do {
            if (!var1.hasNext()) {
                return true;
            }

            var2 = (MobEffectInstance)var1.next();
        } while(var2.isAmbient());

        return false;
    }

    protected void removeEffectParticles() {
        this.entityData.set(DATA_EFFECT_AMBIENCE_ID, false);
        this.entityData.set(DATA_EFFECT_COLOR_ID, 0);
    }

    public boolean removeAllEffects() {
        if (this.level.isClientSide) {
            return false;
        } else {
            Iterator var1 = this.activeEffects.values().iterator();

            boolean var2;
            for(var2 = false; var1.hasNext(); var2 = true) {
                this.onEffectRemoved((MobEffectInstance)var1.next());
                var1.remove();
            }

            return var2;
        }
    }

    public Collection<MobEffectInstance> getActiveEffects() {
        return this.activeEffects.values();
    }

    public Map<MobEffect, MobEffectInstance> getActiveEffectsMap() {
        return this.activeEffects;
    }

    public boolean hasEffect(MobEffect var1) {
        return this.activeEffects.containsKey(var1);
    }

    @Nullable
    public MobEffectInstance getEffect(MobEffect var1) {
        return this.activeEffects.get(var1);
    }

    public boolean addEffect(MobEffectInstance var1) {
        if (!this.canBeAffected(var1)) {
            return false;
        } else {
            MobEffectInstance var2 = this.activeEffects.get(var1.getEffect());
            if (var2 == null) {
                this.activeEffects.put(var1.getEffect(), var1);
                this.onEffectAdded(var1);
                return true;
            } else if (var2.update(var1)) {
                this.onEffectUpdated(var2, true);
                return true;
            } else {
                return false;
            }
        }
    }

    public boolean canBeAffected(MobEffectInstance var1) {
        if (this.getMobType() == MobType.UNDEAD) {
            MobEffect var2 = var1.getEffect();
            return var2 != MobEffects.REGENERATION && var2 != MobEffects.POISON;
        }

        return true;
    }

    public boolean isInvertedHealAndHarm() {
        return this.getMobType() == MobType.UNDEAD;
    }

    @Nullable
    public MobEffectInstance removeEffectNoUpdate(@Nullable MobEffect var1) {
        return this.activeEffects.remove(var1);
    }

    public boolean removeEffect(MobEffect var1) {
        MobEffectInstance var2 = this.removeEffectNoUpdate(var1);
        if (var2 != null) {
            this.onEffectRemoved(var2);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Called when effect is added to this entity.
     *
     * @see EffectAddedEvent
     * @param effect the mob effect instance to be added.
     */
    protected void onEffectAdded(MobEffectInstance effect) {
        this.effectsDirty = true;
        EventManager.callEvent(new EffectAddedEvent(effect,this)); //Anvil
        if (!this.level.isClientSide) {
            effect.getEffect().addAttributeModifiers(this, this.getAttributes(), effect.getAmplifier());
        }

    }

    protected void onEffectUpdated(MobEffectInstance var1, boolean var2) {
        this.effectsDirty = true;
        if (var2 && !this.level.isClientSide) {
            MobEffect var3 = var1.getEffect();
            var3.removeAttributeModifiers(this, this.getAttributes(), var1.getAmplifier());
            var3.addAttributeModifiers(this, this.getAttributes(), var1.getAmplifier());
        }

    }

    protected void onEffectRemoved(MobEffectInstance var1) {
        this.effectsDirty = true;
        if (!this.level.isClientSide) {
            var1.getEffect().removeAttributeModifiers(this, this.getAttributes(), var1.getAmplifier());
        }

    }

    public void heal(float var1) {
        float var2 = this.getHealth();
        if (var2 > 0.0F) {
            this.setHealth(var2 + var1);
        }

    }

    public float getHealth() {
        return this.entityData.get(DATA_HEALTH_ID);
    }

    public void setHealth(float var1) {
        this.entityData.set(DATA_HEALTH_ID, Mth.clamp(var1, 0.0F, this.getMaxHealth()));
    }

    public boolean isDeadOrDying() {
        return this.getHealth() <= 0.0F;
    }

    public boolean hurt(DamageSource var1, float var2) {
        if (this.isInvulnerableTo(var1)) {
            return false;
        } else if (this.level.isClientSide) {
            return false;
        } else if (this.isDeadOrDying()) {
            return false;
        } else if (var1.isFire() && this.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return false;
        } else {
            if (this.isSleeping() && !this.level.isClientSide) {
                this.stopSleeping();
            }

            this.noActionTime = 0;
            float var3 = var2;
            if ((var1 == DamageSource.ANVIL || var1 == DamageSource.FALLING_BLOCK) && !this.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                this.getItemBySlot(EquipmentSlot.HEAD).hurtAndBreak((int)(var2 * 4.0F + this.random.nextFloat() * var2 * 2.0F), this, (var0) -> {
                    var0.broadcastBreakEvent(EquipmentSlot.HEAD);
                });
                var2 *= 0.75F;
            }

            boolean var4 = false;
            float var5 = 0.0F;
            if (var2 > 0.0F && this.isDamageSourceBlocked(var1)) {
                this.hurtCurrentlyUsedShield(var2);
                var5 = var2;
                var2 = 0.0F;
                if (!var1.isProjectile()) {
                    Entity var6 = var1.getDirectEntity();
                    if (var6 instanceof LivingEntity) {
                        this.blockUsingShield((LivingEntity)var6);
                    }
                }

                var4 = true;
            }

            this.animationSpeed = 1.5F;
            boolean var12 = true;
            if ((float)this.invulnerableTime > 10.0F) {
                if (var2 <= this.lastHurt) {
                    return false;
                }

                this.actuallyHurt(var1, var2 - this.lastHurt);
                this.lastHurt = var2;
                var12 = false;
            } else {
                this.lastHurt = var2;
                this.invulnerableTime = 20;
                this.actuallyHurt(var1, var2);
                this.hurtDuration = 10;
                this.hurtTime = this.hurtDuration;
            }

            this.hurtDir = 0.0F;
            Entity var7 = var1.getEntity();
            if (var7 != null && EntitySelector.ATTACK_ALLOWED.test(var7)) {
                if (var7 instanceof LivingEntity) {
                    this.setLastHurtByMob((LivingEntity)var7);
                }

                if (var7 instanceof Player) {
                    this.lastHurtByPlayerTime = 100;
                    this.lastHurtByPlayer = (Player)var7;
                } else if (var7 instanceof Wolf) {
                    Wolf var8 = (Wolf)var7;
                    if (var8.isTame()) {
                        this.lastHurtByPlayerTime = 100;
                        LivingEntity var9 = var8.getOwner();
                        if (var9 != null && var9.getType() == EntityType.PLAYER) {
                            this.lastHurtByPlayer = (Player)var9;
                        } else {
                            this.lastHurtByPlayer = null;
                        }
                    }
                }
            }

            if (var12) {
                if (var4) {
                    this.level.broadcastEntityEvent(this, (byte)29);
                } else if (var1 instanceof EntityDamageSource && ((EntityDamageSource)var1).isThorns()) {
                    this.level.broadcastEntityEvent(this, (byte)33);
                } else {
                    byte var13;
                    if (var1 == DamageSource.DROWN) {
                        var13 = 36;
                    } else if (var1.isFire()) {
                        var13 = 37;
                    } else if (var1 == DamageSource.SWEET_BERRY_BUSH) {
                        var13 = 44;
                    } else {
                        var13 = 2;
                    }

                    this.level.broadcastEntityEvent(this, var13);
                }

                if (var1 != DamageSource.DROWN && (!var4 || var2 > 0.0F)) {
                    this.markHurt();
                }

                if (var7 != null) {
                    double var14 = var7.getX() - this.getX();

                    double var10;
                    for(var10 = var7.getZ() - this.getZ(); var14 * var14 + var10 * var10 < 1.0E-4D; var10 = (Math.random() - Math.random()) * 0.01D) {
                        var14 = (Math.random() - Math.random()) * 0.01D;
                    }

                    this.hurtDir = (float)(Mth.atan2(var10, var14) * 57.2957763671875D - (double)this.yRot);
                    this.knockback(0.4F, var14, var10);
                } else {
                    this.hurtDir = (float)((int)(Math.random() * 2.0D) * 180);
                }
            }

            if (this.isDeadOrDying()) {
                if (!this.checkTotemDeathProtection(var1)) {
                    SoundEvent var15 = this.getDeathSound();
                    if (var12 && var15 != null) {
                        this.playSound(var15, this.getSoundVolume(), this.getVoicePitch());
                    }

                    this.die(var1);
                }
            } else if (var12) {
                this.playHurtSound(var1);
            }

            boolean var16 = !var4 || var2 > 0.0F;
            if (var16) {
                this.lastDamageSource = var1;
                this.lastDamageStamp = this.level.getGameTime();
            }

            if (this instanceof ServerPlayer) {
                CriteriaTriggers.ENTITY_HURT_PLAYER.trigger((ServerPlayer)this, var1, var3, var2, var4);
                if (var5 > 0.0F && var5 < 3.4028235E37F) {
                    ((ServerPlayer)this).awardStat(Stats.DAMAGE_BLOCKED_BY_SHIELD, Math.round(var5 * 10.0F));
                }
            }

            if (var7 instanceof ServerPlayer) {
                CriteriaTriggers.PLAYER_HURT_ENTITY.trigger((ServerPlayer)var7, this, var1, var3, var2, var4);
            }

            return var16;
        }
    }

    protected void blockUsingShield(LivingEntity var1) {
        var1.blockedByShield(this);
    }

    protected void blockedByShield(LivingEntity var1) {
        var1.knockback(0.5F, var1.getX() - this.getX(), var1.getZ() - this.getZ());
    }

    private boolean checkTotemDeathProtection(DamageSource var1) {
        if (var1.isBypassInvul()) {
            return false;
        } else {
            ItemStack var2 = null;
            InteractionHand[] var4 = InteractionHand.values();
            int var5 = var4.length;

            for(int var6 = 0; var6 < var5; ++var6) {
                InteractionHand var7 = var4[var6];
                ItemStack var3 = this.getItemInHand(var7);
                if (var3.getItem() == Items.TOTEM_OF_UNDYING) {
                    var2 = var3.copy();
                    var3.shrink(1);
                    break;
                }
            }

            if (var2 != null) {
                if (this instanceof ServerPlayer) {
                    ServerPlayer var8 = (ServerPlayer)this;
                    var8.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING));
                    CriteriaTriggers.USED_TOTEM.trigger(var8, var2);
                }

                this.setHealth(1.0F);
                this.removeAllEffects();
                this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
                this.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
                this.level.broadcastEntityEvent(this, (byte)35);
            }

            return var2 != null;
        }
    }

    @Nullable
    public DamageSource getLastDamageSource() {
        if (this.level.getGameTime() - this.lastDamageStamp > 40L) {
            this.lastDamageSource = null;
        }

        return this.lastDamageSource;
    }

    protected void playHurtSound(DamageSource var1) {
        SoundEvent var2 = this.getHurtSound(var1);
        if (var2 != null) {
            this.playSound(var2, this.getSoundVolume(), this.getVoicePitch());
        }

    }

    private boolean isDamageSourceBlocked(DamageSource var1) {
        Entity var2 = var1.getDirectEntity();
        boolean var3 = false;
        if (var2 instanceof AbstractArrow) {
            AbstractArrow var4 = (AbstractArrow)var2;
            if (var4.getPierceLevel() > 0) {
                var3 = true;
            }
        }

        if (!var1.isBypassArmor() && this.isBlocking() && !var3) {
            Vec3 var7 = var1.getSourcePosition();
            if (var7 != null) {
                Vec3 var5 = this.getViewVector(1.0F);
                Vec3 var6 = var7.vectorTo(this.position()).normalize();
                var6 = new Vec3(var6.x, 0.0D, var6.z);
                return var6.dot(var5) < 0.0D;
            }
        }

        return false;
    }

    public void die(DamageSource var1) {
        if (!this.removed && !this.dead) {
            Entity var2 = var1.getEntity();
            LivingEntity var3 = this.getKillCredit();
            if (this.deathScore >= 0 && var3 != null) {
                var3.awardKillScore(this, this.deathScore, var1);
            }

            if (var2 != null) {
                var2.killed(this);
            }

            if (this.isSleeping()) {
                this.stopSleeping();
            }

            this.dead = true;
            this.getCombatTracker().recheckStatus();
            if (!this.level.isClientSide) {
                this.dropAllDeathLoot(var1);
                this.createWitherRose(var3);
            }

            this.level.broadcastEntityEvent(this, (byte)3);
            this.setPose(Pose.DYING);
        }
    }

    protected void createWitherRose(@Nullable LivingEntity var1) {
        if (!this.level.isClientSide) {
            boolean var2 = false;
            if (var1 instanceof WitherBoss) {
                if (this.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                    BlockPos var3 = this.blockPosition();
                    BlockState var4 = Blocks.WITHER_ROSE.defaultBlockState();
                    if (this.level.getBlockState(var3).isAir() && var4.canSurvive(this.level, var3)) {
                        this.level.setBlock(var3, var4, 3);
                        var2 = true;
                    }
                }

                if (!var2) {
                    ItemEntity var5 = new ItemEntity(this.level, this.getX(), this.getY(), this.getZ(), new ItemStack(Items.WITHER_ROSE));
                    this.level.addFreshEntity(var5);
                }
            }

        }
    }

    protected void dropAllDeathLoot(DamageSource var1) {
        Entity var2 = var1.getEntity();
        int var3;
        if (var2 instanceof Player) {
            var3 = EnchantmentHelper.getMobLooting((LivingEntity)var2);
        } else {
            var3 = 0;
        }

        boolean var4 = this.lastHurtByPlayerTime > 0;
        if (this.shouldDropLoot() && this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            this.dropFromLootTable(var1, var4);
            this.dropCustomDeathLoot(var1, var3, var4);
        }

        this.dropEquipment();
        this.dropExperience();
    }

    protected void dropEquipment() {
    }

    protected void dropExperience() {
        if (!this.level.isClientSide && (this.isAlwaysExperienceDropper() || this.lastHurtByPlayerTime > 0 && this.shouldDropExperience() && this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT))) {
            int var1 = this.getExperienceReward(this.lastHurtByPlayer);

            while(var1 > 0) {
                int var2 = ExperienceOrb.getExperienceValue(var1);
                var1 -= var2;
                this.level.addFreshEntity(new ExperienceOrb(this.level, this.getX(), this.getY(), this.getZ(), var2));
            }
        }

    }

    protected void dropCustomDeathLoot(DamageSource var1, int var2, boolean var3) {
    }

    public ResourceLocation getLootTable() {
        return this.getType().getDefaultLootTable();
    }

    protected void dropFromLootTable(DamageSource var1, boolean var2) {
        ResourceLocation var3 = this.getLootTable();
        LootTable var4 = this.level.getServer().getLootTables().get(var3);
        net.minecraft.world.level.storage.loot.LootContext.Builder var5 = this.createLootContext(var2, var1);
        var4.getRandomItems(var5.create(LootContextParamSets.ENTITY), this::spawnAtLocation);
    }

    protected net.minecraft.world.level.storage.loot.LootContext.Builder createLootContext(boolean var1, DamageSource var2) {
        net.minecraft.world.level.storage.loot.LootContext.Builder var3 = (new net.minecraft.world.level.storage.loot.LootContext.Builder((ServerLevel)this.level)).withRandom(this.random).withParameter(LootContextParams.THIS_ENTITY, this).withParameter(LootContextParams.BLOCK_POS, this.blockPosition()).withParameter(LootContextParams.DAMAGE_SOURCE, var2).withOptionalParameter(LootContextParams.KILLER_ENTITY, var2.getEntity()).withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, var2.getDirectEntity());
        if (var1 && this.lastHurtByPlayer != null) {
            var3 = var3.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, this.lastHurtByPlayer).withLuck(this.lastHurtByPlayer.getLuck());
        }

        return var3;
    }

    public void knockback(float var1, double var2, double var4) {
        var1 = (float)((double)var1 * (1.0D - this.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)));
        if (var1 > 0.0F) {
            this.hasImpulse = true;
            Vec3 var6 = this.getDeltaMovement();
            Vec3 var7 = (new Vec3(var2, 0.0D, var4)).normalize().scale(var1);
            this.setDeltaMovement(var6.x / 2.0D - var7.x, this.onGround ? Math.min(0.4D, var6.y / 2.0D + (double)var1) : var6.y, var6.z / 2.0D - var7.z);
        }
    }

    @Nullable
    protected SoundEvent getHurtSound(DamageSource var1) {
        return SoundEvents.GENERIC_HURT;
    }

    @Nullable
    protected SoundEvent getDeathSound() {
        return SoundEvents.GENERIC_DEATH;
    }

    protected SoundEvent getFallDamageSound(int var1) {
        return var1 > 4 ? SoundEvents.GENERIC_BIG_FALL : SoundEvents.GENERIC_SMALL_FALL;
    }

    protected SoundEvent getDrinkingSound(ItemStack var1) {
        return var1.getDrinkingSound();
    }

    public SoundEvent getEatingSound(ItemStack var1) {
        return var1.getEatingSound();
    }

    public void setOnGround(boolean var1) {
        super.setOnGround(var1);
        if (var1) {
            this.lastClimbablePos = Optional.empty();
        }

    }

    public Optional<BlockPos> getLastClimbablePos() {
        return this.lastClimbablePos;
    }

    public boolean onClimbable() {
        if (this.isSpectator()) {
            return false;
        } else {
            BlockPos var1 = this.blockPosition();
            BlockState var2 = this.getFeetBlockState();
            Block var3 = var2.getBlock();
            if (var3.is(BlockTags.CLIMBABLE)) {
                this.lastClimbablePos = Optional.of(var1);
                return true;
            } else if (var3 instanceof TrapDoorBlock && this.trapdoorUsableAsLadder(var1, var2)) {
                this.lastClimbablePos = Optional.of(var1);
                return true;
            } else {
                return false;
            }
        }
    }

    public BlockState getFeetBlockState() {
        return this.level.getBlockState(this.blockPosition());
    }

    private boolean trapdoorUsableAsLadder(BlockPos var1, BlockState var2) {
        if (var2.getValue(TrapDoorBlock.OPEN)) {
            BlockState var3 = this.level.getBlockState(var1.below());
            return var3.is(Blocks.LADDER) && var3.getValue(LadderBlock.FACING) == var2.getValue(TrapDoorBlock.FACING);
        }

        return false;
    }

    public boolean isAlive() {
        return !this.removed && this.getHealth() > 0.0F;
    }

    public boolean causeFallDamage(float var1, float var2) {
        boolean var3 = super.causeFallDamage(var1, var2);
        int var4 = this.calculateFallDamage(var1, var2);
        if (var4 > 0) {
            this.playSound(this.getFallDamageSound(var4), 1.0F, 1.0F);
            this.playBlockFallSound();
            this.hurt(DamageSource.FALL, (float)var4);
            return true;
        } else {
            return var3;
        }
    }

    protected int calculateFallDamage(float var1, float var2) {
        MobEffectInstance var3 = this.getEffect(MobEffects.JUMP);
        float var4 = var3 == null ? 0.0F : (float)(var3.getAmplifier() + 1);
        return Mth.ceil((var1 - 3.0F - var4) * var2);
    }

    protected void playBlockFallSound() {
        if (!this.isSilent()) {
            int var1 = Mth.floor(this.getX());
            int var2 = Mth.floor(this.getY() - 0.20000000298023224D);
            int var3 = Mth.floor(this.getZ());
            BlockState var4 = this.level.getBlockState(new BlockPos(var1, var2, var3));
            if (!var4.isAir()) {
                SoundType var5 = var4.getSoundType();
                this.playSound(var5.getFallSound(), var5.getVolume() * 0.5F, var5.getPitch() * 0.75F);
            }

        }
    }

    public int getArmorValue() {
        return Mth.floor(this.getAttributeValue(Attributes.ARMOR));
    }

    protected void hurtArmor(DamageSource var1, float var2) {
    }

    protected void hurtCurrentlyUsedShield(float var1) {
    }

    protected float getDamageAfterArmorAbsorb(DamageSource var1, float var2) {
        if (!var1.isBypassArmor()) {
            this.hurtArmor(var1, var2);
            var2 = CombatRules.getDamageAfterAbsorb(var2, (float)this.getArmorValue(), (float)this.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        }

        return var2;
    }

    protected float getDamageAfterMagicAbsorb(DamageSource var1, float var2) {
        if (var1.isBypassMagic()) {
            return var2;
        } else {
            int var3;
            if (this.hasEffect(MobEffects.DAMAGE_RESISTANCE) && var1 != DamageSource.OUT_OF_WORLD) {
                var3 = (this.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() + 1) * 5;
                int var4 = 25 - var3;
                float var5 = var2 * (float)var4;
                float var6 = var2;
                var2 = Math.max(var5 / 25.0F, 0.0F);
                float var7 = var6 - var2;
                if (var7 > 0.0F && var7 < 3.4028235E37F) {
                    if (this instanceof ServerPlayer) {
                        ((ServerPlayer)this).awardStat(Stats.DAMAGE_RESISTED, Math.round(var7 * 10.0F));
                    } else if (var1.getEntity() instanceof ServerPlayer) {
                        ((ServerPlayer)var1.getEntity()).awardStat(Stats.DAMAGE_DEALT_RESISTED, Math.round(var7 * 10.0F));
                    }
                }
            }

            if (var2 <= 0.0F) {
                return 0.0F;
            } else {
                var3 = EnchantmentHelper.getDamageProtection(this.getArmorSlots(), var1);
                if (var3 > 0) {
                    var2 = CombatRules.getDamageAfterMagicAbsorb(var2, (float)var3);
                }

                return var2;
            }
        }
    }

    protected void actuallyHurt(DamageSource var1, float var2) {
        if (!this.isInvulnerableTo(var1)) {
            var2 = this.getDamageAfterArmorAbsorb(var1, var2);
            var2 = this.getDamageAfterMagicAbsorb(var1, var2);
            float var3 = var2;
            var2 = Math.max(var2 - this.getAbsorptionAmount(), 0.0F);
            this.setAbsorptionAmount(this.getAbsorptionAmount() - (var3 - var2));
            float var4 = var3 - var2;
            if (var4 > 0.0F && var4 < 3.4028235E37F && var1.getEntity() instanceof ServerPlayer) {
                ((ServerPlayer)var1.getEntity()).awardStat(Stats.DAMAGE_DEALT_ABSORBED, Math.round(var4 * 10.0F));
            }

            if (var2 != 0.0F) {
                float var5 = this.getHealth();
                this.setHealth(var5 - var2);
                this.getCombatTracker().recordDamage(var1, var5, var2);
                this.setAbsorptionAmount(this.getAbsorptionAmount() - var2);
            }
        }
    }

    public CombatTracker getCombatTracker() {
        return this.combatTracker;
    }

    @Nullable
    public LivingEntity getKillCredit() {
        if (this.combatTracker.getKiller() != null) {
            return this.combatTracker.getKiller();
        } else if (this.lastHurtByPlayer != null) {
            return this.lastHurtByPlayer;
        } else {
            return this.lastHurtByMob;
        }
    }

    public final float getMaxHealth() {
        return (float)this.getAttributeValue(Attributes.MAX_HEALTH);
    }

    public final int getArrowCount() {
        return this.entityData.get(DATA_ARROW_COUNT_ID);
    }

    public final void setArrowCount(int var1) {
        this.entityData.set(DATA_ARROW_COUNT_ID, var1);
    }

    public final int getStingerCount() {
        return this.entityData.get(DATA_STINGER_COUNT_ID);
    }

    public final void setStingerCount(int var1) {
        this.entityData.set(DATA_STINGER_COUNT_ID, var1);
    }

    private int getCurrentSwingDuration() {
        if (MobEffectUtil.hasDigSpeed(this)) {
            return 6 - (1 + MobEffectUtil.getDigSpeedAmplification(this));
        } else {
            return this.hasEffect(MobEffects.DIG_SLOWDOWN) ? 6 + (1 + this.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier()) * 2 : 6;
        }
    }

    public void swing(InteractionHand var1) {
        this.swing(var1, false);
    }

    public void swing(InteractionHand var1, boolean var2) {
        if (!this.swinging || this.swingTime >= this.getCurrentSwingDuration() / 2 || this.swingTime < 0) {
            this.swingTime = -1;
            this.swinging = true;
            this.swingingArm = var1;
            if (this.level instanceof ServerLevel) {
                ClientboundAnimatePacket var3 = new ClientboundAnimatePacket(this, var1 == InteractionHand.MAIN_HAND ? 0 : 3);
                ServerChunkCache var4 = ((ServerLevel)this.level).getChunkSource();
                if (var2) {
                    var4.broadcastAndSend(this, var3);
                } else {
                    var4.broadcast(this, var3);
                }
            }
        }

    }

    protected void outOfWorld() {
        this.hurt(DamageSource.OUT_OF_WORLD, 4.0F);
    }

    protected void updateSwingTime() {
        int var1 = this.getCurrentSwingDuration();
        if (this.swinging) {
            ++this.swingTime;
            if (this.swingTime >= var1) {
                this.swingTime = 0;
                this.swinging = false;
            }
        } else {
            this.swingTime = 0;
        }

        this.attackAnim = (float)this.swingTime / (float)var1;
    }

    @Nullable
    public AttributeInstance getAttribute(Attribute var1) {
        return this.getAttributes().getInstance(var1);
    }

    public double getAttributeValue(Attribute var1) {
        return this.getAttributes().getValue(var1);
    }

    public double getAttributeBaseValue(Attribute var1) {
        return this.getAttributes().getBaseValue(var1);
    }

    public AttributeMap getAttributes() {
        return this.attributes;
    }

    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    public ItemStack getMainHandItem() {
        return this.getItemBySlot(EquipmentSlot.MAINHAND);
    }

    public ItemStack getOffhandItem() {
        return this.getItemBySlot(EquipmentSlot.OFFHAND);
    }

    public boolean isHolding(Item var1) {
        return this.isHolding((var1x) -> {
            return var1x == var1;
        });
    }

    public boolean isHolding(Predicate<Item> var1) {
        return var1.test(this.getMainHandItem().getItem()) || var1.test(this.getOffhandItem().getItem());
    }

    public ItemStack getItemInHand(InteractionHand var1) {
        if (var1 == InteractionHand.MAIN_HAND) {
            return this.getItemBySlot(EquipmentSlot.MAINHAND);
        } else if (var1 == InteractionHand.OFF_HAND) {
            return this.getItemBySlot(EquipmentSlot.OFFHAND);
        } else {
            throw new IllegalArgumentException("Invalid hand " + var1);
        }
    }

    public void setItemInHand(InteractionHand var1, ItemStack var2) {
        if (var1 == InteractionHand.MAIN_HAND) {
            this.setItemSlot(EquipmentSlot.MAINHAND, var2);
        } else {
            if (var1 != InteractionHand.OFF_HAND) {
                throw new IllegalArgumentException("Invalid hand " + var1);
            }

            this.setItemSlot(EquipmentSlot.OFFHAND, var2);
        }

    }

    public boolean hasItemInSlot(EquipmentSlot var1) {
        return !this.getItemBySlot(var1).isEmpty();
    }

    public abstract Iterable<ItemStack> getArmorSlots();

    public abstract ItemStack getItemBySlot(EquipmentSlot var1);

    public abstract void setItemSlot(EquipmentSlot var1, ItemStack var2);

    public float getArmorCoverPercentage() {
        Iterable var1 = this.getArmorSlots();
        int var2 = 0;
        int var3 = 0;

        for(Iterator var4 = var1.iterator(); var4.hasNext(); ++var2) {
            ItemStack var5 = (ItemStack)var4.next();
            if (!var5.isEmpty()) {
                ++var3;
            }
        }

        return var2 > 0 ? (float)var3 / (float)var2 : 0.0F;
    }

    public void setSprinting(boolean var1) {
        super.setSprinting(var1);
        AttributeInstance var2 = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (var2.getModifier(SPEED_MODIFIER_SPRINTING_UUID) != null) {
            var2.removeModifier(SPEED_MODIFIER_SPRINTING);
        }

        if (var1) {
            var2.addTransientModifier(SPEED_MODIFIER_SPRINTING);
        }

    }

    protected float getSoundVolume() {
        return 1.0F;
    }

    protected float getVoicePitch() {
        return this.isBaby() ? (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.5F : (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F;
    }

    protected boolean isImmobile() {
        return this.isDeadOrDying();
    }

    public void push(Entity var1) {
        if (!this.isSleeping()) {
            super.push(var1);
        }

    }

    private void dismountVehicle(Entity var1) {
        Vec3 var2;
        if (!var1.removed && !this.level.getBlockState(var1.blockPosition()).getBlock().is(BlockTags.PORTALS)) {
            var2 = var1.getDismountLocationForPassenger(this);
        } else {
            var2 = new Vec3(var1.getX(), var1.getY() + (double)var1.getBbHeight(), var1.getZ());
        }

        this.teleportTo(var2.x, var2.y, var2.z);
    }

    protected float getJumpPower() {
        return 0.42F * this.getBlockJumpFactor();
    }

    protected void jumpFromGround() {
        float var1 = this.getJumpPower();
        if (this.hasEffect(MobEffects.JUMP)) {
            var1 += 0.1F * (float)(this.getEffect(MobEffects.JUMP).getAmplifier() + 1);
        }

        Vec3 var2 = this.getDeltaMovement();
        this.setDeltaMovement(var2.x, var1, var2.z);
        if (this.isSprinting()) {
            float var3 = this.yRot * 0.017453292F;
            this.setDeltaMovement(this.getDeltaMovement().add(-Mth.sin(var3) * 0.2F, 0.0D, Mth.cos(var3) * 0.2F));
        }

        this.hasImpulse = true;
    }

    protected void jumpInLiquid(Tag<Fluid> var1) {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.03999999910593033D, 0.0D));
    }

    protected float getWaterSlowDown() {
        return 0.8F;
    }

    public boolean canStandOnFluid(Fluid var1) {
        return false;
    }

    public void travel(Vec3 var1) {
        if (this.isEffectiveAi() || this.isControlledByLocalInstance()) {
            double var2 = 0.08D;
            boolean var4 = this.getDeltaMovement().y <= 0.0D;
            if (var4 && this.hasEffect(MobEffects.SLOW_FALLING)) {
                var2 = 0.01D;
                this.fallDistance = 0.0F;
            }

            FluidState var5 = this.level.getFluidState(this.blockPosition());
            float var8;
            double var24;
            if (this.isInWater() && (!(this instanceof Player) || !((Player)this).abilities.flying) && !this.canStandOnFluid(var5.getType())) {
                var24 = this.getY();
                var8 = this.isSprinting() ? 0.9F : this.getWaterSlowDown();
                float var26 = 0.02F;
                float var28 = (float)EnchantmentHelper.getDepthStrider(this);
                if (var28 > 3.0F) {
                    var28 = 3.0F;
                }

                if (!this.onGround) {
                    var28 *= 0.5F;
                }

                if (var28 > 0.0F) {
                    var8 += (0.54600006F - var8) * var28 / 3.0F;
                    var26 += (this.getSpeed() - var26) * var28 / 3.0F;
                }

                if (this.hasEffect(MobEffects.DOLPHINS_GRACE)) {
                    var8 = 0.96F;
                }

                this.moveRelative(var26, var1);
                this.move(MoverType.SELF, this.getDeltaMovement());
                Vec3 var27 = this.getDeltaMovement();
                if (this.horizontalCollision && this.onClimbable()) {
                    var27 = new Vec3(var27.x, 0.2D, var27.z);
                }

                this.setDeltaMovement(var27.multiply(var8, 0.800000011920929D, var8));
                Vec3 var12 = this.getFluidFallingAdjustedMovement(var2, var4, this.getDeltaMovement());
                this.setDeltaMovement(var12);
                if (this.horizontalCollision && this.isFree(var12.x, var12.y + 0.6000000238418579D - this.getY() + var24, var12.z)) {
                    this.setDeltaMovement(var12.x, 0.30000001192092896D, var12.z);
                }
            } else if (this.isInLava() && (!(this instanceof Player) || !((Player)this).abilities.flying) && !this.canStandOnFluid(var5.getType())) {
                var24 = this.getY();
                this.moveRelative(0.02F, var1);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.5D));
                if (!this.isNoGravity()) {
                    this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -var2 / 4.0D, 0.0D));
                }

                Vec3 var22 = this.getDeltaMovement();
                if (this.horizontalCollision && this.isFree(var22.x, var22.y + 0.6000000238418579D - this.getY() + var24, var22.z)) {
                    this.setDeltaMovement(var22.x, 0.30000001192092896D, var22.z);
                }
            } else if (this.isFallFlying()) {
                Vec3 var6 = this.getDeltaMovement();
                if (var6.y > -0.5D) {
                    this.fallDistance = 1.0F;
                }

                Vec3 var7 = this.getLookAngle();
                var8 = this.xRot * 0.017453292F;
                double var9 = Math.sqrt(var7.x * var7.x + var7.z * var7.z);
                double var11 = Math.sqrt(getHorizontalDistanceSqr(var6));
                double var13 = var7.length();
                float var15 = Mth.cos(var8);
                var15 = (float)((double)var15 * (double)var15 * Math.min(1.0D, var13 / 0.4D));
                var6 = this.getDeltaMovement().add(0.0D, var2 * (-1.0D + (double)var15 * 0.75D), 0.0D);
                double var16;
                if (var6.y < 0.0D && var9 > 0.0D) {
                    var16 = var6.y * -0.1D * (double)var15;
                    var6 = var6.add(var7.x * var16 / var9, var16, var7.z * var16 / var9);
                }

                if (var8 < 0.0F && var9 > 0.0D) {
                    var16 = var11 * (double)(-Mth.sin(var8)) * 0.04D;
                    var6 = var6.add(-var7.x * var16 / var9, var16 * 3.2D, -var7.z * var16 / var9);
                }

                if (var9 > 0.0D) {
                    var6 = var6.add((var7.x / var9 * var11 - var6.x) * 0.1D, 0.0D, (var7.z / var9 * var11 - var6.z) * 0.1D);
                }

                this.setDeltaMovement(var6.multiply(0.9900000095367432D, 0.9800000190734863D, 0.9900000095367432D));
                this.move(MoverType.SELF, this.getDeltaMovement());
                if (this.horizontalCollision && !this.level.isClientSide) {
                    var16 = Math.sqrt(getHorizontalDistanceSqr(this.getDeltaMovement()));
                    double var18 = var11 - var16;
                    float var20 = (float)(var18 * 10.0D - 3.0D);
                    if (var20 > 0.0F) {
                        this.playSound(this.getFallDamageSound((int)var20), 1.0F, 1.0F);
                        this.hurt(DamageSource.FLY_INTO_WALL, var20);
                    }
                }

                if (this.onGround && !this.level.isClientSide) {
                    this.setSharedFlag(7, false);
                }
            } else {
                BlockPos var23 = this.getBlockPosBelowThatAffectsMyMovement();
                float var21 = this.level.getBlockState(var23).getBlock().getFriction();
                var8 = this.onGround ? var21 * 0.91F : 0.91F;
                Vec3 var25 = this.handleRelativeFrictionAndCalculateMovement(var1, var21);
                double var10 = var25.y;
                if (this.hasEffect(MobEffects.LEVITATION)) {
                    var10 += (0.05D * (double)(this.getEffect(MobEffects.LEVITATION).getAmplifier() + 1) - var25.y) * 0.2D;
                    this.fallDistance = 0.0F;
                } else if (this.level.isClientSide && !this.level.hasChunkAt(var23)) {
                    if (this.getY() > 0.0D) {
                        var10 = -0.1D;
                    } else {
                        var10 = 0.0D;
                    }
                } else if (!this.isNoGravity()) {
                    var10 -= var2;
                }

                this.setDeltaMovement(var25.x * (double)var8, var10 * 0.9800000190734863D, var25.z * (double)var8);
            }
        }

        this.calculateEntityAnimation(this, this instanceof FlyingAnimal);
    }

    public void calculateEntityAnimation(LivingEntity var1, boolean var2) {
        var1.animationSpeedOld = var1.animationSpeed;
        double var3 = var1.getX() - var1.xo;
        double var5 = var2 ? var1.getY() - var1.yo : 0.0D;
        double var7 = var1.getZ() - var1.zo;
        float var9 = Mth.sqrt(var3 * var3 + var5 * var5 + var7 * var7) * 4.0F;
        if (var9 > 1.0F) {
            var9 = 1.0F;
        }

        var1.animationSpeed += (var9 - var1.animationSpeed) * 0.4F;
        var1.animationPosition += var1.animationSpeed;
    }

    public Vec3 handleRelativeFrictionAndCalculateMovement(Vec3 var1, float var2) {
        this.moveRelative(this.getFrictionInfluencedSpeed(var2), var1);
        this.setDeltaMovement(this.handleOnClimbable(this.getDeltaMovement()));
        this.move(MoverType.SELF, this.getDeltaMovement());
        Vec3 var3 = this.getDeltaMovement();
        if ((this.horizontalCollision || this.jumping) && this.onClimbable()) {
            var3 = new Vec3(var3.x, 0.2D, var3.z);
        }

        return var3;
    }

    public Vec3 getFluidFallingAdjustedMovement(double var1, boolean var3, Vec3 var4) {
        if (!this.isNoGravity() && !this.isSprinting()) {
            double var5;
            if (var3 && Math.abs(var4.y - 0.005D) >= 0.003D && Math.abs(var4.y - var1 / 16.0D) < 0.003D) {
                var5 = -0.003D;
            } else {
                var5 = var4.y - var1 / 16.0D;
            }

            return new Vec3(var4.x, var5, var4.z);
        } else {
            return var4;
        }
    }

    private Vec3 handleOnClimbable(Vec3 var1) {
        if (this.onClimbable()) {
            this.fallDistance = 0.0F;
            float var2 = 0.15F;
            double var3 = Mth.clamp(var1.x, -0.15000000596046448D, 0.15000000596046448D);
            double var5 = Mth.clamp(var1.z, -0.15000000596046448D, 0.15000000596046448D);
            double var7 = Math.max(var1.y, -0.15000000596046448D);
            if (var7 < 0.0D && !this.getFeetBlockState().is(Blocks.SCAFFOLDING) && this.isSuppressingSlidingDownLadder() && this instanceof Player) {
                var7 = 0.0D;
            }

            var1 = new Vec3(var3, var7, var5);
        }

        return var1;
    }

    private float getFrictionInfluencedSpeed(float var1) {
        return this.onGround ? this.getSpeed() * (0.21600002F / (var1 * var1 * var1)) : this.flyingSpeed;
    }

    public float getSpeed() {
        return this.speed;
    }

    public void setSpeed(float var1) {
        this.speed = var1;
    }

    public boolean doHurtTarget(Entity var1) {
        this.setLastHurtMob(var1);
        return false;
    }

    public void tick() {
        super.tick();
        this.updatingUsingItem();
        this.updateSwimAmount();
        if (!this.level.isClientSide) {
            int var1 = this.getArrowCount();
            if (var1 > 0) {
                if (this.removeArrowTime <= 0) {
                    this.removeArrowTime = 20 * (30 - var1);
                }

                --this.removeArrowTime;
                if (this.removeArrowTime <= 0) {
                    this.setArrowCount(var1 - 1);
                }
            }

            int var2 = this.getStingerCount();
            if (var2 > 0) {
                if (this.removeStingerTime <= 0) {
                    this.removeStingerTime = 20 * (30 - var2);
                }

                --this.removeStingerTime;
                if (this.removeStingerTime <= 0) {
                    this.setStingerCount(var2 - 1);
                }
            }

            EquipmentSlot[] var3 = EquipmentSlot.values();
            int var4 = var3.length;

            for(int var5 = 0; var5 < var4; ++var5) {
                EquipmentSlot var6 = var3[var5];
                ItemStack var7;
                switch(var6.getType()) {
                    case HAND:
                        var7 = this.lastHandItemStacks.get(var6.getIndex());
                        break;
                    case ARMOR:
                        var7 = this.lastArmorItemStacks.get(var6.getIndex());
                        break;
                    default:
                        continue;
                }

                ItemStack var8 = this.getItemBySlot(var6);
                if (!ItemStack.matches(var8, var7)) {
                    ((ServerLevel)this.level).getChunkSource().broadcast(this, new ClientboundSetEquippedItemPacket(this.getId(), var6, var8));
                    if (!var7.isEmpty()) {
                        this.getAttributes().removeAttributeModifiers(var7.getAttributeModifiers(var6));
                    }

                    if (!var8.isEmpty()) {
                        this.getAttributes().addTransientAttributeModifiers(var8.getAttributeModifiers(var6));
                    }

                    switch(var6.getType()) {
                        case HAND:
                            this.lastHandItemStacks.set(var6.getIndex(), var8.copy());
                            break;
                        case ARMOR:
                            this.lastArmorItemStacks.set(var6.getIndex(), var8.copy());
                    }
                }
            }

            if (this.tickCount % 20 == 0) {
                this.getCombatTracker().recheckStatus();
            }

            if (!this.glowing) {
                boolean var12 = this.hasEffect(MobEffects.GLOWING);
                if (this.getSharedFlag(6) != var12) {
                    this.setSharedFlag(6, var12);
                }
            }

            if (this.isSleeping() && !this.checkBedExists()) {
                this.stopSleeping();
            }
        }

        this.aiStep();
        double var11 = this.getX() - this.xo;
        double var13 = this.getZ() - this.zo;
        float var14 = (float)(var11 * var11 + var13 * var13);
        float var15 = this.yBodyRot;
        float var16 = 0.0F;
        this.oRun = this.run;
        float var17 = 0.0F;
        if (var14 > 0.0025000002F) {
            var17 = 1.0F;
            var16 = (float)Math.sqrt(var14) * 3.0F;
            float var9 = (float)Mth.atan2(var13, var11) * 57.295776F - 90.0F;
            float var10 = Mth.abs(Mth.wrapDegrees(this.yRot) - var9);
            if (95.0F < var10 && var10 < 265.0F) {
                var15 = var9 - 180.0F;
            } else {
                var15 = var9;
            }
        }

        if (this.attackAnim > 0.0F) {
            var15 = this.yRot;
        }

        if (!this.onGround) {
            var17 = 0.0F;
        }

        this.run += (var17 - this.run) * 0.3F;
        this.level.getProfiler().push("headTurn");
        var16 = this.tickHeadTurn(var15, var16);
        this.level.getProfiler().pop();
        this.level.getProfiler().push("rangeChecks");

        while(this.yRot - this.yRotO < -180.0F) {
            this.yRotO -= 360.0F;
        }

        while(this.yRot - this.yRotO >= 180.0F) {
            this.yRotO += 360.0F;
        }

        while(this.yBodyRot - this.yBodyRotO < -180.0F) {
            this.yBodyRotO -= 360.0F;
        }

        while(this.yBodyRot - this.yBodyRotO >= 180.0F) {
            this.yBodyRotO += 360.0F;
        }

        while(this.xRot - this.xRotO < -180.0F) {
            this.xRotO -= 360.0F;
        }

        while(this.xRot - this.xRotO >= 180.0F) {
            this.xRotO += 360.0F;
        }

        while(this.yHeadRot - this.yHeadRotO < -180.0F) {
            this.yHeadRotO -= 360.0F;
        }

        while(this.yHeadRot - this.yHeadRotO >= 180.0F) {
            this.yHeadRotO += 360.0F;
        }

        this.level.getProfiler().pop();
        this.animStep += var16;
        if (this.isFallFlying()) {
            ++this.fallFlyTicks;
        } else {
            this.fallFlyTicks = 0;
        }

        if (this.isSleeping()) {
            this.xRot = 0.0F;
        }

    }

    protected float tickHeadTurn(float var1, float var2) {
        float var3 = Mth.wrapDegrees(var1 - this.yBodyRot);
        this.yBodyRot += var3 * 0.3F;
        float var4 = Mth.wrapDegrees(this.yRot - this.yBodyRot);
        boolean var5 = var4 < -90.0F || var4 >= 90.0F;
        if (var4 < -75.0F) {
            var4 = -75.0F;
        }

        if (var4 >= 75.0F) {
            var4 = 75.0F;
        }

        this.yBodyRot = this.yRot - var4;
        if (var4 * var4 > 2500.0F) {
            this.yBodyRot += var4 * 0.2F;
        }

        if (var5) {
            var2 *= -1.0F;
        }

        return var2;
    }

    public void aiStep() {
        if (this.noJumpDelay > 0) {
            --this.noJumpDelay;
        }

        if (this.isControlledByLocalInstance()) {
            this.lerpSteps = 0;
            this.setPacketCoordinates(this.getX(), this.getY(), this.getZ());
        }

        if (this.lerpSteps > 0) {
            double var1 = this.getX() + (this.lerpX - this.getX()) / (double)this.lerpSteps;
            double var3 = this.getY() + (this.lerpY - this.getY()) / (double)this.lerpSteps;
            double var5 = this.getZ() + (this.lerpZ - this.getZ()) / (double)this.lerpSteps;
            double var7 = Mth.wrapDegrees(this.lerpYRot - (double)this.yRot);
            this.yRot = (float)((double)this.yRot + var7 / (double)this.lerpSteps);
            this.xRot = (float)((double)this.xRot + (this.lerpXRot - (double)this.xRot) / (double)this.lerpSteps);
            --this.lerpSteps;
            this.setPos(var1, var3, var5);
            this.setRot(this.yRot, this.xRot);
        } else if (!this.isEffectiveAi()) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.98D));
        }

        if (this.lerpHeadSteps > 0) {
            this.yHeadRot = (float)((double)this.yHeadRot + Mth.wrapDegrees(this.lyHeadRot - (double)this.yHeadRot) / (double)this.lerpHeadSteps);
            --this.lerpHeadSteps;
        }

        Vec3 var13 = this.getDeltaMovement();
        double var2 = var13.x;
        double var4 = var13.y;
        double var6 = var13.z;
        if (Math.abs(var13.x) < 0.003D) {
            var2 = 0.0D;
        }

        if (Math.abs(var13.y) < 0.003D) {
            var4 = 0.0D;
        }

        if (Math.abs(var13.z) < 0.003D) {
            var6 = 0.0D;
        }

        this.setDeltaMovement(var2, var4, var6);
        this.level.getProfiler().push("ai");
        if (this.isImmobile()) {
            this.jumping = false;
            this.xxa = 0.0F;
            this.zza = 0.0F;
        } else if (this.isEffectiveAi()) {
            this.level.getProfiler().push("newAi");
            this.serverAiStep();
            this.level.getProfiler().pop();
        }

        this.level.getProfiler().pop();
        this.level.getProfiler().push("jump");
        if (this.jumping) {
            double var8 = this.getFluidHeight(FluidTags.WATER);
            boolean var10 = this.isInWater() && var8 > 0.0D;
            double var11 = this.getFluidJumpThreshold();
            if (var10 && (!this.onGround || var8 > var11)) {
                this.jumpInLiquid(FluidTags.WATER);
            } else if (this.isInLava()) {
                this.jumpInLiquid(FluidTags.LAVA);
            } else if ((this.onGround || var10 && var8 <= var11) && this.noJumpDelay == 0) {
                this.jumpFromGround();
                this.noJumpDelay = 10;
            }
        } else {
            this.noJumpDelay = 0;
        }

        this.level.getProfiler().pop();
        this.level.getProfiler().push("travel");
        this.xxa *= 0.98F;
        this.zza *= 0.98F;
        this.updateFallFlying();
        AABB var14 = this.getBoundingBox();
        this.travel(new Vec3(this.xxa, this.yya, this.zza));
        this.level.getProfiler().pop();
        this.level.getProfiler().push("push");
        if (this.autoSpinAttackTicks > 0) {
            --this.autoSpinAttackTicks;
            this.checkAutoSpinAttack(var14, this.getBoundingBox());
        }

        this.pushEntities();
        this.level.getProfiler().pop();
        if (!this.level.isClientSide && this.isSensitiveToWater() && this.isInWaterRainOrBubble()) {
            this.hurt(DamageSource.DROWN, 1.0F);
        }

    }

    public boolean isSensitiveToWater() {
        return false;
    }

    private void updateFallFlying() {
        boolean var1 = this.getSharedFlag(7);
        if (var1 && !this.onGround && !this.isPassenger() && !this.hasEffect(MobEffects.LEVITATION)) {
            ItemStack var2 = this.getItemBySlot(EquipmentSlot.CHEST);
            if (var2.getItem() == Items.ELYTRA && ElytraItem.isFlyEnabled(var2)) {
                var1 = true;
                if (!this.level.isClientSide && (this.fallFlyTicks + 1) % 20 == 0) {
                    var2.hurtAndBreak(1, this, (var0) -> {
                        var0.broadcastBreakEvent(EquipmentSlot.CHEST);
                    });
                }
            } else {
                var1 = false;
            }
        } else {
            var1 = false;
        }

        if (!this.level.isClientSide) {
            this.setSharedFlag(7, var1);
        }

    }

    protected void serverAiStep() {
    }

    protected void pushEntities() {
        List var1 = this.level.getEntities(this, this.getBoundingBox(), EntitySelector.pushableBy(this));
        if (!var1.isEmpty()) {
            int var2 = this.level.getGameRules().getInt(GameRules.RULE_MAX_ENTITY_CRAMMING);
            int var3;
            if (var2 > 0 && var1.size() > var2 - 1 && this.random.nextInt(4) == 0) {
                var3 = 0;

                for(int var4 = 0; var4 < var1.size(); ++var4) {
                    if (!((Entity)var1.get(var4)).isPassenger()) {
                        ++var3;
                    }
                }

                if (var3 > var2 - 1) {
                    this.hurt(DamageSource.CRAMMING, 6.0F);
                }
            }

            for(var3 = 0; var3 < var1.size(); ++var3) {
                Entity var5 = (Entity)var1.get(var3);
                this.doPush(var5);
            }
        }

    }

    protected void checkAutoSpinAttack(AABB var1, AABB var2) {
        AABB var3 = var1.minmax(var2);
        List var4 = this.level.getEntities(this, var3);
        if (!var4.isEmpty()) {
            for(int var5 = 0; var5 < var4.size(); ++var5) {
                Entity var6 = (Entity)var4.get(var5);
                if (var6 instanceof LivingEntity) {
                    this.doAutoAttackOnTouch((LivingEntity)var6);
                    this.autoSpinAttackTicks = 0;
                    this.setDeltaMovement(this.getDeltaMovement().scale(-0.2D));
                    break;
                }
            }
        } else if (this.horizontalCollision) {
            this.autoSpinAttackTicks = 0;
        }

        if (!this.level.isClientSide && this.autoSpinAttackTicks <= 0) {
            this.setLivingEntityFlag(4, false);
        }

    }

    protected void doPush(Entity var1) {
        var1.push(this);
    }

    protected void doAutoAttackOnTouch(LivingEntity var1) {
    }

    public void startAutoSpinAttack(int var1) {
        this.autoSpinAttackTicks = var1;
        if (!this.level.isClientSide) {
            this.setLivingEntityFlag(4, true);
        }

    }

    public boolean isAutoSpinAttack() {
        return (this.entityData.get(DATA_LIVING_ENTITY_FLAGS) & 4) != 0;
    }

    public void stopRiding() {
        Entity var1 = this.getVehicle();
        super.stopRiding();
        if (var1 != null && var1 != this.getVehicle() && !this.level.isClientSide) {
            this.dismountVehicle(var1);
        }

    }

    public void rideTick() {
        super.rideTick();
        this.oRun = this.run;
        this.run = 0.0F;
        this.fallDistance = 0.0F;
    }

    public void setJumping(boolean var1) {
        this.jumping = var1;
    }

    public void onItemPickup(ItemEntity var1) {
        Player var2 = var1.getThrower() != null ? this.level.getPlayerByUUID(var1.getThrower()) : null;
        if (var2 instanceof ServerPlayer) {
            CriteriaTriggers.ITEM_PICKED_UP_BY_ENTITY.trigger((ServerPlayer)var2, var1.getItem(), this);
        }

    }

    public void take(Entity var1, int var2) {
        if (!var1.removed && !this.level.isClientSide && (var1 instanceof ItemEntity || var1 instanceof AbstractArrow || var1 instanceof ExperienceOrb)) {
            ((ServerLevel)this.level).getChunkSource().broadcast(var1, new ClientboundTakeItemEntityPacket(var1.getId(), this.getId(), var2));
        }

    }

    public boolean canSee(Entity var1) {
        Vec3 var2 = new Vec3(this.getX(), this.getEyeY(), this.getZ());
        Vec3 var3 = new Vec3(var1.getX(), var1.getEyeY(), var1.getZ());
        return this.level.clip(new ClipContext(var2, var3, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this)).getType() == Type.MISS;
    }

    public float getViewYRot(float var1) {
        return var1 == 1.0F ? this.yHeadRot : Mth.lerp(var1, this.yHeadRotO, this.yHeadRot);
    }

    public boolean isEffectiveAi() {
        return !this.level.isClientSide;
    }

    public boolean isPickable() {
        return !this.removed;
    }

    public boolean isPushable() {
        return this.isAlive() && !this.onClimbable();
    }

    protected void markHurt() {
        this.hurtMarked = this.random.nextDouble() >= this.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
    }

    public float getYHeadRot() {
        return this.yHeadRot;
    }

    public void setYHeadRot(float var1) {
        this.yHeadRot = var1;
    }

    public void setYBodyRot(float var1) {
        this.yBodyRot = var1;
    }

    public float getAbsorptionAmount() {
        return this.absorptionAmount;
    }

    public void setAbsorptionAmount(float var1) {
        if (var1 < 0.0F) {
            var1 = 0.0F;
        }

        this.absorptionAmount = var1;
    }

    public void onEnterCombat() {
    }

    public void onLeaveCombat() {
    }

    protected void updateEffectVisibility() {
        this.effectsDirty = true;
    }

    public abstract HumanoidArm getMainArm();

    public boolean isUsingItem() {
        return (this.entityData.get(DATA_LIVING_ENTITY_FLAGS) & 1) > 0;
    }

    public InteractionHand getUsedItemHand() {
        return (this.entityData.get(DATA_LIVING_ENTITY_FLAGS) & 2) > 0 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private void updatingUsingItem() {
        if (this.isUsingItem()) {
            if (ItemStack.isSameIgnoreDurability(this.getItemInHand(this.getUsedItemHand()), this.useItem)) {
                this.useItem = this.getItemInHand(this.getUsedItemHand());
                this.useItem.onUseTick(this.level, this, this.getUseItemRemainingTicks());
                if (this.shouldTriggerItemUseEffects()) {
                    this.triggerItemUseEffects(this.useItem, 5);
                }

                if (--this.useItemRemaining == 0 && !this.level.isClientSide && !this.useItem.useOnRelease()) {
                    this.completeUsingItem();
                }
            } else {
                this.stopUsingItem();
            }
        }

    }

    private boolean shouldTriggerItemUseEffects() {
        int var1 = this.getUseItemRemainingTicks();
        FoodProperties var2 = this.useItem.getItem().getFoodProperties();
        boolean var3 = var2 != null && var2.isFastFood();
        var3 |= var1 <= this.useItem.getUseDuration() - 7;
        return var3 && var1 % 4 == 0;
    }

    private void updateSwimAmount() {
        this.swimAmountO = this.swimAmount;
        if (this.isVisuallySwimming()) {
            this.swimAmount = Math.min(1.0F, this.swimAmount + 0.09F);
        } else {
            this.swimAmount = Math.max(0.0F, this.swimAmount - 0.09F);
        }

    }

    protected void setLivingEntityFlag(int var1, boolean var2) {
        byte var3 = this.entityData.get(DATA_LIVING_ENTITY_FLAGS);
        int var4;
        if (var2) {
            var4 = var3 | var1;
        } else {
            var4 = var3 & ~var1;
        }

        this.entityData.set(DATA_LIVING_ENTITY_FLAGS, (byte)var4);
    }

    public void startUsingItem(InteractionHand var1) {
        ItemStack var2 = this.getItemInHand(var1);
        if (!var2.isEmpty() && !this.isUsingItem()) {
            this.useItem = var2;
            this.useItemRemaining = var2.getUseDuration();
            if (!this.level.isClientSide) {
                this.setLivingEntityFlag(1, true);
                this.setLivingEntityFlag(2, var1 == InteractionHand.OFF_HAND);
            }

        }
    }

    public void onSyncedDataUpdated(EntityDataAccessor<?> var1) {
        super.onSyncedDataUpdated(var1);
        if (SLEEPING_POS_ID.equals(var1)) {
            if (this.level.isClientSide) {
                this.getSleepingPos().ifPresent(this::setPosToBed);
            }
        } else if (DATA_LIVING_ENTITY_FLAGS.equals(var1) && this.level.isClientSide) {
            if (this.isUsingItem() && this.useItem.isEmpty()) {
                this.useItem = this.getItemInHand(this.getUsedItemHand());
                if (!this.useItem.isEmpty()) {
                    this.useItemRemaining = this.useItem.getUseDuration();
                }
            } else if (!this.isUsingItem() && !this.useItem.isEmpty()) {
                this.useItem = ItemStack.EMPTY;
                this.useItemRemaining = 0;
            }
        }

    }

    public void lookAt(Anchor var1, Vec3 var2) {
        super.lookAt(var1, var2);
        this.yHeadRotO = this.yHeadRot;
        this.yBodyRot = this.yHeadRot;
        this.yBodyRotO = this.yBodyRot;
    }

    protected void triggerItemUseEffects(ItemStack var1, int var2) {
        if (!var1.isEmpty() && this.isUsingItem()) {
            if (var1.getUseAnimation() == UseAnim.DRINK) {
                this.playSound(this.getDrinkingSound(var1), 0.5F, this.level.random.nextFloat() * 0.1F + 0.9F);
            }

            if (var1.getUseAnimation() == UseAnim.EAT) {
                this.spawnItemParticles(var1, var2);
                this.playSound(this.getEatingSound(var1), 0.5F + 0.5F * (float)this.random.nextInt(2), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
            }

        }
    }

    private void spawnItemParticles(ItemStack var1, int var2) {
        for(int var3 = 0; var3 < var2; ++var3) {
            Vec3 var4 = new Vec3(((double)this.random.nextFloat() - 0.5D) * 0.1D, Math.random() * 0.1D + 0.1D, 0.0D);
            var4 = var4.xRot(-this.xRot * 0.017453292F);
            var4 = var4.yRot(-this.yRot * 0.017453292F);
            double var5 = (double)(-this.random.nextFloat()) * 0.6D - 0.3D;
            Vec3 var7 = new Vec3(((double)this.random.nextFloat() - 0.5D) * 0.3D, var5, 0.6D);
            var7 = var7.xRot(-this.xRot * 0.017453292F);
            var7 = var7.yRot(-this.yRot * 0.017453292F);
            var7 = var7.add(this.getX(), this.getEyeY(), this.getZ());
            this.level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, var1), var7.x, var7.y, var7.z, var4.x, var4.y + 0.05D, var4.z);
        }

    }

    protected void completeUsingItem() {
        if (!this.useItem.equals(this.getItemInHand(this.getUsedItemHand()))) {
            this.releaseUsingItem();
        } else {
            if (!this.useItem.isEmpty() && this.isUsingItem()) {
                this.triggerItemUseEffects(this.useItem, 16);
                this.setItemInHand(this.getUsedItemHand(), this.useItem.finishUsingItem(this.level, this));
                this.stopUsingItem();
            }

        }
    }

    public ItemStack getUseItem() {
        return this.useItem;
    }

    public int getUseItemRemainingTicks() {
        return this.useItemRemaining;
    }

    public int getTicksUsingItem() {
        return this.isUsingItem() ? this.useItem.getUseDuration() - this.getUseItemRemainingTicks() : 0;
    }

    public void releaseUsingItem() {
        if (!this.useItem.isEmpty()) {
            this.useItem.releaseUsing(this.level, this, this.getUseItemRemainingTicks());
            if (this.useItem.useOnRelease()) {
                this.updatingUsingItem();
            }
        }

        this.stopUsingItem();
    }

    public void stopUsingItem() {
        if (!this.level.isClientSide) {
            this.setLivingEntityFlag(1, false);
        }

        this.useItem = ItemStack.EMPTY;
        this.useItemRemaining = 0;
    }

    public boolean isBlocking() {
        if (this.isUsingItem() && !this.useItem.isEmpty()) {
            Item var1 = this.useItem.getItem();
            if (var1.getUseAnimation(this.useItem) != UseAnim.BLOCK) {
                return false;
            } else {
                return var1.getUseDuration(this.useItem) - this.useItemRemaining >= 5;
            }
        } else {
            return false;
        }
    }

    public boolean isSuppressingSlidingDownLadder() {
        return this.isShiftKeyDown();
    }

    public boolean isFallFlying() {
        return this.getSharedFlag(7);
    }

    public boolean isVisuallySwimming() {
        return super.isVisuallySwimming() || !this.isFallFlying() && this.getPose() == Pose.FALL_FLYING;
    }

    public boolean randomTeleport(double var1, double var3, double var5, boolean var7) {
        double var8 = this.getX();
        double var10 = this.getY();
        double var12 = this.getZ();
        double var14 = var3;
        boolean var16 = false;
        BlockPos var17 = new BlockPos(var1, var3, var5);
        Level var18 = this.level;
        if (var18.hasChunkAt(var17)) {
            boolean var19 = false;

            while(!var19 && var17.getY() > 0) {
                BlockPos var20 = var17.below();
                BlockState var21 = var18.getBlockState(var20);
                if (var21.getMaterial().blocksMotion()) {
                    var19 = true;
                } else {
                    --var14;
                    var17 = var20;
                }
            }

            if (var19) {
                this.teleportTo(var1, var14, var5);
                if (var18.noCollision(this) && !var18.containsAnyLiquid(this.getBoundingBox())) {
                    var16 = true;
                }
            }
        }

        if (!var16) {
            this.teleportTo(var8, var10, var12);
            return false;
        } else {
            if (var7) {
                var18.broadcastEntityEvent(this, (byte)46);
            }

            if (this instanceof PathfinderMob) {
                ((PathfinderMob)this).getNavigation().stop();
            }

            return true;
        }
    }

    public boolean isAffectedByPotions() {
        return true;
    }

    public boolean attackable() {
        return true;
    }

    public boolean canTakeItem(ItemStack var1) {
        return false;
    }

    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddMobPacket(this);
    }

    public EntityDimensions getDimensions(Pose var1) {
        return var1 == Pose.SLEEPING ? SLEEPING_DIMENSIONS : super.getDimensions(var1).scale(this.getScale());
    }

    public ImmutableList<Pose> getDismountPoses() {
        return ImmutableList.of(Pose.STANDING);
    }

    public AABB getLocalBoundsForPose(Pose var1) {
        EntityDimensions var2 = this.getDimensions(var1);
        return new AABB(-var2.width / 2.0F, 0.0D, -var2.width / 2.0F, var2.width / 2.0F, var2.height, var2.width / 2.0F);
    }

    public Optional<BlockPos> getSleepingPos() {
        return this.entityData.get(SLEEPING_POS_ID);
    }

    public void setSleepingPos(BlockPos var1) {
        this.entityData.set(SLEEPING_POS_ID, Optional.of(var1));
    }

    public void clearSleepingPos() {
        this.entityData.set(SLEEPING_POS_ID, Optional.empty());
    }

    public boolean isSleeping() {
        return this.getSleepingPos().isPresent();
    }

    public void startSleeping(BlockPos var1) {
        if (this.isPassenger()) {
            this.stopRiding();
        }

        BlockState var2 = this.level.getBlockState(var1);
        if (var2.getBlock() instanceof BedBlock) {
            this.level.setBlock(var1, var2.setValue(BedBlock.OCCUPIED, true), 3);
        }

        this.setPose(Pose.SLEEPING);
        this.setPosToBed(var1);
        this.setSleepingPos(var1);
        this.setDeltaMovement(Vec3.ZERO);
        this.hasImpulse = true;
    }

    private void setPosToBed(BlockPos var1) {
        this.setPos((double)var1.getX() + 0.5D, (double)var1.getY() + 0.6875D, (double)var1.getZ() + 0.5D);
    }

    private boolean checkBedExists() {
        return this.getSleepingPos().map((var1) -> {
            return this.level.getBlockState(var1).getBlock() instanceof BedBlock;
        }).orElse(false);
    }

    public void stopSleeping() {
        Optional<BlockPos> var10000 = this.getSleepingPos();
        Level level = this.level;
        level.getClass();
        var10000.filter(level::hasChunkAt).ifPresent((block) -> {
            BlockState blockState = this.level.getBlockState(block);
            if (blockState.getBlock() instanceof BedBlock) {
                this.level.setBlock(block, blockState.setValue(BedBlock.OCCUPIED, false), 3);
                Vec3 var3 = BedBlock.findStandUpPosition(this.getType(), this.level, block, 0).orElseGet(() -> {
                    BlockPos var1 = block.above();
                    return new Vec3((double)var1.getX() + 0.5D, (double)var1.getY() + 0.1D, (double)var1.getZ() + 0.5D);
                });
                this.setPos(var3.x, var3.y, var3.z);
            }

        });
        Vec3 var1 = this.position();
        this.setPose(Pose.STANDING);
        this.setPos(var1.x, var1.y, var1.z);
        this.clearSleepingPos();
    }

    public boolean isInWall() {
        return !this.isSleeping() && super.isInWall();
    }

    protected final float getEyeHeight(Pose var1, EntityDimensions var2) {
        return var1 == Pose.SLEEPING ? 0.2F : this.getStandingEyeHeight(var1, var2);
    }

    protected float getStandingEyeHeight(Pose var1, EntityDimensions var2) {
        return super.getEyeHeight(var1, var2);
    }

    public ItemStack getProjectile(ItemStack var1) {
        return ItemStack.EMPTY;
    }

    public ItemStack eat(Level var1, ItemStack var2) {
        if (var2.isEdible()) {
            var1.playSound(null, this.getX(), this.getY(), this.getZ(), this.getEatingSound(var2), SoundSource.NEUTRAL, 1.0F, 1.0F + (var1.random.nextFloat() - var1.random.nextFloat()) * 0.4F);
            this.addEatEffect(var2, var1, this);
            if (!(this instanceof Player) || !((Player)this).abilities.instabuild) {
                var2.shrink(1);
            }
        }

        return var2;
    }

    private void addEatEffect(ItemStack var1, Level var2, LivingEntity var3) {
        Item var4 = var1.getItem();
        if (var4.isEdible()) {
            List var5 = var4.getFoodProperties().getEffects();
            Iterator var6 = var5.iterator();

            while(var6.hasNext()) {
                Pair var7 = (Pair)var6.next();
                if (!var2.isClientSide && var7.getLeft() != null && var2.random.nextFloat() < (Float)var7.getRight()) {
                    var3.addEffect(new MobEffectInstance((MobEffectInstance)var7.getLeft()));
                }
            }
        }

    }

    private static byte entityEventForEquipmentBreak(EquipmentSlot var0) {
        switch(var0) {
            case MAINHAND:
                return 47;
            case OFFHAND:
                return 48;
            case HEAD:
                return 49;
            case CHEST:
                return 50;
            case FEET:
                return 52;
            case LEGS:
                return 51;
            default:
                return 47;
        }
    }

    public void broadcastBreakEvent(EquipmentSlot var1) {
        this.level.broadcastEntityEvent(this, entityEventForEquipmentBreak(var1));
    }

    public void broadcastBreakEvent(InteractionHand var1) {
        this.broadcastBreakEvent(var1 == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
    }

    static {
        SPEED_MODIFIER_SPRINTING = new AttributeModifier(SPEED_MODIFIER_SPRINTING_UUID, "Sprinting speed boost", 0.30000001192092896D, Operation.MULTIPLY_TOTAL);
        DATA_LIVING_ENTITY_FLAGS = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.BYTE);
        DATA_HEALTH_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.FLOAT);
        DATA_EFFECT_COLOR_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.INT);
        DATA_EFFECT_AMBIENCE_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.BOOLEAN);
        DATA_ARROW_COUNT_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.INT);
        DATA_STINGER_COUNT_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.INT);
        SLEEPING_POS_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
        SLEEPING_DIMENSIONS = EntityDimensions.fixed(0.2F, 0.2F);
    }
}
