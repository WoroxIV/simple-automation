package com.simpleautomation.compat.jade;

import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Ce que Jade affiche pour un contrôleur. Le serveur l'envoie ; sans Jade sur le serveur, le client le recalcule
 * à partir des marionnettes qu'il connaît (sans le tampon, les coffres ni l'avancement, qu'il ne reçoit pas).
 *
 * @param buffered items en attente dans le contrôleur, -1 si inconnu
 */
record ControllerData(int workers, int working, int idle, int noTool, int frozen, int buffered, boolean hasChest,
                      Optional<Component> progress) {
    static final StreamCodec<RegistryFriendlyByteBuf, ControllerData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ControllerData::workers,
            ByteBufCodecs.VAR_INT, ControllerData::working,
            ByteBufCodecs.VAR_INT, ControllerData::idle,
            ByteBufCodecs.VAR_INT, ControllerData::noTool,
            ByteBufCodecs.VAR_INT, ControllerData::frozen,
            ByteBufCodecs.VAR_INT, ControllerData::buffered,
            ByteBufCodecs.BOOL, ControllerData::hasChest,
            ComponentSerialization.OPTIONAL_STREAM_CODEC, ControllerData::progress,
            ControllerData::new);

    /** Côté serveur : tout est connu. */
    static ControllerData server(ZoneControllerBlockEntity be) {
        int buffered = be.inventory().buffer().stream().mapToInt(ItemStack::getCount).sum();
        return count(be, buffered, be.inventory().hasContainer(), Optional.ofNullable(be.job().progress(be)));
    }

    /** Côté client, faute de mieux : seulement les golems. */
    static ControllerData client(ZoneControllerBlockEntity be) {
        return count(be, -1, true, Optional.empty());
    }

    private static ControllerData count(ZoneControllerBlockEntity be, int buffered, boolean hasChest, Optional<Component> progress) {
        int working = 0, idle = 0, noTool = 0, frozen = 0;
        for (Worker w : be.workers()) {
            switch (w.status) {
                case MOVING, WORKING -> working++;
                case IDLE -> idle++;
                case NO_TOOL -> noTool++;
                case FROZEN -> frozen++;
            }
        }
        return new ControllerData(be.workers().size(), working, idle, noTool, frozen, buffered, hasChest, progress);
    }
}
