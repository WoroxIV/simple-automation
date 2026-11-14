package com.simpleautomation.test;

import com.simpleautomation.pipe.PipeBlock;
import com.simpleautomation.compat.FilterGhostPayload;
import com.simpleautomation.pipe.filter.FilterMenu;
import com.simpleautomation.pipe.PipeBlockEntity;
import com.simpleautomation.pipe.PipeSide;
import com.simpleautomation.pipe.filter.ItemFilter;
import com.simpleautomation.registry.ModBlocks;
import com.simpleautomation.registry.ModDataComponents;
import com.simpleautomation.registry.ModItems;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Tests des filtres de pipe. Enregistrés depuis {@link SATests}. */
public final class FilterTests {
    private static final BlockPos SOURCE = new BlockPos(1, 1, 1);
    private static final BlockPos TARGET = new BlockPos(5, 1, 1);

    /** Filtre « lingot de fer » côté Insertion du coffre d'arrivée : seuls les lingots passent, la pierre reste au départ. */
    public static void pipeFilterItem(GameTestHelper h) {
        buildLine(h, new ItemStack(Items.COBBLESTONE, 32), new ItemStack(Items.IRON_INGOT, 16));
        ItemFilter filter = new ItemFilter(List.of(new ItemFilter.Entry(Items.IRON_INGOT, Optional.empty())), false);
        pipe(h, 4).setFilter(Direction.EAST, filterStack(filter));

        h.succeedWhen(() -> {
            h.assertTrue(count(h, TARGET, Items.IRON_INGOT) == 16, "the iron ingots did not reach the target chest");
            h.assertTrue(count(h, TARGET, Items.COBBLESTONE) == 0, "cobblestone went through an iron-only filter");
            h.assertTrue(count(h, SOURCE, Items.COBBLESTONE) == 32, "cobblestone left the source chest");
        });
    }

    /** Filtre « #minecraft:logs » côté Extraction : chêne et bouleau partent, la pierre reste. */
    public static void pipeFilterTag(GameTestHelper h) {
        buildLine(h, new ItemStack(Items.COBBLESTONE, 32), new ItemStack(Items.OAK_LOG, 8), new ItemStack(Items.BIRCH_LOG, 8));
        ItemFilter filter = new ItemFilter(List.of(new ItemFilter.Entry(Items.OAK_LOG, Optional.of(ItemTags.LOGS))), false);
        pipe(h, 2).setFilter(Direction.WEST, filterStack(filter));

        h.succeedWhen(() -> {
            h.assertTrue(count(h, TARGET, Items.OAK_LOG) == 8, "the oak logs did not reach the target chest");
            h.assertTrue(count(h, TARGET, Items.BIRCH_LOG) == 8, "the birch logs did not reach the target chest");
            h.assertTrue(count(h, TARGET, Items.COBBLESTONE) == 0, "cobblestone went through a logs-only filter");
            h.assertTrue(count(h, SOURCE, Items.COBBLESTONE) == 32, "cobblestone left the source chest");
        });
    }

    /** Liste noire « pierre » côté Extraction : les lingots partent, la pierre reste au départ. */
    public static void pipeFilterBlacklist(GameTestHelper h) {
        buildLine(h, new ItemStack(Items.COBBLESTONE, 32), new ItemStack(Items.IRON_INGOT, 16));
        ItemFilter filter = new ItemFilter(List.of(new ItemFilter.Entry(Items.COBBLESTONE, Optional.empty())), true);
        pipe(h, 2).setFilter(Direction.WEST, filterStack(filter));

        h.succeedWhen(() -> {
            h.assertTrue(count(h, TARGET, Items.IRON_INGOT) == 16, "the iron ingots did not reach the target chest");
            h.assertTrue(count(h, TARGET, Items.COBBLESTONE) == 0, "blacklisted cobblestone went through");
            h.assertTrue(count(h, SOURCE, Items.COBBLESTONE) == 32, "cobblestone left the source chest");
        });
    }

