package com.simpleautomation.test;

import com.simpleautomation.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Tests de la ferme (partie 4). Enregistrés depuis {@link SATests}. */
public final class FarmTests {
    private static final BlockPos CHEST = new BlockPos(8, 2, 3);
    private static final BlockPos MELON_STEM = new BlockPos(2, 2, 2);
    private static final BlockPos PUMPKIN_STEM = new BlockPos(4, 2, 2);
    private static final BlockPos ATTACHED = new BlockPos(2, 2, 4);
    private static final BlockPos MELON = new BlockPos(3, 2, 4);

    /**
     * Une tige de melon et une de citrouille à l'âge 7, une tige de melon attachée à son melon (sans tick aléatoire) :
     * le melon est récolté, aucune tige n'est jamais cassée.
     */
    public static void stemsStayIntact(GameTestHelper h) {
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 6; z++) h.setBlock(x, 1, z, x >= 1 && x <= 5 && z >= 1 && z <= 5 ? Blocks.FARMLAND : Blocks.STONE);
        h.setBlock(MELON_STEM, Blocks.MELON_STEM.defaultBlockState().setValue(StemBlock.AGE, StemBlock.MAX_AGE));
        h.setBlock(PUMPKIN_STEM, Blocks.PUMPKIN_STEM.defaultBlockState().setValue(StemBlock.AGE, StemBlock.MAX_AGE));
        h.setBlock(MELON, Blocks.MELON);
        h.setBlock(ATTACHED, Blocks.ATTACHED_MELON_STEM.defaultBlockState().setValue(AttachedStemBlock.FACING, Direction.EAST));
        h.setBlock(0, 2, 0, ModBlocks.BORDER_MARKER.get());
        h.setBlock(6, 2, 6, ModBlocks.BORDER_MARKER.get());
        h.setBlock(7, 2, 3, ModBlocks.FARM_CONTROLLER.get());
        h.setBlock(CHEST, Blocks.CHEST);
        h.spawn(EntityType.COPPER_GOLEM, new BlockPos(0, 2, 3));

        h.onEachTick(() -> {
            assertStem(h, MELON_STEM);
            assertStem(h, PUMPKIN_STEM);
            assertStem(h, ATTACHED);
        });
        h.runAfterDelay(300, () -> {
            h.assertBlockPresent(Blocks.AIR, MELON);
            h.assertTrue(TestKit.count(h, CHEST, Items.MELON_SLICE) >= 3, "the melon was not harvested");
            h.assertTrue(TestKit.count(h, CHEST, Items.MELON_SEEDS) == 0 && TestKit.count(h, CHEST, Items.PUMPKIN_SEEDS) == 0,
                    "a stem was harvested");
            h.succeed();
        });
    }

    private static void assertStem(GameTestHelper h, BlockPos pos) {
        BlockState state = h.getBlockState(pos);
        h.assertTrue(state.getBlock() instanceof StemBlock || state.getBlock() instanceof AttachedStemBlock,
                "the stem at " + pos.toShortString() + " was broken: " + state);
    }

    private FarmTests() {
    }
}
