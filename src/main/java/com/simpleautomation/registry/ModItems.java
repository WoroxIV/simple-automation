package com.simpleautomation.registry;

import com.simpleautomation.SimpleAutomation;
import com.simpleautomation.item.XpCrystalItem;
import com.simpleautomation.pipe.WrenchItem;
import com.simpleautomation.pipe.filter.FilterItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SimpleAutomation.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SimpleAutomation.MODID);

    public static final DeferredItem<BlockItem> BORDER_MARKER = ITEMS.registerSimpleBlockItem("border_marker", ModBlocks.BORDER_MARKER);
    public static final DeferredItem<BlockItem> QUARRY_CONTROLLER = ITEMS.registerSimpleBlockItem("quarry_controller", ModBlocks.QUARRY_CONTROLLER);
    public static final DeferredItem<BlockItem> LUMBER_CONTROLLER = ITEMS.registerSimpleBlockItem("lumber_controller", ModBlocks.LUMBER_CONTROLLER);
    public static final DeferredItem<BlockItem> FORGE_CONTROLLER = ITEMS.registerSimpleBlockItem("forge_controller", ModBlocks.FORGE_CONTROLLER);
    public static final DeferredItem<BlockItem> CRUSHER_CONTROLLER = ITEMS.registerSimpleBlockItem("crusher_controller", ModBlocks.CRUSHER_CONTROLLER);
    public static final DeferredItem<BlockItem> FARM_CONTROLLER = ITEMS.registerSimpleBlockItem("farm_controller", ModBlocks.FARM_CONTROLLER);
    public static final DeferredItem<BlockItem> BARN_CONTROLLER = ITEMS.registerSimpleBlockItem("barn_controller", ModBlocks.BARN_CONTROLLER);
    public static final DeferredItem<BlockItem> FISHERY_CONTROLLER = ITEMS.registerSimpleBlockItem("fishery_controller", ModBlocks.FISHERY_CONTROLLER);
    public static final DeferredItem<BlockItem> GUARD_CONTROLLER = ITEMS.registerSimpleBlockItem("guard_controller", ModBlocks.GUARD_CONTROLLER);
    public static final DeferredItem<BlockItem> PIPE = ITEMS.registerSimpleBlockItem("pipe", ModBlocks.PIPE);
    public static final DeferredItem<WrenchItem> WRENCH = ITEMS.registerItem("wrench", WrenchItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<FilterItem> FILTER = ITEMS.registerItem("filter", FilterItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<XpCrystalItem> XP_CRYSTAL = ITEMS.registerItem("xp_crystal", XpCrystalItem::new, p -> p.rarity(Rarity.UNCOMMON));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.simple_automation"))
            .withTabsBefore(CreativeModeTabs.REDSTONE_BLOCKS)
            .icon(() -> QUARRY_CONTROLLER.get().getDefaultInstance())
            .displayItems((params, out) -> {
                out.accept(BORDER_MARKER.get());
                out.accept(QUARRY_CONTROLLER.get());
                out.accept(LUMBER_CONTROLLER.get());
                out.accept(FORGE_CONTROLLER.get());
                out.accept(CRUSHER_CONTROLLER.get());
                out.accept(FARM_CONTROLLER.get());
                out.accept(BARN_CONTROLLER.get());
                out.accept(FISHERY_CONTROLLER.get());
                out.accept(GUARD_CONTROLLER.get());
                out.accept(PIPE.get());
                out.accept(WRENCH.get());
                out.accept(FILTER.get());
                out.accept(XP_CRYSTAL.get());
            })
            .build());

    private ModItems() {
    }
}
