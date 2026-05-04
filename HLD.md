# FloVerse Mod — High-Level Design

## Purpose

A NeoForge mod created for the streamer **Floneriel**. It introduces a single custom NPC — the **TigerGirl** — that automatically appears in every Minecraft village, sells a custom currency item (**Florics**), and respawns after being killed. The mod is intentionally minimal: one NPC, one item, one block, one economy loop.

---

## File Tree

```
src/main/
├── java/com/tosturi/floversemod/
│   ├── FloVerseMod.java                  — mod entry point, event bus wiring
│   ├── FloVerseCreativeModeTabs.java     — creative tab registration
│   ├── Config.java                       — configurable constants
│   ├── block/ModBlocks.java              — block registry
│   ├── item/ModItems.java                — item registry
│   ├── entity/
│   │   ├── ModEntities.java              — entity type registry
│   │   └── custom/TigerGirlEntity.java  — NPC logic and AI
│   │   └── client/PlaceholderRenderer.java — temporary renderer (vanilla villager skin)
│   ├── data/TigerGirlVillageData.java    — persistent world state (SavedData)
│   ├── event/
│   │   ├── TigerGirlVillageHandler.java  — spawn / death / respawn event logic
│   │   └── TigerGirlRespawnManager.java  — respawn timer manager
│   └── datagen/
│       ├── ModModelProvider.java         — block + item model generation
│       ├── ModRecipeProvider.java        — crafting recipe generation
│       ├── ModLootTableProvider.java     — block loot table generation
│       ├── ModLanguageProvider.java      — English translations
│       └── ModLanguageProviderRuRu.java  — Russian translations
└── resources/
    └── data/floversemod/
        ├── trade_set/tiger_girl_trades.json
        └── villager_trade/
            ├── gold_to_florics.json
            └── diamond_to_florics.json
```

---

## Content

### Florics (item)
A custom currency. Has no use beyond being traded for. Appears in the FloVerse creative tab.

### Florics Box (block)
A storage-compression block. Crafted from a 3×3 grid of Florics (9 → 1 box). Breaks back into 9 Florics when mined (loot table). Primarily exists as a convenient way to store large quantities of Florics.

### TigerGirl (NPC entity)
A villager-like NPC that inhabits villages. She does not despawn naturally, does not breed, and cannot age. She is the only source of Florics in the world.

---

## Systems

### 1 — Village Detection

**Primary trigger:** a 600-tick server tick loop scans for `MEETING` POIs within 128 blocks of each player. This is the reliable discovery path — it runs after all chunks are fully loaded, avoiding the race condition where the bell's POI chunk isn't ready yet.

**Secondary trigger (fast path):** `EntityJoinLevelEvent` fires when any `Villager` joins the level. The handler searches for a `MEETING` POI within 64 blocks of the villager's physical position. This catches the common case immediately on world load when POIs happen to be ready.

**Logic (both paths):**
1. Find the bell (`MEETING` POI) near the trigger position.
2. Call `registerVillage(bellPos)` — normalizes the bell position to the nearest 32-block grid and checks if this village is already tracked.
3. If new: register it and call `spawnTigerGirl`.
4. If already registered: do nothing (no log — expected steady state).

**Village key:** the bell position is snapped to the nearest 32-block grid (X and Z only) to prevent multiple registrations for the same village due to minor position differences.

**Multiple bells:** if a second bell is found within `VILLAGE_RADIUS` blocks of an already-registered village, it is treated as belonging to the same village and ignored.

---

### 2 — Spawn Position Selection

When a TigerGirl needs to be spawned near a bell:

1. **Chunk guard:** if the bell's chunk is not loaded, defer — skip the attempt and reschedule for `CHECK_INTERVAL` (600) ticks later. Heightmap and block-state queries return garbage on unloaded chunks.
2. **Duplicate guard:** check for an existing `TigerGirlEntity` within `VILLAGE_RADIUS` of the bell. If one is found, repair the data mapping and abort — prevents duplicate spawns if saved data was lost between sessions.
3. Choose a random offset within a radius derived from `VILLAGE_RADIUS` (capped at 16 blocks).
4. Skip attempt if that chunk is not loaded.
5. Query the heightmap (`MOTION_BLOCKING_NO_LEAVES`) for the top surface at that X/Z.
6. Validate: `canSeeSky(surface)` must be true (rejects positions under rooftops or building overhangs; accepts open ground, paths, plazas), the block below must be solid, and the two blocks above must be air.
7. Retry up to 30 times. If no valid position is found, schedule a retry for `CHECK_INTERVAL` ticks later (not the full respawn delay).

TigerGirl's home position is set to the bell with a 20-block radius (`setHomeTo(bell, 20)`) before she is added to the world. `Mob` persists `home_pos` and `home_radius` to NBT automatically.

---

### 3 — TigerGirl AI

TigerGirl uses Minecraft's **Brain** system, which is activity-based rather than goal-based.

**Attributes:**
- Max health: 100 HP
- Movement speed: 0.5 (idle) / 0.65 (chasing, 1.3× modifier)
- Attack damage: 6 HP per hit
- Follow range: 32 blocks

**Sensors:** `NEAREST_LIVING_ENTITIES`, `NEAREST_PLAYERS`, `VILLAGER_HOSTILES` (populates `NEAREST_HOSTILE`), `HURT_BY`.

**CORE activity** (always running):
- Floats in water.
- Tracks and rotates toward current look target.
- Executes `WALK_TARGET` navigation (`MoveToTargetSink` — required for any movement to work).

