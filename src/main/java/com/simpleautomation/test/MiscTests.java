package com.simpleautomation.test;

import com.simpleautomation.registry.ModBlocks;
import com.simpleautomation.registry.ModTags;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Tests de la ferme, de la scierie et de l'enclos (itération 2). Enregistrés depuis {@link SATests}. */
public final class MiscTests {

    /** 25 cases de terre labourée vide, 30 graines de blé au coffre : tout est semé, et il reste exactement 5 graines. */
    public static void farmPlantsSeedsFromChest(GameTestHelper h) {
        var farmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE);
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 6; z++) h.setBlock(x, 1, z, x >= 1 && x <= 5 && z >= 1 && z <= 5 ? farmland : Blocks.STONE.defaultBlockState());
        h.setBlock(0, 2, 0, ModBlocks.BORDER_MARKER.get());
        h.setBlock(6, 2, 6, ModBlocks.BORDER_MARKER.get());
        h.setBlock(7, 2, 3, ModBlocks.FARM_CONTROLLER.get());
        h.setBlock(8, 2, 3, Blocks.CHEST);
        h.getBlockEntity(new BlockPos(8, 2, 3), ChestBlockEntity.class).setItem(0, new ItemStack(Items.WHEAT_SEEDS, 30));
        h.spawn(EntityType.COPPER_GOLEM, new BlockPos(0, 2, 3));
        h.spawn(EntityType.COPPER_GOLEM, new BlockPos(6, 2, 1));
        h.succeedWhen(() -> {
            for (int x = 1; x <= 5; x++)
                for (int z = 1; z <= 5; z++) h.assertBlockPresent(Blocks.WHEAT, x, 2, z);
            int seeds = count(h, new BlockPos(8, 2, 3), Items.WHEAT_SEEDS);
            h.assertTrue(seeds == 5, "expected 5 seeds left in the chest, got " + seeds);
        });
    }

    /** Graines de citrouille au coffre : le golem sème des tiges, jamais deux côte à côte (la place du fruit reste libre). */
    public static void farmSowsPumpkinSeeds(GameTestHelper h) {
        var farmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE);
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 6; z++) h.setBlock(x, 1, z, x >= 1 && x <= 5 && z >= 1 && z <= 5 ? farmland : Blocks.STONE.defaultBlockState());
        h.setBlock(0, 2, 0, ModBlocks.BORDER_MARKER.get());
        h.setBlock(6, 2, 6, ModBlocks.BORDER_MARKER.get());
        h.setBlock(7, 2, 3, ModBlocks.FARM_CONTROLLER.get());
        h.setBlock(8, 2, 3, Blocks.CHEST);
        h.getBlockEntity(new BlockPos(8, 2, 3), ChestBlockEntity.class).setItem(0, new ItemStack(Items.PUMPKIN_SEEDS, 30));
        h.spawn(EntityType.COPPER_GOLEM, new BlockPos(0, 2, 3));
        h.succeedWhen(() -> {
            int stems = 0;
            for (int x = 1; x <= 5; x++)
                for (int z = 1; z <= 5; z++) {
                    if (!h.getBlockState(new BlockPos(x, 2, z)).is(Blocks.PUMPKIN_STEM)) continue;
                    stems++;
                    h.assertFalse(h.getBlockState(new BlockPos(x + 1, 2, z)).is(Blocks.PUMPKIN_STEM)
                            || h.getBlockState(new BlockPos(x, 2, z + 1)).is(Blocks.PUMPKIN_STEM), "two stems side by side at " + x + "," + z);
                }
            h.assertTrue(stems >= 7, "expected at least 7 pumpkin stems, got " + stems);
            int seeds = count(h, new BlockPos(8, 2, 3), Items.PUMPKIN_SEEDS);
            h.assertTrue(seeds == 30 - stems, "seeds lost: " + seeds + " left for " + stems + " stems");
        });
    }

    /** Un palétuvier posé sur ses racines, dans la boue : le golem à la hache l'abat, les racines restent. */
    public static void lumberFellsMangrove(GameTestHelper h) {
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 8; z++) h.setBlock(x, 1, z, Blocks.MUD);
        h.setBlock(3, 2, 3, Blocks.MANGROVE_ROOTS);
        for (int y = 3; y <= 7; y++) h.setBlock(3, y, 3, Blocks.MANGROVE_LOG);
        var leaves = Blocks.MANGROVE_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int y = 6; y <= 8; y++)
                    if (!(dx == 0 && dz == 0 && y <= 7)) h.setBlock(3 + dx, y, 3 + dz, leaves);
        h.setBlock(1, 2, 1, ModBlocks.BORDER_MARKER.get());
        h.setBlock(5, 2, 5, ModBlocks.BORDER_MARKER.get());
        h.setBlock(6, 2, 6, ModBlocks.LUMBER_CONTROLLER.get());
        h.setBlock(7, 2, 6, Blocks.CHEST);
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(2, 2, 2));
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        h.succeedWhen(() -> {
            for (int y = 3; y <= 7; y++) h.assertBlockPresent(Blocks.AIR, 3, y, 3);
            h.assertBlockPresent(Blocks.MANGROVE_ROOTS, 3, 2, 3);
            int logs = count(h, new BlockPos(7, 2, 6), Items.MANGROVE_LOG);
            h.assertTrue(logs >= 5, "expected 5 mangrove logs in the chest, got " + logs);
        });
    }

    /** Deux vaches, le blé du coffre réparti en deux piles d'un seul : elles doivent quand même se reproduire, et les deux blés partir. */
    public static void ranchBreedsSplitStacks(GameTestHelper h) {
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
        ChestBlockEntity chest = h.getBlockEntity(new BlockPos(8, 2, 3), ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.WHEAT, 1));
        chest.setItem(13, new ItemStack(Items.WHEAT, 1));
        h.spawn(EntityType.COW, new BlockPos(2, 2, 3));
        h.spawn(EntityType.COW, new BlockPos(4, 2, 3));
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(3, 2, 2));
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT));
        h.succeedWhen(() -> {
            h.assertTrue(h.getEntities(EntityType.COW).size() >= 3, "no calf yet: " + h.getEntities(EntityType.COW).size() + " cows");
            int wheat = count(h, new BlockPos(8, 2, 3), Items.WHEAT);
            h.assertTrue(wheat == 0, "expected both wheat to be eaten, " + wheat + " left");
        });
    }

    /** Graines de pastèque au coffre, un golem à la houe en fer (accepté) : des tiges semées, jamais côte à côte, aucune graine perdue. */
    public static void farmSowsMelonWithHoe(GameTestHelper h) {
        var farmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE);
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 6; z++) h.setBlock(x, 1, z, x >= 1 && x <= 5 && z >= 1 && z <= 5 ? farmland : Blocks.STONE.defaultBlockState());
        h.setBlock(0, 2, 0, ModBlocks.BORDER_MARKER.get());
        h.setBlock(6, 2, 6, ModBlocks.BORDER_MARKER.get());
        h.setBlock(7, 2, 3, ModBlocks.FARM_CONTROLLER.get());
        h.setBlock(8, 2, 3, Blocks.CHEST);
        h.getBlockEntity(new BlockPos(8, 2, 3), ChestBlockEntity.class).setItem(0, new ItemStack(Items.MELON_SEEDS, 30));
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(0, 2, 3));
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
        h.succeedWhen(() -> {
            var be = h.getBlockEntity(new BlockPos(7, 2, 3), com.simpleautomation.zone.ZoneControllerBlockEntity.class);
            h.assertTrue(be.workers().size() == 1 && be.workers().getFirst().status != com.simpleautomation.zone.Worker.Status.NO_TOOL,
                    "the hoe golem is not working");
            int stems = 0;
            for (int x = 1; x <= 5; x++)
                for (int z = 1; z <= 5; z++) {
                    if (!h.getBlockState(new BlockPos(x, 2, z)).is(Blocks.MELON_STEM)) continue;
                    stems++;
                    h.assertFalse(h.getBlockState(new BlockPos(x + 1, 2, z)).is(Blocks.MELON_STEM)
                            || h.getBlockState(new BlockPos(x, 2, z + 1)).is(Blocks.MELON_STEM), "two stems side by side at " + x + "," + z);
                }
            h.assertTrue(stems >= 7, "expected at least 7 melon stems, got " + stems);
            int seeds = count(h, new BlockPos(8, 2, 3), Items.MELON_SEEDS);
            h.assertTrue(seeds == 30 - stems, "seeds lost: " + seeds + " left for " + stems + " stems");
        });
    }

    /**
     * Baies sucrées et cacao (sans ticks aléatoires) : le buisson mûr est cueilli et reste en place à l'âge 1, celui qui n'est
     * pas mûr n'est pas touché ; le cacao mûr est récolté et replanté jeune, tourné vers la même bûche.
     */
    public static void farmBerriesAndCocoa(GameTestHelper h) {
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 6; z++) h.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
        BlockPos ripeBush = new BlockPos(2, 2, 2);
        BlockPos youngBush = new BlockPos(2, 2, 4);
        h.setBlock(ripeBush, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3));
        h.setBlock(youngBush, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 2));
        BlockPos cocoa = new BlockPos(4, 2, 3);
        h.setBlock(4, 2, 4, Blocks.JUNGLE_LOG);
        h.setBlock(cocoa, Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, Direction.SOUTH).setValue(CocoaBlock.AGE, CocoaBlock.MAX_AGE));
        h.setBlock(1, 2, 1, ModBlocks.BORDER_MARKER.get());
        h.setBlock(5, 2, 5, ModBlocks.BORDER_MARKER.get());
        h.setBlock(7, 2, 3, ModBlocks.FARM_CONTROLLER.get());
        BlockPos chest = new BlockPos(8, 2, 3);
        h.setBlock(chest, Blocks.CHEST);
        h.spawn(EntityType.COPPER_GOLEM, new BlockPos(3, 2, 1));

        h.succeedWhen(() -> {
            h.assertTrue(count(h, chest, Items.SWEET_BERRIES) >= 1, "no sweet berries in the chest");
            h.assertTrue(count(h, chest, Items.COCOA_BEANS) >= 1, "no cocoa beans in the chest");
            BlockState bush = h.getBlockState(ripeBush);
            h.assertTrue(bush.is(Blocks.SWEET_BERRY_BUSH) && bush.getValue(SweetBerryBushBlock.AGE) == 1, "the picked bush is not back to age 1: " + bush);
            h.assertTrue(h.getBlockState(youngBush).getValue(SweetBerryBushBlock.AGE) == 2, "the unripe bush was touched");
            BlockState c = h.getBlockState(cocoa);
            h.assertTrue(c.is(Blocks.COCOA) && c.getValue(CocoaBlock.AGE) == 0 && c.getValue(CocoaBlock.FACING) == Direction.SOUTH,
                    "the cocoa was not replanted young against the same log: " + c);
            h.assertBlockPresent(Blocks.JUNGLE_LOG, new BlockPos(4, 2, 4));
            h.assertTrue(h.getEntities(EntityType.ITEM).isEmpty(), "items were left on the ground");
        });
    }

    /**
     * Le tag simple_automation:farm_crops (rempli le temps du test avec les lianes torsadées, sans ticks aléatoires) :
     * la liane à l'âge maximal est récoltée et replantée à l'âge 0 avec une liane du coffre ; l'autre n'est pas touchée.
     */
    public static void farmCropsTag(GameTestHelper h) {
        var old = TestKit.setTag(h, BuiltInRegistries.BLOCK, ModTags.FARM_CROPS, List.of(BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.TWISTING_VINES)));
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 6; z++) h.setBlock(x, 1, z, Blocks.STONE);
        BlockPos ripe = new BlockPos(2, 2, 2);
        BlockPos young = new BlockPos(4, 2, 4);
        h.setBlock(ripe, Blocks.TWISTING_VINES.defaultBlockState().setValue(GrowingPlantHeadBlock.AGE, GrowingPlantHeadBlock.MAX_AGE));
        h.setBlock(young, Blocks.TWISTING_VINES.defaultBlockState().setValue(GrowingPlantHeadBlock.AGE, 10));
        h.setBlock(1, 2, 1, ModBlocks.BORDER_MARKER.get());
        h.setBlock(5, 2, 5, ModBlocks.BORDER_MARKER.get());
        h.setBlock(7, 2, 3, ModBlocks.FARM_CONTROLLER.get());
        BlockPos chest = new BlockPos(8, 2, 3);
        h.setBlock(chest, Blocks.CHEST);
        h.getBlockEntity(chest, ChestBlockEntity.class).setItem(0, new ItemStack(Items.TWISTING_VINES, 4));
        h.spawn(EntityType.COPPER_GOLEM, new BlockPos(3, 2, 1));

        h.startSequence()
                .thenWaitUntil(() -> {
                    BlockState s = h.getBlockState(ripe);
                    h.assertTrue(s.is(Blocks.TWISTING_VINES) && s.getValue(GrowingPlantHeadBlock.AGE) == 0, "the ripe vine was not harvested and replanted: " + s);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    TestKit.setTag(BuiltInRegistries.BLOCK, ModTags.FARM_CROPS, old);
                    h.assertTrue(h.getBlockState(young).getValue(GrowingPlantHeadBlock.AGE) == 10, "the young vine was touched");
                    h.assertTrue(count(h, chest, Items.TWISTING_VINES) <= 4, "vines appeared from nowhere");
                })
                .thenSucceed();
    }

    /** Une pousse et une pomme au sol dans la scierie (l'une au bord du feuillage, hors des bornes) : elles finissent au coffre. */
    public static void lumberCollectsItems(GameTestHelper h) {
        TestKit.floorAndZone(h, ModBlocks.LUMBER_CONTROLLER.get());
        h.spawnItem(Items.OAK_SAPLING, new BlockPos(3, 2, 3));
        h.spawnItem(Items.APPLE, new BlockPos(3, 3, 2));
        h.spawnItem(Items.BIRCH_SAPLING, new BlockPos(0, 2, 3));
        h.succeedWhen(() -> {
            h.assertTrue(count(h, TestKit.CHEST, Items.OAK_SAPLING) == 1, "the oak sapling is not in the chest");
            h.assertTrue(count(h, TestKit.CHEST, Items.APPLE) == 1, "the apple is not in the chest");
            h.assertTrue(count(h, TestKit.CHEST, Items.BIRCH_SAPLING) == 1, "the sapling under the leaves' overhang is not in the chest");
            h.assertTrue(h.getEntities(EntityType.ITEM).isEmpty(), "items left on the ground");
        });
    }

    /**
     * Un champignon carmin géant sur du nylium, un champignon carmin au coffre : les tiges sont abattues et rangées,
     * le chapeau reste en l'air, et un champignon carmin est replanté sur le nylium.
     */
    public static void lumberFellsNetherFungus(GameTestHelper h) {
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 8; z++) h.setBlock(x, 1, z, Blocks.CRIMSON_NYLIUM);
        for (int y = 2; y <= 6; y++) h.setBlock(3, y, 3, Blocks.CRIMSON_STEM);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int y = 6; y <= 7; y++)
                    if (!(dx == 0 && dz == 0 && y == 6)) h.setBlock(3 + dx, y, 3 + dz, Blocks.NETHER_WART_BLOCK);
        h.setBlock(1, 2, 1, ModBlocks.BORDER_MARKER.get());
        h.setBlock(5, 2, 5, ModBlocks.BORDER_MARKER.get());
        h.setBlock(6, 2, 6, ModBlocks.LUMBER_CONTROLLER.get());
        BlockPos chest = new BlockPos(7, 2, 6);
        h.setBlock(chest, Blocks.CHEST);
        h.getBlockEntity(chest, ChestBlockEntity.class).setItem(0, new ItemStack(Items.CRIMSON_FUNGUS, 2));
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(2, 2, 2));
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        h.succeedWhen(() -> {
            for (int y = 3; y <= 6; y++) h.assertBlockPresent(Blocks.AIR, 3, y, 3);
            int stems = count(h, chest, Items.CRIMSON_STEM);
            h.assertTrue(stems >= 5, "expected 5 crimson stems in the chest, got " + stems);
            h.assertBlockPresent(Blocks.CRIMSON_FUNGUS, 3, 2, 3);
            h.assertTrue(count(h, chest, Items.CRIMSON_FUNGUS) == 1, "expected one fungus used from the chest");
            h.assertBlockPresent(Blocks.NETHER_WART_BLOCK, 3, 7, 3);
        });
    }

    /** Un cacao mûr haut sur un tronc de jungle (6 blocs au-dessus des bornes) : il est récolté et replanté jeune. */
    public static void farmHighCocoa(GameTestHelper h) {
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 6; z++) h.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
        for (int y = 2; y <= 9; y++) h.setBlock(4, y, 4, Blocks.JUNGLE_LOG);
        BlockPos cocoa = new BlockPos(4, 8, 3);
        h.setBlock(cocoa, Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, Direction.SOUTH).setValue(CocoaBlock.AGE, CocoaBlock.MAX_AGE));
        h.setBlock(1, 2, 1, ModBlocks.BORDER_MARKER.get());
        h.setBlock(5, 2, 5, ModBlocks.BORDER_MARKER.get());
        h.setBlock(7, 2, 3, ModBlocks.FARM_CONTROLLER.get());
        BlockPos chest = new BlockPos(8, 2, 3);
        h.setBlock(chest, Blocks.CHEST);
        h.spawn(EntityType.COPPER_GOLEM, new BlockPos(3, 2, 1));

        h.succeedWhen(() -> {
            h.assertTrue(count(h, chest, Items.COCOA_BEANS) >= 1, "the high cocoa was not harvested");
            BlockState c = h.getBlockState(cocoa);
            h.assertTrue(c.is(Blocks.COCOA) && c.getValue(CocoaBlock.AGE) == 0, "the high cocoa was not replanted young: " + c);
        });
    }

    /**
     * Le tag farm_seeds (rempli le temps du test) avec une pousse de chêne (pas d'« age » : la ferme ne saurait pas la
     * récolter) et des baies sucrées : seules les baies sont semées, les pousses restent au coffre.
     */
    public static void farmSeedsTagSkipsUnharvestable(GameTestHelper h) {
        TestKit.setTag(h, BuiltInRegistries.ITEM, ModTags.FARM_SEEDS,
                List.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.OAK_SAPLING), BuiltInRegistries.ITEM.wrapAsHolder(Items.SWEET_BERRIES)));
        var farmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE);
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 6; z++) h.setBlock(x, 1, z, x >= 2 && x <= 4 && z >= 2 && z <= 4 ? farmland : Blocks.STONE.defaultBlockState());
        h.setBlock(1, 2, 1, ModBlocks.BORDER_MARKER.get());
        h.setBlock(5, 2, 5, ModBlocks.BORDER_MARKER.get());
        h.setBlock(7, 2, 3, ModBlocks.FARM_CONTROLLER.get());
        BlockPos chest = new BlockPos(8, 2, 3);
        h.setBlock(chest, Blocks.CHEST);
        h.getBlockEntity(chest, ChestBlockEntity.class).setItem(0, new ItemStack(Items.OAK_SAPLING, 4));
        h.getBlockEntity(chest, ChestBlockEntity.class).setItem(1, new ItemStack(Items.SWEET_BERRIES, 16));
        h.spawn(EntityType.COPPER_GOLEM, new BlockPos(1, 2, 3));

        h.succeedWhen(() -> {
            int bushes = TestKit.countBlocks(h, Blocks.SWEET_BERRY_BUSH, new BlockPos(2, 2, 2), new BlockPos(4, 2, 4));
            h.assertTrue(bushes == 9, "expected 9 sweet berry bushes, got " + bushes);
            h.assertTrue(TestKit.countBlocks(h, Blocks.OAK_SAPLING, new BlockPos(2, 2, 2), new BlockPos(4, 2, 4)) == 0, "a sapling was sown");
            h.assertTrue(count(h, chest, Items.OAK_SAPLING) == 4, "saplings left the chest");
            h.assertTrue(count(h, chest, Items.SWEET_BERRIES) == 7, "expected 7 berries left, got " + count(h, chest, Items.SWEET_BERRIES));
        });
    }

    /** Un petit champignon carmin abattu, champignons carmin et poudre d'os au coffre : il est replanté puis regrandit. */
    public static void lumberBonemealsFungus(GameTestHelper h) {
        BlockPos chest = netherYard(h, new ItemStack(Items.CRIMSON_FUNGUS, 2));
        h.getBlockEntity(chest, ChestBlockEntity.class).setItem(1, new ItemStack(Items.BONE_MEAL, 32));
        h.succeedWhen(() -> {
            h.assertTrue(count(h, chest, Items.BONE_MEAL) < 32, "no bone meal used");
            h.assertTrue(count(h, chest, Items.CRIMSON_STEM) > 3 || h.getBlockState(new BlockPos(3, 2, 3)).is(Blocks.CRIMSON_STEM),
                    "the replanted fungus did not grow: " + h.getBlockState(new BlockPos(3, 2, 3)));
        });
    }

    /** Seulement des champignons biscornus au coffre pour une souche sur nylium carmin : rien n'est planté, rien n'est pris. */
    public static void lumberSkipsWrongFungus(GameTestHelper h) {
        BlockPos chest = netherYard(h, new ItemStack(Items.WARPED_FUNGUS, 2));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(count(h, chest, Items.CRIMSON_STEM) >= 3, "the stem was not felled"))
                .thenIdle(120)
                .thenExecute(() -> {
                    h.assertBlockPresent(Blocks.AIR, 3, 2, 3);
                    h.assertTrue(count(h, chest, Items.WARPED_FUNGUS) == 2, "a warped fungus was used on crimson nylium");
                })
                .thenSucceed();
    }

    /** Sol de nylium carmin, une tige carmin de 3 blocs avec un bout de chapeau, une scierie et un golem à la hache ; renvoie le coffre. */
    private static BlockPos netherYard(GameTestHelper h, ItemStack inChest) {
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 8; z++) h.setBlock(x, 1, z, Blocks.CRIMSON_NYLIUM);
        for (int y = 2; y <= 4; y++) h.setBlock(3, y, 3, Blocks.CRIMSON_STEM);
        // Un chapeau minimal sur les côtés (rien au-dessus : la colonne reste libre pour que le champignon regrandisse).
        for (Direction d : Direction.Plane.HORIZONTAL) h.setBlock(new BlockPos(3, 4, 3).relative(d), Blocks.NETHER_WART_BLOCK);
        h.setBlock(1, 2, 1, ModBlocks.BORDER_MARKER.get());
        h.setBlock(5, 2, 5, ModBlocks.BORDER_MARKER.get());
        h.setBlock(6, 2, 6, ModBlocks.LUMBER_CONTROLLER.get());
        BlockPos chest = new BlockPos(7, 2, 6);
        h.setBlock(chest, Blocks.CHEST);
        h.getBlockEntity(chest, ChestBlockEntity.class).setItem(0, inChest);
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(2, 2, 2));
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        return chest;
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

    private MiscTests() {
    }
}
