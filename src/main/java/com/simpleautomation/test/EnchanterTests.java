package com.simpleautomation.test;

import com.simpleautomation.registry.ModBlocks;
import com.simpleautomation.registry.ModItems;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Tests de l'enchanteur. Enregistrés depuis {@link SATests}. */
public final class EnchanterTests {
    private static final BlockPos TABLE = new BlockPos(3, 2, 3);

    /**
     * Une table au milieu de la zone avec 15 bibliothèques (puissance 15 : la 3e option coûte toujours 30 niveaux, soit
     * 97 + 102 + 107 = 306 XP). Un livre, 3 lapis et 31 cristaux (310 XP) au coffre : on obtient un livre enchanté, plus
     * de livre, de lapis ni de cristaux, et les 4 XP de monnaie restent au tampon (sous un cristal : ils n'en sortent pas).
     */
    public static void enchantsBookAndKeepsChange(GameTestHelper h) {
        ZoneControllerBlockEntity be = TestKit.floorAndZone(h, ModBlocks.ENCHANTER_CONTROLLER.get());
        tableWithShelves(h);
        stock(h, new ItemStack(Items.BOOK), 3, 31);
        golem(h, new BlockPos(2, 2, 2));
        h.succeedWhen(() -> {
            h.assertTrue(be.workers().size() == 1, "golem not recruited");
            h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.ENCHANTED_BOOK) == 1, "no enchanted book in the chest");
            h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.BOOK) == 0, "the plain book is still there");
            h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.LAPIS_LAZULI) == 0, "lapis left: " + TestKit.count(h, TestKit.CHEST, Items.LAPIS_LAZULI));
            h.assertTrue(TestKit.count(h, TestKit.CHEST, ModItems.XP_CRYSTAL.get()) == 0, "crystals left");
            h.assertTrue(Math.abs(be.xp().stored() - 4.0) < 1.0E-6, "expected 4 xp of change, got " + be.xp().stored());
            h.assertTrue(TestKit.hasLine(TestKit.describe(be), "message.simple_automation.enchanter.tip"), "no warning line");
        });
    }

    /**
     * Seul un objet enchantable non enchanté est pris : une pioche en fer est enchantée, l'épée déjà enchantée et le
     * bâton (non enchantable) restent tels quels. Le golem ne dépense que le prix d'un seul enchantement : le second lot
     * de lapis et de cristaux reste intact.
     */
    public static void enchantsOnlyPlainItems(GameTestHelper h) {
        ZoneControllerBlockEntity be = TestKit.floorAndZone(h, ModBlocks.ENCHANTER_CONTROLLER.get());
        tableWithShelves(h);
        ChestBlockEntity chest = TestKit.chest(h, TestKit.CHEST);
        chest.setItem(0, TestKit.enchanted(h, Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 1));
        chest.setItem(1, new ItemStack(Items.STICK));
        chest.setItem(2, new ItemStack(Items.IRON_PICKAXE));
        chest.setItem(3, new ItemStack(Items.LAPIS_LAZULI, 6));
        chest.setItem(4, new ItemStack(ModItems.XP_CRYSTAL.get(), 62));
        golem(h, new BlockPos(2, 2, 2));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.LAPIS_LAZULI) == 3, "the pickaxe was not enchanted yet"))
                .thenIdle(200)
                .thenExecute(() -> {
                    int pickaxes = 0;
                    for (int i = 0; i < chest.getContainerSize(); i++) {
                        ItemStack s = chest.getItem(i);
                        if (s.is(Items.IRON_PICKAXE)) {
                            pickaxes++;
                            h.assertTrue(s.isEnchanted(), "the pickaxe is not enchanted");
                        }
                        if (s.is(Items.DIAMOND_SWORD)) h.assertTrue(s.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS).size() == 1, "the sword was enchanted again");
                        if (s.is(Items.STICK)) h.assertTrue(!s.isEnchanted(), "the stick is enchanted");
                    }
                    h.assertTrue(pickaxes == 1, "expected 1 pickaxe, got " + pickaxes);
                    h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.LAPIS_LAZULI) == 3, "more than one enchanting was paid");
                    int crystals = TestKit.count(h, TestKit.CHEST, ModItems.XP_CRYSTAL.get());
                    h.assertTrue(crystals == 62 - 31, "expected 31 crystals left, got " + crystals);
                    h.assertTrue(be.workers().size() == 1, "worker lost");
                })
                .thenSucceed();
    }

    /**
     * Trop peu d'XP (30 cristaux = 300 XP pour 306) : rien n'est enchanté ni dépensé, le livre, les lapis et les cristaux
     * restent au coffre.
     */
    public static void notEnoughXpSpendsNothing(GameTestHelper h) {
        ZoneControllerBlockEntity be = TestKit.floorAndZone(h, ModBlocks.ENCHANTER_CONTROLLER.get());
        tableWithShelves(h);
        stock(h, new ItemStack(Items.BOOK), 3, 30);
        golem(h, new BlockPos(2, 2, 2));
        h.startSequence()
                .thenIdle(300)
                .thenExecute(() -> {
                    h.assertTrue(be.workers().size() == 1, "golem not recruited");
                    h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.BOOK) == 1, "the book was taken");
                    h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.ENCHANTED_BOOK) == 0, "a book was enchanted");
                    h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.LAPIS_LAZULI) == 3, "lapis was spent");
                    h.assertTrue(TestKit.count(h, TestKit.CHEST, ModItems.XP_CRYSTAL.get()) == 30, "crystals were spent");
                })
                .thenSucceed();
    }

    /** Sans table dans la zone : le golem ne fait rien et le contrôleur le dit. Avec une table, la ligne disparaît. */
    public static void noTableIsReported(GameTestHelper h) {
        ZoneControllerBlockEntity be = TestKit.floorAndZone(h, ModBlocks.ENCHANTER_CONTROLLER.get());
        stock(h, new ItemStack(Items.BOOK), 3, 31);
        golem(h, new BlockPos(2, 2, 2));
        h.startSequence()
                .thenIdle(200)
                .thenExecute(() -> {
                    h.assertTrue(TestKit.hasLine(TestKit.describe(be), "message.simple_automation.enchanter.no_table"), "no 'no table' line");
                    h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.BOOK) == 1, "a book was used without a table");
                    h.setBlock(TABLE, Blocks.ENCHANTING_TABLE);
                })
                .thenIdle(120)
                .thenExecute(() -> h.assertFalse(TestKit.hasLine(TestKit.describe(be), "message.simple_automation.enchanter.no_table"), "still no table line"))
                .thenSucceed();
    }

    /** Table au centre de la zone et 15 bibliothèques sur l'anneau à 2 blocs (hors des bornes aux coins). */
    private static void tableWithShelves(GameTestHelper h) {
        h.setBlock(TABLE, Blocks.ENCHANTING_TABLE);
        int[][] ring = {{1, 2}, {1, 3}, {1, 4}, {5, 2}, {5, 3}, {5, 4}, {2, 1}, {3, 1}, {4, 1}, {2, 5}, {3, 5}, {4, 5}};
        for (int[] p : ring) h.setBlock(p[0], 2, p[1], Blocks.BOOKSHELF);
        for (int z = 2; z <= 4; z++) h.setBlock(1, 3, z, Blocks.BOOKSHELF);
    }

    private static void stock(GameTestHelper h, ItemStack input, int lapis, int crystals) {
        ChestBlockEntity chest = TestKit.chest(h, TestKit.CHEST);
        chest.setItem(0, input);
        chest.setItem(1, new ItemStack(Items.LAPIS_LAZULI, lapis));
        chest.setItem(2, new ItemStack(ModItems.XP_CRYSTAL.get(), crystals));
    }

    private static void golem(GameTestHelper h, BlockPos at) {
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, at);
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOOK));
    }

    private EnchanterTests() {
    }
}
