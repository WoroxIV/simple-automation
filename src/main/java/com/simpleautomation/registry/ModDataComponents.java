package com.simpleautomation.registry;

import com.simpleautomation.SimpleAutomation;
import com.simpleautomation.pipe.filter.ItemFilter;
import com.simpleautomation.workshop.RecipeCard;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, SimpleAutomation.MODID);

    /** Réglage d'un filtre de pipe. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemFilter>> FILTER = COMPONENTS.registerComponentType("filter",
            b -> b.persistent(ItemFilter.CODEC).networkSynchronized(ItemFilter.STREAM_CODEC).cacheEncoding());

    /** Réglage d'une fiche de recette (station, cases fantômes, résultat annoncé). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RecipeCard>> RECIPE_CARD = COMPONENTS.registerComponentType("recipe_card",
            b -> b.persistent(RecipeCard.CODEC).networkSynchronized(RecipeCard.STREAM_CODEC).cacheEncoding());

    private ModDataComponents() {
    }
}
