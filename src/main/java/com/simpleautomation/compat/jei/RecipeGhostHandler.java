package com.simpleautomation.compat.jei;

import com.simpleautomation.client.RecipeCardScreen;
import com.simpleautomation.compat.RecipeGhostPayload;
import com.simpleautomation.workshop.RecipeCardMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Glisser un objet de JEI sur une case de la fiche de recette : la case le retient, comme un clic avec l'objet en main. */
final class RecipeGhostHandler implements IGhostIngredientHandler<RecipeCardScreen> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(RecipeCardScreen gui, ITypedIngredient<I> ingredient, boolean doStart) {
        Optional<ItemStack> stack = ingredient.getItemStack();
        if (stack.isEmpty() || stack.get().isEmpty()) return List.of();
        List<Target<I>> targets = new ArrayList<>();
        for (Slot slot : gui.getMenu().slots) {
            if (!RecipeCardMenu.isGhost(slot) || !gui.getMenu().acceptsDrop(slot.index)) continue;
            Rect2i area = new Rect2i(gui.getGuiLeft() + slot.x, gui.getGuiTop() + slot.y, 16, 16);
            int index = slot.index;
            targets.add(new Target<>() {
                @Override
                public Rect2i getArea() {
                    return area;
                }

                @Override
                public void accept(I dropped) {
                    send(gui, index, stack.get());
                }
            });
        }
        return targets;
    }

    /** Maj-clic sur un objet de JEI : il va dans la première entrée vide. */
    @Override
    public <I> boolean quickMove(RecipeCardScreen gui, ITypedIngredient<I> ingredient) {
        Optional<ItemStack> stack = ingredient.getItemStack();
        if (stack.isEmpty() || stack.get().isEmpty()) return false;
        for (Slot slot : gui.getMenu().slots) {
            if (RecipeCardMenu.isGhost(slot) && slot.index < gui.getMenu().station().inputs() && !slot.hasItem()) {
                send(gui, slot.index, stack.get());
                return true;
            }
        }
        return false;
    }

    @Override
    public void onComplete() {
    }

    private static void send(RecipeCardScreen gui, int slot, ItemStack stack) {
        ClientPacketDistributor.sendToServer(new RecipeGhostPayload(gui.getMenu().containerId, slot, stack));
    }
}
