package com.simpleautomation.zone;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Contour de la zone en particules, envoyé au seul joueur qui a cliqué. */
public final class ZoneOutline {
    private static final DustParticleOptions DUST = new DustParticleOptions(0xE07B3A, 1.5F);

    public static void show(ServerLevel level, ServerPlayer player, ZoneBounds b) {
        double y = b.markerY() + 0.5;
        for (int x = b.minX(); x <= b.maxX() + 1; x++) {
            spawn(level, player, x, y, b.minZ());
            spawn(level, player, x, y, b.maxZ() + 1);
        }
        for (int z = b.minZ(); z <= b.maxZ() + 1; z++) {
            spawn(level, player, b.minX(), y, z);
            spawn(level, player, b.maxX() + 1, y, z);
        }
    }

    private static void spawn(ServerLevel level, ServerPlayer player, double x, double y, double z) {
        level.sendParticles(player, DUST, true, true, x, y, z, 1, 0, 0, 0, 0);
    }

    private ZoneOutline() {
    }
}
