package com.simpleautomation.workshop;

import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Base des stations « instantanées » : le golem prend les objets aux coffres, applique la recette et range le résultat
 * (établi, table de forgeron, tailleur de pierre). Les cases de la fiche donnent le modèle ; pour chaque case le golem
 * préfère l'objet fantôme lui-même, sinon n'importe quel objet des coffres que la même recette accepte à cette place
 * (planches d'une autre essence pour des bâtons, par exemple) tant que le résultat reste le même objet.
 */
abstract class RecipeStation implements Station {
    /** Ce que le golem sort de la station : le résultat, puis les restes (seaux vides...). */
    record Output(ItemStack result, List<ItemStack> leftovers) {
    }

    /** Applique la recette à ces objets, case par case, pour obtenir {@code expected} (même objet) ; null si elle ne marche pas. */
    abstract @Nullable Output run(ServerLevel level, List<ItemStack> grid, ItemStack expected);

    private boolean valid(ServerLevel level, List<ItemStack> grid, ItemStack expected) {
        Output out = run(level, grid, expected);
        return out != null && sameResult(out.result(), expected);
    }

    /**
     * Le résultat est-il celui de la fiche ? Objet et composants (une flèche à effet doit garder la potion de la fiche) ;
     * la table de forgeron, elle, garde volontairement l'usure et les enchantements de l'objet amélioré.
     */
    boolean sameResult(ItemStack result, ItemStack expected) {
        return ItemStack.isSameItemSameComponents(result, expected);
    }

    /** Les objets à prendre, case par case (vide pour une case vide), ou null si les coffres ne les ont pas. */
    private @Nullable List<ItemStack> pick(ZoneControllerBlockEntity zone, ServerLevel level, RecipeCard card, ItemStack expected) {
        List<ItemStack> grid = new ArrayList<>(inputs());
        for (int i = 0; i < inputs(); i++) grid.add(card.input(i).copy());
        List<ItemStack> stock = zone.inventory().stacks();
        List<ItemStack> picked = new ArrayList<>(inputs());
        for (int i = 0; i < inputs(); i++) {
            ItemStack ghost = card.input(i);
            if (ghost.isEmpty()) {
                picked.add(ItemStack.EMPTY);
                continue;
            }
            ItemStack chosen = ItemStack.EMPTY;
            // D'abord l'objet de la fiche, puis les autres que la recette accepte ici.
            for (int pass = 0; pass < 2 && chosen.isEmpty(); pass++) {
                for (ItemStack s : stock) {
                    if (s.isEmpty() || ItemStack.isSameItem(s, ghost) != (pass == 0)) continue;
                    if (free(stock, picked, s) < 1) continue;
                    // Autre objet, ou même objet aux composants différents (autre potion...) : la recette doit le confirmer.
                    if (pass == 1 || !ItemStack.isSameItemSameComponents(s, ghost)) {
                        grid.set(i, s.copyWithCount(1));
                        if (!valid(level, grid, expected)) continue;
                    }
                    chosen = s.copyWithCount(1);
                    break;
                }
            }
            if (chosen.isEmpty()) return null;
            grid.set(i, chosen);
            picked.add(chosen);
        }
        return valid(level, grid, expected) ? picked : null;
    }

    /** Combien d'exemplaires identiques à {@code s} restent après ceux déjà choisis. */
    private static int free(List<ItemStack> stock, List<ItemStack> picked, ItemStack s) {
        int total = 0;
        for (ItemStack o : stock) if (ItemStack.isSameItemSameComponents(o, s)) total += o.getCount();
        for (ItemStack p : picked) if (ItemStack.isSameItemSameComponents(p, s)) total--;
        return total;
    }

    @Override
    public @Nullable Step nextStep(ZoneControllerBlockEntity zone, ServerLevel level, RecipeCard card, BlockPos pos) {
        ItemStack expected = preview(level, card);
        if (expected.isEmpty()) return null;
        return pick(zone, level, card, expected) != null ? Step.CRAFT : null;
    }

    @Override
    public void complete(ZoneControllerBlockEntity zone, ServerLevel level, RecipeCard card, BlockPos pos, Step step) {
        if (step != Step.CRAFT) return;
        ItemStack expected = preview(level, card);
        if (expected.isEmpty()) return;
        List<ItemStack> picked = pick(zone, level, card, expected);
        if (picked == null) return;
        // On prend tout d'abord : si un objet a disparu entre-temps, tout est rendu et rien n'est fabriqué.
        List<ItemStack> taken = new ArrayList<>();
        for (ItemStack p : picked) {
            if (p.isEmpty()) continue;
            ItemStack got = zone.inventory().extract(s -> ItemStack.isSameItemSameComponents(s, p), 1);
            if (got.isEmpty()) {
                for (ItemStack t : taken) zone.inventory().output(t);
                return;
            }
            taken.add(got);
        }
        // Les objets réellement pris (leurs composants comptent : outil enchanté, usé...) remplacent les modèles.
        List<ItemStack> grid = new ArrayList<>(picked.size());
        int next = 0;
        for (ItemStack p : picked) grid.add(p.isEmpty() ? ItemStack.EMPTY : taken.get(next++));
        Output out = run(level, grid, expected);
        if (out == null || out.result().isEmpty() || !out.result().is(expected.getItem())) {
            for (ItemStack t : taken) zone.inventory().output(t);
            return;
        }
        zone.inventory().output(out.result());
        for (ItemStack left : out.leftovers()) zone.inventory().output(left);
    }
}
