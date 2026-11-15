package com.simpleautomation.compat;

import com.simpleautomation.SimpleAutomation;
import com.simpleautomation.workshop.RecipeCardMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Un objet glissé depuis JEI sur une case de la fiche de recette ouverte. Comme pour {@link FilterGhostPayload}, le serveur
 * met l'objet un instant « en main » dans le menu, rejoue le clic normal sur la case fantôme, puis remet ce que le joueur
 * tenait. L'objet part en entier (potions comprises) : la case ne retient que l'objet et sa potion.
 */
public record RecipeGhostPayload(int containerId, int slot, ItemStack stack) implements CustomPacketPayload {
    public static final Type<RecipeGhostPayload> TYPE = new Type<>(SimpleAutomation.id("recipe_ghost"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeGhostPayload> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT, RecipeGhostPayload::containerId,
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT, RecipeGhostPayload::slot,
            ItemStack.OPTIONAL_STREAM_CODEC, RecipeGhostPayload::stack,
            RecipeGhostPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RecipeGhostPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) apply(player, payload);
        });
    }

    /** Applique le dépôt sur la fiche ouverte par ce joueur (public pour les gametests). Renvoie faux si le paquet est refusé. */
    public static boolean apply(ServerPlayer player, RecipeGhostPayload payload) {
        if (!(player.containerMenu instanceof RecipeCardMenu menu) || menu.containerId != payload.containerId()) return false;
        ItemStack stack = payload.stack();
        if (stack.isEmpty() || !stack.getItem().isEnabled(player.level().enabledFeatures())) return false;
        if (!menu.acceptsDrop(payload.slot()) || !menu.stillValid(player)) return false;
        ItemStack carried = menu.getCarried();
        menu.setCarried(stack.copyWithCount(1));
        menu.clicked(payload.slot(), 0, ClickType.PICKUP, player);
        menu.setCarried(carried);
        return true;
    }
}
