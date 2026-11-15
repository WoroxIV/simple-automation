package com.simpleautomation.job;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/**
 * Travail confié à un golem : le bloc visé, l'endroit où il se tient pour le faire,
 * des blocs en plus pour les métiers qui en traitent plusieurs d'un coup (toutes les bûches d'un arbre),
 * et les animaux concernés pour l'étable.
 */
public record Task(BlockPos target, BlockPos stand, List<BlockPos> extra, List<UUID> entities) {
    public Task(BlockPos target, BlockPos stand) {
        this(target, stand, List.of(), List.of());
    }

    public Task(BlockPos target, BlockPos stand, List<BlockPos> extra) {
        this(target, stand, extra, List.of());
    }
}
