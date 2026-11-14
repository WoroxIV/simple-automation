package com.simpleautomation.pipe.filter;

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
import net.minecraft.world.level.Level;

/**
 * Le filtre de pipe. Clic droit dans le vide : ouvre son réglage. Clic droit sur un bras de pipe côté conteneur : il s'y pose
 * (voir {@link com.simpleautomation.pipe.PipeBlock}).
 */
public class FilterItem extends Item {
    public FilterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            ItemStack stack = player.getItemInHand(hand);
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new FilterMenu(id, inventory, hand), stack.getHoverName()),
                    buf -> buf.writeEnum(hand));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        ItemFilter filter = stack.getOrDefault(ModDataComponents.FILTER.get(), ItemFilter.EMPTY);
        tooltip.accept(Component.translatable(filter.blacklist() ? "gui.simple_automation.filter.blacklist" : "gui.simple_automation.filter.whitelist")
                .withStyle(filter.blacklist() ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY));
        for (ItemFilter.Entry e : filter.entries()) {
            if (e.isEmpty()) continue;
            Component line = e.tag().<Component>map(t -> Component.literal("#" + t.location()))
                    .orElseGet(() -> e.item().getName());
            tooltip.accept(Component.literal(" - ").append(line).withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