    /**
     * Le menu du filtre côté serveur : un objet posé sur une case fantôme (le curseur ne perd rien), le clic droit qui fait
     * défiler les tags puis revient à l'objet exact, le bouton liste blanche / noire, le clic main vide qui efface.
     * Puis le dépôt depuis JEI ({@link FilterGhostPayload}) : accepté sur une case du filtre, refusé partout ailleurs.
     */
    public static void filterMenuServerSide(GameTestHelper h) {
        FakePlayer player = TestKit.player(h, new BlockPos(1, 1, 1));
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.FILTER.get()));
        FilterMenu menu = new FilterMenu(1, player.getInventory(), InteractionHand.MAIN_HAND);
        player.containerMenu = menu;
        try {
            // Un lingot posé sur la case 0.
            menu.setCarried(new ItemStack(Items.IRON_INGOT));
            menu.clicked(0, 0, ClickType.PICKUP, player);
            h.assertTrue(held(player).entry(0).item() == Items.IRON_INGOT && held(player).entry(0).tag().isEmpty(), "slot 0 is not the iron ingot");
            h.assertTrue(menu.getCarried().is(Items.IRON_INGOT) && menu.getCarried().getCount() == 1, "the cursor lost its item");
            menu.setCarried(ItemStack.EMPTY);

            // Clic droit : chaque tag du lingot, dans l'ordre, puis retour à l'objet exact.
            List<TagKey<Item>> tags = ItemFilter.sortedTags(Items.IRON_INGOT);
            h.assertTrue(!tags.isEmpty(), "the iron ingot has no tag");
            for (TagKey<Item> tag : tags) {
                menu.clicked(0, 1, ClickType.PICKUP, player);
                h.assertTrue(held(player).entry(0).tag().equals(Optional.of(tag)), "expected tag " + tag.location() + ", got " + held(player).entry(0).tag());
            }
            menu.clicked(0, 1, ClickType.PICKUP, player);
            h.assertTrue(held(player).entry(0).tag().isEmpty(), "the tag cycle did not come back to the exact item");

            // Bouton liste blanche / noire.
            h.assertFalse(held(player).blacklist(), "a new filter is a blacklist");
            menu.clickMenuButton(player, FilterMenu.BUTTON_MODE);
            h.assertTrue(held(player).blacklist(), "the button did not switch to blacklist");
            menu.clickMenuButton(player, FilterMenu.BUTTON_MODE);
            h.assertFalse(held(player).blacklist(), "the button did not switch back to whitelist");

            // Clic main vide : la case est effacée.
            menu.clicked(0, 0, ClickType.PICKUP, player);
            h.assertTrue(held(player).entry(0).isEmpty(), "an empty-handed click did not clear the slot");

            // Dépôt depuis JEI : accepté sur la case 3, le curseur du joueur est rendu tel quel.
            ItemStack cursor = new ItemStack(Items.APPLE, 3);
            menu.setCarried(cursor);
            h.assertTrue(FilterGhostPayload.apply(player, new FilterGhostPayload(1, 3, Items.GOLD_INGOT)), "the JEI drop was refused");
            h.assertTrue(held(player).entry(3).item() == Items.GOLD_INGOT && held(player).entry(3).tag().isEmpty(), "slot 3 is not the gold ingot");
            h.assertTrue(menu.getCarried() == cursor && cursor.getCount() == 3, "the cursor was not given back");
            menu.setCarried(ItemStack.EMPTY);
            h.assertFalse(player.getInventory().contains(new ItemStack(Items.GOLD_INGOT)), "the JEI drop gave an item");

            // Refusés : mauvais menu, case hors du filtre, air, case de l'inventaire.
            ItemFilter before = held(player);
            h.assertFalse(FilterGhostPayload.apply(player, new FilterGhostPayload(2, 4, Items.DIAMOND)), "wrong container id accepted");
            h.assertFalse(FilterGhostPayload.apply(player, new FilterGhostPayload(1, -1, Items.DIAMOND)), "slot -1 accepted");
            h.assertFalse(FilterGhostPayload.apply(player, new FilterGhostPayload(1, 9, Items.DIAMOND)), "slot 9 accepted");
            h.assertFalse(FilterGhostPayload.apply(player, new FilterGhostPayload(1, 20, Items.DIAMOND)), "inventory slot accepted");
            h.assertFalse(FilterGhostPayload.apply(player, new FilterGhostPayload(1, 4, Items.AIR)), "air accepted");
            h.assertTrue(held(player).equals(before), "a refused drop changed the filter");
        } finally {
            player.containerMenu = player.inventoryMenu;
        }
        h.assertFalse(FilterGhostPayload.apply(player, new FilterGhostPayload(1, 4, Items.DIAMOND)), "a drop without an open filter was accepted");
        player.getInventory().clearContent();
        h.succeed();
    }

    /**
     * Le filtre posé sur le bras côté coffre d'un clic droit, repris d'un clic droit main vide ; refusé sur un bras sans conteneur.
     * Reposé puis la pipe cassée : le filtre tombe au sol avec elle.
     */
    public static void filterPlacedAndRemovedByHand(GameTestHelper h) {
        h.setBlock(SOURCE, Blocks.CHEST);
        BlockPos pipePos = new BlockPos(2, 1, 1);
        h.setBlock(pipePos, ModBlocks.PIPE.get());
        BlockPos abs = h.absolutePos(pipePos);
        PipeBlock.refresh(h.getLevel(), abs);
        h.assertTrue(PipeBlock.side(h.getBlockState(pipePos), Direction.WEST) == PipeSide.INSERT, "the pipe does not see the chest");
        FakePlayer player = TestKit.player(h, new BlockPos(2, 1, 2));
        player.getInventory().clearContent();
        ItemFilter filter = new ItemFilter(List.of(new ItemFilter.Entry(Items.IRON_INGOT, Optional.empty())), false);
        BlockHitResult west = hit(abs, Direction.WEST);
        BlockHitResult east = hit(abs, Direction.EAST);

        // Bras sans conteneur : refusé, le filtre reste en main.
        player.setItemInHand(InteractionHand.MAIN_HAND, filterStack(filter));
        h.getLevel().getBlockState(abs).useItemOn(player.getMainHandItem(), h.getLevel(), player, InteractionHand.MAIN_HAND, east);
        h.assertTrue(pipe(h, 2).filterStack(Direction.EAST).isEmpty() && player.getMainHandItem().is(ModItems.FILTER.get()),
                "a filter was put on an arm without a container");

        // Bras côté coffre : posé, la main se vide.
        h.getLevel().getBlockState(abs).useItemOn(player.getMainHandItem(), h.getLevel(), player, InteractionHand.MAIN_HAND, west);
        h.assertTrue(pipe(h, 2).filterStack(Direction.WEST).is(ModItems.FILTER.get()), "the filter was not put on the arm");
        h.assertTrue(player.getMainHandItem().isEmpty(), "the filter was not taken from the hand");
        h.assertTrue(filter.equals(pipe(h, 2).filter(Direction.WEST)), "the filter settings changed");

        // Main vide sur le bras : repris.
        h.getLevel().getBlockState(abs).useWithoutItem(h.getLevel(), player, west);
        h.assertTrue(pipe(h, 2).filterStack(Direction.WEST).isEmpty(), "the filter was not taken back");
        h.assertTrue(player.getInventory().contains(s -> s.is(ModItems.FILTER.get())), "the filter did not come back to the player");

        // Reposé, puis la pipe cassée : le filtre tombe au sol.
        player.getInventory().clearContent();
        pipe(h, 2).setFilter(Direction.WEST, filterStack(filter));
        h.getLevel().destroyBlock(abs, true);
        h.assertItemEntityPresent(ModItems.FILTER.get(), pipePos, 2.0);
        h.killAllEntitiesOfClass(ItemEntity.class);
        h.succeed();
    }

    /**
     * La clé sur un bras côté coffre : Insertion → Extraction → Désactivé → Insertion.
     * Sur un bras vers une autre pipe : séparées des deux côtés, puis reliées.
     */
    public static void wrenchCyclesSides(GameTestHelper h) {
        h.setBlock(SOURCE, Blocks.CHEST);
        h.setBlock(2, 1, 1, ModBlocks.PIPE.get());
        h.setBlock(3, 1, 1, ModBlocks.PIPE.get());
        BlockPos a = h.absolutePos(new BlockPos(2, 1, 1));
        BlockPos b = h.absolutePos(new BlockPos(3, 1, 1));
        PipeBlock.refresh(h.getLevel(), a);
        PipeBlock.refresh(h.getLevel(), b);
        FakePlayer player = TestKit.player(h, new BlockPos(2, 1, 2));
        ItemStack wrench = new ItemStack(ModItems.WRENCH.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);

        h.assertTrue(side(h, a, Direction.WEST) == PipeSide.INSERT, "expected INSERT at start, got " + side(h, a, Direction.WEST));
        wrench(h, player, wrench, a, Direction.WEST);
        h.assertTrue(side(h, a, Direction.WEST) == PipeSide.EXTRACT, "expected EXTRACT, got " + side(h, a, Direction.WEST));
        wrench(h, player, wrench, a, Direction.WEST);
        h.assertTrue(side(h, a, Direction.WEST) == PipeSide.NONE && pipe(h, 2).isDisabled(Direction.WEST), "expected disabled, got " + side(h, a, Direction.WEST));
        wrench(h, player, wrench, a, Direction.WEST);
        h.assertTrue(side(h, a, Direction.WEST) == PipeSide.INSERT && !pipe(h, 2).isDisabled(Direction.WEST),
                "expected INSERT again, got " + side(h, a, Direction.WEST));

        h.assertTrue(side(h, a, Direction.EAST) == PipeSide.PIPE, "the two pipes are not joined at start");
        wrench(h, player, wrench, a, Direction.EAST);
        h.assertTrue(side(h, a, Direction.EAST) == PipeSide.NONE && side(h, b, Direction.WEST) == PipeSide.NONE, "the pipes were not separated");
        h.assertTrue(pipe(h, 2).isDisabled(Direction.EAST) && pipe(h, 3).isDisabled(Direction.WEST), "the separation is not stored on both pipes");
        wrench(h, player, wrench, a, Direction.EAST);
        h.assertTrue(side(h, a, Direction.EAST) == PipeSide.PIPE && side(h, b, Direction.WEST) == PipeSide.PIPE, "the pipes were not joined again");
        player.getInventory().clearContent();
        h.succeed();
    }

    private static void wrench(GameTestHelper h, FakePlayer player, ItemStack wrench, BlockPos pipe, Direction arm) {
        wrench.useOn(new UseOnContext(h.getLevel(), player, InteractionHand.MAIN_HAND, wrench, hit(pipe, arm)));
    }

    private static PipeSide side(GameTestHelper h, BlockPos abs, Direction d) {
        return PipeBlock.side(h.getLevel().getBlockState(abs), d);
    }

    /** Un clic au bout du bras {@code arm} d'une pipe (position absolue). */
    private static BlockHitResult hit(BlockPos pipe, Direction arm) {
        Vec3 at = Vec3.atCenterOf(pipe).add(arm.getStepX() * 0.4, arm.getStepY() * 0.4, arm.getStepZ() * 0.4);
        return new BlockHitResult(at, arm, pipe, false);
    }

    private static ItemFilter held(FakePlayer player) {
        return player.getMainHandItem().getOrDefault(ModDataComponents.FILTER.get(), ItemFilter.EMPTY);
    }

    private static ItemStack filterStack(ItemFilter filter) {
        ItemStack stack = new ItemStack(ModItems.FILTER.get());
        stack.set(ModDataComponents.FILTER.get(), filter);
        return stack;
    }

    /** Coffre → 3 pipes → coffre, la première pipe en Extraction côté coffre de départ (les piles d'abord, dans l'ordre). */
    private static void buildLine(GameTestHelper h, ItemStack... contents) {
        h.setBlock(SOURCE, Blocks.CHEST);
        h.setBlock(TARGET, Blocks.CHEST);
        ChestBlockEntity source = h.getBlockEntity(SOURCE, ChestBlockEntity.class);
        for (int i = 0; i < contents.length; i++) source.setItem(i, contents[i]);
        for (int x = 2; x <= 4; x++) h.setBlock(x, 1, 1, ModBlocks.PIPE.get());
        for (int x = 2; x <= 4; x++) PipeBlock.refresh(h.getLevel(), h.absolutePos(new BlockPos(x, 1, 1)));
        BlockPos first = h.absolutePos(new BlockPos(2, 1, 1));
        h.getLevel().setBlock(first, h.getLevel().getBlockState(first).setValue(PipeBlock.PROPERTIES.get(Direction.WEST), PipeSide.EXTRACT), 3);
    }

    private static PipeBlockEntity pipe(GameTestHelper h, int x) {
        return h.getBlockEntity(new BlockPos(x, 1, 1), PipeBlockEntity.class);
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

    private FilterTests() {
    }
}
