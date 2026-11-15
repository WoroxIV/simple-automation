package com.simpleautomation.test;

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
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Tests de l'apiculteur (contrôleur Farm, partie 7.3). Enregistrés depuis {@link SATests}. */
public final class BeekeepingTests {
    private static final BlockPos HIVE = new BlockPos(3, 2, 4);
    /** Haut au-dessus des bornes (cacao : jusqu'à 12 blocs). */
    private static final BlockPos HIGH_NEST = new BlockPos(2, 9, 2);

    private static BlockState full(net.minecraft.world.level.block.Block hive) {
        return hive.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, BeehiveBlock.MAX_HONEY_LEVELS);
    }

    private static void golem(GameTestHelper h, Item tool) {
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(3, 2, 3));
        golem.setNoAi(true);
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
    }

    /**
     * Une ruche pleine et un nid plein bien au-dessus des bornes, un golem aux cisailles : 3 rayons de miel par ruche (la
     * table vanilla en donne toujours 3), niveaux de miel remis à 0, les deux blocs restent, 2 points d'usure, aucune
     * fiole de miel. Un golem à la houe ne les touche pas (voir l'autre test).
     */
    public static void shearsGiveHoneycombs(GameTestHelper h) {
        ZoneControllerBlockEntity be = TestKit.floorAndZone(h, ModBlocks.FARM_CONTROLLER.get());
        h.setBlock(HIVE, full(Blocks.BEEHIVE));
        h.setBlock(HIGH_NEST, full(Blocks.BEE_NEST));
        golem(h, Items.SHEARS);
        h.succeedWhen(() -> {
            h.assertTrue(be.workers().size() == 1, "golem not recruited");
            h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.HONEYCOMB) == 6, "honeycombs: " + TestKit.count(h, TestKit.CHEST, Items.HONEYCOMB));
            h.assertTrue(h.getBlockState(HIVE).getValue(BeehiveBlock.HONEY_LEVEL) == 0, "the hive is not reset");
            h.assertTrue(h.getBlockState(HIGH_NEST).getValue(BeehiveBlock.HONEY_LEVEL) == 0, "the high nest is not reset");
            h.assertTrue(h.getBlockState(HIVE).is(Blocks.BEEHIVE) && h.getBlockState(HIGH_NEST).is(Blocks.BEE_NEST), "a hive was broken");
            Worker w = be.workers().getFirst();
            h.assertTrue(w.tool.getDamageValue() == 2, "shears wear: " + w.tool.getDamageValue());
            h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.HONEY_BOTTLE) == 0, "honey bottles from shears");
        });
    }

    /**
     * Un golem tenant une fiole vide, trois fioles vides au coffre : une seule ruche pleine donne 1 fiole de miel, il reste
     * 2 fioles vides, aucun rayon, le niveau retombe à 0.
     */
    public static void bottleGivesHoneyBottle(GameTestHelper h) {
        ZoneControllerBlockEntity be = TestKit.floorAndZone(h, ModBlocks.FARM_CONTROLLER.get());
        TestKit.chest(h, TestKit.CHEST).setItem(0, new ItemStack(Items.GLASS_BOTTLE, 3));
        h.setBlock(HIVE, full(Blocks.BEEHIVE));
        golem(h, Items.GLASS_BOTTLE);
        h.succeedWhen(() -> {
            h.assertTrue(be.workers().size() == 1, "golem not recruited");
            h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.HONEY_BOTTLE) == 1, "honey bottles: " + TestKit.count(h, TestKit.CHEST, Items.HONEY_BOTTLE));
            h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.GLASS_BOTTLE) == 2, "empty bottles left: " + TestKit.count(h, TestKit.CHEST, Items.GLASS_BOTTLE));
            h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.HONEYCOMB) == 0, "honeycombs from a bottle");
            h.assertTrue(h.getBlockState(HIVE).getValue(BeehiveBlock.HONEY_LEVEL) == 0, "the hive is not reset");
        });
    }

    /** Un golem à la houe laisse la ruche pleine ; la ferme accepte cisailles et fiole, refuse un bâton. */
    public static void hoeIgnoresHives(GameTestHelper h) {
        ZoneControllerBlockEntity be = TestKit.floorAndZone(h, ModBlocks.FARM_CONTROLLER.get());
        h.setBlock(HIVE, full(Blocks.BEEHIVE));
        golem(h, Items.IRON_HOE);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(be.workers().size() == 1, "golem not recruited"))
                .thenIdle(120)
                .thenExecute(() -> {
                    h.assertTrue(h.getBlockState(HIVE).getValue(BeehiveBlock.HONEY_LEVEL) == BeehiveBlock.MAX_HONEY_LEVELS, "a hoe golem emptied the hive");
                    h.assertTrue(be.job().acceptsTool(be, new ItemStack(Items.SHEARS)), "shears refused");
                    h.assertTrue(be.job().acceptsTool(be, new ItemStack(Items.GLASS_BOTTLE)), "a bottle is refused");
                    h.assertFalse(be.job().acceptsTool(be, new ItemStack(Items.STICK)), "a stick is accepted");
                })
                .thenSucceed();
    }

    private BeekeepingTests() {
    }
}
