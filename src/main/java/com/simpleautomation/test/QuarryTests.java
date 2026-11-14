package com.simpleautomation.test;

import com.simpleautomation.job.QuarryJob;
import com.simpleautomation.registry.ModBlocks;
import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Tests de la carrière : mains nues, gravité des golems. Enregistrés depuis {@link SATests}. */
public final class QuarryTests {

    /** Une carrière 4×4 sur 2 couches de terre, un golem sans outil : la couche du haut doit finir au coffre. */
    public static void bareHandDigsDirt(GameTestHelper h) {
        for (int x = 1; x <= 7; x++)
            for (int z = 1; z <= 6; z++)
                for (int y = 1; y <= 2; y++) h.setBlock(x, y, z, Blocks.DIRT);
        h.setBlock(1, 3, 1, ModBlocks.BORDER_MARKER.get());
        h.setBlock(4, 3, 4, ModBlocks.BORDER_MARKER.get());
        BlockPos controller = new BlockPos(6, 3, 2);
        h.setBlock(controller, ModBlocks.QUARRY_CONTROLLER.get());
        h.setBlock(7, 3, 2, Blocks.CHEST);
        h.spawn(EntityType.COPPER_GOLEM, new BlockPos(2, 3, 2));

        h.succeedWhen(() -> {
            ZoneControllerBlockEntity be = h.getBlockEntity(controller, ZoneControllerBlockEntity.class);
            h.assertTrue(be.workers().size() == 1, "golem not recruited: " + be.workers().size() + " workers");
            h.assertTrue(be.workers().getFirst().tool.isEmpty(), "the golem should have no tool");
            for (int x = 1; x <= 4; x++)
                for (int z = 1; z <= 4; z++)
                    h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(x, 2, z))).isAir(), "dirt layer not dug at " + x + "," + z);
            int dirt = count(h, new BlockPos(7, 3, 2), Items.DIRT);
            h.assertTrue(dirt >= 16, "expected at least 16 dirt in the chest, got " + dirt);
        });
    }

    /**
     * Carrière 5×5 : terre et pierre en damier, puis une couche de pierre, puis de l'obsidienne que personne ne casse.
     * Un golem à mains nues, un à la pelle, un à la pioche. À chaque tick, aucun ouvrier ne doit avoir de l'air sous les pieds,
     * et les deux couches du haut doivent finir creusées.
     */
    public static void noFloating(GameTestHelper h) {
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 6; z++)
                for (int y = 1; y <= 3; y++) h.setBlock(x, y, z, Blocks.STONE);
        for (int x = 1; x <= 5; x++)
            for (int z = 1; z <= 5; z++) {
                h.setBlock(x, 1, z, Blocks.OBSIDIAN);
                if ((x + z) % 2 == 0) h.setBlock(x, 3, z, Blocks.DIRT);
            }
        h.setBlock(1, 4, 1, ModBlocks.BORDER_MARKER.get());
        h.setBlock(5, 4, 5, ModBlocks.BORDER_MARKER.get());
        BlockPos controller = new BlockPos(7, 4, 3);
        h.setBlock(controller, ModBlocks.QUARRY_CONTROLLER.get());
        h.setBlock(8, 4, 3, Blocks.CHEST);
        h.spawn(EntityType.COPPER_GOLEM, new BlockPos(2, 4, 2));
        CopperGolem shovel = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(4, 4, 2));
        shovel.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SHOVEL));
        CopperGolem pickaxe = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(3, 4, 4));
        pickaxe.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));

        h.onEachTick(() -> assertNoneFloating(h, controller));
        h.succeedWhen(() -> {
            ZoneControllerBlockEntity be = h.getBlockEntity(controller, ZoneControllerBlockEntity.class);
            h.assertTrue(be.workers().size() == 3, "expected 3 workers, got " + be.workers().size());
            for (int y = 2; y <= 3; y++)
                for (int x = 1; x <= 5; x++)
                    for (int z = 1; z <= 5; z++)
                        h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(x, y, z))).isAir(), "not dug at " + x + "," + y + "," + z);
            assertNoneFloating(h, controller);
        });
    }

    /**
     * Une couche de terre sur deux couches de pierre, un golem sans outil : il creuse la terre puis la carrière attend
     * sur la pierre (elle ne doit ni la sauter ni se dire finie). On lui donne une pioche : la pierre est creusée.
     */
    public static void waitsForPickaxe(GameTestHelper h) {
        for (int x = 1; x <= 7; x++)
            for (int z = 1; z <= 6; z++)
                for (int y = 1; y <= 3; y++) h.setBlock(x, y, z, y == 3 ? Blocks.DIRT : Blocks.STONE);
        h.setBlock(1, 4, 1, ModBlocks.BORDER_MARKER.get());
        h.setBlock(4, 4, 4, ModBlocks.BORDER_MARKER.get());
        BlockPos controller = new BlockPos(6, 4, 2);
        h.setBlock(controller, ModBlocks.QUARRY_CONTROLLER.get());
        h.setBlock(7, 4, 2, Blocks.CHEST);
        h.spawn(EntityType.COPPER_GOLEM, new BlockPos(2, 4, 2));
        int stoneY = h.absolutePos(new BlockPos(0, 2, 0)).getY();

        h.startSequence()
                .thenWaitUntil(() -> {
                    ZoneControllerBlockEntity be = h.getBlockEntity(controller, ZoneControllerBlockEntity.class);
                    h.assertTrue(be.workers().size() == 1, "golem not recruited");
                    assertLayerDug(h, 3);
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    ZoneControllerBlockEntity be = h.getBlockEntity(controller, ZoneControllerBlockEntity.class);
                    QuarryJob job = (QuarryJob) be.job();
                    h.assertFalse(job.isFinished(), "the quarry finished without a pickaxe");
                    h.assertTrue(job.layerY() == stoneY, "the quarry left the stone layer: now at Y " + job.layerY() + ", stone at " + stoneY);
                    be.workers().getFirst().tool = new ItemStack(Items.IRON_PICKAXE);
                })
                .thenWaitUntil(() -> assertLayerDug(h, 2))
                .thenSucceed();
    }

    /**
     * Carrière 4×4 sur deux couches de pierre ; dessous, une grotte (couche vide) avec de la lave, de l'eau et un coffre.
     * À chaque tick, aucun golem ne flotte (de la cobble est posée sous ses pieds) ; une fois la grotte passée,
     * plus aucun fluide dans la zone et le coffre est toujours là, avec son contenu.
     */
    public static void floorOverCave(GameTestHelper h) {
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 8; z++)
                for (int y = 1; y <= 4; y++) h.setBlock(x, y, z, Blocks.STONE);
        for (int x = 2; x <= 5; x++)
            for (int z = 2; z <= 5; z++) h.setBlock(x, 3, z, Blocks.AIR);
        h.setBlock(5, 3, 5, Blocks.LAVA);
        h.setBlock(2, 3, 5, Blocks.WATER);
        // Le coffre, dans un coin de la grotte, muré du côté des fluides.
        BlockPos cave = new BlockPos(2, 3, 2);
        h.setBlock(3, 3, 2, Blocks.STONE);
        h.setBlock(2, 3, 3, Blocks.STONE);
        h.setBlock(3, 3, 3, Blocks.STONE);
        h.setBlock(cave, Blocks.CHEST);
        TestKit.chest(h, cave).setItem(0, new ItemStack(Items.DIAMOND, 5));
        h.setBlock(2, 5, 2, ModBlocks.BORDER_MARKER.get());
        h.setBlock(5, 5, 5, ModBlocks.BORDER_MARKER.get());
        BlockPos controller = new BlockPos(7, 5, 3);
        h.setBlock(controller, ModBlocks.QUARRY_CONTROLLER.get());
        h.setBlock(8, 5, 3, Blocks.CHEST);
        CopperGolem a = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(3, 5, 3));
        a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        CopperGolem b = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(4, 5, 4));
        b.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        int caveY = h.absolutePos(cave).getY();

        h.onEachTick(() -> assertNoneFloating(h, controller));
        h.succeedWhen(() -> {
            QuarryJob job = (QuarryJob) h.getBlockEntity(controller, ZoneControllerBlockEntity.class).job();
            h.assertTrue(job.layerY() < caveY || job.isFinished(), "the quarry has not passed the cave yet (layer " + job.layerY() + ")");
            for (int x = 2; x <= 5; x++)
                for (int z = 2; z <= 5; z++)
                    for (int y = 3; y <= 4; y++)
                        h.assertTrue(h.getBlockState(new BlockPos(x, y, z)).getFluidState().isEmpty(), "fluid left at " + x + "," + y + "," + z);
            h.assertBlockPresent(Blocks.CHEST, cave);
            h.assertTrue(TestKit.count(h, cave, Items.DIAMOND) == 5, "the chest under the golems lost its contents");
        });
    }

    /**
     * Une couche de pierre avec de la lave et de l'eau dedans, de la lave collée à la zone (dehors) et sous la couche.
     * Une fois la couche creusée : plus aucun fluide dans la zone, la lave d'à côté est bouchée par de la cobble.
     */
    public static void sealsLavaAndWater(GameTestHelper h) {
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 8; z++)
                for (int y = 1; y <= 4; y++) h.setBlock(x, y, z, Blocks.STONE);
        h.setBlock(3, 4, 3, Blocks.LAVA);
        h.setBlock(4, 4, 4, Blocks.WATER);
        BlockPos outside = new BlockPos(1, 4, 3);
        h.setBlock(outside, Blocks.LAVA);
        BlockPos below = new BlockPos(4, 3, 2);
        h.setBlock(below, Blocks.LAVA);
        h.setBlock(2, 5, 2, ModBlocks.BORDER_MARKER.get());
        h.setBlock(5, 5, 5, ModBlocks.BORDER_MARKER.get());
        BlockPos controller = new BlockPos(7, 5, 3);
        h.setBlock(controller, ModBlocks.QUARRY_CONTROLLER.get());
        h.setBlock(8, 5, 3, Blocks.CHEST);
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(3, 5, 4));
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));

        h.succeedWhen(() -> {
            for (int x = 2; x <= 5; x++)
                for (int z = 2; z <= 5; z++)
                    h.assertTrue(h.getBlockState(new BlockPos(x, 4, z)).isAir(), "layer not dug at " + x + ",4," + z);
            h.assertBlockPresent(Blocks.COBBLESTONE, outside);
            h.assertTrue(h.getBlockState(below).getFluidState().isEmpty(), "the lava under the layer was not sealed");
            for (int x = 1; x <= 6; x++)
                for (int z = 1; z <= 6; z++)
                    h.assertTrue(h.getBlockState(new BlockPos(x, 4, z)).getFluidState().isEmpty(), "fluid left at " + x + ",4," + z);
        });
    }

    /** Pioche Silk Touch : la pierre arrive au coffre telle quelle (pas de cobble), et la pioche s'use d'un point par bloc. */
    public static void silkTouchAndWear(GameTestHelper h) {
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 6; z++) h.setBlock(x, 1, z, Blocks.STONE);
        h.setBlock(2, 2, 2, ModBlocks.BORDER_MARKER.get());
        h.setBlock(3, 2, 3, ModBlocks.BORDER_MARKER.get());
        BlockPos controller = new BlockPos(5, 2, 2);
        BlockPos chest = new BlockPos(6, 2, 2);
        h.setBlock(controller, ModBlocks.QUARRY_CONTROLLER.get());
        h.setBlock(chest, Blocks.CHEST);
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(1, 2, 1));
        golem.setItemInHand(InteractionHand.MAIN_HAND,
                TestKit.enchanted(h, Items.DIAMOND_PICKAXE, net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH, 1));

        h.succeedWhen(() -> {
            ZoneControllerBlockEntity be = h.getBlockEntity(controller, ZoneControllerBlockEntity.class);
            h.assertTrue(be.workers().size() == 1, "golem not recruited");
            int stone = TestKit.count(h, chest, Items.STONE);
            h.assertTrue(stone >= 4, "expected 4 stone (silk touch) in the chest, got " + stone);
            h.assertTrue(TestKit.count(h, chest, Items.COBBLESTONE) == 0, "silk touch gave cobblestone");
            int damage = be.workers().getFirst().tool.getDamageValue();
            h.assertTrue(damage >= 4, "the pickaxe did not wear: damage " + damage);
        });
    }

    private static void assertLayerDug(GameTestHelper h, int y) {
        for (int x = 1; x <= 4; x++)
            for (int z = 1; z <= 4; z++)
                h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(x, y, z))).isAir(), "not dug at " + x + "," + y + "," + z);
    }

    private static void assertNoneFloating(GameTestHelper h, BlockPos controller) {
        ZoneControllerBlockEntity be = h.getBlockEntity(controller, ZoneControllerBlockEntity.class);
        for (Worker w : be.workers()) {
            if (!ZoneControllerBlockEntity.hasFloor(h.getLevel(), w.stand)) {
                h.fail("worker " + w.status + " with " + w.tool.getItem() + " floats at " + h.relativePos(w.stand));
            }
        }
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

    private QuarryTests() {
    }
}
