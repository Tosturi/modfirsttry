package com.tosturi.floversemod.event;

import com.tosturi.floversemod.Config;
import com.tosturi.floversemod.FloVerseMod;
import com.tosturi.floversemod.data.TigerGirlVillageData;
import com.tosturi.floversemod.entity.ModEntities;
import com.tosturi.floversemod.entity.custom.TigerGirlEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = FloVerseMod.MODID)
public class TigerGirlVillageHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int CHECK_INTERVAL = 600;
    private static int tickCounter = 0;

    private static final TigerGirlRespawnManager respawnManager = new TigerGirlRespawnManager();

    // Bells whose TigerGirl was not found on the last liveness check.
    // A respawn is only scheduled after two consecutive failed checks,
    // preventing false positives when TigerGirl's chunk hasn't loaded yet.
    private static final Set<BlockPos> pendingLivenessConfirmation = new HashSet<>();

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        tickCounter = 0;
        pendingLivenessConfirmation.clear();
    }

    // -----------------------------------------------------------------------
    // Village registration — fired once per villager spawn, not on a global scan

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof Villager villager)) return;

        // Search from the villager's current position — HOME memory is not populated yet
        // when EntityJoinLevelEvent fires on world load, so we can't rely on it.
        BlockPos villagerPos = villager.blockPosition();
        level.getPoiManager()
                .getInRange(h -> h.is(PoiTypes.MEETING), villagerPos, 64, PoiManager.Occupancy.ANY)
                .map(PoiRecord::getPos)
                .findFirst()
                .ifPresent(bellPos -> {
                    TigerGirlVillageData data = TigerGirlVillageData.get(level);
                    if (data.registerVillage(bellPos)) {
                        LOGGER.info("[TigerGirl] New village registered at bell {}, attempting spawn", bellPos);
                        spawnTigerGirl(level, bellPos, data);
                    }
                });
    }

    // -----------------------------------------------------------------------
    // Tick loop — respawn timers + liveness check only

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.dimension() != Level.OVERWORLD) continue;
            TigerGirlVillageData data = TigerGirlVillageData.get(level);

            // Fire due respawn timers — only when the bell's chunk is loaded so that
            // heightmap and block queries in findSpawnPos return real values.
            List<BlockPos> due = respawnManager.collectDue(level, data);
            for (BlockPos bell : due) {
                if (!level.isLoaded(bell)) {
                    data.schedulePendingRespawn(bell, level.getGameTime() + CHECK_INTERVAL);
                    continue;
                }
                TigerGirlVillageData.VillageEntry entry = data.getEntry(bell);
                if (entry != null && !entry.villageDead()) {
                    spawnTigerGirl(level, bell, data);
                }
            }

            // Liveness check: only fires when a player is within VILLAGE_RADIUS of the bell
            // (horizontally). That guarantees both the bell's chunk AND every chunk within
            // TigerGirl's home range are fully loaded, eliminating false positives caused by
            // TigerGirl's chunk loading a tick after the bell's chunk.
            double radius = Config.VILLAGE_RADIUS.get();
            double radiusSq = radius * radius;
            for (Map.Entry<BlockPos, TigerGirlVillageData.VillageEntry> mapEntry
                    : data.getAllEntries().entrySet()) {

                BlockPos bell = mapEntry.getKey();
                TigerGirlVillageData.VillageEntry entry = mapEntry.getValue();

                boolean playerNearBell = level.players().stream().anyMatch(p -> {
                    double dx = p.getX() - bell.getX();
                    double dz = p.getZ() - bell.getZ();
                    return dx * dx + dz * dz < radiusSq;
                });
                if (!playerNearBell) continue;

                // Village alive check — only reliable when chunks are loaded (player nearby).
                boolean hasVillagers = !level.getEntitiesOfClass(
                        Villager.class, new AABB(bell).inflate(radius)).isEmpty();
                if (!hasVillagers && !entry.villageDead()) {
                    LOGGER.info("[TigerGirl] No villagers near bell {}, marking village dead", bell);
                    data.setVillageDead(bell, true);
                    entry = data.getEntry(bell); // refresh after mutation
                } else if (hasVillagers && entry.villageDead()) {
                    LOGGER.info("[TigerGirl] Villagers returned to bell {}, reviving village", bell);
                    data.setVillageDead(bell, false);
                    entry = data.getEntry(bell);
                    // Village revived but TigerGirl was never rescheduled (respawn fired while village
                    // was dead and consumed the pending timer without rescheduling). Trigger a new spawn.
                    if (entry.tigerGirlUUID() == null && !data.getPendingRespawns().containsKey(bell)) {
                        LOGGER.info("[TigerGirl] Village {} revived with no TigerGirl, scheduling spawn", bell);
                        respawnManager.schedule(bell, level.getGameTime(), data);
                    }
                }

                if (entry.tigerGirlUUID() == null) continue;

                Entity found = level.getEntity(entry.tigerGirlUUID());
                if (found instanceof TigerGirlEntity tg && tg.isAlive()) {
                    pendingLivenessConfirmation.remove(bell);
                    continue;
                }

                // Check for an orphan (UUID mismatch) before concluding she's gone.
                List<TigerGirlEntity> orphans = level.getEntitiesOfClass(
                        TigerGirlEntity.class, new AABB(bell).inflate(radius));
                if (!orphans.isEmpty()) {
                    data.setTigerGirl(bell, orphans.get(0).getUUID());
                    pendingLivenessConfirmation.remove(bell);
                    continue;
                }

                // Two-strike rule: only schedule a respawn after two consecutive failed checks.
                // This prevents false positives when TigerGirl's chunk hasn't loaded yet
                // (e.g. right after a player re-logs or she chased a monster far from the bell).
                if (!pendingLivenessConfirmation.contains(bell)) {
                    LOGGER.info("[TigerGirl] Liveness check: TigerGirl {} not found near bell {}, will confirm next check",
                            entry.tigerGirlUUID(), bell);
                    pendingLivenessConfirmation.add(bell);
                    continue;
                }

                pendingLivenessConfirmation.remove(bell);
                LOGGER.info("[TigerGirl] Liveness check: TigerGirl {} confirmed missing (two checks) near bell {}, scheduling respawn",
                        entry.tigerGirlUUID(), bell);
                // Do NOT clearTigerGirl here — only onLivingDeath owns that.
                // spawnTigerGirl will detect the stored UUID and abort if she's
                // just in an unloaded chunk, preventing false-positive duplicates.
                respawnManager.schedule(bell, level.getGameTime(), data);
            }

            // Village discovery: scan for unregistered bells near players.
            // This is the primary discovery mechanism — EntityJoinLevelEvent fires during
            // chunk load when the bell's POI may not be ready yet, so this tick scan acts
            // as a reliable fallback that runs once all chunks are fully loaded.
            for (ServerPlayer player : level.players()) {
                level.getPoiManager()
                        .getInRange(h -> h.is(PoiTypes.MEETING), player.blockPosition(), 128, PoiManager.Occupancy.ANY)
                        .map(PoiRecord::getPos)
                        .forEach(bellPos -> {
                            if (data.registerVillage(bellPos)) {
                                LOGGER.info("[TigerGirl] Tick scan registered new village at bell {}, spawning", bellPos);
                                spawnTigerGirl(level, bellPos, data);
                            }
                        });
            }
        }
    }

    // -----------------------------------------------------------------------
    // Death handler

    @SubscribeEvent
    public static void onLivingConversion(LivingConversionEvent.Pre event) {
        if (event.getEntity() instanceof TigerGirlEntity) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof TigerGirlEntity tigerGirl)) return;
        if (!(tigerGirl.level() instanceof ServerLevel level)) return;

        TigerGirlVillageData data = TigerGirlVillageData.get(level);
        UUID dyingUUID = tigerGirl.getUUID();

        BlockPos bell = data.getBellPosForUUID(dyingUUID);
        if (bell == null) {
            LOGGER.warn("[TigerGirl] TigerGirl {} died but had no village bell mapping!", dyingUUID);
            return;
        }

        LOGGER.info("[TigerGirl] TigerGirl {} died at {}, scheduling respawn for bell {}", dyingUUID, tigerGirl.blockPosition(), bell);
        data.clearTigerGirl(bell);
        respawnManager.schedule(bell, level.getGameTime(), data);
    }

    // -----------------------------------------------------------------------
    // Spawn helpers

    private static void spawnTigerGirl(ServerLevel level, BlockPos bell, TigerGirlVillageData data) {
        double radius = Config.VILLAGE_RADIUS.get();
        double radiusSq = radius * radius;

        // Dead-village guard: never spawn if all villagers have left or been killed.
        TigerGirlVillageData.VillageEntry bellEntry = data.getEntry(bell);
        if (bellEntry != null && bellEntry.villageDead()) {
            LOGGER.info("[TigerGirl] Village at bell {} is dead (no villagers), skipping spawn", bell);
            return;
        }

        // Precheck: if data already records a UUID for this bell, verify before spawning.
        // onLivingDeath is the only path that clears the UUID. If it's still set, either:
        //   - she's alive (somewhere) → cancel
        //   - getEntity returns null → she's frozen in an unloaded chunk (alive) → reschedule
        //   - she's present but dead (shouldn't happen; onLivingDeath always fires) → clear and proceed
        if (bellEntry != null && bellEntry.tigerGirlUUID() != null) {
            Entity stored = level.getEntity(bellEntry.tigerGirlUUID());
            if (stored instanceof TigerGirlEntity tg && tg.isAlive()) {
                return;
            }
            if (stored == null) {
                data.schedulePendingRespawn(bell, level.getGameTime() + CHECK_INTERVAL);
                return;
            }
            data.clearTigerGirl(bell);
        }

        // Guard 1: TigerGirl entity physically near this bell (covers normal case + data loss recovery).
        List<TigerGirlEntity> nearby = level.getEntitiesOfClass(
                TigerGirlEntity.class, new AABB(bell).inflate(radius));
        if (!nearby.isEmpty()) {
            TigerGirlEntity existing = nearby.get(0);
            LOGGER.warn("[TigerGirl] TigerGirl {} already exists near bell {}, repairing data and aborting duplicate spawn",
                    existing.getUUID(), bell);
            data.setTigerGirl(bell, existing.getUUID());
            return;
        }

        // Guard 2: another registered bell within VILLAGE_RADIUS already tracks a live TigerGirl.
        // This catches the case where the village has a second bell and the TigerGirl is
        // physically near that other bell (not this one), so the entity search above missed her.
        for (Map.Entry<BlockPos, TigerGirlVillageData.VillageEntry> mapEntry : data.getAllEntries().entrySet()) {
            BlockPos otherBell = mapEntry.getKey();
            UUID otherUUID = mapEntry.getValue().tigerGirlUUID();
            if (otherUUID == null) continue;
            double dx = bell.getX() - otherBell.getX();
            double dz = bell.getZ() - otherBell.getZ();
            if (dx * dx + dz * dz > radiusSq) continue;
            Entity found = level.getEntity(otherUUID);
            if (found instanceof TigerGirlEntity tg && tg.isAlive()) {
                LOGGER.warn("[TigerGirl] Bell {} is within village radius of bell {} which already has TigerGirl {}, aborting duplicate spawn",
                        bell, otherBell, otherUUID);
                data.setTigerGirl(bell, otherUUID);
                return;
            }
            if (found == null) {
                // UUID set but entity not in any loaded chunk — she's alive but frozen.
                // Abort to avoid spawning a duplicate that would coexist once she loads.
                data.schedulePendingRespawn(bell, level.getGameTime() + CHECK_INTERVAL);
                return;
            }
        }

        BlockPos spawnPos = findSpawnPos(level, bell);
        if (spawnPos == null) {
            LOGGER.warn("[TigerGirl] Could not find valid spawn position near bell {}, scheduling retry in {} ticks", bell, CHECK_INTERVAL);
            data.schedulePendingRespawn(bell, level.getGameTime() + CHECK_INTERVAL);
            return;
        }

        TigerGirlEntity entity = ModEntities.TIGER_GIRL.get().create(
                level, null, spawnPos, EntitySpawnReason.EVENT, false, false
        );
        if (entity == null) {
            LOGGER.error("[TigerGirl] EntityType.create() returned null at {}", spawnPos);
            return;
        }

        entity.teleportTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        entity.setHomeTo(bell, 20);
        if (!level.addFreshEntity(entity)) {
            LOGGER.error("[TigerGirl] addFreshEntity() rejected entity at {}", spawnPos);
            return;
        }
        data.setTigerGirl(bell, entity.getUUID());
        LOGGER.info("[TigerGirl] Spawned TigerGirl {} at X={} Y={} Z={} (bell {})",
                entity.getUUID(), spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), bell);
    }

    private static BlockPos findSpawnPos(ServerLevel level, BlockPos center) {
        int radius = Math.min((int) (Config.VILLAGE_RADIUS.get() / 5), 16);
        for (int attempt = 0; attempt < 30; attempt++) {
            int dx = level.getRandom().nextIntBetweenInclusive(-radius, radius);
            int dz = level.getRandom().nextIntBetweenInclusive(-radius, radius);
            BlockPos candidate = new BlockPos(center.getX() + dx, 0, center.getZ() + dz);
            if (!level.isLoaded(candidate)) continue;
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, candidate);
            BlockPos below = surface.below();
            // canSeeSky: true only for outdoor positions — rejects building interiors and rooftops
            // covered by a roof block while accepting open ground, paths, and plazas.
            if (level.canSeeSky(surface)
                    && level.getBlockState(below).isSolid()
                    && level.getBlockState(surface).isAir()
                    && level.getBlockState(surface.above()).isAir()) {
                return surface;
            }
        }
        return null;
    }
}
