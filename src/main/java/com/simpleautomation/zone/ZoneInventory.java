package com.simpleautomation.zone;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Coffres (et tout conteneur) collés au contrôleur. Ce qui ne rentre pas attend dans un petit tampon,
 * et la zone se met en pause tant que ce tampon est plein : rien n'est jamais jeté au sol.
 */
public final class ZoneInventory {
    public static final Codec<List<ItemStack>> BUFFER_CODEC = ItemStack.OPTIONAL_CODEC.listOf();
    /** Au-delà de ce nombre de piles en attente, les golems s'arrêtent. */
    private static final int BUFFER_LIMIT = 9;

    private final ZoneControllerBlockEntity owner;
    private final List<ItemStack> buffer = new ArrayList<>();
    private final BufferExtractor extractor = new BufferExtractor();

    public ZoneInventory(ZoneControllerBlockEntity owner) {
        this.owner = owner;
    }

    /** Premier item des coffres qui passe le filtre (copie, rien n'est retiré), ou une pile vide. */
    public ItemStack find(Predicate<ItemStack> filter) {
        for (ResourceHandler<ItemResource> h : handlers()) {
            for (int i = 0; i < h.size(); i++) {
                ItemResource r = h.getResource(i);
                if (r.isEmpty()) continue;
                ItemStack s = r.toStack(h.getAmountAsInt(i));
                if (filter.test(s)) return s;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Nombre total d'items qui passent le filtre, toutes piles de tous les coffres confondues. */
    public int count(Predicate<ItemStack> filter) {
        int total = 0;
        for (ResourceHandler<ItemResource> h : handlers()) {
            for (int i = 0; i < h.size(); i++) {
                ItemResource r = h.getResource(i);
                if (!r.isEmpty() && filter.test(r.toStack())) total += h.getAmountAsInt(i);
            }
        }
        return total;
    }

    /** Inventaires collés au contrôleur (coffres, barils, pipes…), sans les autres contrôleurs (dont on ne fait que vider le tampon). */
    public List<ResourceHandler<ItemResource>> handlers() {
        Level level = owner.getLevel();
        List<ResourceHandler<ItemResource>> list = new ArrayList<>(6);
        if (level == null) return list;
        BlockPos pos = owner.getBlockPos();
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (!level.isLoaded(n) || level.getBlockEntity(n) instanceof ZoneControllerBlockEntity) continue;
            ResourceHandler<ItemResource> h = level.getCapability(Capabilities.Item.BLOCK, n, d.getOpposite());
            if (h != null) list.add(h);
        }
        return list;
    }

    public boolean hasContainer() {
        return !handlers().isEmpty();
    }

    /** Range une pile dans les coffres ; le reste va dans le tampon. */
    public void output(ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemStack rest = insert(stack.copy());
        if (rest.isEmpty()) return;
        // Complète d'abord les piles identiques du tampon : 9 pierres une à une ne doivent pas faire 9 piles (et une pause).
        for (ItemStack s : buffer) {
            if (rest.isEmpty()) break;
            if (!ItemStack.isSameItemSameComponents(s, rest)) continue;
            int moved = Math.min(rest.getCount(), s.getMaxStackSize() - s.getCount());
            if (moved <= 0) continue;
            s.grow(moved);
            rest.shrink(moved);
        }
        if (!rest.isEmpty()) buffer.add(rest);
        owner.setChanged();
    }

    private ItemStack insert(ItemStack stack) {
        for (ResourceHandler<ItemResource> h : handlers()) {
            stack = ItemUtil.insertItemReturnRemaining(h, stack, false, null);
            if (stack.isEmpty()) break;
        }
        return stack;
    }

    /** Réessaie de vider le tampon. Renvoie vrai si la zone doit rester en pause. */
    public boolean flushAndCheckBlocked() {
        if (buffer.isEmpty()) return false;
        List<ResourceHandler<ItemResource>> hs = handlers();
        if (!hs.isEmpty()) {
            for (int i = buffer.size() - 1; i >= 0; i--) {
                ItemStack s = buffer.get(i);
                for (ResourceHandler<ItemResource> h : hs) {
                    s = ItemUtil.insertItemReturnRemaining(h, s, false, null);
                    if (s.isEmpty()) break;
                }
                if (s.isEmpty()) buffer.remove(i);
                else buffer.set(i, s);
            }
            owner.setChanged();
        }
        buffer.removeIf(ItemStack::isEmpty);
        return buffer.size() >= BUFFER_LIMIT;
    }

    /** Retire jusqu'à {@code count} items qui passent le filtre. Renvoie une pile vide si aucun coffre n'en a. */
    public ItemStack extract(Predicate<ItemStack> filter, int count) {
        for (ResourceHandler<ItemResource> h : handlers()) {
            ResourceStack<ItemResource> got = ResourceHandlerUtil.extractFirst(h, r -> filter.test(r.toStack()), count, null);
            if (got != null && got.amount() > 0) return got.resource().toStack(got.amount());
        }
        return ItemStack.EMPTY;
    }

    public List<ItemStack> buffer() {
        return buffer;
    }

    /** Le tampon vu de l'extérieur (pipe en Extraction, hopper) : on peut seulement en retirer. */
    public ResourceHandler<ItemResource> extractor() {
        return extractor;
    }

    public void load(List<ItemStack> stacks) {
        buffer.clear();
        stacks.stream().filter(s -> !s.isEmpty()).forEach(buffer::add);
    }

    /**
     * Accès en extraction seule au tampon. Pendant une transaction, une pile vidée reste à sa place (vide)
     * pour ne pas décaler les index ; elle est retirée de la liste une fois la transaction validée.
     */
    private final class BufferExtractor extends SnapshotJournal<List<ItemStack>> implements ResourceHandler<ItemResource> {
        @Override
        public int size() {
            return buffer.size();
        }

        @Override
        public ItemResource getResource(int index) {
            return index < buffer.size() ? ItemResource.of(buffer.get(index)) : ItemResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            return index < buffer.size() ? buffer.get(index).getCount() : 0;
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return 0;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return false;
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (index >= buffer.size() || resource.isEmpty() || amount <= 0) return 0;
            ItemStack s = buffer.get(index);
            if (s.isEmpty() || !resource.matches(s)) return 0;
            int taken = Math.min(amount, s.getCount());
            updateSnapshots(transaction);
            buffer.set(index, s.copyWithCount(s.getCount() - taken));
            return taken;
        }

        @Override
        protected List<ItemStack> createSnapshot() {
            List<ItemStack> copy = new ArrayList<>(buffer.size());
            for (ItemStack s : buffer) copy.add(s.copy());
            return copy;
        }

        @Override
        protected void revertToSnapshot(List<ItemStack> snapshot) {
            buffer.clear();
            buffer.addAll(snapshot);
        }

        @Override
        protected void onRootCommit(List<ItemStack> originalState) {
            buffer.removeIf(ItemStack::isEmpty);
            owner.setChanged();
        }
    }
}
