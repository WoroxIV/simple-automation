package com.simpleautomation.workshop;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Table de forgeron : modèle, base et ajout (netherite, finitions d'armure). */
public final class SmithingStation extends RecipeStation {
    @Override
    public String id() {
        return "smithing";
    }

    @Override
    public boolean matches(BlockState state) {
        return state.is(Blocks.SMITHING_TABLE);
    }

    @Override
    public int inputs() {
        return 3;
    }

    @Override
    public int[][] layout() {
        return new int[][] {{26, 35}, {62, 35}, {98, 35}, {142, 35}};
    }

    @Override
    public int workTicks(Step step) {
        return 50;
    }

    private static @Nullable RecipeHolder<SmithingRecipe> recipe(ServerLevel level, SmithingRecipeInput input) {
        if (input.isEmpty()) return null;
        return level.recipeAccess().getRecipeFor(RecipeType.SMITHING, input, level).orElse(null);
    }

    @Override
    public ItemStack preview(ServerLevel level, RecipeCard card) {
        SmithingRecipeInput input = new SmithingRecipeInput(card.input(0), card.input(1), card.input(2));
        RecipeHolder<SmithingRecipe> holder = recipe(level, input);
        return holder == null ? ItemStack.EMPTY : holder.value().assemble(input, level.registryAccess());
    }

    @Override
    @Nullable Output run(ServerLevel level, List<ItemStack> grid, ItemStack expected) {
        SmithingRecipeInput input = new SmithingRecipeInput(grid.get(0), grid.get(1), grid.get(2));
        RecipeHolder<SmithingRecipe> holder = recipe(level, input);
        return holder == null ? null : new Output(holder.value().assemble(input, level.registryAccess()), List.of());
    }
}
