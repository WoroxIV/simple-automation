package com.simpleautomation.compat.jei;

import com.simpleautomation.client.FilterScreen;
import com.simpleautomation.compat.FilterGhostPayload;
import com.simpleautomation.pipe.filter.FilterMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Glisser un objet de JEI sur une case du filtre : la case le retient, comme un clic avec l'objet en main. */
final class FilterGhostHandler implements IGhostIngredientHandler<FilterScreen> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(FilterScreen gui, ITypedIngredient<I> ingredient, boolean doStart) {
        Optional<ItemStack> stack = ingredient.getItemStack();
        if (stack.isEmpty() || stack.get().isEmpty()) return List.of();
        List<Target<I>> targets = new ArrayList<>(9);
        for (Slot slot : gui.getMenu().slots) {
            if (!FilterMenu.isGhost(slot)) continue;
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

    /** Maj-clic sur un objet de JEI : il va dans la première case vide. */
    @Override
    public <I> boolean quickMove(FilterScreen gui, ITypedIngredient<I> ingredient) {
        Optional<ItemStack> stack = ingredient.getItemStack();
        if (stack.isEmpty() || stack.get().isEmpty()) return false;
        for (Slot slot : gui.getMenu().slots) {
            if (FilterMenu.isGhost(slot) && !slot.hasItem()) {
                send(gui, slot.index, stack.get());
                return true;
            }
        }
        return false;
    }

    @Override
    public void onComplete() {
    }

    private static void send(FilterScreen gui, int slot, ItemStack stack) {
        ClientPacketDistributor.sendToServer(new FilterGhostPayload(gui.getMenu().containerId, slot, stack.getItem()));
    }
}
