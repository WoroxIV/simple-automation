package com.simpleautomation.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jspecify.annotations.Nullable;

/**
 * Un faux mod de protection pour les tests : il refuse aux golems (joueurs fictifs) de casser, poser ou toucher un animal
 * dans les zones de test qui le demandent. Les tests tournent en parallèle : chacun ne protège que sa propre arène.
 */
final class TestClaims {
    static final List<AABB> DENY_BREAK = new ArrayList<>();
    static final List<AABB> DENY_PLACE = new ArrayList<>();
    static final List<AABB> DENY_INTERACT = new ArrayList<>();
    /** Dernière pose demandée par un joueur fictif dans chaque arène protégée : le bloc annoncé aux protections. */
    static final Map<AABB, BlockState> PLACED = new HashMap<>();

    static void register() {
        NeoForge.EVENT_BUS.addListener(BlockEvent.BreakEvent.class, e -> {
            if (e.getPlayer() instanceof FakePlayer && inside(DENY_BREAK, e.getPos())) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, e -> {
            AABB box = e.getEntity() instanceof FakePlayer ? find(DENY_PLACE, e.getPos()) : null;
            if (box == null) return;
            PLACED.put(box, e.getPlacedBlock());
            e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.EntityInteract.class, e -> {
            if (e.getEntity() instanceof FakePlayer && inside(DENY_INTERACT, e.getTarget().blockPosition())) e.setCanceled(true);
        });
    }

    private static boolean inside(List<AABB> boxes, BlockPos pos) {
        return find(boxes, pos) != null;
    }

    private static @Nullable AABB find(List<AABB> boxes, BlockPos pos) {
        Vec3 v = Vec3.atCenterOf(pos);
        for (AABB box : boxes) if (box.contains(v)) return box;
        return null;
    }

    private TestClaims() {
    }
}
