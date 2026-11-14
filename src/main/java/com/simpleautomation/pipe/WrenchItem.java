package com.simpleautomation.pipe;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * La clé : clic sur un bras de pipe.
 * <ul>
 * <li>vers une autre pipe : sépare ou relie les deux ;</li>
 * <li>vers un conteneur : Insertion → Extraction → Désactivé → Insertion ;</li>
 * <li>accroupi : casse la pipe d'un coup.</li>
 * </ul>
 */
public class WrenchItem extends Item {
    public WrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PipeBlock)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        Player player = ctx.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            level.destroyBlock(pos, !player.hasInfiniteMaterials(), player);
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof PipeBlockEntity pipe)) return InteractionResult.PASS;

        Direction side = PipeBlock.sideFromHit(pos, ctx.getClickLocation(), ctx.getClickedFace());
        BlockPos neighborPos = pos.relative(side);
        BlockState neighbor = level.getBlockState(neighborPos);
        String message;

        if (neighbor.getBlock() instanceof PipeBlock && level.getBlockEntity(neighborPos) instanceof PipeBlockEntity other) {
            boolean separate = PipeBlock.side(state, side) == PipeSide.PIPE;
            pipe.setDisabled(side, separate);
            other.setDisabled(side.getOpposite(), separate);
            PipeBlock.refresh(level, pos);
            PipeBlock.refresh(level, neighborPos);
            message = separate ? "separated" : "joined";
        } else if (level.getCapability(Capabilities.Item.BLOCK, neighborPos, side.getOpposite()) != null) {
            PipeSide current = PipeBlock.side(state, side);
            if (pipe.isDisabled(side)) {
                pipe.setDisabled(side, false);
                level.setBlock(pos, PipeBlock.recompute(level, pos, state.setValue(PipeBlock.PROPERTIES.get(side), PipeSide.INSERT)), Block.UPDATE_ALL);
                message = "insert";
            } else if (current == PipeSide.INSERT) {
                level.setBlock(pos, state.setValue(PipeBlock.PROPERTIES.get(side), PipeSide.EXTRACT), Block.UPDATE_ALL);
                message = "extract";
            } else {
                pipe.setDisabled(side, true);
                PipeBlock.refresh(level, pos);
                message = "disabled";
            }
        } else {
            return InteractionResult.PASS;
        }
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, 1.4F);
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.simple_automation.wrench." + message).withStyle(ChatFormatting.GOLD), true);
        }
        return InteractionResult.SUCCESS;
    }
}
