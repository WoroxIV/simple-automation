package com.simpleautomation.job;

import com.simpleautomation.zone.ZoneBounds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Repère un arbre entier à partir de sa souche : toute bûche du tag minecraft:logs (bois moddés compris, tiges des
 * champignons géants du Nether aussi), avec un feuillage naturel (feuilles, ou blocs de verrues et champilampes).
 */
public final class TreeScanner {
    public static final int MAX_LOGS = 256;
    /** Les branches peuvent dépasser un peu des bornes. */
    static final int OVERHANG = 3;
    /** Moins de feuilles naturelles que ça : ce n'est pas un arbre (une maison en rondins, par exemple). */
    private static final int MIN_LEAVES = 3;

    /**
     * Toutes les bûches reliées à la souche (diagonales comprises, pour les acacias), la souche en premier.
     * Liste vide si ce n'est pas un vrai arbre.
     */
    public static List<BlockPos> collect(ServerLevel level, BlockPos base, ZoneBounds bounds) {
        List<BlockPos> logs = new ArrayList<>();
        if (!level.getBlockState(base).is(BlockTags.LOGS)) return logs;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(base);
        seen.add(base);
        int leaves = 0;
        while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
            BlockPos pos = queue.poll();
            logs.add(pos);
            if (leaves < MIN_LEAVES) {
                for (Direction d : Direction.values()) {
                    if (isFoliage(level.getBlockState(pos.relative(d)))) leaves++;
                }
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos next = pos.offset(dx, dy, dz);
                        if (next.getY() < base.getY() || !withinOverhang(bounds, next) || !seen.add(next)) continue;
                        if (level.isLoaded(next) && level.getBlockState(next).is(BlockTags.LOGS)) queue.add(next);
                    }
                }
            }
        }
        if (leaves < MIN_LEAVES) logs.clear();
        return logs;
    }

    /**
     * Feuillage naturel : des feuilles qui ne sont pas posées par un joueur (y compris les feuilles moddées du tag
     * minecraft:leaves), ou le chapeau d'un champignon géant (blocs de verrues, champilampes).
     */
    static boolean isFoliage(BlockState state) {
        if (state.is(BlockTags.WART_BLOCKS) || state.is(Blocks.SHROOMLIGHT)) return true;
        if (!(state.getBlock() instanceof LeavesBlock) && !state.is(BlockTags.LEAVES)) return false;
        return !state.hasProperty(LeavesBlock.PERSISTENT) || !state.getValue(LeavesBlock.PERSISTENT);
    }

    /**
     * Le bloc {@code state} (en {@code pos}) peut-il porter la souche d'un arbre ? Un sol solide, ou les racines
     * de palétuvier et la boue des mangroves. Jamais une bûche ni des feuilles (une branche posée sur le feuillage n'est pas une souche).
     */
    public static boolean isGround(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.WART_BLOCKS)) return false;
        return state.is(Blocks.MANGROVE_ROOTS) || state.is(Blocks.MUDDY_MANGROVE_ROOTS) || state.is(Blocks.MUD)
                || state.isFaceSturdy(level, pos, Direction.UP);
    }

    private static boolean withinOverhang(ZoneBounds b, BlockPos p) {
        return p.getX() >= b.minX() - OVERHANG && p.getX() <= b.maxX() + OVERHANG
                && p.getZ() >= b.minZ() - OVERHANG && p.getZ() <= b.maxZ() + OVERHANG;
    }

    private TreeScanner() {
    }
}
