package com.simpleautomation.test;

import com.mojang.serialization.DataResult;
import com.simpleautomation.compat.RecipeGhostPayload;
import com.simpleautomation.registry.ModBlocks;
import com.simpleautomation.registry.ModDataComponents;
import com.simpleautomation.registry.ModItems;
import com.simpleautomation.workshop.RecipeCard;
import com.simpleautomation.workshop.RecipeCardMenu;
import com.simpleautomation.workshop.Station;
import com.simpleautomation.workshop.Stations;
import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Tests de l'atelier et des fiches de recette. Enregistrés depuis {@link SATests}. */
public final class WorkshopTests {
    private static final BlockPos STATION = new BlockPos(3, 2, 3);

    // ------------------------------------------------------------------ fiches

    /** Remplit la case d'un menu comme un clic avec cet objet en main (vide = clic main vide). */
    private static void click(RecipeCardMenu menu, FakePlayer player, int slot, ItemStack stack) {
        menu.setCarried(stack);
        menu.clicked(slot, 0, ClickType.PICKUP, player);
        menu.setCarried(ItemStack.EMPTY);
    }

    private static ItemStack blankCard(Station station) {
        ItemStack card = new ItemStack(ModItems.RECIPE_CARD.get());
        card.set(ModDataComponents.RECIPE_CARD.get(), RecipeCard.blank(station));
        return card;
    }

