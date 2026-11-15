package com.simpleautomation.workshop;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Tailleur de pierre : une entrée, et le résultat choisi parmi les recettes que cette entrée permet. */
public final class StonecutterStation extends RecipeStation {
    @Override
    public String id() {
        return "stonecutter";
    }

    @Override
    public boolean matches(BlockState state) {
        return state.is(Blocks.STONECUTTER);
    }

    @Override
    public int inputs() {
        return 1;
    }

    @Override
    public int[][] layout() {
        return new int[][] {{56, 35}, {116, 35}};
    }

    @Override
    public boolean hasChoices() {
        return true;
    }

    @Override
    public int workTicks(Step step) {
        return 20;
    }

    /** Les résultats que cet objet permet, dans l'ordre des recettes. */
    private static List<ItemStack> results(ServerLevel level, ItemStack input) {
        List<ItemStack> list = new ArrayList<>();
        if (input.isEmpty()) return list;
        SingleRecipeInput single = new SingleRecipeInput(input);
        for (RecipeHolder<StonecutterRecipe> h : level.recipeAccess().recipeMap().byType(RecipeType.STONECUTTING)) {
            if (!h.value().input().test(input)) continue;
            ItemStack r = h.value().assemble(single, level.registryAccess());
            if (!r.isEmpty()) list.add(r);
        }
        return list;
    }

    @Override
    public List<ItemStack> choices(ServerLevel level, RecipeCard card) {
        return results(level, card.input(0));
    }

    /** Le résultat choisi s'il est encore possible, sinon le premier. */
    @Override
    public ItemStack preview(ServerLevel level, RecipeCard card) {
        List<ItemStack> choices = choices(level, card);
        for (ItemStack c : choices) if (!card.result().isEmpty() && ItemStack.isSameItem(c, card.result())) return c;
        return choices.isEmpty() ? ItemStack.EMPTY : choices.get(0);
    }

    @Override
    @Nullable Output run(ServerLevel level, List<ItemStack> grid, ItemStack expected) {
        for (ItemStack r : results(level, grid.get(0))) {
            if (r.is(expected.getItem())) return new Output(r, List.of());
        }
        return null;
    }
}
