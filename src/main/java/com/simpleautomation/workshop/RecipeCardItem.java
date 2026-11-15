package com.simpleautomation.workshop;

import com.simpleautomation.registry.ModDataComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * La fiche de recette : une fiche = une recette sur une station, et le golem la tient en main.
 * <ul>
 * <li>Clic droit avec une fiche vierge sur une station posée : la fiche retient ce type de station et s'ouvre.
 * Accroupi, le même geste sur une autre station change la station de la fiche (les cases sont vidées).</li>
 * <li>Clic droit dans le vide : rouvre l'interface.</li>
 * </ul>
 * Le clic sur la station passe par {@link #onItemUseFirst} : l'établi et les autres ouvrent sinon leur propre interface avant l'objet.
 */
public class RecipeCardItem extends Item {
    public RecipeCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        Station station = Stations.at(state);
        RecipeCard card = stack.get(ModDataComponents.RECIPE_CARD.get());
        if (player == null || station == null || (card != null && !player.isShiftKeyDown())) return InteractionResult.PASS;
        if (!context.getLevel().isClientSide()) {
            if (card == null || card.station() != station) stack.set(ModDataComponents.RECIPE_CARD.get(), RecipeCard.blank(station));
            open(player, context.getHand(), stack);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.has(ModDataComponents.RECIPE_CARD.get())) {
            if (level.isClientSide()) player.displayClientMessage(Component.translatable("message.simple_automation.recipe_card.blank").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) open(player, hand, stack);
        return InteractionResult.SUCCESS;
    }

    private static void open(Player player, InteractionHand hand, ItemStack stack) {
        RecipeCard card = stack.get(ModDataComponents.RECIPE_CARD.get());
        if (card == null) return;
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new RecipeCardMenu(id, inventory, hand), stack.getHoverName()), buf -> {
            buf.writeEnum(hand);
            buf.writeUtf(card.station().id());
        });
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        RecipeCard card = stack.get(ModDataComponents.RECIPE_CARD.get());
        if (card == null) {
            tooltip.accept(Component.translatable("item.simple_automation.recipe_card.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.accept(Component.translatable("station.simple_automation." + card.station().id()).withStyle(ChatFormatting.GOLD));
        if (card.result().isEmpty()) {
            tooltip.accept(Component.translatable("item.simple_automation.recipe_card.no_recipe").withStyle(ChatFormatting.RED));
        } else {
            tooltip.accept(Component.translatable("item.simple_automation.recipe_card.result", card.result().getCount(), card.result().getHoverName())
                    .withStyle(ChatFormatting.GREEN));
        }
        for (ItemStack in : card.inputs()) {
            if (in.isEmpty()) continue;
            tooltip.accept(Component.literal(" - ").append(in.getHoverName()).withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
