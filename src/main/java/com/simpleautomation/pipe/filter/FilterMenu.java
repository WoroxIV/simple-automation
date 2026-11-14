package com.simpleautomation.pipe.filter;

import com.simpleautomation.registry.ModDataComponents;
import com.simpleautomation.registry.ModMenus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Interface du filtre : 9 cases fantômes (rien n'est jamais pris ni consommé) et le bouton liste blanche / noire.
 * <ul>
 * <li>clic avec un objet : la case le retient ;</li>
 * <li>clic main vide : la case est effacée ;</li>
 * <li>clic droit main vide sur une case remplie : objet exact → chaque tag de l'objet → objet exact.</li>
 * </ul>
 * Tout se décide sur le serveur, qui réécrit le filtre tenu en main à chaque changement.
 */
public class FilterMenu extends AbstractContainerMenu {
    public static final int BUTTON_MODE = 0;

    private final InteractionHand hand;
    /** Case de l'inventaire qui tient le filtre : on ne la laisse pas bouger tant que l'interface est ouverte. */
    private final int lockedSlot;
    private final SimpleContainer ghost = new SimpleContainer(ItemFilter.SIZE);
    /** 0 = objet exact, n = n-ième tag de {@link ItemFilter#sortedTags}. */
    private final DataSlot[] tagIndex = new DataSlot[ItemFilter.SIZE];
    private final DataSlot blacklist = DataSlot.standalone();

    /** Côté client : la main arrive dans le paquet d'ouverture. */
    public FilterMenu(int id, Inventory inventory, @Nullable RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf != null ? buf.readEnum(InteractionHand.class) : InteractionHand.MAIN_HAND);
    }

    public FilterMenu(int id, Inventory inventory, InteractionHand hand) {
        super(ModMenus.FILTER.get(), id);
        this.hand = hand;
        this.lockedSlot = hand == InteractionHand.MAIN_HAND ? inventory.getSelectedSlot() : Inventory.SLOT_OFFHAND;
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 3; col++) addSlot(new GhostSlot(ghost, col + row * 3, 62 + col * 18, 17 + row * 18));
        addStandardInventorySlots(inventory, 8, 84);
        for (int i = 0; i < ItemFilter.SIZE; i++) tagIndex[i] = addDataSlot(DataSlot.standalone());
        addDataSlot(blacklist);

        ItemFilter filter = inventory.player.getItemInHand(hand).getOrDefault(ModDataComponents.FILTER.get(), ItemFilter.EMPTY);
        for (int i = 0; i < ItemFilter.SIZE; i++) {
            ItemFilter.Entry e = filter.entry(i);
            if (e.isEmpty()) continue;
            ghost.setItem(i, new ItemStack(e.item()));
            tagIndex[i].set(e.tag().map(t -> ItemFilter.sortedTags(e.item()).indexOf(t) + 1).orElse(0));
        }
        blacklist.set(filter.blacklist() ? 1 : 0);
    }

    public boolean isBlacklist() {
        return blacklist.get() == 1;
    }

    /** Tag choisi pour une case (vide = objet exact). */
    public Optional<TagKey<Item>> tagOf(int index) {
        ItemStack stack = ghost.getItem(index);
        int t = tagIndex[index].get();
        if (stack.isEmpty() || t <= 0) return Optional.empty();
        List<TagKey<Item>> tags = ItemFilter.sortedTags(stack.getItem());
        return t <= tags.size() ? Optional.of(tags.get(t - 1)) : Optional.empty();
    }

    public static boolean isGhost(Slot slot) {
        return slot instanceof GhostSlot;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < ItemFilter.SIZE) {
            if (!player.level().isClientSide()) clickGhost(slotId, button, clickType, player);
            return;
        }
        if (slotId >= 0 && slotId < slots.size()) {
            Slot slot = slots.get(slotId);
            if (slot.container == player.getInventory() && slot.getContainerSlot() == lockedSlot) return;
        }
        if (clickType == ClickType.SWAP && button == lockedSlot) return;
        super.clicked(slotId, button, clickType, player);
    }

    private void clickGhost(int index, int button, ClickType clickType, Player player) {
        ItemStack carried = getCarried();
        if (clickType == ClickType.PICKUP && !carried.isEmpty()) {
            ghost.setItem(index, carried.copyWithCount(1));
            tagIndex[index].set(0);
        } else if (clickType == ClickType.PICKUP && button == 1 && !ghost.getItem(index).isEmpty()) {
            int count = ItemFilter.sortedTags(ghost.getItem(index).getItem()).size();
            int next = tagIndex[index].get() + 1;
            tagIndex[index].set(next > count ? 0 : next);
        } else if (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE) {
            ghost.setItem(index, ItemStack.EMPTY);
            tagIndex[index].set(0);
        } else {
            return;
        }
        save(player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_MODE) return false;
        blacklist.set(isBlacklist() ? 0 : 1);
        save(player);
        return true;
    }

    /** Réécrit le filtre tenu en main (serveur seulement). */
    private void save(Player player) {
        if (player.level().isClientSide()) return;
        ItemStack held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof FilterItem)) return;
        List<ItemFilter.Entry> entries = new ArrayList<>(ItemFilter.SIZE);
        for (int i = 0; i < ItemFilter.SIZE; i++) {
            ItemStack stack = ghost.getItem(i);
            entries.add(stack.isEmpty() ? ItemFilter.Entry.NONE : new ItemFilter.Entry(stack.getItem(), tagOf(i)));
        }
        held.set(ModDataComponents.FILTER.get(), new ItemFilter(entries, isBlacklist()));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !isGhost(slot) && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !isGhost(slot);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(hand).getItem() instanceof FilterItem;
    }

    /** Case qui montre un objet sans jamais le contenir vraiment. */
    private static final class GhostSlot extends Slot {
        GhostSlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
