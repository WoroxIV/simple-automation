package com.simpleautomation.zone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Rectangle d'une zone, défini par deux bornes en coins opposés (bornes incluses).
 * {@code markerY} est la hauteur la plus basse des deux bornes : la quarry mine en dessous, la scierie travaille au-dessus.
 */
public record ZoneBounds(BlockPos markerA, BlockPos markerB) {
    public static final Codec<ZoneBounds> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("a").forGetter(ZoneBounds::markerA),
            BlockPos.CODEC.fieldOf("b").forGetter(ZoneBounds::markerB)
    ).apply(i, ZoneBounds::new));

    /** Écart vertical maximal entre les deux bornes, et entre les bornes et le contrôleur. */
    private static final int MAX_VERTICAL_GAP = 8;
    /** Le contrôleur peut être dans le rectangle ou jusqu'à 2 blocs autour. */
    private static final int CONTROLLER_MARGIN = 2;

    public int minX() { return Math.min(markerA.getX(), markerB.getX()); }
    public int maxX() { return Math.max(markerA.getX(), markerB.getX()); }
    public int minZ() { return Math.min(markerA.getZ(), markerB.getZ()); }
    public int maxZ() { return Math.max(markerA.getZ(), markerB.getZ()); }
    public int markerY() { return Math.min(markerA.getY(), markerB.getY()); }
    public int sizeX() { return maxX() - minX() + 1; }
    public int sizeZ() { return maxZ() - minZ() + 1; }

    public boolean containsColumn(int x, int z) {
        return x >= minX() && x <= maxX() && z >= minZ() && z <= maxZ();
    }

    /** Distance horizontale (en colonnes) d'un point au rectangle : 0 dedans, 1 juste à côté… */
    public int columnDistance(int x, int z) {
        int dx = Math.max(Math.max(minX() - x, 0), x - maxX());
        int dz = Math.max(Math.max(minZ() - z, 0), z - maxZ());
        return Math.max(dx, dz);
    }

    public boolean intersects(ZoneBounds o) {
        return minX() <= o.maxX() && maxX() >= o.minX() && minZ() <= o.maxZ() && maxZ() >= o.minZ();
    }

    public enum Problem { NO_MARKER, ONE_MARKER, TOO_LARGE, CONTROLLER_OUTSIDE, OVERLAP }

    public sealed interface Result permits Found, Failed {}
    public record Found(ZoneBounds bounds) implements Result {}
    public record Failed(Problem problem) implements Result {}

    /**
     * Cherche les bornes autour du contrôleur parmi les block entities des chunks déjà chargés (aucun chunk n'est chargé pour ça),
     * et garde la plus petite paire valide qui entoure le contrôleur.
     */
    public static Result find(ServerLevel level, BlockPos controller, int maxSize, ZoneControllerBlockEntity self) {
        int radius = maxSize + CONTROLLER_MARGIN;
        List<BlockPos> markers = new ArrayList<>();
        int minCx = SectionPos.blockToSectionCoord(controller.getX() - radius);
        int maxCx = SectionPos.blockToSectionCoord(controller.getX() + radius);
        int minCz = SectionPos.blockToSectionCoord(controller.getZ() - radius);
        int maxCz = SectionPos.blockToSectionCoord(controller.getZ() + radius);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof BorderMarkerBlockEntity && !be.isRemoved()
                            && Math.abs(be.getBlockPos().getY() - controller.getY()) <= MAX_VERTICAL_GAP) {
                        markers.add(be.getBlockPos());
                    }
                }
            }
        }
        if (markers.isEmpty()) return new Failed(Problem.NO_MARKER);
        if (markers.size() == 1) return new Failed(Problem.ONE_MARKER);

        // Les bornes d'une autre zone ne comptent pas.
        markers.removeIf(m -> ZoneRegistry.markerUsedByOther(level, m, self));
        if (markers.isEmpty()) return new Failed(Problem.NO_MARKER);
        if (markers.size() == 1) return new Failed(Problem.ONE_MARKER);

        // Classement : les deux bornes les plus proches du contrôleur (somme des distances), puis une vraie surface (2×2 au moins).
        // C'est ce qu'un joueur attend quand plusieurs zones sont côte à côte.
        Problem worst = Problem.CONTROLLER_OUTSIDE;
        ZoneBounds best = null;
        long bestScore = Long.MAX_VALUE;
        for (int i = 0; i < markers.size(); i++) {
            for (int j = i + 1; j < markers.size(); j++) {
                BlockPos a = markers.get(i);
                BlockPos b = markers.get(j);
                if (Math.abs(a.getY() - b.getY()) > MAX_VERTICAL_GAP) continue;
                ZoneBounds candidate = new ZoneBounds(a, b);
                int distance = candidate.columnDistance(controller.getX(), controller.getZ());
                if (distance > CONTROLLER_MARGIN) continue;
                if (candidate.sizeX() > maxSize || candidate.sizeZ() > maxSize) {
                    worst = Problem.TOO_LARGE;
                    continue;
                }
                if (ZoneRegistry.overlapsOther(level, candidate, self)) {
                    if (worst != Problem.TOO_LARGE) worst = Problem.OVERLAP;
                    continue;
                }
                double reach = Math.hypot(a.getX() - controller.getX(), a.getZ() - controller.getZ())
                        + Math.hypot(b.getX() - controller.getX(), b.getZ() - controller.getZ());
                boolean flat = candidate.sizeX() < 2 || candidate.sizeZ() < 2;
                long score = Math.round(reach * 100) * 2 + (flat ? 1 : 0);
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return best != null ? new Found(best) : new Failed(worst);
    }
}
