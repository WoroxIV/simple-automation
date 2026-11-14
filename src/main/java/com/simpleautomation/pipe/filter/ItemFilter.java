package com.simpleautomation.pipe.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Réglage d'un filtre : 9 cases (un objet, éventuellement remplacé par un de ses tags) et un mode liste blanche / noire.
 * Une liste blanche vide ne laisse rien passer ; une liste noire vide laisse tout passer.
 * Le tag est gardé par son nom (et non par un numéro) pour rester juste si un datapack ajoute des tags.
 */
public record ItemFilter(List<Entry> entries, boolean blacklist) {
    public static final int SIZE = 9;
    public static final ItemFilter EMPTY = new ItemFilter(List.of(), false);

    /** Une case : l'objet montré, et le tag choisi (vide = cet objet exactement). */
    public record Entry(Item item, Optional<TagKey<Item>> tag) {
        public static final Entry NONE = new Entry(Items.AIR, Optional.empty());
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Entry::item),
                TagKey.codec(Registries.ITEM).optionalFieldOf("tag").forGetter(Entry::tag)
        ).apply(i, Entry::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.registry(Registries.ITEM), Entry::item,
                TagKey.streamCodec(Registries.ITEM).apply(ByteBufCodecs::optional), Entry::tag,
                Entry::new);

        public boolean isEmpty() {
            return item == Items.AIR;
        }

        public boolean matches(ItemResource r) {
            if (isEmpty()) return false;
            return tag.isPresent() ? r.getHolder().is(tag.get()) : r.is(item);
        }

        public boolean matches(ItemStack s) {
            if (isEmpty()) return false;
            return tag.isPresent() ? s.is(tag.get()) : s.is(item);
        }
    }

    public static final Codec<ItemFilter> CODEC = RecordCodecBuilder.create(i -> i.group(
            Entry.CODEC.sizeLimitedListOf(SIZE).optionalFieldOf("entries", List.of()).forGetter(ItemFilter::entries),
            Codec.BOOL.optionalFieldOf("blacklist", false).forGetter(ItemFilter::blacklist)
    ).apply(i, ItemFilter::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemFilter> STREAM_CODEC = StreamCodec.composite(
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(SIZE)), ItemFilter::entries,
            ByteBufCodecs.BOOL, ItemFilter::blacklist,
            ItemFilter::new);

    /** Toujours exactement 9 cases, liste immuable. */
    public ItemFilter {
        List<Entry> fixed = new ArrayList<>(Collections.nCopies(SIZE, Entry.NONE));
        for (int i = 0; i < Math.min(SIZE, entries.size()); i++) fixed.set(i, entries.get(i));
        entries = List.copyOf(fixed);
    }

    public Entry entry(int index) {
        return entries.get(index);
    }

    public boolean isEmpty() {
        for (Entry e : entries) if (!e.isEmpty()) return false;
        return true;
    }

    public boolean test(ItemResource r) {
        if (r.isEmpty()) return false;
        boolean matched = false;
        for (Entry e : entries) {
            if (e.matches(r)) {
                matched = true;
                break;
            }
        }
        return matched != blacklist;
    }

    public boolean test(ItemStack s) {
        if (s.isEmpty()) return false;
        boolean matched = false;
        for (Entry e : entries) {
            if (e.matches(s)) {
                matched = true;
                break;
            }
        }
        return matched != blacklist;
    }

    /** Tags d'un objet, triés par nom : le même ordre sur le client et le serveur, pour faire défiler les tags. */
    public static List<TagKey<Item>> sortedTags(Item item) {
        return item.builtInRegistryHolder().tags().sorted(Comparator.comparing(t -> t.location().toString())).toList();
    }
}
