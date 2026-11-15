package com.simpleautomation.workshop;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import org.jspecify.annotations.Nullable;

/** Établi : grille 3 × 3, recettes de fabrication (formées ou sans forme). */
public final class CraftingStation extends RecipeStation {
    @Override
    public String id() {
        return "crafting";
    }

    @Override
    public boolean matches(BlockState state) {
        return state.is(Tags.Blocks.PLAYER_WORKSTATIONS_CRAFTING_TABLES);
    }

    @Override
    public int inputs() {
        return 9;
    }

    @Override
    public int[][] layout() {
        int[][] l = new int[10][];
        for (int i = 0; i < 9; i++) l[i] = new int[] {30 + (i % 3) * 18, 17 + (i / 3) * 18};
        l[9] = new int[] {124, 35};
        return l;
    }

    @Override
    public int workTicks(Step step) {
        return 30;
    }

    private static @Nullable RecipeHolder<CraftingRecipe> recipe(ServerLevel level, CraftingInput input) {
        if (input.isEmpty()) return null;
        return level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
    }

    @Override
    public ItemStack preview(ServerLevel level, RecipeCard card) {
        List<ItemStack> grid = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) grid.add(card.input(i));
        CraftingInput input = CraftingInput.of(3, 3, grid);
        RecipeHolder<CraftingRecipe> holder = recipe(level, input);
        return holder == null ? ItemStack.EMPTY : holder.value().assemble(input, level.registryAccess());
    }

    @Override
    @Nullable Output run(ServerLevel level, List<ItemStack> grid, ItemStack expected) {
        CraftingInput input = CraftingInput.of(3, 3, grid);
        RecipeHolder<CraftingRecipe> holder = recipe(level, input);
        if (holder == null) return null;
        List<ItemStack> leftovers = new ArrayList<>();
        for (ItemStack s : holder.value().getRemainingItems(input)) if (!s.isEmpty()) leftovers.add(s);
        return new Output(holder.value().assemble(input, level.registryAccess()), leftovers);
    }
}
