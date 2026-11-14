package com.simpleautomation.test;

import com.simpleautomation.registry.ModBlocks;
import com.simpleautomation.registry.ModTags;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Tests du broyeur. Enregistrés depuis {@link SATests}. */
public final class CrusherTests {
    private static final BlockPos CHEST = new BlockPos(7, 2, 3);

    /**
     * 3 minerais de fer au coffre, un golem à la pioche en fer : un minerai doit apparaître sur un poste de la zone,
     * puis disparaître, et les 3 fers bruts arriver au coffre, sans rien laisser au sol ni dans la zone.
     */
    public static void crusherPlacesAndBreaks(GameTestHelper h) {
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 8; z++) h.setBlock(x, 1, z, Blocks.STONE);
        h.setBlock(1, 2, 1, ModBlocks.BORDER_MARKER.get());
        h.setBlock(5, 2, 5, ModBlocks.BORDER_MARKER.get());
        h.setBlock(6, 2, 3, ModBlocks.CRUSHER_CONTROLLER.get());
        h.setBlock(CHEST, Blocks.CHEST);
        h.getBlockEntity(CHEST, ChestBlockEntity.class).setItem(0, new ItemStack(Items.IRON_ORE, 3));
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(2, 2, 2));
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        AtomicBoolean seen = new AtomicBoolean();

        h.succeedWhen(() -> {
            int ores = oresInZone(h);
            if (ores > 0) seen.set(true);
            h.assertTrue(seen.get(), "no ore placed on a post yet");
            h.assertTrue(ores == 0, "an ore is still on a post");
            h.assertTrue(count(h, Items.IRON_ORE) == 0, "ores left in the chest");
            int raw = count(h, Items.RAW_IRON);
            h.assertTrue(raw >= 3, "expected 3 raw iron in the chest, got " + raw);
            h.assertTrue(h.getEntities(EntityType.ITEM).isEmpty(), "items were dropped");
        });
    }

    /**
     * 2 minerais de fer au coffre. Dès qu'un minerai est posé, on reprend la pioche du golem et on recharge le contrôleur
     * (comme un redémarrage du serveur) : le minerai reste posé. On rend une pioche : ce minerai-là est cassé en premier,
     * avant d'en prendre un autre au coffre.
     */
    public static void resumesPlacedOre(GameTestHelper h) {
        TestKit.floorAndZone(h, ModBlocks.CRUSHER_CONTROLLER.get());
        TestKit.chest(h, CHEST).setItem(0, new ItemStack(Items.IRON_ORE, 2));
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(2, 2, 2));
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        BlockPos[] post = new BlockPos[1];

        h.startSequence()
                .thenWaitUntil(() -> {
                    post[0] = orePost(h);
                    h.assertTrue(post[0] != null, "no ore placed yet");
                })
                .thenExecute(() -> {
                    ZoneControllerBlockEntity be = TestKit.zone(h, TestKit.CONTROLLER);
                    be.workers().getFirst().tool = ItemStack.EMPTY;
                    // Redémarrage : sauvegarde puis rechargement du contrôleur (les tâches en cours sont perdues).
                    var registries = h.getLevel().registryAccess();
                    var tag = be.saveCustomOnly(registries);
                    be.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    h.assertBlockPresent(Blocks.IRON_ORE, post[0]);
                    h.assertTrue(count(h, Items.IRON_ORE) == 1, "an ore was taken without a pickaxe");
                    ZoneControllerBlockEntity be = TestKit.zone(h, TestKit.CONTROLLER);
                    h.assertTrue(be.workers().size() == 1, "worker lost on reload");
                    be.workers().getFirst().tool = new ItemStack(Items.IRON_PICKAXE);
                })
                .thenWaitUntil(() -> h.assertBlockNotPresent(Blocks.IRON_ORE, post[0]))
                .thenExecute(() -> {
                    h.assertTrue(count(h, Items.IRON_ORE) == 1, "a new ore was taken before the placed one was crushed");
                    h.assertTrue(count(h, Items.RAW_IRON) >= 1, "the placed ore gave nothing");
                })
                .thenWaitUntil(() -> {
                    h.assertTrue(count(h, Items.IRON_ORE) == 0 && oresInZone(h) == 0, "the second ore was not crushed");
                    h.assertTrue(count(h, Items.RAW_IRON) >= 2, "expected 2 raw iron");
                })
                .thenSucceed();
    }

    /** Débris antiques : le premier redonne un débris, il retourne au coffre et le broyeur n'en prend plus. */
    public static void ancientDebrisUncrushable(GameTestHelper h) {
        TestKit.floorAndZone(h, ModBlocks.CRUSHER_CONTROLLER.get());
        TestKit.chest(h, CHEST).setItem(0, new ItemStack(Items.ANCIENT_DEBRIS, 2));
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(2, 2, 2));
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));

        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(TestKit.hasLine(TestKit.describe(TestKit.zone(h, TestKit.CONTROLLER)),
                        "message.simple_automation.crusher.uncrushable"), "ancient debris not marked uncrushable"))
                .thenIdle(100)
                .thenExecute(() -> {
                    h.assertTrue(count(h, Items.ANCIENT_DEBRIS) == 2, "ancient debris lost: " + count(h, Items.ANCIENT_DEBRIS) + " left");
                    h.assertTrue(TestKit.countBlocks(h, Blocks.ANCIENT_DEBRIS, new BlockPos(1, 2, 1), new BlockPos(5, 2, 5)) == 0,
                            "ancient debris still placed in the zone");
                    h.assertTrue(h.getEntities(EntityType.ITEM).isEmpty(), "items were dropped");
                })
                .thenSucceed();
    }

    /**
     * Liste noire (remplie le temps du test) avec le minerai de lapis : le charbon est broyé, le lapis et le bloc de fer brut
     * restent au coffre.
     */
    public static void blacklistTag(GameTestHelper h) {
        var old = TestKit.setTag(h, BuiltInRegistries.ITEM, ModTags.CRUSHER_BLACKLIST, List.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.LAPIS_ORE)));
        TestKit.floorAndZone(h, ModBlocks.CRUSHER_CONTROLLER.get());
        ChestBlockEntity chest = TestKit.chest(h, CHEST);
        chest.setItem(0, new ItemStack(Items.LAPIS_ORE, 2));
        chest.setItem(1, new ItemStack(Items.COAL_ORE, 2));
        chest.setItem(2, new ItemStack(Items.RAW_IRON_BLOCK, 1));
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(2, 2, 2));
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));

        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(count(h, Items.COAL_ORE) == 0, "coal ore not crushed");
                    h.assertTrue(count(h, Items.COAL) >= 2, "expected 2 coal");
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    TestKit.setTag(BuiltInRegistries.ITEM, ModTags.CRUSHER_BLACKLIST, old);
                    h.assertTrue(count(h, Items.LAPIS_ORE) == 2, "a blacklisted ore was taken");
                    h.assertTrue(count(h, Items.RAW_IRON_BLOCK) == 1, "a raw iron block was taken");
                    h.assertTrue(TestKit.countBlocks(h, Blocks.LAPIS_ORE, new BlockPos(1, 2, 1), new BlockPos(5, 2, 5)) == 0, "lapis placed in the zone");
                })
                .thenSucceed();
    }

    /** Le poste où un minerai de fer est posé, ou null. */
    private static BlockPos orePost(GameTestHelper h) {
        for (int x = 1; x <= 5; x++)
            for (int z = 1; z <= 5; z++)
                if (h.getBlockState(new BlockPos(x, 2, z)).is(Blocks.IRON_ORE)) return new BlockPos(x, 2, z);
        return null;
    }

    /** Minerais de fer posés dans la zone (au niveau des bornes). */
    private static int oresInZone(GameTestHelper h) {
        int n = 0;
        for (int x = 1; x <= 5; x++)
            for (int z = 1; z <= 5; z++)
                if (h.getBlockState(new BlockPos(x, 2, z)).is(Blocks.IRON_ORE)) n++;
        return n;
    }

    private static int count(GameTestHelper h, Item item) {
        ChestBlockEntity chest = h.getBlockEntity(CHEST, ChestBlockEntity.class);
        int n = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack s = chest.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private CrusherTests() {
    }
}
