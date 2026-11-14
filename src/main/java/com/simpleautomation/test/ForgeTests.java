package com.simpleautomation.test;

import com.simpleautomation.registry.ModBlocks;
import com.simpleautomation.zone.Worker;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Tests de la forge : répartition du minerai et du carburant entre les fours. Enregistrés depuis {@link SATests}. */
public final class ForgeTests {
    private static final BlockPos CHEST = new BlockPos(7, 2, 3);
    private static final BlockPos[] FURNACES = {new BlockPos(2, 2, 2), new BlockPos(4, 2, 2), new BlockPos(3, 2, 4)};

    /**
     * Trois fours, 24 fers bruts et 6 charbons au coffre, un golem à mains nues : chaque four doit recevoir sa part
     * (8 fers au plus) et un seul charbon, au lieu que tout parte dans le premier four.
     */
    public static void forgeSplitsBetweenFurnaces(GameTestHelper h) {
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 8; z++) h.setBlock(x, 1, z, Blocks.STONE);
        h.setBlock(1, 2, 1, ModBlocks.BORDER_MARKER.get());
        h.setBlock(5, 2, 5, ModBlocks.BORDER_MARKER.get());
        h.setBlock(6, 2, 3, ModBlocks.FORGE_CONTROLLER.get());
        h.setBlock(CHEST, Blocks.CHEST);
        for (BlockPos f : FURNACES) h.setBlock(f, Blocks.FURNACE);
        ChestBlockEntity chest = h.getBlockEntity(CHEST, ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.RAW_IRON, 24));
        chest.setItem(1, new ItemStack(Items.COAL, 6));
        h.spawn(EntityType.COPPER_GOLEM, new BlockPos(2, 2, 4));

        h.succeedWhen(() -> {
            StringBuilder state = new StringBuilder();
            boolean all = true;
            for (BlockPos p : FURNACES) {
                AbstractFurnaceBlockEntity f = h.getBlockEntity(p, AbstractFurnaceBlockEntity.class);
                ItemStack in = f.getItem(0);
                state.append(p.toShortString()).append('=').append(in.getCount()).append(' ').append(in.getItem())
                        .append('/').append(f.getItem(1).getCount()).append('/').append(f.getItem(2).getCount()).append("; ");
                h.assertTrue(in.getCount() <= 8, "a furnace got more than its share: " + state);
                all &= in.is(Items.RAW_IRON) || f.getItem(2).is(Items.IRON_INGOT);
            }
            h.assertTrue(all, "not every furnace received iron: " + state);
            int coal = count(h, CHEST, Items.COAL);
            h.assertTrue(coal >= 3, "too much fuel taken, " + coal + " coal left: " + state);
        });
    }

    /**
     * Deux hauts fourneaux et deux fumoirs, 16 fers bruts, 16 bœufs crus et 10 charbons : chaque haut fourneau reçoit 8 fers,
     * chaque fumoir 8 bœufs (jamais l'inverse), et chacun un seul charbon (8 items × 100 ticks = un charbon brûlé deux fois
     * plus vite). Tout finit cuit au coffre et il reste exactement 6 charbons.
     */
    public static void blastAndSmokerShares(GameTestHelper h) {
        TestKit.floorAndZone(h, ModBlocks.FORGE_CONTROLLER.get());
        BlockPos[] blast = {new BlockPos(2, 2, 2), new BlockPos(4, 2, 2)};
        BlockPos[] smokers = {new BlockPos(2, 2, 4), new BlockPos(4, 2, 4)};
        for (BlockPos p : blast) h.setBlock(p, Blocks.BLAST_FURNACE);
        for (BlockPos p : smokers) h.setBlock(p, Blocks.SMOKER);
        ChestBlockEntity chest = TestKit.chest(h, CHEST);
        chest.setItem(0, new ItemStack(Items.RAW_IRON, 16));
        chest.setItem(1, new ItemStack(Items.BEEF, 16));
        chest.setItem(2, new ItemStack(Items.COAL, 10));
        // Deux golems à mains nues : les quatre fours sont servis vite, avant que le premier ait trop avancé.
        h.spawn(EntityType.COPPER_GOLEM, new BlockPos(3, 2, 3));
        h.spawn(EntityType.COPPER_GOLEM, new BlockPos(3, 2, 1));

        h.onEachTick(() -> {
            for (BlockPos p : blast) {
                AbstractFurnaceBlockEntity f = h.getBlockEntity(p, AbstractFurnaceBlockEntity.class);
                h.assertFalse(f.getItem(0).is(Items.BEEF), "beef went into a blast furnace");
                h.assertTrue(f.getItem(0).getCount() <= 8, "a blast furnace got more than its share: " + f.getItem(0));
                h.assertTrue(f.getItem(1).getCount() <= 1, "a blast furnace got more than one coal");
            }
            for (BlockPos p : smokers) {
                AbstractFurnaceBlockEntity f = h.getBlockEntity(p, AbstractFurnaceBlockEntity.class);
                h.assertFalse(f.getItem(0).is(Items.RAW_IRON), "iron went into a smoker");
                h.assertTrue(f.getItem(0).getCount() <= 8, "a smoker got more than its share: " + f.getItem(0));
                h.assertTrue(f.getItem(1).getCount() <= 1, "a smoker got more than one coal");
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(TestKit.count(h, CHEST, Items.IRON_INGOT) == 16, "expected 16 iron ingots, got " + TestKit.count(h, CHEST, Items.IRON_INGOT));
            h.assertTrue(TestKit.count(h, CHEST, Items.COOKED_BEEF) == 16, "expected 16 steaks, got " + TestKit.count(h, CHEST, Items.COOKED_BEEF));
            int coal = TestKit.count(h, CHEST, Items.COAL);
            h.assertTrue(coal == 6, "expected exactly 4 coal burnt, " + coal + " left");
        });
    }

    /**
     * Un four éteint avec du minerai mais sans carburant (et rien au coffre pour le relancer), un four allumé qui cuit.
     * Le golem à la pelle attise le four allumé et ne s'occupe jamais du four éteint.
     */
    public static void shovelSkipsUnlit(GameTestHelper h) {
        TestKit.floorAndZone(h, ModBlocks.FORGE_CONTROLLER.get());
        BlockPos unlit = new BlockPos(2, 2, 2);
        BlockPos lit = new BlockPos(4, 2, 4);
        h.setBlock(unlit, Blocks.FURNACE);
        h.setBlock(lit, Blocks.FURNACE);
        h.getBlockEntity(unlit, AbstractFurnaceBlockEntity.class).setItem(0, new ItemStack(Items.RAW_IRON, 8));
        AbstractFurnaceBlockEntity cooking = h.getBlockEntity(lit, AbstractFurnaceBlockEntity.class);
        cooking.setItem(0, new ItemStack(Items.RAW_GOLD, 64));
        cooking.setItem(1, new ItemStack(Items.COAL, 8));
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(3, 2, 3));
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_SHOVEL));
        BlockPos unlitAbs = h.absolutePos(unlit);
        boolean[] stoked = new boolean[1];

        h.onEachTick(() -> {
            for (Worker w : TestKit.zone(h, TestKit.CONTROLLER).workers()) {
                h.assertFalse(w.task != null && w.task.target().equals(unlitAbs), "the shovel golem went to the unlit furnace");
                if (w.status == Worker.Status.WORKING && w.task != null && w.workTotal >= 100) stoked[0] = true;
            }
        });
        h.runAfterDelay(300, () -> {
            h.assertTrue(stoked[0], "the lit furnace was never stoked");
            h.assertTrue(h.getBlockEntity(unlit, AbstractFurnaceBlockEntity.class).getItem(0).getCount() == 8, "the unlit furnace changed");
            h.succeed();
        });
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

    private ForgeTests() {
    }
}
