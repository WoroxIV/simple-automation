package com.simpleautomation.compat;

import com.simpleautomation.SimpleAutomation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Paquets des intégrations. Toujours enregistrés, même sans JEI : ils ne touchent à aucune classe d'un autre mod,
 * et client et serveur gardent ainsi la même liste de paquets.
 */
@EventBusSubscriber(modid = SimpleAutomation.MODID)
public final class CompatNetwork {
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(FilterGhostPayload.TYPE, FilterGhostPayload.STREAM_CODEC, FilterGhostPayload::handle);
        event.registrar("1").playToServer(RecipeGhostPayload.TYPE, RecipeGhostPayload.STREAM_CODEC, RecipeGhostPayload::handle);
    }

    private CompatNetwork() {
    }
}
