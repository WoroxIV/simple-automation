package com.simpleautomation.zone;

import com.simpleautomation.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ne stocke rien : sa seule raison d'être est que les contrôleurs trouvent les bornes en parcourant
 * les block entities des chunks chargés, au lieu de scanner des milliers de blocs.
 */
public class BorderMarkerBlockEntity extends BlockEntity {
    public BorderMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BORDER_MARKER.get(), pos, state);
    }
}