**IDLE activity** (default):
- `StartAttacking` (priority 1): detects the nearest hostile entity (`NEAREST_HOSTILE` memory), sets it as `ATTACK_TARGET`. Skipped while trading.
- `RandomStroll` (priority 2): wanders slowly at 0.5 speed within the 20-block home radius around the bell. `LandRandomPos` respects `setHomeTo` automatically via `GoalUtils.mobRestricted()`.
- `SetEntityLookTargetSometimes` (priority 3): occasionally looks at nearby players.

**FIGHT activity** (activates when `ATTACK_TARGET` memory is present):
- `StopAttackingIfTargetInvalid`: clears the target if it dies or becomes unreachable.
- `SetWalkTargetFromAttackTargetIfTargetOutOfReach` at 0.65 speed: chases the target.
- `MeleeAttack` with 20-tick cooldown: strikes when in range.
- On exit: erases `ATTACK_TARGET` and `WALK_TARGET` for clean state.

**Activity transitions:** `brain.setActiveActivityToFirstValid(FIGHT, IDLE)` is called every tick in `customServerAiStep` after the brain ticks.

**Combat leash:** if TigerGirl drifts more than `homeRadius + 16` blocks (36 blocks) from the bell while in combat, `ATTACK_TARGET` and `WALK_TARGET` are cleared and navigation is stopped. This ensures she stays close enough to the bell that the liveness check always finds her in a loaded chunk.

**Stop when trading:** every tick while `isTrading()` is true, `WALK_TARGET` is erased and navigation is stopped. This prevents `RandomStroll` from setting a new target on the very next tick.

**Persistence:** calls `setPersistenceRequired()` in the constructor. `AbstractVillager` does not override `checkDespawn()` in this MC version, so without this flag she would despawn like a regular mob when the player is >128 blocks away.

**Zombie immunity:** `LivingConversionEvent.Pre` is cancelled for `TigerGirlEntity` in the event handler.

---

### 4 — Trading

**Trigger:** player right-clicks TigerGirl with an empty hand.

**Logic:**
1. If her trade offer list is empty (first interaction), load offers from the `tiger_girl_trades` trade set defined in JSON.
2. Open the standard merchant trade screen.

**Available trades (defined in JSON):**
| Cost | Reward |
|---|---|
| 8 Gold Ingots | 1 Florics |
| 1 Diamond | 4 Florics |

Both trades have unlimited uses and grant no experience. The trade set uses Minecraft's data-driven `TradeSet` / `VillagerTrade` registry, so trades can be modified without recompiling.

---

### 5 — Death and Respawn

**On death:**
1. The death event fires.
2. The system looks up which bell this TigerGirl belongs to using an in-memory UUID → bell reverse index (O(1) lookup).
3. Her UUID is cleared from the village record.
4. A respawn timer is scheduled: `currentGameTick + RESPAWN_DELAY_TICKS` (default 3000 ticks ≈ 2.5 minutes).
5. The timer is persisted to disk immediately so it survives a server restart.

**Respawn tick loop** (runs every 600 ticks ≈ 30 seconds):
1. Collect all pending respawn entries whose scheduled tick has passed.
2. **Skip if the bell's chunk is not loaded** — heightmap queries on unloaded chunks return garbage, causing spawn to fail. Defer by rescheduling for the next cycle.
3. For each due entry, call `spawnTigerGirl` (which includes the duplicate guard and position search).

**Liveness check** (same 600-tick loop):
- For each registered village that has a TigerGirl UUID recorded, verify the entity still exists and is alive.
- **Requires a player within `VILLAGE_RADIUS` blocks (XZ) of the bell** before checking. This guarantees that both the bell's chunk and TigerGirl's chunk (within 36 blocks of the bell, due to the combat leash) are fully loaded — eliminating false positives caused by TigerGirl's chunk loading a tick after the bell's chunk.
- **Orphan repair:** before scheduling a respawn, check for any `TigerGirlEntity` within `VILLAGE_RADIUS` of the bell. If one exists but with a different UUID (data was lost/repaired partially), update the mapping and skip the respawn.
- If the entity is genuinely missing (player nearby, entity not found, no orphan), treat it the same as a death and schedule a respawn.

---

### 6 — Persistent State

All village data is stored per-world using Minecraft's `SavedData` mechanism (written to the world's `data/` folder as NBT).

**Stored per village (keyed by bell position):**
- TigerGirl's UUID (null if not currently spawned)
- `villageDead` flag (reserved for future use — e.g., if all villagers are gone)

**Stored globally:**
- Pending respawn timers: bell position → game tick at which to fire

**Transient (in-memory only, rebuilt on load):**
- UUID → bell reverse index for O(1) death lookups

---

### 7 — Configuration

Two values exposed in `floversemod-common.toml`:

| Key | Default | Description |
|---|---|---|
| `respawnDelayTicks` | 3000 | Ticks between TigerGirl death and respawn (~2.5 min) |
| `villageRadius` | 80.0 | Radius used for spawn position search scaling |

---

## Known Limitations

- **TigerGirl model is a placeholder** — she currently renders using the vanilla villager texture. A custom Blockbench model has not been created yet.
- **Village discovery delay** — discovery via the tick scan has up to a 30-second delay on first visit. The `EntityJoinLevelEvent` fast path reduces this in most cases, but is not guaranteed when POIs aren't ready at chunk load time.
- **`villageDead` flag unused** — persisted in `VillageEntry` but not yet acted on. Reserved for future dead-village detection (all villagers gone).
