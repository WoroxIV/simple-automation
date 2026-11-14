package com.simpleautomation.pipe;

import com.simpleautomation.SAConfig;
import com.simpleautomation.pipe.filter.ItemFilter;
import com.simpleautomation.registry.ModBlockEntities;
import com.simpleautomation.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * Garde les côtés désactivés à la clé et les filtres posés sur les bras, et fait le travail des côtés en Extraction :
 * tous les N ticks, une pile passe directement du conteneur à une destination du réseau (chacune son tour).
 * Un filtre filtre tout ce qui passe par son bras : ce qui est aspiré, ce qui entre dans le conteneur, ce qu'il pousse dans la pipe.
 */
public class PipeBlockEntity extends BlockEntity {
    private int disabledMask;
    private final ItemStack[] filters = new ItemStack[6];
    private final int[] roundRobin = new int[6];
    private int insertRoundRobin;
    private int cooldown;
    private List<PipeNetwork.Destination> destinations = List.of();
    private int cacheVersion = -1;

    public PipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PIPE.get(), pos, state);
        Arrays.fill(filters, ItemStack.EMPTY);
    }

    public int disabledMask() {
        return disabledMask;
    }

    public boolean isDisabled(Direction d) {
        return (disabledMask >> d.ordinal() & 1) == 1;
    }

    public void setDisabled(Direction d, boolean disabled) {
        disabledMask = disabled ? disabledMask | 1 << d.ordinal() : disabledMask & ~(1 << d.ordinal());
        setChanged();
    }

    // ---------------------------------------------------------------- filtres

    /** L'objet filtre posé sur un bras (vide s'il n'y en a pas). */
    public ItemStack filterStack(Direction d) {
        return filters[d.ordinal()];
    }

    public boolean hasFilters() {
        for (ItemStack stack : filters) if (!stack.isEmpty()) return true;
        return false;
    }

    public @Nullable ItemFilter filter(Direction d) {
        ItemStack stack = filters[d.ordinal()];
        return stack.isEmpty() ? null : stack.getOrDefault(ModDataComponents.FILTER.get(), ItemFilter.EMPTY);
    }

    /** Vrai si rien ne bloque cet objet sur ce bras. */
    public boolean accepts(Direction d, ItemResource resource) {
        ItemFilter f = filter(d);
        return f == null || f.test(resource);
    }

    /** Pose (ou retire, avec une pile vide) le filtre d'un bras ; renvoie l'ancien, à rendre au joueur. */
    public ItemStack setFilter(Direction d, ItemStack stack) {
        ItemStack old = filters[d.ordinal()];
        filters[d.ordinal()] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        setChanged();
        if (level != null) {
            PipeNetwork.invalidate(level);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        return old;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            for (int i = 0; i < filters.length; i++) {
                // Comme un coffre cassé : rien ne disparaît, même avec la règle blockDrops désactivée.
                if (!filters[i].isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), filters[i]);
                filters[i] = ItemStack.EMPTY;
            }
        }
        super.preRemoveSideEffects(pos, state);
    }

    /** Destinations du réseau, recalculées seulement si une pipe a changé quelque part. */
    public List<PipeNetwork.Destination> destinations() {
        if (level == null) return List.of();
        int version = PipeNetwork.version(level);
        if (version != cacheVersion) {
            destinations = PipeNetwork.collect(level, worldPosition);
            cacheVersion = version;
        }
        return destinations;
    }

    /** Destinations sans celles situées en {@code exclude} (on ne renvoie jamais un item d'où il vient). */
    List<PipeNetwork.Destination> destinationsExcept(BlockPos exclude) {
        List<PipeNetwork.Destination> all = destinations();
        List<PipeNetwork.Destination> out = new ArrayList<>(all.size());
        for (PipeNetwork.Destination d : all) if (!d.pos().equals(exclude)) out.add(d);
        return out;
    }

    int nextInsertIndex(int size) {
        int i = Math.floorMod(insertRoundRobin, size);
        insertRoundRobin = i + 1;
        return i;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PipeBlockEntity be) {
        if (--be.cooldown > 0) return;
        be.cooldown = SAConfig.PIPE_TICKS_PER_OPERATION.getAsInt();
        for (Direction d : Direction.values()) {
            if (PipeBlock.side(state, d) == PipeSide.EXTRACT) be.extractFrom(level, d);
        }
    }

    private void extractFrom(Level level, Direction d) {
        BlockPos sourcePos = worldPosition.relative(d);
        ResourceHandler<ItemResource> source = level.getCapability(Capabilities.Item.BLOCK, sourcePos, d.getOpposite());
        if (source == null) return;
        List<PipeNetwork.Destination> targets = destinationsExcept(sourcePos);
        int n = targets.size();
        if (n == 0) return;
        int amount = SAConfig.PIPE_ITEMS_PER_OPERATION.getAsInt();
        int start = Math.floorMod(roundRobin[d.ordinal()], n);
        ItemFilter own = filter(d);
        for (int i = 0; i < n; i++) {
            PipeNetwork.Destination dest = targets.get((start + i) % n);
            ResourceHandler<ItemResource> target = level.getCapability(Capabilities.Item.BLOCK, dest.pos(), dest.face());
            if (target == null) continue;
            // Un objet qu'aucune destination n'accepte reste simplement dans la source.
            if (ResourceHandlerUtil.move(source, target, r -> (own == null || own.test(r)) && dest.accepts(r), amount, null) > 0) {
                roundRobin[d.ordinal()] = (start + i + 1) % n;
                return;
            }
        }
    }

    /** Ce que voit un hopper ou un contrôleur collé à la pipe : un point d'entrée vers tout le réseau. */
    public @Nullable ResourceHandler<ItemResource> inserter(@Nullable Direction side) {
        if (side == null || isDisabled(side) || PipeBlock.side(getBlockState(), side) == PipeSide.PIPE) return null;
        return new PipeInsertHandler(this, side);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("disabled", disabledMask);
        for (Direction d : Direction.values()) {
            if (!filters[d.ordinal()].isEmpty()) out.store("filter_" + d.getSerializedName(), ItemStack.CODEC, filters[d.ordinal()]);
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        disabledMask = in.getIntOr("disabled", 0);
        for (Direction d : Direction.values()) {
            filters[d.ordinal()] = in.read("filter_" + d.getSerializedName(), ItemStack.CODEC).orElse(ItemStack.EMPTY);
        }
    }

    /** Une pipe qui revient avec son chunk peut changer le réseau (ses filtres, ses côtés) : les caches se recalculent. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) PipeNetwork.invalidate(level);
    }

    /** Le client reçoit les côtés et les filtres, pour dessiner les filtres sur les bras. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
