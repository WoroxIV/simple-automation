package com.simpleautomation.pipe;

import com.simpleautomation.pipe.filter.ItemFilter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * Réseau de pipes : la liste des destinations (côtés en Insertion) joignables depuis une pipe.
 * Chaque pipe garde sa liste en cache ; le moindre changement de pipe dans le monde incrémente un numéro de version
 * et les caches se recalculent la prochaine fois qu'on s'en sert (poser ou retirer un filtre compte aussi). Aucun scan à chaque tick.
 */
public final class PipeNetwork {
    public static final int MAX_PIPES = 4096;
    private static final Map<Level, int[]> VERSIONS = new WeakHashMap<>();

    /** Une destination : le conteneur, sa face qui touche la pipe, et le filtre posé sur ce bras (null = tout passe). */
    public record Destination(BlockPos pos, Direction face, @Nullable ItemFilter filter) {
        public boolean accepts(ItemResource resource) {
            return filter == null || filter.test(resource);
        }
    }

    public static int version(Level level) {
        return VERSIONS.computeIfAbsent(level, l -> new int[1])[0];
    }

    public static void invalidate(Level level) {
        VERSIONS.computeIfAbsent(level, l -> new int[1])[0]++;
    }

    public static List<Destination> collect(Level level, BlockPos start) {
        List<Destination> result = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof PipeBlock)) continue;
            for (Direction d : Direction.values()) {
                PipeSide side = state.getValue(PipeBlock.PROPERTIES.get(d));
                BlockPos n = pos.relative(d);
                if (side == PipeSide.PIPE) {
                    if (seen.size() < MAX_PIPES && level.isLoaded(n) && seen.add(n)) queue.add(n);
                } else if (side == PipeSide.INSERT) {
                    ItemFilter filter = level.getBlockEntity(pos) instanceof PipeBlockEntity be ? be.filter(d) : null;
                    result.add(new Destination(n, d.getOpposite(), filter));
                }
            }
        }
        return result;
    }

    private PipeNetwork() {
    }
}
