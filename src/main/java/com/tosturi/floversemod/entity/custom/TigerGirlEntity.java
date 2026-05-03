package com.tosturi.floversemod.entity.custom;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MeleeAttack;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.behavior.RandomStroll;
import net.minecraft.world.entity.ai.behavior.SetEntityLookTargetSometimes;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromAttackTargetIfTargetOutOfReach;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.ai.behavior.Swim;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.Level;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public class TigerGirlEntity extends AbstractVillager {

    private static final ResourceKey<TradeSet> TIGER_GIRL_TRADES = ResourceKey.create(
            Registries.TRADE_SET,
            Identifier.fromNamespaceAndPath("floversemod", "tiger_girl_trades")
    );

    private static final Brain.Provider<TigerGirlEntity> BRAIN_PROVIDER = Brain.<TigerGirlEntity>provider(
            List.of(
                    SensorType.NEAREST_LIVING_ENTITIES,
                    SensorType.NEAREST_PLAYERS,
                    SensorType.VILLAGER_HOSTILES,
                    SensorType.HURT_BY
            ),
            body -> {
                List<ActivityData<TigerGirlEntity>> activities = new ArrayList<>();
                activities.add(ActivityData.create(Activity.CORE, 0,
                        ImmutableList.<BehaviorControl<? super TigerGirlEntity>>of(
                                new Swim<>(0.8f),
                                new LookAtTargetSink(45, 90),
                                new MoveToTargetSink()
                        )
                ));
                activities.add(ActivityData.create(Activity.IDLE,
                        ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super TigerGirlEntity>>>of(
                                Pair.of(1, StartAttacking.<TigerGirlEntity>create(
                                        (level, self) -> !self.isTrading(),
                                        (level, self) -> self.getBrain()
                                                .getMemory(MemoryModuleType.NEAREST_HOSTILE)
                                                .filter(LivingEntity::isAlive)
                                )),
                                Pair.of(2, RandomStroll.stroll(0.5f)),
                                Pair.of(3, SetEntityLookTargetSometimes.create(EntityType.PLAYER, 6.0f, UniformInt.of(30, 60)))
                        )
                ));
                activities.add(ActivityData.create(Activity.FIGHT,
                        ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super TigerGirlEntity>>>of(
                                Pair.of(0, StopAttackingIfTargetInvalid.<TigerGirlEntity>create()),
                                Pair.of(1, SetWalkTargetFromAttackTargetIfTargetOutOfReach.<TigerGirlEntity>create(0.65f)),
                                Pair.of(2, MeleeAttack.<TigerGirlEntity>create(20))
                        ),
                        ImmutableSet.of(Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT)),
                        ImmutableSet.of(MemoryModuleType.ATTACK_TARGET, MemoryModuleType.WALK_TARGET)
                ));
                return activities;
            }
    );

    public TigerGirlEntity(EntityType<? extends AbstractVillager> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D);
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        super.setTradingPlayer(player);
        if (player != null) {
            this.getNavigation().stop();
            this.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }
    }

    // -----------------------------------------------------------------------
    // Brain

    @Override
    protected Brain<TigerGirlEntity> makeBrain(Brain.Packed packed) {
        Brain<TigerGirlEntity> brain = BRAIN_PROVIDER.makeBrain(this, packed);
        brain.setCoreActivities(java.util.Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
        return brain;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void customServerAiStep(ServerLevel level) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("tigerGirlBrain");
        Brain<TigerGirlEntity> brain = (Brain<TigerGirlEntity>) this.getBrain();
        brain.tick(level, this);
        brain.setActiveActivityToFirstValid(List.of(Activity.FIGHT, Activity.IDLE));
        if (this.isTrading()) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            this.getNavigation().stop();
        } else if (this.hasHome() && brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            BlockPos home = this.getHomePosition();
            double leashRange = this.getHomeRadius() + 16.0;
            if (this.distanceToSqr(home.getX() + 0.5, this.getY(), home.getZ() + 0.5) > leashRange * leashRange) {
                brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
                brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                this.getNavigation().stop();
            }
        }
        profiler.pop();
        super.customServerAiStep(level);
    }

    // -----------------------------------------------------------------------
    // Trading

    @Override
    public @NonNull InteractionResult mobInteract(@NonNull Player player, @NonNull InteractionHand hand) {
        if (this.isAlive() && !this.isTrading() && hand == InteractionHand.MAIN_HAND) {
            if (!this.level().isClientSide()) {
                if (this.getOffers().isEmpty()) {
                    this.updateTrades((ServerLevel) this.level());
                }
                this.setTradingPlayer(player);
                this.openTradingScreen(player, this.getDisplayName(), 1);
                return InteractionResult.SUCCESS_SERVER;
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void updateTrades(ServerLevel level) {
        this.addOffersFromTradeSet(level, this.getOffers(), TIGER_GIRL_TRADES);
    }

    @Override
    protected void rewardTradeXp(@NonNull MerchantOffer offer) {}

    @Override
    public boolean isBaby() {
        return false;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(@NonNull ServerLevel level, @NonNull AgeableMob otherParent) {
        return null;
    }
}
