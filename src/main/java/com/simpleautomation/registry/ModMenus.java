package com.simpleautomation.registry;

import com.simpleautomation.SimpleAutomation;
import com.simpleautomation.pipe.filter.FilterMenu;
import com.simpleautomation.workshop.RecipeCardMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, SimpleAutomation.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<FilterMenu>> FILTER = MENUS.register("filter", () -> IMenuTypeExtension.create(FilterMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<RecipeCardMenu>> RECIPE_CARD = MENUS.register("recipe_card", () -> IMenuTypeExtension.create(RecipeCardMenu::new));

    private ModMenus() {
    }
}
