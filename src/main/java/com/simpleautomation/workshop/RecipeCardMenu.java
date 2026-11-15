package com.simpleautomation.workshop;

import com.simpleautomation.registry.ModDataComponents;
import com.simpleautomation.registry.ModItems;
import com.simpleautomation.registry.ModMenus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Interface d'une fiche de recette : des cases fantômes (rien n'est jamais pris ni consommé), une par entrée de la station,
 * et la case du résultat, que le serveur calcule et synchronise comme n'importe quelle case.
 * <ul>
 * <li>clic avec un objet : la case le retient (un seul) ; clic main vide ou Maj-clic : la case est effacée ;</li>
 * <li>tailleur de pierre : un clic sur le résultat passe au suivant (clic droit : au précédent), ou retient l'objet en main s'il est possible ;</li>
 * <li>glisser un objet depuis JEI sur une case : même effet qu'un clic avec l'objet (voir {@link com.simpleautomation.compat.RecipeGhostPayload}).</li>
 * </ul>
 * Tout se décide sur le serveur, qui réécrit la fiche tenue en main à chaque changement.
 */
public class RecipeCardMenu extends AbstractContainerMenu {
    private final Station station;
    private final InteractionHand hand;
    /** Case de l'inventaire qui tient la fiche : on ne la laisse pas bouger tant que l'interface est ouverte. */
    private final int lockedSlot;
    /** Les entrées puis le résultat. */
    private final SimpleContainer ghost;
    /** Tailleur de pierre : rang du résultat parmi les choix (à partir de 0) et nombre de choix, pour l'affichage. */
    private final DataSlot choiceIndex = DataSlot.standalone();
    private final DataSlot choiceCount = DataSlot.standalone();

    /** Côté client : la main et la station arrivent dans le paquet d'ouverture. */
    public RecipeCardMenu(int id, Inventory inventory, @Nullable RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf != null ? buf.readEnum(InteractionHand.class) : InteractionHand.MAIN_HAND,
                buf != null ? stationOrDefault(buf.readUtf()) : Stations.CRAFTING);
    }

    public RecipeCardMenu(int id, Inventory inventory, InteractionHand hand) {
        this(id, inventory, hand, stationOf(inventory.player.getItemInHand(hand)));
    }

    private RecipeCardMenu(int id, Inventory inventory, InteractionHand hand, Station station) {
        super(ModMenus.RECIPE_CARD.get(), id);
        this.station = station;
        this.hand = hand;
        this.lockedSlot = hand == InteractionHand.MAIN_HAND ? inventory.getSelectedSlot() : Inventory.SLOT_OFFHAND;
        this.ghost = new SimpleContainer(station.inputs() + 1);
        int[][] layout = station.layout();
        for (int i = 0; i <= station.inputs(); i++) addSlot(new GhostSlot(ghost, i, layout[i][0], layout[i][1]));
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlot(choiceIndex);
        addDataSlot(choiceCount);

        RecipeCard card = inventory.player.getItemInHand(hand).get(ModDataComponents.RECIPE_CARD.get());
        if (card != null && card.station() == station) {
            for (int i = 0; i < station.inputs(); i++) ghost.setItem(i, card.input(i).copy());
            ghost.setItem(station.inputs(), card.result().copy());
            if (inventory.player.level() instanceof ServerLevel level) refresh(level, null);
        }
    }

    private static Station stationOrDefault(String id) {
        Station s = Stations.byId(id);
        return s != null ? s : Stations.CRAFTING;
    }

    private static Station stationOf(ItemStack card) {
        RecipeCard c = card.get(ModDataComponents.RECIPE_CARD.get());
        return c != null ? c.station() : Stations.CRAFTING;
    }

    public Station station() {
        return station;
    }

    /** Position (rang) du résultat choisi et nombre de choix : 0 et 0 hors tailleur de pierre. */
    public int choiceIndex() {
        return choiceIndex.get();
    }

    public int choiceCount() {
        return choiceCount.get();
    }

    public static boolean isGhost(Slot slot) {
        return slot instanceof GhostSlot;
    }

    /** Case du résultat : on peut la cliquer seulement si la station laisse choisir. */
    public boolean isResultSlot(int slotId) {
        return slotId == station.inputs();
    }

    /** Une case où l'on peut déposer un objet (pour JEI) : une entrée, ou le résultat d'une station à choix. */
    public boolean acceptsDrop(int slotId) {
        return slotId >= 0 && (slotId < station.inputs() || (isResultSlot(slotId) && station.hasChoices()));
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId <= station.inputs()) {
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
        if (!(player.level() instanceof ServerLevel level)) return;
        ItemStack carried = getCarried();
        if (isResultSlot(index)) {
            if (!station.hasChoices() || clickType != ClickType.PICKUP) return;
            if (!carried.isEmpty()) {
                refresh(level, carried);
                save(player);
            } else {
                cycle(level, button == 1 ? -1 : 1, player);
            }
            return;
        }
        if (clickType == ClickType.PICKUP && !carried.isEmpty()) {
            ghost.setItem(index, RecipeCard.ghostOf(carried));
        } else if (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE) {
            ghost.setItem(index, ItemStack.EMPTY);
        } else {
            return;
        }
        if (station.hasChoices()) ghost.setItem(station.inputs(), ItemStack.EMPTY);
        refresh(level, null);
        save(player);
    }

    /** Résultat suivant (ou précédent) du tailleur de pierre. */
    private void cycle(ServerLevel level, int step, Player player) {
        List<ItemStack> choices = station.choices(level, current());
        if (choices.isEmpty()) return;
        int next = Math.floorMod(choiceIndex.get() + step, choices.size());
        ghost.setItem(station.inputs(), choices.get(next).copy());
        refresh(level, null);
        save(player);
    }

    /** La fiche telle que les cases la montrent. */
    private RecipeCard current() {
        List<ItemStack> inputs = new ArrayList<>(station.inputs());
        for (int i = 0; i < station.inputs(); i++) inputs.add(ghost.getItem(i).copy());
        return new RecipeCard(station, inputs, ghost.getItem(station.inputs()).copy());
    }

    /**
     * Recalcule le résultat annoncé (et, au tailleur de pierre, le rang du choix). {@code pick} : un objet que le joueur
     * veut comme résultat (tailleur de pierre), retenu seulement s'il est possible.
     */
    private void refresh(ServerLevel level, @Nullable ItemStack pick) {
        RecipeCard card = current();
        if (station.hasChoices()) {
            List<ItemStack> choices = station.choices(level, card);
            ItemStack chosen = card.result();
            if (pick != null) {
                for (ItemStack c : choices) {
                    if (ItemStack.isSameItem(c, pick)) {
                        chosen = c;
                        break;
                    }
                }
            }
            int index = 0;
            for (int i = 0; i < choices.size(); i++) {
                if (!chosen.isEmpty() && ItemStack.isSameItem(choices.get(i), chosen)) {
                    index = i;
                    break;
                }
            }
            choiceCount.set(choices.size());
            choiceIndex.set(index);
            ghost.setItem(station.inputs(), choices.isEmpty() ? ItemStack.EMPTY : choices.get(index).copy());
        } else {
            ghost.setItem(station.inputs(), station.preview(level, card));
        }
    }

    /** Réécrit la fiche tenue en main (serveur seulement). */
    private void save(Player player) {
        if (player.level().isClientSide()) return;
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(ModItems.RECIPE_CARD.get())) return;
        held.set(ModDataComponents.RECIPE_CARD.get(), current());
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
        return player.getItemInHand(hand).is(ModItems.RECIPE_CARD.get());
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
