package com.simpleautomation.compat;

import com.simpleautomation.SimpleAutomation;
import com.simpleautomation.pipe.filter.FilterMenu;
import com.simpleautomation.pipe.filter.ItemFilter;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Un objet glissé depuis JEI sur une case du filtre ouvert. Le joueur n'a pas cet objet : le serveur le met un instant
 * « en main » dans le menu, rejoue le clic normal sur la case fantôme (qui en garde une copie), puis remet ce que le joueur tenait.
 * Rien n'est donné ni pris : une case fantôme ne se vide jamais dans l'inventaire.
 */
public record FilterGhostPayload(int containerId, int slot, Item item) implements CustomPacketPayload {
    public static final Type<FilterGhostPayload> TYPE = new Type<>(SimpleAutomation.id("filter_ghost"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FilterGhostPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FilterGhostPayload::containerId,
            ByteBufCodecs.VAR_INT, FilterGhostPayload::slot,
            ByteBufCodecs.registry(Registries.ITEM), FilterGhostPayload::item,
            FilterGhostPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FilterGhostPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) apply(player, payload);
        });
    }

    /** Applique le dépôt sur le filtre ouvert par ce joueur (public pour les gametests). Renvoie faux si le paquet est refusé. */
    public static boolean apply(ServerPlayer player, FilterGhostPayload payload) {
        if (!(player.containerMenu instanceof FilterMenu menu) || menu.containerId != payload.containerId()) return false;
        if (payload.slot() < 0 || payload.slot() >= ItemFilter.SIZE || !FilterMenu.isGhost(menu.getSlot(payload.slot()))) return false;
        if (payload.item() == Items.AIR || !payload.item().isEnabled(player.level().enabledFeatures()) || !menu.stillValid(player)) return false;
        ItemStack carried = menu.getCarried();
        menu.setCarried(new ItemStack(payload.item()));
        menu.clicked(payload.slot(), 0, ClickType.PICKUP, player);
        menu.setCarried(carried);
        return true;
    }
}
