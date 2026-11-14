package com.simpleautomation.compat.jei;

import com.simpleautomation.SimpleAutomation;
import com.simpleautomation.client.FilterScreen;
import com.simpleautomation.registry.ModItems;
import com.simpleautomation.zone.ZoneControllerBlock;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Intégration JEI (optionnelle, découverte par JEI grâce à l'annotation) :
 * une page d'explication par objet du mod, et le glisser-déposer d'objets dans les cases du filtre.
 */
@JeiPlugin
public final class SAJeiPlugin implements IModPlugin {
    private static final Identifier UID = SimpleAutomation.id("jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    /** Chaque objet a sa page « jei.simple_automation.info.<nom> » ; les contrôleurs y ajoutent le mode d'emploi commun. */
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Component controllerCommon = Component.translatable("jei.simple_automation.info.controller");
        for (DeferredHolder<Item, ? extends Item> entry : ModItems.ITEMS.getEntries()) {
            Item item = entry.get();
            Component own = Component.translatable("jei.simple_automation.info." + entry.getId().getPath());
            if (item instanceof BlockItem block && block.getBlock() instanceof ZoneControllerBlock) {
                registration.addIngredientInfo(item, own, controllerCommon);
            } else {
                registration.addIngredientInfo(item, own);
            }
        }
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(FilterScreen.class, new FilterGhostHandler());
    }
}
