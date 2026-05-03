package com.tosturi.floversemod.event;

import com.tosturi.floversemod.Config;
import com.tosturi.floversemod.FloVerseMod;
import com.tosturi.floversemod.data.TigerGirlVillageData;
import com.tosturi.floversemod.entity.ModEntities;
import com.tosturi.floversemod.entity.custom.TigerGirlEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = FloVerseMod.MODID)
public class TigerGirlVillageHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int CHECK_INTERVAL = 600;
    private static int tickCounter = 0;

    private static final TigerGirlRespawnManager respawnManager = new TigerGirlRespawnManager();

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        tickCounter = 0;
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
                .ifPresentOrElse(bellPos -> {
                    TigerGirlVillageData data = TigerGirlVillageData.get(level);
                    if (data.registerVillage(bellPos)) {
                        LOGGER.info("[TigerGirl] New village registered at bell {}, attempting spawn", bellPos);
                        spawnTigerGirl(level, bellPos, data);
                    } else {
                        LOGGER.debug("[TigerGirl] Village at bell {} already registered, skipping", bellPos);
                    }
                }, () -> LOGGER.debug("[TigerGirl] Villager {} joined with no MEETING POI within 64 blocks", villager.getUUID()));
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

            // Fire due respawn timers
            List<BlockPos> due = respawnManager.collectDue(level, data);
            for (BlockPos bell : due) {
                TigerGirlVillageData.VillageEntry entry = data.getEntry(bell);
                if (entry != null && !entry.villageDead()) {
                    spawnTigerGirl(level, bell, data);
                }
            }

            // Liveness check: only runs when the bell chunk is loaded so we don't
            // mistake "entity in unloaded chunk" for "entity dead".
            for (Map.Entry<BlockPos, TigerGirlVillageData.VillageEntry> mapEntry
                    : data.getAllEntries().entrySet()) {

                BlockPos bell = mapEntry.getKey();
                TigerGirlVillageData.VillageEntry entry = mapEntry.getValue();

                if (entry.tigerGirlUUID() == null) continue;
                if (!level.isLoaded(bell)) continue;

                Entity found = level.getEntity(entry.tigerGirlUUID());
                if (found instanceof TigerGirlEntity tg && tg.isAlive()) continue;

                LOGGER.info("[TigerGirl] Liveness check: TigerGirl {} missing while bell {} is loaded, scheduling respawn",
                        entry.tigerGirlUUID(), bell);
                data.clearTigerGirl(bell);
                respawnManager.schedule(bell, level.getGameTime(), data);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Death handler

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
        BlockPos spawnPos = findSpawnPos(level, bell);
        if (spawnPos == null) {
            LOGGER.warn("[TigerGirl] Could not find valid spawn position near bell {} after 10 attempts", bell);
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
        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = level.getRandom().nextIntBetweenInclusive(-radius, radius);
            int dz = level.getRandom().nextIntBetweenInclusive(-radius, radius);
            BlockPos surface = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(center.getX() + dx, 0, center.getZ() + dz)
            );
            BlockPos below = surface.below();
            if (level.getBlockState(below).isSolid()
                    && level.getBlockState(below.below()).isSolid()
                    && level.getBlockState(surface).isAir()
                    && level.getBlockState(surface.above()).isAir()) {
                return surface;
            }
        }
        return null;
    }
}
