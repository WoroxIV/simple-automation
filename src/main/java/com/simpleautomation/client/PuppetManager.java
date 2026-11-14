package com.simpleautomation.client;

import com.simpleautomation.zone.PuppetBridge;
import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Crée, met à jour et retire les marionnettes de chaque contrôleur visible. Elles vivent tant que le contrôleur
 * est chargé côté client ; au-delà de la distance d'affichage des entités, le jeu ne les dessine plus.
 */
public final class PuppetManager implements PuppetBridge {
    private static final Map<BlockPos, Map<UUID, PuppetGolem>> PUPPETS = new HashMap<>();
    /** Identifiants négatifs : jamais utilisés par le serveur pour de vraies entités. */
    private static int nextId = -1_000_000;

    @Override
    public void tick(ZoneControllerBlockEntity controller) {
        if (!(controller.getLevel() instanceof ClientLevel level)) return;
        Map<UUID, PuppetGolem> puppets = PUPPETS.computeIfAbsent(controller.getBlockPos().immutable(), p -> new HashMap<>());
        Set<UUID> present = new HashSet<>();
        for (Worker w : controller.workers()) {
            present.add(w.id);
            PuppetGolem puppet = puppets.get(w.id);
            if (puppet == null || puppet.isRemoved() || puppet.level() != level) {
                puppet = new PuppetGolem(level, controller.getBlockPos().immutable(), w.id, Vec3.atBottomCenterOf(w.stand));
                puppet.setId(nextId--);
                level.addEntity(puppet);
                puppets.put(w.id, puppet);
            }
            puppet.apply(w);
        }
        for (Iterator<Map.Entry<UUID, PuppetGolem>> it = puppets.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, PuppetGolem> e = it.next();
            if (!present.contains(e.getKey())) {
                e.getValue().discard();
                it.remove();
            }
        }
    }

    @Override
    public void remove(ZoneControllerBlockEntity controller) {
        Map<UUID, PuppetGolem> puppets = PUPPETS.remove(controller.getBlockPos());
        if (puppets != null) puppets.values().forEach(PuppetGolem::discard);
    }

    /** À la déconnexion, le monde client disparaît avec ses entités : on oublie tout. */
    public static void clear() {
        PUPPETS.clear();
    }
}
