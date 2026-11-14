package com.simpleautomation.pipe;

import com.mojang.serialization.MapCodec;
import com.simpleautomation.pipe.filter.FilterItem;
import com.simpleautomation.registry.ModBlockEntities;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jspecify.annotations.Nullable;

/**
 * L'unique pipe du mod. Elle se relie seule aux pipes voisines et aux conteneurs ; la clé sépare deux pipes
 * ou change un côté conteneur entre Insertion, Extraction et Désactivé.
 * Clic droit avec un filtre sur un bras côté conteneur : le filtre s'y pose ; clic droit main vide sur le bras : on le récupère
 * (sans avoir à s'accroupir : accroupi avec un bouclier ou une torche dans l'autre main, la vanilla n'appelle jamais le bloc).
 */
public class PipeBlock extends BaseEntityBlock {
    public static final MapCodec<PipeBlock> CODEC = simpleCodec(PipeBlock::new);
    public static final Map<Direction, EnumProperty<PipeSide>> PROPERTIES = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.values()) PROPERTIES.put(d, EnumProperty.create(d.getSerializedName(), PipeSide.class));
    }

    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);

    static {
        ARMS.put(Direction.NORTH, Block.box(5, 5, 0, 11, 11, 5));
        ARMS.put(Direction.SOUTH, Block.box(5, 5, 11, 11, 11, 16));
        ARMS.put(Direction.WEST, Block.box(0, 5, 5, 5, 11, 11));
        ARMS.put(Direction.EAST, Block.box(11, 5, 5, 16, 11, 11));
        ARMS.put(Direction.DOWN, Block.box(5, 0, 5, 11, 5, 11));
        ARMS.put(Direction.UP, Block.box(5, 11, 5, 11, 16, 11));
    }

    private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

    public PipeBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (EnumProperty<PipeSide> p : PROPERTIES.values()) state = state.setValue(p, PipeSide.NONE);
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        PROPERTIES.values().forEach(builder::add);
    }

    public static PipeSide side(BlockState state, Direction d) {
        return state.getValue(PROPERTIES.get(d));
    }

    // ---------------------------------------------------------------- connexions

    /** Calcule ce qu'il y a d'un côté, en gardant le mode Insertion/Extraction déjà choisi. */
    static PipeSide computeSide(LevelReader level, BlockPos pos, Direction d, PipeSide current, int disabledMask) {
        if ((disabledMask >> d.ordinal() & 1) == 1) return PipeSide.NONE;
        BlockPos n = pos.relative(d);
        BlockState neighbor = level.getBlockState(n);
        if (neighbor.getBlock() instanceof PipeBlock) {
            boolean refused = level.getBlockEntity(n) instanceof PipeBlockEntity other && other.isDisabled(d.getOpposite());
            return refused ? PipeSide.NONE : PipeSide.PIPE;
        }
        if (level instanceof Level l && l.getCapability(Capabilities.Item.BLOCK, n, d.getOpposite()) != null) {
            return current == PipeSide.EXTRACT ? PipeSide.EXTRACT : PipeSide.INSERT;
        }
        return PipeSide.NONE;
    }

    /** Recalcule les six côtés d'une pipe déjà posée. */
    public static BlockState recompute(LevelReader level, BlockPos pos, BlockState state) {
        int mask = level.getBlockEntity(pos) instanceof PipeBlockEntity be ? be.disabledMask() : 0;
        for (Direction d : Direction.values()) {
            EnumProperty<PipeSide> p = PROPERTIES.get(d);
            state = state.setValue(p, computeSide(level, pos, d, state.getValue(p), mask));
        }
        return state;
    }

    /** Recalcule et applique (utilisé par la clé et les tests). */
    public static void refresh(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PipeBlock)) return;
        BlockState updated = recompute(level, pos, state);
        if (updated != state) level.setBlock(pos, updated, Block.UPDATE_ALL);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return recompute(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        int mask = level.getBlockEntity(pos) instanceof PipeBlockEntity be ? be.disabledMask() : 0;
        EnumProperty<PipeSide> p = PROPERTIES.get(direction);
        return state.setValue(p, computeSide(level, pos, direction, state.getValue(p), mask));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        PipeNetwork.invalidate(level);
        level.invalidateCapabilities(pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        PipeNetwork.invalidate(level);
    }

    /** Quel bras a été visé : le cœur renvoie la face cliquée, un bras renvoie sa direction. */
    public static Direction sideFromHit(BlockPos pos, Vec3 hit, Direction clickedFace) {
        double x = hit.x - pos.getX() - 0.5;
        double y = hit.y - pos.getY() - 0.5;
        double z = hit.z - pos.getZ() - 0.5;
        double ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        if (Math.max(ax, Math.max(ay, az)) < 0.19) return clickedFace;
        if (ax >= ay && ax >= az) return x > 0 ? Direction.EAST : Direction.WEST;
        if (ay >= az) return y > 0 ? Direction.UP : Direction.DOWN;
        return z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    // ---------------------------------------------------------------- filtres

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (!(stack.getItem() instanceof FilterItem)) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        Direction side = sideFromHit(pos, hit.getLocation(), hit.getDirection());
        if (!side(state, side).isContainer()) {
            if (!level.isClientSide()) player.displayClientMessage(Component.translatable("message.simple_automation.filter.no_container"), true);
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof PipeBlockEntity pipe)) return InteractionResult.PASS;
        ItemStack old = pipe.setFilter(side, stack);
        stack.consume(1, player);
        giveBack(player, old);
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.7F, 1.2F);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // useWithoutItem est aussi appelé main pleine (un objet sans action sur la pipe) : on ne retire qu'à main vide.
        if (!player.getMainHandItem().isEmpty() || !(level.getBlockEntity(pos) instanceof PipeBlockEntity pipe)) return InteractionResult.PASS;
        Direction side = sideFromHit(pos, hit.getLocation(), hit.getDirection());
        if (pipe.filterStack(side).isEmpty()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        giveBack(player, pipe.setFilter(side, ItemStack.EMPTY));
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.7F, 1.2F);
        return InteractionResult.SUCCESS;
    }

    private static void giveBack(Player player, ItemStack stack) {
        if (!stack.isEmpty()) player.getInventory().placeItemBackInInventory(stack);
    }

    // ---------------------------------------------------------------- forme et block entity

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.computeIfAbsent(state, s -> {
            VoxelShape shape = CORE;
            for (Direction d : Direction.values()) {
                if (side(s, d) != PipeSide.NONE) shape = Shapes.or(shape, ARMS.get(d));
            }
            return shape;
        });
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PipeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        for (Direction d : Direction.values()) {
            if (side(state, d) == PipeSide.EXTRACT) return createTickerHelper(type, ModBlockEntities.PIPE.get(), PipeBlockEntity::serverTick);
        }
        return null;
    }
}
