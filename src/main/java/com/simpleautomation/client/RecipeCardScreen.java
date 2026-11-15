package com.simpleautomation.client;

import com.simpleautomation.workshop.RecipeCardMenu;
import com.simpleautomation.workshop.Station;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Écran d'une fiche de recette : un fond gris dessiné sans texture (les cases sont celles de la station choisie),
 * les cases fantômes en entrée, une flèche et la case du résultat.
 */
public class RecipeCardScreen extends AbstractContainerScreen<RecipeCardMenu> {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int DARK = 0xFF555555;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int TEXT = 0xFF404040;

    public RecipeCardScreen(RecipeCardMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    /**
     * Titre et station sur une seule ligne (« Fiche de recette : Établi ») : la grille de l'établi commence à y = 17,
     * comme celle de l'établi vanilla, il n'y a la place que pour une ligne de texte au-dessus.
     */
    private Component header = Component.empty();

    @Override
    protected void init() {
        super.init();
        header = Component.translatable("gui.simple_automation.recipe_card.title", title,
                Component.translatable("station.simple_automation." + menu.station().id()));
        // Trop long pour le panneau (« Fiche de recette : Table de forgeron ») : la station seule suffit.
        if (font.width(header) > imageWidth - 16) header = Component.translatable("station.simple_automation." + menu.station().id());
        titleLabelX = (imageWidth - font.width(header)) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        // Panneau en relief.
        graphics.fill(x, y, x + imageWidth, y + imageHeight, DARK);
        graphics.fill(x, y, x + imageWidth - 2, y + imageHeight - 2, LIGHT);
        graphics.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, PANEL);
        for (Slot slot : menu.slots) {
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            graphics.fill(sx, sy, sx + 18, sy + 18, LIGHT);
            graphics.fill(sx, sy, sx + 17, sy + 17, DARK);
            graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT);
        }
        // Flèche vers le résultat, au milieu de l'espace libre entre la dernière entrée et lui.
        Station station = menu.station();
        int[][] layout = station.layout();
        int last = station.inputs() - 1;
        int arrowX = (layout[last][0] + 16 + layout[station.inputs()][0]) / 2 - font.width("→") / 2;
        graphics.drawString(font, "→", x + arrowX, y + layout[station.inputs()][1] + 4, TEXT, false);
        // Les « + » entre les entrées d'une ligne (forgeron, alambic).
        if (station.inputs() > 1 && station.inputs() <= 3) {
            for (int i = 0; i < station.inputs() - 1; i++) {
                int px = (layout[i][0] + 16 + layout[i + 1][0]) / 2 - font.width("+") / 2;
                graphics.drawString(font, "+", x + px, y + layout[i][1] + 4, TEXT, false);
            }
        }
        // Tailleur de pierre : rang du choix sous le résultat.
        if (station.hasChoices() && menu.choiceCount() > 0) {
            String text = (menu.choiceIndex() + 1) + " / " + menu.choiceCount();
            graphics.drawString(font, text, x + layout[station.inputs()][0] + 8 - font.width(text) / 2, y + layout[station.inputs()][1] + 22, TEXT, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, header, titleLabelX, titleLabelY, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
    }

    /** Sur une case fantôme : à quoi sert un clic. */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        if (hoveredSlot == null || !RecipeCardMenu.isGhost(hoveredSlot)) return lines;
        if (menu.isResultSlot(hoveredSlot.index)) {
            if (menu.station().hasChoices()) lines.add(Component.translatable("gui.simple_automation.recipe_card.cycle_hint").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            lines.add(Component.translatable("gui.simple_automation.recipe_card.clear_hint").withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }
}
