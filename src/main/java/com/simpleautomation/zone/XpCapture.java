package com.simpleautomation.zone;

import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import org.jspecify.annotations.Nullable;

/**
 * Pendant qu'un golem frappe un animal ou un monstre, l'XP de sa mort (calculée par la vanilla, avec ses règles de
 * « tué par un joueur ») va dans la zone au lieu d'apparaître en orbes. La mort et le calcul de l'XP se font pendant
 * le coup ({@code hurtServer} → {@code die} → {@code dropExperience}), sur le fil du serveur : un simple champ suffit.
 */
public final class XpCapture {
    private static @Nullable ZoneControllerBlockEntity current;

    /** Exécute un coup en capturant l'XP d'une éventuelle mort pour cette zone. */
    public static void during(ZoneControllerBlockEntity zone, Runnable hit) {
        ZoneControllerBlockEntity previous = current;
        current = zone;
        try {
            hit.run();
        } finally {
            current = previous;
        }
    }

    /** Branché sur le bus NeoForge. */
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        ZoneControllerBlockEntity zone = current;
        if (zone == null || event.getEntity().level().isClientSide()) return;
        zone.xp().add(event.getDroppedExperience());
        event.setDroppedExperience(0);
    }

    private XpCapture() {
    }
}
