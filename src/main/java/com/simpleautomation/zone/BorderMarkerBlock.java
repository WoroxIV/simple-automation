package com.simpleautomation.zone;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Borne de délimitation : deux bornes en coins opposés définissent le rectangle d'une zone. */
public class BorderMarkerBlock extends BaseEntityBlock {
    public static final MapCodec<BorderMarkerBlock> CODEC = simpleCodec(BorderMarkerBlock::new);
    private static final VoxelShape SHAPE = Block.box(6, 0, 6, 10, 14, 10);

    public BorderMarkerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BorderMarkerBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            ZoneControllerBlockEntity owner = ZoneRegistry.findUsingMarker(server, pos);
            if (owner != null && owner.bounds() != null) {
                ZoneOutline.show(server, sp, owner.bounds());
                sp.displayClientMessage(Component.translatable("message.simple_automation.marker.linked",
                        owner.getBlockPos().getX(), owner.getBlockPos().getY(), owner.getBlockPos().getZ()), true);
            } else {
                sp.displayClientMessage(Component.translatable("message.simple_automation.marker.alone"), true);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
