package com.simpleautomation.test;

import com.simpleautomation.SAConfig;
import com.simpleautomation.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.Bees;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Tests du replantage de la scierie (partie 1). Enregistrés depuis {@link SATests}. */
public final class LumberTests {

    private static final BlockPos CHEST = new BlockPos(7, 2, 6);

    /** Sol, bornes (1..5), contrôleur et coffre, un golem à la hache en fer. */
    private static ChestBlockEntity yard(GameTestHelper h, Block floor) {
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 8; z++) h.setBlock(x, 1, z, floor);
        h.setBlock(1, 2, 1, ModBlocks.BORDER_MARKER.get());
        h.setBlock(5, 2, 5, ModBlocks.BORDER_MARKER.get());
        h.setBlock(6, 2, 6, ModBlocks.LUMBER_CONTROLLER.get());
        h.setBlock(CHEST, Blocks.CHEST);
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(1, 2, 4));
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        return h.getBlockEntity(CHEST, ChestBlockEntity.class);
    }

    /** Tronc 2×2 (x 3..4, z 3..4), voir {@link #cap}. */
    private static void megaTrunk(GameTestHelper h, Block log) {
        for (int y = 2; y <= 7; y++)
            for (int x = 3; x <= 4; x++)
                for (int z = 3; z <= 4; z++) h.setBlock(x, y, z, log);
        cap(h, 3, 8, 3);
    }

    /**
     * Un tronc n'est un arbre qu'avec du feuillage : des blocs de verrue en comptent, mais ne sont pas récoltés et ne
     * donnent aucune pousse. Seules les pousses du coffre comptent donc pour le replantage.
     */
    private static void cap(GameTestHelper h, int x, int y, int z) {
        for (BlockPos p : new BlockPos[] {new BlockPos(x, y, z), new BlockPos(x + 1, y, z), new BlockPos(x, y, z + 1),
                new BlockPos(x - 1, y - 1, z), new BlockPos(x, y - 1, z - 1)}) {
            if (h.getBlockState(p).isAir()) h.setBlock(p, Blocks.NETHER_WART_BLOCK);
        }
    }

    /** Chêne noir 2×2, 4 pousses de chêne noir et 8 de chêne au coffre : le carré est replanté en chêne noir, sans mélange. */
    public static void darkOakReplantedAsSquare(GameTestHelper h) {
        ChestBlockEntity chest = yard(h, Blocks.DIRT);
        chest.setItem(0, new ItemStack(Items.OAK_SAPLING, 8));
        chest.setItem(1, new ItemStack(Items.DARK_OAK_SAPLING, 4));
        megaTrunk(h, Blocks.DARK_OAK_LOG);
        h.succeedWhen(() -> {
            for (int x = 3; x <= 4; x++)
                for (int z = 3; z <= 4; z++) h.assertBlockPresent(Blocks.DARK_OAK_SAPLING, x, 2, z);
            h.assertTrue(TestKit.count(h, CHEST, Items.OAK_SAPLING) == 8, "oak saplings were used for a dark oak");
        });
    }

    /**
     * Chêne noir 2×2 avec seulement 3 pousses de chêne noir : rien n'est planté, pas même une seule (il ne pousse qu'en carré).
     * Pas d'autre espèce au coffre : le test épicéa raccourcit le délai de secours en parallèle.
     */
    public static void darkOakWaitsForFour(GameTestHelper h) {
        ChestBlockEntity chest = yard(h, Blocks.DIRT);
        chest.setItem(1, new ItemStack(Items.DARK_OAK_SAPLING, 3));
        megaTrunk(h, Blocks.DARK_OAK_LOG);
        h.runAfterDelay(400, () -> {
            h.assertBlockPresent(Blocks.AIR, 3, 7, 3); // L'arbre a bien été abattu.
            int planted = TestKit.countBlocks(h, Blocks.DARK_OAK_SAPLING, new BlockPos(3, 2, 3), new BlockPos(4, 2, 4));
            h.assertTrue(planted == 0, "expected nothing planted with 3 dark oak saplings, got " + planted);
            h.assertTrue(TestKit.count(h, CHEST, Items.DARK_OAK_SAPLING) == 3, "dark oak saplings left the chest");
            h.assertTrue(TestKit.hasLine(TestKit.describe(TestKit.zone(h, new BlockPos(6, 2, 6))), "message.simple_automation.lumber.needs_saplings"),
                    "the controller does not say it waits for saplings");
            h.succeed();
        });
    }

    /** Épicéa 2×2 avec une seule pousse d'épicéa : après le délai de secours, elle est plantée seule à la base. */
    public static void spruceFallsBackToOne(GameTestHelper h) {
        int old = SAConfig.LUMBER_FALLBACK_SECONDS.getAsInt();
        SAConfig.LUMBER_FALLBACK_SECONDS.set(2);
        TestKit.onFinish(h, () -> SAConfig.LUMBER_FALLBACK_SECONDS.set(old));
        ChestBlockEntity chest = yard(h, Blocks.DIRT);
        chest.setItem(0, new ItemStack(Items.SPRUCE_SAPLING, 1));
        megaTrunk(h, Blocks.SPRUCE_LOG);
        h.succeedWhen(() -> {
            h.assertBlockPresent(Blocks.SPRUCE_SAPLING, 3, 2, 3);
            int planted = TestKit.countBlocks(h, Blocks.SPRUCE_SAPLING, new BlockPos(3, 2, 3), new BlockPos(4, 2, 4));
            h.assertTrue(planted == 1, "expected 1 spruce sapling, got " + planted);
        });
    }

    /** Chêne au large feuillage, coffre vide : les feuilles tombent avec l'arbre et leurs pousses servent à le replanter. */
    public static void leavesGiveSaplings(GameTestHelper h) {
        yard(h, Blocks.DIRT);
        for (int y = 2; y <= 6; y++) h.setBlock(3, y, 3, Blocks.OAK_LOG);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        // ~200 feuilles : la probabilité qu'aucune ne donne de pousse (5 % chacune) est négligeable.
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                for (int y = 4; y <= 8; y++)
                    if (!(dx == 0 && dz == 0 && y <= 6)) h.setBlock(3 + dx, y, 3 + dz, leaves);
        h.succeedWhen(() -> {
            h.assertBlockPresent(Blocks.OAK_SAPLING, 3, 2, 3);
            h.assertBlockPresent(Blocks.AIR, 3, 8, 3);
            h.assertBlockPresent(Blocks.AIR, 0, 6, 0);
            h.assertTrue(TestKit.count(h, CHEST, Items.STICK) > 0, "no sticks from the leaves");
        });
    }

    /** Palétuvier sur racines, une propagule au coffre : elle est plantée sur la boue à côté des racines. */
    public static void mangroveReplantedBesideRoots(GameTestHelper h) {
        ChestBlockEntity chest = yard(h, Blocks.MUD);
        chest.setItem(0, new ItemStack(Items.MANGROVE_PROPAGULE, 1));
        h.setBlock(3, 2, 3, Blocks.MANGROVE_ROOTS);
        for (int y = 3; y <= 7; y++) h.setBlock(3, y, 3, Blocks.MANGROVE_LOG);
        cap(h, 3, 8, 3);
        h.succeedWhen(() -> {
            h.assertBlockPresent(Blocks.AIR, 3, 5, 3);
            h.assertBlockPresent(Blocks.MANGROVE_ROOTS, 3, 2, 3);
            int planted = TestKit.countBlocks(h, Blocks.MANGROVE_PROPAGULE, new BlockPos(0, 2, 0), new BlockPos(6, 3, 6));
            h.assertTrue(planted == 1, "expected 1 propagule planted next to the roots, got " + planted);
        });
    }

    /**
     * Un chêne avec un nid d'abeilles contre le tronc (une abeille dedans, miel au niveau 3) : le nid ne reste pas en
     * l'air, il arrive au coffre avec son abeille et son miel.
     */
    public static void beeNestFallsWithTree(GameTestHelper h) {
        yard(h, Blocks.DIRT);
        for (int y = 2; y <= 6; y++) h.setBlock(3, y, 3, Blocks.OAK_LOG);
        cap(h, 3, 7, 3);
        h.setBlock(4, 4, 3, Blocks.BEE_NEST.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 3));
        h.getBlockEntity(new BlockPos(4, 4, 3), BeehiveBlockEntity.class).storeBee(BeehiveBlockEntity.Occupant.create(0));
        h.succeedWhen(() -> {
            h.assertBlockPresent(Blocks.AIR, 3, 4, 3);
            h.assertBlockPresent(Blocks.AIR, 4, 4, 3);
            ItemStack nest = ItemStack.EMPTY;
            ChestBlockEntity chest = TestKit.chest(h, CHEST);
            for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(Items.BEE_NEST)) nest = chest.getItem(i);
            h.assertFalse(nest.isEmpty(), "the bee nest did not reach the chest");
            var bees = nest.getOrDefault(DataComponents.BEES, Bees.EMPTY).bees();
            h.assertTrue(bees.size() == 1, "expected the bee inside the nest, got " + bees.size());
            var props = nest.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
            h.assertTrue(Integer.valueOf(3).equals(props.get(BeehiveBlock.HONEY_LEVEL)), "honey level lost: " + props);
        });
    }

    private LumberTests() {
    }
}
