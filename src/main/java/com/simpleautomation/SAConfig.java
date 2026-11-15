package com.simpleautomation;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Réglages serveur (fichier simple_automation-server.toml dans le dossier du monde). */
public final class SAConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MAX_ZONE_SIZE;
    public static final ModConfigSpec.IntValue MAX_WORKERS_PER_ZONE;
    public static final ModConfigSpec.IntValue GLOBAL_BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue MIN_TICKS_PER_BLOCK;
    public static final ModConfigSpec.DoubleValue WORK_SPEED_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue WORKER_WALK_SPEED;
    public static final ModConfigSpec.DoubleValue CRUSHER_MULTIPLIER;
    public static final ModConfigSpec.IntValue LUMBER_FALLBACK_SECONDS;
    public static final ModConfigSpec.BooleanValue LUMBER_WAIT_FOR_SAPLINGS;
    public static final ModConfigSpec.IntValue BARN_MAX_ANIMALS;
    public static final ModConfigSpec.IntValue PIPE_ITEMS_PER_OPERATION;
    public static final ModConfigSpec.IntValue PIPE_TICKS_PER_OPERATION;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("zones");
        MAX_ZONE_SIZE = b.comment("Largest side of a work zone, in blocks (distance between the two border markers).")
                .defineInRange("maxZoneSize", 64, 2, 256);
        MAX_WORKERS_PER_ZONE = b.comment("Maximum copper golems per zone. 0 = no limit (the global budget still keeps the server safe).")
                .defineInRange("maxWorkersPerZone", 0, 0, 1024);
        b.pop();
        b.push("performance");
        GLOBAL_BLOCKS_PER_TICK = b.comment("How many blocks all golems of the server may break per tick, all zones together. Work slows down above it instead of lagging.")
                .defineInRange("globalBlocksPerTick", 64, 1, 4096);
        MIN_TICKS_PER_BLOCK = b.comment("A golem never breaks blocks faster than one every N ticks, whatever its tool.")
                .defineInRange("minTicksPerBlock", 2, 1, 200);
        b.pop();
        b.push("balance");
        WORK_SPEED_MULTIPLIER = b.comment("Multiplies the time a golem needs per block. 1.0 = same speed as a player with the same tool, 2.0 = twice slower.")
                .defineInRange("workTimeMultiplier", 1.0, 0.1, 100.0);
        WORKER_WALK_SPEED = b.comment("Distance a golem walks per tick between two targets, in blocks.")
                .defineInRange("walkSpeed", 0.25, 0.05, 4.0);
        CRUSHER_MULTIPLIER = b.comment("The crusher mines each ore like the golem's pickaxe would (Fortune included, Silk Touch ignored), then multiplies the loot by this.")
                .defineInRange("crusherMultiplier", 1.5, 1.0, 4.0);
        LUMBER_FALLBACK_SECONDS = b.comment("The lumber yard replants the species of the felled tree. If none of its saplings is in the chests after this many seconds, it plants another species instead. 0 = never, wait for the right sapling.")
                .defineInRange("lumberFallbackSeconds", 60, 0, 3600);
        LUMBER_WAIT_FOR_SAPLINGS = b.comment("When a felled tree is waiting for saplings, lumber golems stop felling new trees until it is replanted (false = keep cutting, the spot is replanted as soon as saplings arrive).")
                .define("lumberWaitForSaplings", false);
        BARN_MAX_ANIMALS = b.comment("A barn breeds each species up to this many animals (babies included), and golems with a weapon cull the adults above it.")
                .defineInRange("barnMaxAnimals", 10, 2, 256);
        PIPE_ITEMS_PER_OPERATION = b.comment("Items an extracting pipe connection moves at once.")
                .defineInRange("pipeItemsPerOperation", 64, 1, 4096);
        PIPE_TICKS_PER_OPERATION = b.comment("Ticks between two moves of an extracting pipe connection.")
                .defineInRange("pipeTicksPerOperation", 10, 1, 200);
        b.pop();
        SPEC = b.build();
    }

    private SAConfig() {
    }
}
