package com.simpleautomation.zone;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simpleautomation.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Bloc de contrôle d'une zone. Son métier (quarry, scierie…) est fixé par le bloc posé. */
public class ZoneControllerBlock extends BaseEntityBlock {
    public static final EnumProperty<ZoneStatus> STATUS = EnumProperty.create("status", ZoneStatus.class);
    public static final MapCodec<ZoneControllerBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            JobType.CODEC.fieldOf("job").forGetter(ZoneControllerBlock::jobType),
            propertiesCodec()
    ).apply(i, ZoneControllerBlock::new));

    private final JobType jobType;

    public ZoneControllerBlock(JobType jobType, Properties properties) {
        super(properties);
        this.jobType = jobType;
        registerDefaultState(stateDefinition.any().setValue(STATUS, ZoneStatus.INVALID));
    }

    public JobType jobType() {
        return jobType;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STATUS);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ZoneControllerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.ZONE_CONTROLLER.get(),
                level.isClientSide() ? ZoneControllerBlockEntity::clientTick : ZoneControllerBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ZoneControllerBlockEntity be) {
            if (placer instanceof Player player) be.setOwner(player);
            be.revalidate(true);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof ZoneControllerBlockEntity be) {
            be.revalidate(true);
            be.describeTo(sp);
        }
        return InteractionResult.SUCCESS;
    }
}