    /** Une fiche réglée comme par le menu : un objet par case d'entrée (les cases vides sont des piles vides). */
    private static ItemStack card(GameTestHelper h, Station station, ItemStack... inputs) {
        FakePlayer player = TestKit.player(h, new BlockPos(1, 1, 1));
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, blankCard(station));
        RecipeCardMenu menu = new RecipeCardMenu(1, player.getInventory(), InteractionHand.MAIN_HAND);
        for (int i = 0; i < inputs.length; i++) if (!inputs[i].isEmpty()) click(menu, player, i, inputs[i]);
        ItemStack card = player.getMainHandItem().copy();
        player.getInventory().clearContent();
        return card;
    }

    private static RecipeCard of(ItemStack card) {
        return card.get(ModDataComponents.RECIPE_CARD.get());
    }

    private static ItemStack potion(net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> type) {
        return PotionContents.createItemStack(Items.POTION, type);
    }

    private static int potions(GameTestHelper h, net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> type) {
        ChestBlockEntity chest = TestKit.chest(h, TestKit.CHEST);
        int n = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack s = chest.getItem(i);
            PotionContents c = s.get(DataComponents.POTION_CONTENTS);
            if (s.is(Items.POTION) && c != null && c.is(type)) n += s.getCount();
        }
        return n;
    }

    /**
     * Les menus côté serveur, une station après l'autre : l'établi (3 × 3, résultat annoncé et recopié sur la fiche tenue,
     * case effacée d'un clic main vide), la table de forgeron, le tailleur de pierre (choix par clic et par dépôt JEI, qui
     * refuse un objet impossible), l'alambic (potion de base + ingrédient), puis le dépôt JEI et ses refus.
     */
    public static void cardMenusServerSide(GameTestHelper h) {
        FakePlayer player = TestKit.player(h, new BlockPos(1, 1, 1));
        player.getInventory().clearContent();
        try {
            // Établi : deux planches l'une sur l'autre donnent 4 bâtons.
            player.setItemInHand(InteractionHand.MAIN_HAND, blankCard(Stations.CRAFTING));
            RecipeCardMenu menu = new RecipeCardMenu(1, player.getInventory(), InteractionHand.MAIN_HAND);
            player.containerMenu = menu;
            click(menu, player, 1, new ItemStack(Items.OAK_PLANKS, 5));
            h.assertTrue(menu.getSlot(9).getItem().is(Items.OAK_BUTTON), "one plank should make a button");
            click(menu, player, 4, new ItemStack(Items.OAK_PLANKS));
            h.assertTrue(menu.getSlot(9).getItem().is(Items.STICK) && menu.getSlot(9).getItem().getCount() == 4, "no 4 sticks in the result slot");
            RecipeCard held = of(player.getMainHandItem());
            h.assertTrue(held.result().is(Items.STICK) && held.input(1).is(Items.OAK_PLANKS) && held.input(1).getCount() == 1, "the held card was not updated");
            // Des cases fantômes : le curseur est rendu, l'inventaire n'a rien gagné.
            h.assertTrue(player.getInventory().isEmpty() || player.getInventory().countItem(Items.OAK_PLANKS) == 0, "a ghost slot gave an item");
            click(menu, player, 4, ItemStack.EMPTY);
            h.assertTrue(menu.getSlot(9).getItem().is(Items.OAK_BUTTON) && of(player.getMainHandItem()).input(4).isEmpty(), "the empty-handed click did not clear the slot");
            click(menu, player, 1, ItemStack.EMPTY);
            h.assertTrue(menu.getSlot(9).getItem().isEmpty() && of(player.getMainHandItem()).result().isEmpty(), "the card still has a result with no input");
            // Le résultat se clique sans effet à l'établi.
            click(menu, player, 9, new ItemStack(Items.DIAMOND));
            h.assertTrue(menu.getSlot(9).getItem().isEmpty(), "the crafting result slot took an item");

            // Table de forgeron : lingot de netherite sur épée en diamant avec le modèle.
            player.setItemInHand(InteractionHand.MAIN_HAND, blankCard(Stations.SMITHING));
            menu = new RecipeCardMenu(2, player.getInventory(), InteractionHand.MAIN_HAND);
            player.containerMenu = menu;
            click(menu, player, 0, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
            click(menu, player, 1, new ItemStack(Items.DIAMOND_SWORD));
            click(menu, player, 2, new ItemStack(Items.NETHERITE_INGOT));
            h.assertTrue(menu.getSlot(3).getItem().is(Items.NETHERITE_SWORD), "no netherite sword in the smithing result slot");

            // Tailleur de pierre : les choix de la cobblestone, passage au suivant, dépôt JEI d'un résultat possible et impossible.
            player.setItemInHand(InteractionHand.MAIN_HAND, blankCard(Stations.STONECUTTER));
            menu = new RecipeCardMenu(3, player.getInventory(), InteractionHand.MAIN_HAND);
            player.containerMenu = menu;
            click(menu, player, 0, new ItemStack(Items.COBBLESTONE));
            int count = menu.choiceCount();
            h.assertTrue(count >= 3, "expected the cobblestone choices, got " + count);
            ItemStack first = menu.getSlot(1).getItem().copy();
            h.assertTrue(!first.isEmpty() && menu.choiceIndex() == 0, "no first choice");
            menu.clicked(1, 0, ClickType.PICKUP, player);
            h.assertTrue(menu.choiceIndex() == 1 && !ItemStack.isSameItem(menu.getSlot(1).getItem(), first), "the click did not pick the next result");
            menu.clicked(1, 1, ClickType.PICKUP, player);
            h.assertTrue(menu.choiceIndex() == 0 && ItemStack.isSameItem(menu.getSlot(1).getItem(), first), "the right-click did not go back");
            menu.clicked(1, 1, ClickType.PICKUP, player);
            h.assertTrue(menu.choiceIndex() == count - 1, "the right-click did not wrap around");
            h.assertTrue(RecipeGhostPayload.apply(player, new RecipeGhostPayload(3, 1, new ItemStack(Items.COBBLESTONE_SLAB))), "the JEI drop of a possible result was refused");
            h.assertTrue(menu.getSlot(1).getItem().is(Items.COBBLESTONE_SLAB) && menu.getSlot(1).getItem().getCount() == 2, "the slab was not chosen");
            h.assertTrue(of(player.getMainHandItem()).result().is(Items.COBBLESTONE_SLAB), "the choice was not saved on the card");
            RecipeGhostPayload.apply(player, new RecipeGhostPayload(3, 1, new ItemStack(Items.DIAMOND)));
            h.assertTrue(menu.getSlot(1).getItem().is(Items.COBBLESTONE_SLAB), "an impossible result was chosen");
            // Changer l'entrée remet le premier choix.
            click(menu, player, 0, new ItemStack(Items.STONE));
            h.assertTrue(menu.choiceIndex() == 0 && !menu.getSlot(1).getItem().isEmpty(), "a new input did not reset the choice");

            // Alambic : eau + verrue du Nether donnent une potion étrange ; le dépôt JEI garde la potion.
            player.setItemInHand(InteractionHand.MAIN_HAND, blankCard(Stations.BREWING));
            menu = new RecipeCardMenu(4, player.getInventory(), InteractionHand.MAIN_HAND);
            player.containerMenu = menu;
            h.assertTrue(RecipeGhostPayload.apply(player, new RecipeGhostPayload(4, 0, potion(Potions.WATER))), "the JEI potion drop was refused");
            click(menu, player, 1, new ItemStack(Items.NETHER_WART));
            ItemStack awkward = menu.getSlot(2).getItem();
            h.assertTrue(awkward.is(Items.POTION) && awkward.get(DataComponents.POTION_CONTENTS).is(Potions.AWKWARD), "no awkward potion in the brewing result slot");
            h.assertTrue(of(player.getMainHandItem()).input(0).get(DataComponents.POTION_CONTENTS).is(Potions.WATER), "the card lost the potion type");
            // Sans mélange possible : pas de résultat.
            click(menu, player, 1, new ItemStack(Items.STICK));
            h.assertTrue(menu.getSlot(2).getItem().isEmpty(), "a stick brewed something");

            // JEI : refusés (mauvais menu, case de l'inventaire, case hors fiche, air, résultat de l'alambic).
            RecipeCard before = of(player.getMainHandItem());
            h.assertFalse(RecipeGhostPayload.apply(player, new RecipeGhostPayload(9, 0, new ItemStack(Items.DIAMOND))), "wrong container id accepted");
            h.assertFalse(RecipeGhostPayload.apply(player, new RecipeGhostPayload(4, -1, new ItemStack(Items.DIAMOND))), "slot -1 accepted");
            h.assertFalse(RecipeGhostPayload.apply(player, new RecipeGhostPayload(4, 12, new ItemStack(Items.DIAMOND))), "inventory slot accepted");
            h.assertFalse(RecipeGhostPayload.apply(player, new RecipeGhostPayload(4, 2, new ItemStack(Items.DIAMOND))), "the brewing result slot accepted a drop");
            h.assertFalse(RecipeGhostPayload.apply(player, new RecipeGhostPayload(4, 0, ItemStack.EMPTY)), "air accepted");
            h.assertTrue(of(player.getMainHandItem()).equals(before), "a refused drop changed the card");
        } finally {
            player.containerMenu = player.inventoryMenu;
        }
        h.assertFalse(RecipeGhostPayload.apply(player, new RecipeGhostPayload(4, 0, new ItemStack(Items.DIAMOND))), "a drop without an open card was accepted");
        player.getInventory().clearContent();
        h.succeed();
    }

    /**
     * Le clic droit sur une station avec une fiche vierge la règle sur cette station (même sur l'établi, qui ouvrirait sinon
     * sa propre interface) ; une fiche réglée laisse la station faire son travail, et accroupi elle change de station.
     * Aussi : la fiche sauvegardée et rechargée (codec) est la même, et deux fiches identiques sont égales.
     */
    public static void cardConfiguredOnStation(GameTestHelper h) {
        h.setBlock(2, 2, 2, Blocks.CRAFTING_TABLE);
        h.setBlock(3, 2, 2, Blocks.STONECUTTER);
        h.setBlock(4, 2, 2, Blocks.CHEST);
        FakePlayer player = TestKit.player(h, new BlockPos(3, 2, 4));
        player.getInventory().clearContent();
        ItemStack stack = new ItemStack(ModItems.RECIPE_CARD.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        h.assertFalse(stack.has(ModDataComponents.RECIPE_CARD.get()), "a new card is not blank");

        // Un coffre n'est pas une station.
        h.assertTrue(use(h, player, stack, new BlockPos(4, 2, 2)) == InteractionResult.PASS && !stack.has(ModDataComponents.RECIPE_CARD.get()), "a chest configured the card");
        // L'établi règle la fiche.
        h.assertTrue(use(h, player, stack, new BlockPos(2, 2, 2)).consumesAction(), "the crafting table did not take the click");
        RecipeCard card = of(stack);
        h.assertTrue(card != null && card.station() == Stations.CRAFTING && card.inputs().size() == 9, "the card is not set for the crafting table");
        player.containerMenu = player.inventoryMenu;
        // Fiche réglée : le tailleur de pierre fait son travail (PASS), accroupi il change la station.
        h.assertTrue(use(h, player, stack, new BlockPos(3, 2, 2)) == InteractionResult.PASS && of(stack).station() == Stations.CRAFTING, "a configured card took the click");
        player.setShiftKeyDown(true);
        h.assertTrue(use(h, player, stack, new BlockPos(3, 2, 2)).consumesAction() && of(stack).station() == Stations.STONECUTTER, "sneaking did not change the station");
        player.setShiftKeyDown(false);
        player.containerMenu = player.inventoryMenu;

        // Sauvegarde et rechargement.
        RecipeCard full = of(card(h, Stations.CRAFTING, ItemStack.EMPTY, new ItemStack(Items.OAK_PLANKS), ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.OAK_PLANKS)));
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, h.getLevel().registryAccess());
        DataResult<Tag> encoded = RecipeCard.CODEC.encodeStart(ops, full);
        RecipeCard back = RecipeCard.CODEC.parse(ops, encoded.getOrThrow()).getOrThrow();
        h.assertTrue(back.equals(full) && back.hashCode() == full.hashCode(), "the card changed through its codec");
        h.assertTrue(back.result().is(Items.STICK), "the card lost its result");
        h.assertFalse(back.equals(RecipeCard.blank(Stations.CRAFTING)), "different cards are equal");
        player.getInventory().clearContent();
        h.succeed();
    }

    private static InteractionResult use(GameTestHelper h, FakePlayer player, ItemStack stack, BlockPos at) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(h.absolutePos(at)), Direction.UP, h.absolutePos(at), false);
        return stack.getItem().onItemUseFirst(stack, new UseOnContext(h.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit));
    }

    // ------------------------------------------------------------------ golems

    private static CopperGolem golem(GameTestHelper h, ItemStack card) {
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(2, 2, 2));
        golem.setItemInHand(InteractionHand.MAIN_HAND, card);
        return golem;
    }

    /**
     * Établi et fiche « planches vers bâtons » (modèle : planches de chêne) : le coffre n'a que des planches d'épicéa, que la
     * recette accepte au même titre. Les 2 planches deviennent 4 bâtons, une seule fois.
     */
    public static void craftsSticks(GameTestHelper h) {
        ZoneControllerBlockEntity be = TestKit.floorAndZone(h, ModBlocks.WORKSHOP_CONTROLLER.get());
        h.setBlock(STATION, Blocks.CRAFTING_TABLE);
        TestKit.chest(h, TestKit.CHEST).setItem(0, new ItemStack(Items.SPRUCE_PLANKS, 2));
        golem(h, card(h, Stations.CRAFTING, ItemStack.EMPTY, new ItemStack(Items.OAK_PLANKS), ItemStack.EMPTY,
                ItemStack.EMPTY, new ItemStack(Items.OAK_PLANKS)));
        h.succeedWhen(() -> {
            h.assertTrue(be.workers().size() == 1, "golem not recruited");
            h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.STICK) == 4, "expected 4 sticks, got " + TestKit.count(h, TestKit.CHEST, Items.STICK));
            h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.SPRUCE_PLANKS) == 0, "planks left");
        });
    }

    /** Les restes de la recette sortent aussi : un gâteau rend ses 3 seaux vides. */
    public static void craftingLeftovers(GameTestHelper h) {
        TestKit.floorAndZone(h, ModBlocks.WORKSHOP_CONTROLLER.get());
        h.setBlock(STATION, Blocks.CRAFTING_TABLE);
        ChestBlockEntity chest = TestKit.chest(h, TestKit.CHEST);
        chest.setItem(0, new ItemStack(Items.MILK_BUCKET));
        chest.setItem(1, new ItemStack(Items.MILK_BUCKET));
        chest.setItem(2, new ItemStack(Items.MILK_BUCKET));
        chest.setItem(3, new ItemStack(Items.SUGAR, 2));
        chest.setItem(4, new ItemStack(Items.EGG));
        chest.setItem(5, new ItemStack(Items.WHEAT, 3));
        ItemStack milk = new ItemStack(Items.MILK_BUCKET);
        ItemStack sugar = new ItemStack(Items.SUGAR);
        golem(h, card(h, Stations.CRAFTING, milk, milk, milk, sugar, new ItemStack(Items.EGG), sugar,
                new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT)));
        h.succeedWhen(() -> {
            h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.CAKE) == 1, "no cake");
            h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.BUCKET) == 3, "expected 3 empty buckets, got " + TestKit.count(h, TestKit.CHEST, Items.BUCKET));
            h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.MILK_BUCKET) == 0, "milk left");
        });
    }

    /** Table de forgeron : l'épée en diamant enchantée devient une épée en netherite qui garde son enchantement. */
    public static void smithsNetheriteSword(GameTestHelper h) {
        TestKit.floorAndZone(h, ModBlocks.WORKSHOP_CONTROLLER.get());
        h.setBlock(STATION, Blocks.SMITHING_TABLE);
        ChestBlockEntity chest = TestKit.chest(h, TestKit.CHEST);
        chest.setItem(0, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
        chest.setItem(1, TestKit.enchanted(h, Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 3));
        chest.setItem(2, new ItemStack(Items.NETHERITE_INGOT));
        golem(h, card(h, Stations.SMITHING, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), new ItemStack(Items.DIAMOND_SWORD),
                new ItemStack(Items.NETHERITE_INGOT)));
        h.succeedWhen(() -> {
            ItemStack sword = ItemStack.EMPTY;
            for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(Items.NETHERITE_SWORD)) sword = chest.getItem(i);
            h.assertTrue(!sword.isEmpty(), "no netherite sword");
            h.assertTrue(sword.isEnchanted(), "the enchantment was lost");
            h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.DIAMOND_SWORD) == 0 && TestKit.count(h, TestKit.CHEST, Items.NETHERITE_INGOT) == 0
                    && TestKit.count(h, TestKit.CHEST, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) == 0, "materials left");
        });
    }

    /** Tailleur de pierre : 3 cobblestones deviennent 6 dalles (résultat choisi sur la fiche, 2 dalles par pierre). */
    public static void stonecutsSlabs(GameTestHelper h) {
        TestKit.floorAndZone(h, ModBlocks.WORKSHOP_CONTROLLER.get());
        h.setBlock(STATION, Blocks.STONECUTTER);
        TestKit.chest(h, TestKit.CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        FakePlayer player = TestKit.player(h, new BlockPos(1, 1, 1));
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, blankCard(Stations.STONECUTTER));
        RecipeCardMenu menu = new RecipeCardMenu(1, player.getInventory(), InteractionHand.MAIN_HAND);
        player.containerMenu = menu;
        click(menu, player, 0, new ItemStack(Items.COBBLESTONE));
        click(menu, player, 1, new ItemStack(Items.COBBLESTONE_SLAB));
        ItemStack card = player.getMainHandItem().copy();
        player.containerMenu = player.inventoryMenu;
        player.getInventory().clearContent();
        h.assertTrue(of(card).result().is(Items.COBBLESTONE_SLAB), "the card does not make slabs");
        golem(h, card);
        h.succeedWhen(() -> {
            h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.COBBLESTONE_SLAB) == 6, "expected 6 slabs, got " + TestKit.count(h, TestKit.CHEST, Items.COBBLESTONE_SLAB));
            h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.COBBLESTONE) == 0, "cobblestone left");
        });
    }

    /**
     * Alambic et fiche « eau + verrue » : le golem charge 3 eaux, la verrue et la poudre de blaze, le vrai alambic brasse,
     * puis il le vide : 3 potions étranges au coffre, la poudre dépensée.
     */
    public static void brewsAwkward(GameTestHelper h) {
        ZoneControllerBlockEntity be = TestKit.floorAndZone(h, ModBlocks.WORKSHOP_CONTROLLER.get());
        h.setBlock(STATION, Blocks.BREWING_STAND);
        ChestBlockEntity chest = TestKit.chest(h, TestKit.CHEST);
        for (int i = 0; i < 3; i++) chest.setItem(i, potion(Potions.WATER));
        chest.setItem(3, new ItemStack(Items.NETHER_WART));
        chest.setItem(4, new ItemStack(Items.BLAZE_POWDER));
        golem(h, card(h, Stations.BREWING, potion(Potions.WATER), new ItemStack(Items.NETHER_WART)));
        h.succeedWhen(() -> {
            h.assertTrue(be.workers().size() == 1, "golem not recruited");
            h.assertTrue(potions(h, Potions.AWKWARD) == 3, "expected 3 awkward potions, got " + potions(h, Potions.AWKWARD));
            h.assertTrue(potions(h, Potions.WATER) == 0, "water left");
            h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.NETHER_WART) == 0, "wart left");
        });
    }

    /**
     * Enchaînement sur un seul alambic, par les coffres : un golem « eau + verrue » et un golem « étrange + poudre de blaze »
     * (force). Le second attend que le premier ait vidé l'alambic : 2 potions de force au bout, rien n'est perdu.
     */
    public static void brewingChain(GameTestHelper h) {
        TestKit.floorAndZone(h, ModBlocks.WORKSHOP_CONTROLLER.get());
        h.setBlock(STATION, Blocks.BREWING_STAND);
        ChestBlockEntity chest = TestKit.chest(h, TestKit.CHEST);
        chest.setItem(0, potion(Potions.WATER));
        chest.setItem(1, potion(Potions.WATER));
        chest.setItem(2, new ItemStack(Items.NETHER_WART));
        chest.setItem(3, new ItemStack(Items.BLAZE_POWDER, 4));
        golem(h, card(h, Stations.BREWING, potion(Potions.WATER), new ItemStack(Items.NETHER_WART)));
        golem(h, card(h, Stations.BREWING, potion(Potions.AWKWARD), new ItemStack(Items.BLAZE_POWDER)));
        h.succeedWhen(() -> {
            h.assertTrue(potions(h, Potions.STRENGTH) == 2, "expected 2 strength potions, got " + potions(h, Potions.STRENGTH));
            h.assertTrue(potions(h, Potions.AWKWARD) == 0 && potions(h, Potions.WATER) == 0, "intermediate potions left");
        });
    }

    /**
     * Sans station du type de la fiche, le golem est en attente (« ? ») et le contrôleur le dit ; une fiche vierge n'est pas
     * acceptée non plus. Avec la station posée, il se met au travail. Sans matière dans les coffres, rien ne bouge.
     */
    public static void missingStationAndMaterial(GameTestHelper h) {
        ZoneControllerBlockEntity be = TestKit.floorAndZone(h, ModBlocks.WORKSHOP_CONTROLLER.get());
        h.setBlock(STATION, Blocks.CRAFTING_TABLE);
        TestKit.chest(h, TestKit.CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 2));
        ItemStack slabs = card(h, Stations.STONECUTTER, new ItemStack(Items.COBBLESTONE));
        golem(h, slabs);
        golem(h, blankCard(Stations.CRAFTING));
        h.startSequence()
                .thenIdle(200)
                .thenExecute(() -> {
                    h.assertTrue(be.workers().size() == 2, "golems not recruited");
                    for (Worker w : be.workers()) h.assertTrue(w.status == Worker.Status.NO_TOOL, "a golem works without a usable card: " + w.status);
                    h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.COBBLESTONE) == 2, "cobblestone was used without a stonecutter");
                    h.assertTrue(TestKit.hasLine(TestKit.describe(be), "message.simple_automation.workshop.missing_station"), "no 'missing station' line");
                    h.setBlock(new BlockPos(4, 2, 4), Blocks.STONECUTTER);
                })
                .thenWaitUntil(() -> h.assertTrue(TestKit.count(h, TestKit.CHEST, Items.COBBLESTONE) == 0, "the stonecutter was not used once placed"))
                .thenExecute(() -> {
                    // La fiche vierge reste sans travail, l'autre a fini.
                    h.assertTrue(be.workers().stream().filter(w -> w.status == Worker.Status.NO_TOOL).count() == 1, "the blank-card golem should stay without work");
                })
                .thenSucceed();
    }

    private WorkshopTests() {
    }
}
