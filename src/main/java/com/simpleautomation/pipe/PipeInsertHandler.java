package com.simpleautomation.pipe;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Inventaire « virtuel » d'une pipe : ce qu'on y met part aussitôt vers les destinations du réseau.
 * On ne peut rien en retirer. C'est ce qui permet de brancher une pipe directement sur un contrôleur ou sous un hopper.
 * Le filtre du bras d'entrée trie ce qui entre ; chaque destination n'accepte que ce que son filtre laisse passer.
 */
final class PipeInsertHandler implements ResourceHandler<ItemResource> {
    /** Garde-fou contre une boucle (une pipe d'un autre mod qui renverrait dans la nôtre). */
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private final PipeBlockEntity pipe;
    private final Direction side;
    private final BlockPos from;

    PipeInsertHandler(PipeBlockEntity pipe, Direction side) {
        this.pipe = pipe;
        this.side = side;
        this.from = pipe.getBlockPos().relative(side);
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public ItemResource getResource(int index) {
        return ItemResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        return 0;
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return resource.isEmpty() ? 64 : resource.getMaxStackSize();
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return pipe.accepts(side, resource);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        Level level = pipe.getLevel();
        if (level == null || pipe.isRemoved() || resource.isEmpty() || amount <= 0 || !pipe.accepts(side, resource)) return 0;
        int[] depth = DEPTH.get();
        if (depth[0] > 2) return 0;
        depth[0]++;
        try {
            List<PipeNetwork.Destination> targets = pipe.destinationsExcept(from);
            int n = targets.size();
            if (n == 0) return 0;
            int start = pipe.nextInsertIndex(n);
            int inserted = 0;
            for (int i = 0; i < n && inserted < amount; i++) {
                PipeNetwork.Destination d = targets.get((start + i) % n);
                if (!d.accepts(resource)) continue;
                ResourceHandler<ItemResource> target = level.getCapability(Capabilities.Item.BLOCK, d.pos(), d.face());
                if (target != null) inserted += target.insert(resource, amount - inserted, transaction);
            }
            return inserted;
        } finally {
            depth[0]--;
        }
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return 0;
    }
}
