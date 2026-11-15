package com.simpleautomation.workshop;

import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Alambic : on utilise le vrai bloc, donc les 20 secondes vanilla. Deux étapes, chacune réservée par un seul golem :
 * <ul>
 * <li>vider : l'alambic ne brasse pas et contient des potions du résultat de la fiche, le golem les range aux coffres ;</li>
 * <li>charger : l'alambic est libre (aucune case occupée), le golem y met jusqu'à 3 potions de base, l'ingrédient et,
 * si la réserve de carburant est vide, de la poudre de blaze.</li>
 * </ul>
 * Une première fiche peut ainsi vider ce qu'une autre a chargé : eau vers étrange, puis étrange vers force, sur deux golems.
 */
public final class BrewingStation implements Station {
    private static final int INGREDIENT_SLOT = 3;
    private static final int FUEL_SLOT = 4;
    private static final int BOTTLES = 3;

    @Override
    public String id() {
        return "brewing";
    }

    @Override
    public boolean matches(BlockState state) {
        return state.is(Blocks.BREWING_STAND);
    }

    @Override
    public int inputs() {
        return 2;
    }

    @Override
    public int[][] layout() {
        return new int[][] {{44, 35}, {80, 35}, {124, 35}};
    }

    @Override
    public int workTicks(Step step) {
        return 20;
    }

    /** Potion de base mélangée à l'ingrédient, ou vide s'il n'y a pas de mélange. */
    @Override
    public ItemStack preview(ServerLevel level, RecipeCard card) {
        ItemStack base = card.input(0);
        ItemStack ingredient = card.input(1);
        if (base.isEmpty() || ingredient.isEmpty()) return ItemStack.EMPTY;
        PotionBrewing brewing = level.potionBrewing();
        if (!brewing.isIngredient(ingredient) || !brewing.hasMix(base, ingredient)) return ItemStack.EMPTY;
        ItemStack mixed = brewing.mix(ingredient, base);
        return mixed.isEmpty() || same(mixed, base) ? ItemStack.EMPTY : mixed;
    }

    /** Même objet et même potion (le reste des composants n'a pas d'importance pour une fiole). */
    static boolean same(ItemStack a, ItemStack b) {
        return a.is(b.getItem()) && Objects.equals(a.get(DataComponents.POTION_CONTENTS), b.get(DataComponents.POTION_CONTENTS));
    }

    private static @Nullable BrewingStandBlockEntity stand(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BrewingStandBlockEntity s ? s : null;
    }

    private static int count(ZoneControllerBlockEntity zone, ItemStack model) {
        return zone.inventory().count(s -> same(s, model));
    }

    private static int countIngredient(ZoneControllerBlockEntity zone, ItemStack model) {
        return zone.inventory().count(s -> ItemStack.isSameItem(s, model));
    }

    private static boolean hasFuel(ZoneControllerBlockEntity zone) {
        return zone.inventory().count(s -> s.is(ItemTags.BREWING_FUEL)) > 0;
    }

    private static boolean free(BrewingStandBlockEntity stand) {
        for (int i = 0; i <= INGREDIENT_SLOT; i++) if (!stand.getItem(i).isEmpty()) return false;
        return true;
    }

    /** Carburant à apporter : la réserve de l'alambic est vide et sa case aussi. */
    private static boolean needsFuel(BrewingStandBlockEntity stand) {
        return stand.fuel <= 0 && stand.getItem(FUEL_SLOT).isEmpty();
    }

    @Override
    public @Nullable Step nextStep(ZoneControllerBlockEntity zone, ServerLevel level, RecipeCard card, BlockPos pos) {
        BrewingStandBlockEntity stand = stand(level, pos);
        ItemStack result = preview(level, card);
        if (stand == null || result.isEmpty() || stand.brewTime != 0) return null;
        for (int i = 0; i < BOTTLES; i++) if (same(stand.getItem(i), result)) return Step.UNLOAD;
        if (!free(stand)) return null;
        if (count(zone, card.input(0)) < 1 || countIngredient(zone, card.input(1)) < 1) return null;
        if (needsFuel(stand) && !hasFuel(zone)) return null;
        return Step.LOAD;
    }

    @Override
    public void complete(ZoneControllerBlockEntity zone, ServerLevel level, RecipeCard card, BlockPos pos, Step step) {
        BrewingStandBlockEntity stand = stand(level, pos);
        ItemStack result = preview(level, card);
        if (stand == null || result.isEmpty() || stand.brewTime != 0) return;
        if (step == Step.UNLOAD) {
            for (int i = 0; i < BOTTLES; i++) {
                ItemStack s = stand.getItem(i);
                if (!same(s, result)) continue;
                stand.setItem(i, ItemStack.EMPTY);
                zone.inventory().output(s);
            }
        } else if (step == Step.LOAD && free(stand)) {
            load(zone, stand, card);
        }
    }

    private static void load(ZoneControllerBlockEntity zone, BrewingStandBlockEntity stand, RecipeCard card) {
        ItemStack base = card.input(0);
        ItemStack ingredient = zone.inventory().extract(s -> ItemStack.isSameItem(s, card.input(1)), 1);
        if (ingredient.isEmpty()) return;
        ItemStack fuel = ItemStack.EMPTY;
        if (needsFuel(stand)) {
            fuel = zone.inventory().extract(s -> s.is(ItemTags.BREWING_FUEL), 1);
            if (fuel.isEmpty()) {
                zone.inventory().output(ingredient);
                return;
            }
        }
        int loaded = 0;
        for (int i = 0; i < BOTTLES; i++) {
            ItemStack bottle = zone.inventory().extract(s -> same(s, base), 1);
            if (bottle.isEmpty()) break;
            stand.setItem(i, bottle.copyWithCount(1));
            loaded++;
        }
        if (loaded == 0) {
            zone.inventory().output(ingredient);
            zone.inventory().output(fuel);
            return;
        }
        stand.setItem(INGREDIENT_SLOT, ingredient);
        if (!fuel.isEmpty()) stand.setItem(FUEL_SLOT, fuel);
        stand.setChanged();
    }
}
