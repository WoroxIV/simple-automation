package com.simpleautomation.workshop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * Réglage d'une fiche de recette : la station visée, un objet « fantôme » par case d'entrée (jamais consommé, il sert de
 * modèle) et le résultat annoncé. Le résultat est calculé par le serveur à chaque changement et recopié ici : le client n'a
 * pas les recettes, mais doit pouvoir l'afficher dans l'infobulle.
 */
public record RecipeCard(Station station, List<ItemStack> inputs, ItemStack result) {
    public static final Codec<RecipeCard> CODEC = RecordCodecBuilder.create(i -> i.group(
            Stations.CODEC.fieldOf("station").forGetter(RecipeCard::station),
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("inputs").forGetter(RecipeCard::inputs),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("result", ItemStack.EMPTY).forGetter(RecipeCard::result)
    ).apply(i, RecipeCard::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeCard> STREAM_CODEC = StreamCodec.composite(
            Stations.STREAM_CODEC, RecipeCard::station,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), RecipeCard::inputs,
            ItemStack.OPTIONAL_STREAM_CODEC, RecipeCard::result,
            RecipeCard::new);

    /** Fiche vierge d'une station : des cases vides et pas de résultat. */
    public static RecipeCard blank(Station station) {
        List<ItemStack> empty = new ArrayList<>(station.inputs());
        for (int i = 0; i < station.inputs(); i++) empty.add(ItemStack.EMPTY);
        return new RecipeCard(station, empty, ItemStack.EMPTY);
    }

    /** Objet fantôme d'une case (vide si la liste est plus courte que prévu, par exemple après un changement de station). */
    public ItemStack input(int index) {
        return index >= 0 && index < inputs.size() ? inputs.get(index) : ItemStack.EMPTY;
    }

    /** Ce qu'une case retient d'un objet : l'objet seul, et la potion pour les fioles (le reste est sans importance). */
    public static ItemStack ghostOf(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack ghost = new ItemStack(stack.getItem());
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null) ghost.set(DataComponents.POTION_CONTENTS, potion);
        return ghost;
    }

    /** Le résultat est annoncé : la recette existe. */
    public boolean isComplete() {
        return !result.isEmpty();
    }

    // Les piles n'ont pas d'égalité de valeur : sans ceci deux fiches identiques seraient « différentes » et le menu renverrait la case à chaque tick.
    @Override
    public boolean equals(Object other) {
        return other instanceof RecipeCard c && station == c.station && ItemStack.listMatches(inputs, c.inputs) && ItemStack.matches(result, c.result);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * System.identityHashCode(station) + ItemStack.hashStackList(inputs)) + ItemStack.hashItemAndComponents(result);
    }
}
