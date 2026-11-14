package com.simpleautomation.test;

import static com.simpleautomation.test.TestKit.zone;

import com.simpleautomation.registry.ModBlocks;
import com.simpleautomation.zone.Claims;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.phys.AABB;

/**
 * Tests des protections (claims) : un faux mod de protection ({@link TestClaims}) refuse aux golems de casser, poser ou
 * toucher un animal dans l'arène du test ; les golems ne doivent rien faire, ni rien perdre. Enregistrés depuis {@link SATests}.
 */
public final class ClaimsTests {

    /** Interaction refusée : le mouton garde sa laine ; protection levée, il reste de côté un moment (pas de boucle dessus). */
    public static void shearDenied(GameTestHelper h) {
        AABB box = deny(h, TestClaims.DENY_INTERACT);
        TestKit.floorAndZone(h, ModBlocks.SHEPHERD_CONTROLLER.get());
        Sheep sheep = h.spawn(EntityType.SHEEP, new BlockPos(3, 2, 4));
        sheep.setNoAi(true);
        golem(h, new BlockPos(2, 2, 2), Items.SHEARS);

        h.startSequence()
                .thenIdle(150)
                .thenExecute(() -> {
                    h.assertFalse(sheep.isSheared(), "a protected sheep was sheared");
                    h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.WHITE_WOOL) == 0, "wool reached the chest");
                    h.assertTrue(zone(h, TestKit.CONTROLLER).workers().size() == 1, "golem not recruited");
                    h.assertTrue(zone(h, TestKit.CONTROLLER).workers().getFirst().tool.getDamageValue() == 0, "the shears wore on a refused sheep");
                    TestClaims.DENY_INTERACT.remove(box);
                })
                .thenIdle(100)
                .thenExecute(() -> h.assertFalse(sheep.isSheared(), "the refused sheep was retried right away"))
                .thenSucceed();
    }

    /** Interaction refusée : les vaches ne tombent pas amoureuses et le blé reste au coffre, même une fois la protection levée. */
    public static void ranchDenied(GameTestHelper h) {
        AABB box = deny(h, TestClaims.DENY_INTERACT);
        AnimalTests.fencedRanch(h);
        BlockPos chest = new BlockPos(8, 2, 3);
        TestKit.chest(h, chest).setItem(0, new ItemStack(Items.WHEAT, 8));
        Cow a = h.spawn(EntityType.COW, new BlockPos(2, 2, 3));
        Cow b = h.spawn(EntityType.COW, new BlockPos(4, 2, 3));
        golem(h, new BlockPos(3, 2, 2), Items.WHEAT);

        h.startSequence()
                .thenIdle(200)
                .thenExecute(() -> {
                    h.assertFalse(a.isInLove() || b.isInLove(), "a protected cow fell in love");
                    h.assertTrue(h.getEntities(EntityType.COW).size() == 2, "a calf was born in a protected zone");
                    h.assertTrue(TestKit.count(h, chest, Items.WHEAT) == 8, "food was taken for refused animals");
                    TestClaims.DENY_INTERACT.remove(box);
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    h.assertTrue(h.getEntities(EntityType.COW).size() == 2 && !a.isInLove() && !b.isInLove(), "the refused cows were retried right away");
                    h.assertTrue(TestKit.count(h, chest, Items.WHEAT) == 8, "food was taken for refused animals");
                })
                .thenSucceed();
    }

    /** Casse refusée : la pierre reste, rien au coffre, la pioche ne s'use pas ; protection levée, la couche est creusée. */
    public static void quarryBreakDenied(GameTestHelper h) {
        AABB box = deny(h, TestClaims.DENY_BREAK);
        TestKit.floorAndZone(h, ModBlocks.QUARRY_CONTROLLER.get());
        golem(h, new BlockPos(3, 2, 3), Items.IRON_PICKAXE);

        h.startSequence()
                .thenIdle(200)
                .thenExecute(() -> {
                    h.assertTrue(stoneInLayer(h) == 25, "a protected block was broken: " + stoneInLayer(h) + " stone left");
                    h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.COBBLESTONE) == 0, "cobblestone reached the chest");
                    h.assertTrue(zone(h, TestKit.CONTROLLER).workers().getFirst().tool.getDamageValue() == 0, "the pickaxe wore on refused blocks");
                    TestClaims.DENY_BREAK.remove(box);
                })
                .thenWaitUntil(() -> h.assertTrue(stoneInLayer(h) == 0, "the layer was not dug once allowed: " + stoneInLayer(h) + " stone left"))
                .thenSucceed();
    }

    /**
     * Pose refusée au broyeur : aucun minerai posé, ils restent au coffre et le contrôleur le signale ; la protection a bien
     * été consultée avec le bon bloc. Protection levée, les minerais sont broyés.
     */
    public static void crusherPlaceDenied(GameTestHelper h) {
        AABB box = deny(h, TestClaims.DENY_PLACE);
        TestKit.floorAndZone(h, ModBlocks.CRUSHER_CONTROLLER.get());
        TestKit.chest(h, TestKit.CHEST).setItem(0, new ItemStack(Items.IRON_ORE, 2));
        golem(h, new BlockPos(3, 2, 3), Items.IRON_PICKAXE);

        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(TestClaims.PLACED.containsKey(box), "the protection was never asked"))
                .thenIdle(20)
                .thenExecute(() -> {
                    h.assertTrue(TestClaims.PLACED.get(box).is(Blocks.IRON_ORE), "the protection was asked about " + TestClaims.PLACED.get(box));
                    h.assertTrue(TestKit.countBlocks(h, Blocks.IRON_ORE, new BlockPos(1, 2, 1), new BlockPos(5, 2, 5)) == 0, "an ore was placed in a protected zone");
                    h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.IRON_ORE) == 2, "an ore left the chest");
                    h.assertTrue(TestKit.hasLine(TestKit.describe(zone(h, TestKit.CONTROLLER)), "message.simple_automation.crusher.denied"), "no 'denied' line");
                    TestClaims.DENY_PLACE.remove(box);
                    TestClaims.PLACED.remove(box);
                })
                .thenWaitUntil(() -> {
                    h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.IRON_ORE) == 0, "ores not crushed once allowed");
                    h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.RAW_IRON) >= 2, "expected 2 raw iron");
                })
                .thenSucceed();
    }

    /**
     * Pose refusée à la ferme : le blé mûr est récolté mais pas replanté (graines au coffre), la terre vide n'est pas semée
     * et aucune graine ne quitte le coffre. Protection levée : tout est semé (les cases refusées sont réessayées après
     * {@code FarmJob.REFUSED_TICKS}).
     */
    public static void farmSowDenied(GameTestHelper h) {
        AABB box = deny(h, TestClaims.DENY_PLACE);
        var farmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE);
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 8; z++) h.setBlock(x, 1, z, x >= 2 && x <= 4 && z >= 2 && z <= 4 ? farmland : Blocks.STONE.defaultBlockState());
        h.setBlock(1, 2, 1, ModBlocks.BORDER_MARKER.get());
        h.setBlock(5, 2, 5, ModBlocks.BORDER_MARKER.get());
        h.setBlock(TestKit.CONTROLLER, ModBlocks.FARM_CONTROLLER.get());
        h.setBlock(TestKit.CHEST, Blocks.CHEST);
        TestKit.chest(h, TestKit.CHEST).setItem(0, new ItemStack(Items.WHEAT_SEEDS, 16));
        BlockPos ripe = new BlockPos(3, 2, 3);
        h.setBlock(ripe, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        golem(h, new BlockPos(1, 2, 3), null);

        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.WHEAT) >= 1, "the ripe wheat was not harvested"))
                .thenIdle(100)
                .thenExecute(() -> {
                    h.assertTrue(TestKit.countBlocks(h, Blocks.WHEAT, new BlockPos(2, 2, 2), new BlockPos(4, 2, 4)) == 0, "a crop was planted in a protected zone");
                    h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.WHEAT_SEEDS) >= 16, "seeds were lost: " + TestKit.count(h, TestKit.CHEST, Items.WHEAT_SEEDS));
                    TestClaims.DENY_PLACE.remove(box);
                    TestClaims.PLACED.remove(box);
                })
                .thenWaitUntil(() -> h.assertTrue(TestKit.countBlocks(h, Blocks.WHEAT, new BlockPos(2, 2, 2), new BlockPos(4, 2, 4)) == 9, "not sown once allowed: "
                        + TestKit.countBlocks(h, Blocks.WHEAT, new BlockPos(2, 2, 2), new BlockPos(4, 2, 4)) + " of 9"))
                .thenSucceed();
    }

    /**
     * Les règles de {@link Claims} elles-mêmes : jamais sur une borne ni à côté du contrôleur ; permis sans protection ;
     * refusé par une protection qui voit le bloc demandé ; interaction permise puis refusée.
     */
    public static void rules(GameTestHelper h) {
        TestKit.floorAndZone(h, ModBlocks.RANCH_CONTROLLER.get());
        Cow cow = h.spawn(EntityType.COW, new BlockPos(3, 2, 3));
        cow.setNoAi(true);

        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(zone(h, TestKit.CONTROLLER).bounds() != null, "zone not found"))
                .thenExecute(() -> {
                    ZoneControllerBlockEntity be = zone(h, TestKit.CONTROLLER);
                    var cobble = Blocks.COBBLESTONE.defaultBlockState();
                    h.assertFalse(Claims.canPlace(be, h.absolutePos(new BlockPos(1, 2, 1)), cobble), "placing on a marker was allowed");
                    h.assertFalse(Claims.canPlace(be, h.absolutePos(new BlockPos(6, 2, 4)), cobble), "placing next to the controller was allowed");
                    BlockPos free = h.absolutePos(new BlockPos(3, 3, 2));
                    h.assertTrue(Claims.canPlace(be, free, cobble), "placing on a free spot was refused");
                    h.assertTrue(Claims.canInteract(be, cow), "interacting with a cow was refused");
                    h.assertTrue(h.getBlockState(new BlockPos(3, 3, 2)).isAir(), "canPlace changed the world");

                    AABB box = deny(h, TestClaims.DENY_PLACE);
                    AABB box2 = deny(h, TestClaims.DENY_INTERACT);
                    try {
                        h.assertFalse(Claims.canPlace(be, free, Blocks.OAK_SAPLING.defaultBlockState()), "a protection could not refuse a placement");
                        h.assertTrue(TestClaims.PLACED.get(box) != null && TestClaims.PLACED.get(box).is(Blocks.OAK_SAPLING),
                                "the protection saw " + TestClaims.PLACED.get(box) + " instead of the sapling");
                        h.assertFalse(Claims.canInteract(be, cow), "a protection could not refuse an interaction");
                    } finally {
                        TestClaims.DENY_PLACE.remove(box);
                        TestClaims.DENY_INTERACT.remove(box2);
                        TestClaims.PLACED.remove(box);
                    }
                    h.assertTrue(be.fakePlayer().getMainHandItem().isEmpty(), "the fake player kept an item in hand");
                })
                .thenSucceed();
    }

    /** Protège l'arène du test, pour ce genre d'action ; la protection est levée à la fin du test, même raté. */
    private static AABB deny(GameTestHelper h, List<AABB> list) {
        AABB box = h.getBounds();
        list.add(box);
        TestKit.onFinish(h, () -> {
            list.remove(box);
            TestClaims.PLACED.remove(box);
        });
        return box;
    }

    private static int stoneInLayer(GameTestHelper h) {
        return TestKit.countBlocks(h, Blocks.STONE, new BlockPos(1, 1, 1), new BlockPos(5, 1, 5));
    }

    private static CopperGolem golem(GameTestHelper h, BlockPos pos, @org.jspecify.annotations.Nullable Item tool) {
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, pos);
        golem.setNoAi(true);
        if (tool != null) golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
        return golem;
    }

    private ClaimsTests() {
    }
}
