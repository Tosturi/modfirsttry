package com.tosturi.floversemod.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tosturi.floversemod.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.resources.Identifier;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TigerGirlVillageData extends SavedData {

    private static final Codec<Map<String, VillageEntry>> VILLAGES_CODEC =
            Codec.unboundedMap(Codec.STRING, VillageEntry.CODEC);
    private static final Codec<Map<String, Long>> RESPAWNS_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.LONG);

    private static final Codec<TigerGirlVillageData> FULL_CODEC = RecordCodecBuilder.create(i -> i.group(
            VILLAGES_CODEC.optionalFieldOf("villages", Map.of())
                    .forGetter(TigerGirlVillageData::toVillageStringMap),
            RESPAWNS_CODEC.optionalFieldOf("pending_respawns", Map.of())
                    .forGetter(TigerGirlVillageData::toPendingStringMap)
    ).apply(i, TigerGirlVillageData::fromMaps));

    public static final SavedDataType<TigerGirlVillageData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("floversemod", "tiger_girl_villages"),
            TigerGirlVillageData::new,
            FULL_CODEC
    );

    private final Map<BlockPos, VillageEntry> villageEntries = new HashMap<>();
    private final Map<BlockPos, Long> pendingRespawns = new HashMap<>();
    /** Transient reverse index — not persisted, rebuilt on load. */
    private final Map<UUID, BlockPos> uuidToVillage = new HashMap<>();

    public TigerGirlVillageData() {}

    // -----------------------------------------------------------------------
    // Serialisation helpers

    private static TigerGirlVillageData fromMaps(Map<String, VillageEntry> villages,
                                                  Map<String, Long> respawns) {
        TigerGirlVillageData data = new TigerGirlVillageData();
        villages.forEach((key, entry) -> {
            BlockPos pos = parsePos(key);
            if (pos != null) {
                data.villageEntries.put(pos, entry);
                if (entry.tigerGirlUUID() != null) {
                    data.uuidToVillage.put(entry.tigerGirlUUID(), pos);
                }
            }
        });
        respawns.forEach((key, tick) -> {
            BlockPos pos = parsePos(key);
            if (pos != null) data.pendingRespawns.put(pos, tick);
        });
        return data;
    }

    private Map<String, VillageEntry> toVillageStringMap() {
        Map<String, VillageEntry> result = new HashMap<>();
        villageEntries.forEach((pos, entry) -> result.put(posKey(pos), entry));
        return result;
    }

    private Map<String, Long> toPendingStringMap() {
        Map<String, Long> result = new HashMap<>();
        pendingRespawns.forEach((pos, tick) -> result.put(posKey(pos), tick));
        return result;
    }

    @Nullable
    private static BlockPos parsePos(String key) {
        String[] parts = key.split(",");
        if (parts.length != 3) return null;
        try {
            return new BlockPos(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String posKey(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    // -----------------------------------------------------------------------
    // API

    public static TigerGirlVillageData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean registerVillage(BlockPos center) {
        BlockPos key = normalizePos(center);
        if (villageEntries.containsKey(key)) return false;

        double mergeRadiusSq = Config.VILLAGE_RADIUS.get() * Config.VILLAGE_RADIUS.get();
        for (BlockPos existing : villageEntries.keySet()) {
            double dx = center.getX() - existing.getX();
            double dz = center.getZ() - existing.getZ();
            if (dx * dx + dz * dz <= mergeRadiusSq) return false;
        }

        villageEntries.put(key, new VillageEntry(null, false));
        setDirty();
        return true;
    }

    public void setTigerGirl(BlockPos center, UUID uuid) {
        BlockPos key = normalizePos(center);
        VillageEntry old = villageEntries.get(key);
        if (old != null && old.tigerGirlUUID() != null) {
            uuidToVillage.remove(old.tigerGirlUUID());
        }
        pendingRespawns.remove(key);
        villageEntries.put(key, new VillageEntry(uuid, false));
        uuidToVillage.put(uuid, key);
        setDirty();
    }

    public void clearTigerGirl(BlockPos center) {
        BlockPos key = normalizePos(center);
        VillageEntry old = villageEntries.get(key);
        if (old != null) {
            if (old.tigerGirlUUID() != null) uuidToVillage.remove(old.tigerGirlUUID());
            villageEntries.put(key, new VillageEntry(null, old.villageDead()));
            setDirty();
        }
    }

    public void schedulePendingRespawn(BlockPos center, long fireTick) {
        pendingRespawns.put(normalizePos(center), fireTick);
        setDirty();
    }

    public void clearPendingRespawn(BlockPos center) {
        pendingRespawns.remove(normalizePos(center));
        setDirty();
    }

    public Map<BlockPos, Long> getPendingRespawns() {
        return Map.copyOf(pendingRespawns);
    }

    @Nullable
    public VillageEntry getEntry(BlockPos center) {
        return villageEntries.get(normalizePos(center));
    }

    public Map<BlockPos, VillageEntry> getAllEntries() {
        return Map.copyOf(villageEntries);
    }

    /** O(1) lookup — uses the transient reverse index. */
    @Nullable
    public BlockPos getBellPosForUUID(UUID uuid) {
        return uuidToVillage.get(uuid);
    }

    private BlockPos normalizePos(BlockPos pos) {
        int nx = (int) Math.round(pos.getX() / 32.0) * 32;
        int nz = (int) Math.round(pos.getZ() / 32.0) * 32;
        return new BlockPos(nx, 0, nz);
    }

    // -----------------------------------------------------------------------

    public record VillageEntry(
            @Nullable UUID tigerGirlUUID,
            boolean villageDead
    ) {
        public static final Codec<VillageEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("tiger_girl_uuid", "")
                        .<UUID>xmap(
                                s -> s.isEmpty() ? null : UUID.fromString(s),
                                uuid -> uuid == null ? "" : uuid.toString()
                        )
                        .forGetter(VillageEntry::tigerGirlUUID),
                Codec.BOOL.optionalFieldOf("village_dead", false)
                        .forGetter(VillageEntry::villageDead)
        ).apply(i, VillageEntry::new));

    }
}
