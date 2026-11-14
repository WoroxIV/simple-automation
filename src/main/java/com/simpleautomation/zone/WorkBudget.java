package com.simpleautomation.zone;

import com.simpleautomation.SAConfig;

/**
 * Plafond de blocs cassés par tick sur tout le serveur. Au-delà, les golems attendent le tick suivant :
 * ajouter des centaines de golems ralentit le travail au lieu de faire laguer le serveur.
 */
public final class WorkBudget {
    private static int remaining;

    /** Appelé au début de chaque tick serveur. */
    public static void reset() {
        remaining = SAConfig.GLOBAL_BLOCKS_PER_TICK.getAsInt();
    }

    public static boolean tryConsume(int amount) {
        if (remaining < amount && remaining < SAConfig.GLOBAL_BLOCKS_PER_TICK.getAsInt()) return false;
        remaining -= amount;
        return true;
    }

    private WorkBudget() {
    }
}
