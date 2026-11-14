package com.simpleautomation.client;

import com.simpleautomation.pipe.filter.FilterMenu;
import com.simpleautomation.pipe.filter.ItemFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Écran du filtre : le fond vanilla du distributeur (3×3) et un bouton liste blanche / liste noire. */
public class FilterScreen extends AbstractContainerScreen<FilterMenu> {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/dispenser.png");

    private Button modeButton;
    private boolean shownBlacklist;

    public FilterScreen(FilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
        modeButton = addRenderableWidget(Button.builder(modeLabel(), b -> {
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, FilterMenu.BUTTON_MODE);
        }).bounds(leftPos + 6, topPos + 34, 52, 20).build());
        refreshButton();
    }

    private Component modeLabel() {
        return Component.translatable(menu.isBlacklist() ? "gui.simple_automation.filter.blacklist_short" : "gui.simple_automation.filter.whitelist_short");
    }

    /** Le mode arrive du serveur par un DataSlot : on met le bouton à jour quand il change. */
    private void refreshButton() {
        shownBlacklist = menu.isBlacklist();
        modeButton.setMessage(modeLabel());
        modeButton.setTooltip(Tooltip.create(Component.translatable(shownBlacklist
                ? "gui.simple_automation.filter.blacklist_hint" : "gui.simple_automation.filter.whitelist_hint")));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (shownBlacklist != menu.isBlacklist()) refreshButton();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
    }

    /** Sur une case fantôme : le tag choisi, ou « objet exact », et l'astuce du clic droit. */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        if (hoveredSlot == null || !FilterMenu.isGhost(hoveredSlot)) return lines;
        Optional<TagKey<Item>> tag = menu.tagOf(hoveredSlot.index);
        if (tag.isPresent()) {
            lines.add(Component.translatable("gui.simple_automation.filter.tag", "#" + tag.get().location()).withStyle(ChatFormatting.AQUA));
        } else {
            lines.add(Component.translatable("gui.simple_automation.filter.exact").withStyle(ChatFormatting.AQUA));
        }
        if (!ItemFilter.sortedTags(stack.getItem()).isEmpty()) {
            lines.add(Component.translatable("gui.simple_automation.filter.cycle_hint").withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }
}
