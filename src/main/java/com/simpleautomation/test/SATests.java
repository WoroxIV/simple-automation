package com.simpleautomation.test;

import com.simpleautomation.SimpleAutomation;
import com.simpleautomation.pipe.PipeBlock;
import com.simpleautomation.pipe.PipeBlockEntity;
import com.simpleautomation.pipe.PipeSide;
import com.simpleautomation.registry.ModBlocks;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.gamerules.GameRuleMap;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Tests en jeu, lancés par {@code gradlew runGameTestServer} : une vraie zone, un vrai golem, et on vérifie le résultat.
 */
public final class SATests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, SimpleAutomation.MODID);

    /** Environnement par défaut : les règles de jeu normales. */
    static final String DEFAULT = "default";
    /** Règle blockDrops désactivée (lot à part, pour ne pas gêner les autres tests). */
    static final String NO_BLOCK_DROPS = "no_block_drops";
    /** Pas de ticks aléatoires : les plantes ne poussent pas toutes seules pendant le test. */
    static final String NO_RANDOM_TICKS = "no_random_ticks";

    private record Spec(int maxTicks, String env) {
    }

    /** Nom du test → durée maximale en ticks et environnement. */
    private static final java.util.Map<String, Spec> TESTS = new java.util.LinkedHashMap<>();

    private static void test(String name, Consumer<GameTestHelper> fn, int maxTicks) {
        test(name, fn, maxTicks, DEFAULT);
    }

    private static void test(String name, Consumer<GameTestHelper> fn, int maxTicks, String env) {
        FUNCTIONS.register(name, () -> fn);
        TESTS.put(name, new Spec(maxTicks, env));
    }

    static {
        test("quarry_digs_layer", SATests::quarryDigsLayer, 900);
        test("lumber_fells_and_replants", SATests::lumberFellsAndReplants, 900);
        test("pipe_moves_items", SATests::pipeMovesItems, 200);
        test("pipe_separated_by_wrench", SATests::pipeSeparatedByWrench, 200);
        test("forge_smelts_with_stoking", SATests::forgeSmeltsWithStoking, 1500);
        test("crusher_fortune_multiplies", SATests::crusherFortuneMultiplies, 900);
        test("farm_harvests_and_replants", SATests::farmHarvestsAndReplants, 600);
        test("shepherd_shears_sheep", SATests::shepherdShearsSheep, 600);
        test("ranch_breeds", SATests::ranchBreeds, 900);
        test("ranch_culls_extra_adults", SATests::ranchCulls, 900);
        test("lightning_deoxidizes_workers", SATests::lightningDeoxidizes, 200);
        // Itération 2
        test("recruit_after_controller_replaced", FrameworkTests::recruitAfterControllerReplaced, 400);
        test("controller_buffer_extractable", FrameworkTests::controllerBufferExtractable, 200);
        test("quarry_bare_hand_digs_dirt", QuarryTests::bareHandDigsDirt, 900);
        test("quarry_no_floating", QuarryTests::noFloating, 1200);
        test("crusher_places_and_breaks", CrusherTests::crusherPlacesAndBreaks, 400);
        test("forge_splits_between_furnaces", ForgeTests::forgeSplitsBetweenFurnaces, 900);
        test("pipe_filter_item", FilterTests::pipeFilterItem, 200);
        test("pipe_filter_tag", FilterTests::pipeFilterTag, 200);
        test("farm_plants_seeds_from_chest", MiscTests::farmPlantsSeedsFromChest, 1200);
        test("lumber_fells_mangrove", MiscTests::lumberFellsMangrove, 900);
        test("ranch_breeds_split_stacks", MiscTests::ranchBreedsSplitStacks, 900);
        // Itération 2, corrections de revue
        test("quarry_waits_for_pickaxe", QuarryTests::waitsForPickaxe, 1500);
        test("farm_sows_pumpkin_seeds", MiscTests::farmSowsPumpkinSeeds, 1200);
        // Avant la sortie : recrutement, repos, carrière, broyeur, forge, filtres, clé, oxydation, pause, enclos, claims, outils
        test("recruit_skips_old_golem", RecruitTests::skipsOldGolem, 300);
        test("recruit_fresh_and_orphan", RecruitTests::freshAndOrphan, 200);
        test("recruit_freed_needs_lead", RecruitTests::freedNeedsLead, 300);
        test("release_by_hand_needs_lead", RecruitTests::releaseByHandNeedsLead, 300);
        test("mark_born_keeps_first", RecruitTests::markBornKeepsFirst, 40);
        test("recruit_old_golem_through_portal", RecruitTests::oldGolemThroughPortal, 200);
        test("idle_rests_near_busy", RestTests::idleRestsNearBusy, 400);
        test("idle_returns_to_controller", RestTests::idleReturnsToController, 300);
        test("quarry_finished_returns", RestTests::quarryFinishedReturns, 1200);
        test("quarry_floor_over_cave", QuarryTests::floorOverCave, 900);
        test("quarry_seals_lava_and_water", QuarryTests::sealsLavaAndWater, 900);
        test("quarry_silk_touch_and_wear", QuarryTests::silkTouchAndWear, 900);
        test("crusher_resumes_placed_ore", CrusherTests::resumesPlacedOre, 900);
        test("crusher_ancient_debris_uncrushable", CrusherTests::ancientDebrisUncrushable, 900);
        test("crusher_blacklist_tag", CrusherTests::blacklistTag, 900);
        test("forge_blast_and_smoker_shares", ForgeTests::blastAndSmokerShares, 1500);
        test("forge_shovel_skips_unlit", ForgeTests::shovelSkipsUnlit, 1500);
        test("pipe_filter_blacklist", FilterTests::pipeFilterBlacklist, 200);
        test("filter_menu_server_side", FilterTests::filterMenuServerSide, 40);
        test("filter_placed_and_removed_by_hand", FilterTests::filterPlacedAndRemovedByHand, 40);
        test("wrench_cycles_sides", FilterTests::wrenchCyclesSides, 40);
        test("worker_oxidizes_over_time", GolemTests::oxidizesOverTime, 200);
        test("worker_wax_scrape_and_tool", GolemTests::waxScrapeAndTool, 300);
        test("zone_pauses_when_chests_full", GolemTests::pausesWhenChestsFull, 900);
        test("break_drops_without_block_drops", GolemTests::breakDropsWithoutBlockDrops, 200, NO_BLOCK_DROPS);
        test("ranch_food_check", AnimalTests::ranchFoodCheck, 600);
        test("ranch_fence_hint", AnimalTests::ranchFenceHint, 200);
        test("shear_efficiency_and_wear", AnimalTests::shearEfficiencyAndWear, 600);
        test("claims_shear_denied", ClaimsTests::shearDenied, 300);
        test("claims_ranch_denied", ClaimsTests::ranchDenied, 400);
        test("claims_quarry_break_denied", ClaimsTests::quarryBreakDenied, 900);
        test("claims_crusher_place_denied", ClaimsTests::crusherPlaceDenied, 900);
        test("claims_farm_sow_denied", ClaimsTests::farmSowDenied, 1600);
        test("claims_rules", ClaimsTests::rules, 100);
        test("farm_sows_melon_with_hoe", MiscTests::farmSowsMelonWithHoe, 1200);
        test("farm_berries_and_cocoa", MiscTests::farmBerriesAndCocoa, 900, NO_RANDOM_TICKS);
        test("farm_crops_tag", MiscTests::farmCropsTag, 600, NO_RANDOM_TICKS);
        test("lumber_collects_items", MiscTests::lumberCollectsItems, 200);
        test("tool_efficiency_speeds_up", GolemTests::efficiencySpeedsUp, 40);
        test("work_budget_cap", GolemTests::workBudgetCap, 40);
        test("tool_change_rechecked", GolemTests::toolChangeRechecked, 200);
        test("lumber_fells_nether_fungus", MiscTests::lumberFellsNetherFungus, 900);
        test("lumber_bonemeals_fungus", MiscTests::lumberBonemealsFungus, 1200);
        test("lumber_skips_wrong_fungus", MiscTests::lumberSkipsWrongFungus, 900);
        test("farm_high_cocoa", MiscTests::farmHighCocoa, 900, NO_RANDOM_TICKS);
        test("farm_seeds_tag_skips_unharvestable", MiscTests::farmSeedsTagSkipsUnharvestable, 900, NO_RANDOM_TICKS);
        // Évolutions : XP en cristaux, Lumber, Crusher, Farm, Barn, nouveaux contrôleurs
        test("xp_bank_makes_crystals", XpTests::bankMakesCrystals, 100);
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(SATests::registerTests);
        TestClaims.register();
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        java.util.Map<String, Holder<TestEnvironmentDefinition>> envs = new java.util.HashMap<>();
        envs.put(DEFAULT, event.registerEnvironment(SimpleAutomation.id(DEFAULT)));
        envs.put(NO_BLOCK_DROPS, event.registerEnvironment(SimpleAutomation.id(NO_BLOCK_DROPS),
                new TestEnvironmentDefinition.SetGameRules(new GameRuleMap.Builder().set(GameRules.BLOCK_DROPS, false).build())));
        envs.put(NO_RANDOM_TICKS, event.registerEnvironment(SimpleAutomation.id(NO_RANDOM_TICKS),
                new TestEnvironmentDefinition.SetGameRules(new GameRuleMap.Builder().set(GameRules.RANDOM_TICK_SPEED, 0).build())));
        TESTS.forEach((name, spec) -> {
            ResourceKey<Consumer<GameTestHelper>> fn = ResourceKey.create(Registries.TEST_FUNCTION, SimpleAutomation.id(name));
            event.registerTest(SimpleAutomation.id(name),
                    new FunctionGameTestInstance(fn, new TestData<>(envs.get(spec.env()), SimpleAutomation.id("arena"), spec.maxTicks(), 0, true)));
        });
    }

    /** Une carrière 4×4 sur 4 couches de pierre, un golem à la pioche en diamant : la couche du haut doit finir au coffre. */
    private static void quarryDigsLayer(GameTestHelper h) {
        for (int x = 1; x <= 6; x++)
            for (int z = 1; z <= 6; z++)
                for (int y = 1; y <= 4; y++) h.setBlock(x, y, z, Blocks.STONE);
        h.setBlock(1, 5, 1, ModBlocks.BORDER_MARKER.get());
        h.setBlock(4, 5, 4, ModBlocks.BORDER_MARKER.get());
        h.setBlock(6, 5, 2, ModBlocks.QUARRY_CONTROLLER.get());
        h.setBlock(7, 5, 2, Blocks.CHEST);
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(2, 5, 2));
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));

        h.succeedWhen(() -> {
            for (int x = 1; x <= 4; x++)
                for (int z = 1; z <= 4; z++)
                    h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(x, 4, z))).isAir(), "top layer not dug at " + x + "," + z);
            int cobble = count(h, new BlockPos(7, 5, 2), Items.COBBLESTONE);
            h.assertTrue(cobble >= 16, "expected at least 16 cobblestone in the chest, got " + cobble);
        });
    }

    /** Un chêne dans une scierie, un golem à la hache en fer : l'arbre disparaît d'un coup, les bûches vont au coffre, une pousse est replantée. */
    private static void lumberFellsAndReplants(GameTestHelper h) {
        for (int x = 0; x <= 7; x++)
            for (int z = 0; z <= 7; z++) h.setBlock(x, 1, z, Blocks.DIRT);
        for (int y = 2; y <= 6; y++) h.setBlock(3, y, 3, Blocks.OAK_LOG);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int y = 5; y <= 7; y++)
                    if (!(dx == 0 && dz == 0 && y <= 6)) h.setBlock(3 + dx, y, 3 + dz, leaves);
        h.setBlock(1, 2, 1, ModBlocks.BORDER_MARKER.get());
        h.setBlock(5, 2, 5, ModBlocks.BORDER_MARKER.get());
        h.setBlock(6, 2, 6, ModBlocks.LUMBER_CONTROLLER.get());
        h.setBlock(7, 2, 6, Blocks.CHEST);
        h.getBlockEntity(new BlockPos(7, 2, 6), ChestBlockEntity.class).setItem(0, new ItemStack(Items.OAK_SAPLING, 4));
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(2, 2, 2));
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));

        h.succeedWhen(() -> {
            for (int y = 3; y <= 6; y++) h.assertBlockPresent(Blocks.AIR, 3, y, 3);
            int logs = count(h, new BlockPos(7, 2, 6), Items.OAK_LOG);
            h.assertTrue(logs >= 5, "expected 5 oak logs in the chest, got " + logs);
            h.assertBlockPresent(Blocks.OAK_SAPLING, 3, 2, 3);
        });
    }

    /** Coffre → 3 pipes → coffre, le premier côté en Extraction : la pile entière doit arriver. */
    private static void pipeMovesItems(GameTestHelper h) {
        buildPipeLine(h);
        h.succeedWhen(() -> {
            h.assertTrue(count(h, new BlockPos(5, 1, 1), Items.COBBLESTONE) == 64, "the 64 cobblestone did not reach the second chest");
            h.assertTrue(count(h, new BlockPos(1, 1, 1), Items.COBBLESTONE) == 0, "the first chest is not empty");
        });
    }

    /** Même ligne, mais séparée à la clé au milieu : rien ne doit passer. */
    private static void pipeSeparatedByWrench(GameTestHelper h) {
        buildPipeLine(h);
        BlockPos a = h.absolutePos(new BlockPos(3, 1, 1));
        BlockPos b = h.absolutePos(new BlockPos(4, 1, 1));
        ((PipeBlockEntity) h.getLevel().getBlockEntity(a)).setDisabled(net.minecraft.core.Direction.EAST, true);
        ((PipeBlockEntity) h.getLevel().getBlockEntity(b)).setDisabled(net.minecraft.core.Direction.WEST, true);
        PipeBlock.refresh(h.getLevel(), a);
        PipeBlock.refresh(h.getLevel(), b);
        h.runAfterDelay(100, () -> {
            h.assertTrue(count(h, new BlockPos(5, 1, 1), Items.COBBLESTONE) == 0, "items crossed a separated pipe");
            h.succeed();
        });
    }

    private static void buildPipeLine(GameTestHelper h) {
        h.setBlock(1, 1, 1, Blocks.CHEST);
        h.setBlock(5, 1, 1, Blocks.CHEST);
        h.getBlockEntity(new BlockPos(1, 1, 1), ChestBlockEntity.class).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        for (int x = 2; x <= 4; x++) h.setBlock(x, 1, 1, ModBlocks.PIPE.get());
        for (int x = 2; x <= 4; x++) PipeBlock.refresh(h.getLevel(), h.absolutePos(new BlockPos(x, 1, 1)));
        BlockPos first = h.absolutePos(new BlockPos(2, 1, 1));
        h.getLevel().setBlock(first, h.getLevel().getBlockState(first).setValue(PipeBlock.PROPERTIES.get(net.minecraft.core.Direction.WEST), PipeSide.EXTRACT), 3);
    }

    /** Une forge avec un four, du minerai et du charbon au coffre, un golem à la pelle en netherite : 8 lingots doivent revenir. */
    private static void forgeSmeltsWithStoking(GameTestHelper h) {
        floorAndZone(h, ModBlocks.FORGE_CONTROLLER.get());
        h.setBlock(3, 2, 3, Blocks.FURNACE);
        ChestBlockEntity chest = h.getBlockEntity(new BlockPos(7, 2, 3), ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.RAW_IRON, 8));
        chest.setItem(1, new ItemStack(Items.COAL, 2));
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(2, 2, 2));
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_SHOVEL));
        h.succeedWhen(() -> {
            int ingots = count(h, new BlockPos(7, 2, 3), Items.IRON_INGOT);
            h.assertTrue(ingots >= 8, "expected 8 iron ingots, got " + ingots + " " + diag(h));
        });
    }

    /** 8 minerais de fer au coffre, un golem à la pioche Fortune III : on attend bien plus de 8 fers bruts. */
    private static void crusherFortuneMultiplies(GameTestHelper h) {
        floorAndZone(h, ModBlocks.CRUSHER_CONTROLLER.get());
        h.getBlockEntity(new BlockPos(7, 2, 3), ChestBlockEntity.class).setItem(0, new ItemStack(Items.IRON_ORE, 8));
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE), 3);
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(2, 2, 2));
        golem.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
        h.succeedWhen(() -> {
            int raw = count(h, new BlockPos(7, 2, 3), Items.RAW_IRON);
            h.assertTrue(count(h, new BlockPos(7, 2, 3), Items.IRON_ORE) == 0, "ores left in the chest " + diag(h));
            h.assertTrue(raw >= 20, "expected at least 20 raw iron from 8 ores with Fortune III, got " + raw);
        });
    }

    /** Trois épis de blé mûrs sur terre labourée : le golem à mains nues les récolte et replante. */
    private static void farmHarvestsAndReplants(GameTestHelper h) {
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 6; z++) h.setBlock(x, 1, z, x >= 1 && x <= 5 && z >= 1 && z <= 5 ? Blocks.FARMLAND : Blocks.STONE);
        BlockPos[] crops = {new BlockPos(2, 2, 2), new BlockPos(3, 2, 3), new BlockPos(4, 2, 4)};
        for (BlockPos c : crops) h.setBlock(c, Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
        h.setBlock(0, 2, 0, ModBlocks.BORDER_MARKER.get());
        h.setBlock(6, 2, 6, ModBlocks.BORDER_MARKER.get());
        h.setBlock(7, 2, 3, ModBlocks.FARM_CONTROLLER.get());
        h.setBlock(8, 2, 3, Blocks.CHEST);
        h.spawn(EntityType.COPPER_GOLEM, new BlockPos(0, 2, 3));
        h.succeedWhen(() -> {
            int wheat = count(h, new BlockPos(8, 2, 3), Items.WHEAT);
            h.assertTrue(wheat >= 3, "expected 3 wheat in the chest, got " + wheat);
            for (BlockPos c : crops) {
                var st = h.getLevel().getBlockState(h.absolutePos(c));
                h.assertTrue(st.is(Blocks.WHEAT) && st.getValue(net.minecraft.world.level.block.CropBlock.AGE) < 7, "not replanted at " + c);
            }
        });
    }

    /** Deux moutons immobiles, un golem aux cisailles : la laine doit finir au coffre. */
    private static void shepherdShearsSheep(GameTestHelper h) {
        floorAndZone(h, ModBlocks.SHEPHERD_CONTROLLER.get());
        for (int i = 0; i < 2; i++) {
            var sheep = h.spawn(EntityType.SHEEP, new BlockPos(2 + i * 2, 2, 4));
            sheep.setNoAi(true);
        }
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(2, 2, 2));
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
        h.succeedWhen(() -> {
            int wool = count(h, new BlockPos(7, 2, 3), Items.WHITE_WOOL);
            h.assertTrue(wool >= 2, "expected wool in the chest, got " + wool);
            h.assertTrue(h.getEntities(EntityType.SHEEP).stream().allMatch(net.minecraft.world.entity.animal.sheep.Sheep::isSheared), "a sheep is not sheared");
        });
    }

    /** Deux vaches dans un enclos fermé, du blé au coffre, un golem qui tient du blé : un veau doit naître. */
    private static void ranchBreeds(GameTestHelper h) {
        fencedRanch(h);
        h.getBlockEntity(new BlockPos(8, 2, 3), ChestBlockEntity.class).setItem(0, new ItemStack(Items.WHEAT, 8));
        h.spawn(EntityType.COW, new BlockPos(2, 2, 3));
        h.spawn(EntityType.COW, new BlockPos(4, 2, 3));
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(3, 2, 2));
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT));
        h.succeedWhen(() -> h.assertTrue(h.getEntities(EntityType.COW).size() >= 3, "no calf yet: " + h.getEntities(EntityType.COW).size() + " cows"));
    }

    /** Douze poules (seuil 10), un golem à l'épée : il doit en rester exactement 10. */
    private static void ranchCulls(GameTestHelper h) {
        fencedRanch(h);
        for (int i = 0; i < 12; i++) h.spawn(EntityType.CHICKEN, new BlockPos(2 + i % 3, 2, 2 + i / 4));
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(3, 2, 3));
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        h.runAfterDelay(400, () -> {
            int left = h.getEntities(EntityType.CHICKEN).size();
            h.assertTrue(left == 10, "expected 10 chickens left, got " + left);
            h.succeed();
        });
    }

    /** Un golem oxydé recruté dans une zone, puis un éclair : il redevient « altéré ». */
    private static void lightningDeoxidizes(GameTestHelper h) {
        floorAndZone(h, ModBlocks.QUARRY_CONTROLLER.get());
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(3, 2, 3));
        golem.setWeatherState(net.minecraft.world.level.block.WeatheringCopper.WeatherState.OXIDIZED);
        // Ciré : sinon le golem vanilla oxydé peut se changer en statue (0,58 % par tick) avant d'être recruté.
        golem.nextWeatheringTick = -2L;
        h.runAfterDelay(40, () -> {
            var bolt = EntityType.LIGHTNING_BOLT.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
            bolt.setVisualOnly(true);
            BlockPos at = h.absolutePos(new BlockPos(3, 2, 3));
            bolt.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            h.getLevel().addFreshEntity(bolt);
        });
        h.runAfterDelay(50, () -> {
            var be = h.getBlockEntity(new BlockPos(6, 2, 3), com.simpleautomation.zone.ZoneControllerBlockEntity.class);
            h.assertTrue(be.workers().size() == 1, "golem not recruited " + diag(h));
            h.assertTrue(be.workers().getFirst().weather == net.minecraft.world.level.block.WeatheringCopper.WeatherState.WEATHERED,
                    "expected WEATHERED after lightning, got " + be.workers().getFirst().weather);
            h.succeed();
        });
    }

    /** Enclos clôturé : bornes (1,2,1)–(5,2,5), clôture tout autour, contrôleur en (7,2,3), coffre en (8,2,3). */
    private static void fencedRanch(GameTestHelper h) {
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 6; z++) h.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
        for (int i = 0; i <= 6; i++) {
            h.setBlock(i, 2, 0, Blocks.OAK_FENCE);
            h.setBlock(i, 2, 6, Blocks.OAK_FENCE);
            h.setBlock(0, 2, i, Blocks.OAK_FENCE);
            h.setBlock(6, 2, i, Blocks.OAK_FENCE);
        }
        h.setBlock(1, 2, 1, ModBlocks.BORDER_MARKER.get());
        h.setBlock(5, 2, 5, ModBlocks.BORDER_MARKER.get());
        h.setBlock(7, 2, 3, ModBlocks.RANCH_CONTROLLER.get());
        h.setBlock(8, 2, 3, Blocks.CHEST);
    }

    /** Sol, deux bornes (1,2,1)–(5,2,5), contrôleur en (6,2,3) et coffre collé en (7,2,3). */
    private static void floorAndZone(GameTestHelper h, net.minecraft.world.level.block.Block controller) {
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 8; z++) h.setBlock(x, 1, z, Blocks.STONE);
        h.setBlock(1, 2, 1, ModBlocks.BORDER_MARKER.get());
        h.setBlock(5, 2, 5, ModBlocks.BORDER_MARKER.get());
        h.setBlock(6, 2, 3, controller);
        h.setBlock(7, 2, 3, Blocks.CHEST);
    }

    /** État du contrôleur en (6,2,3), pour comprendre un échec. */
    private static String diag(GameTestHelper h) {
        var be = h.getBlockEntity(new BlockPos(6, 2, 3), com.simpleautomation.zone.ZoneControllerBlockEntity.class);
        StringBuilder sb = new StringBuilder("[bounds=").append(be.bounds()).append(" workers=");
        for (var w : be.workers()) sb.append(w.status).append('/').append(w.tool.getItem()).append(' ');
        return sb.append(']').toString();
    }

    private static int count(GameTestHelper h, BlockPos chestPos, Item item) {
        ChestBlockEntity chest = h.getBlockEntity(chestPos, ChestBlockEntity.class);
        int n = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack s = chest.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private SATests() {
    }
}
