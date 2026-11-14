package com.simpleautomation.compat.jade;

import com.simpleautomation.pipe.PipeBlock;
import com.simpleautomation.pipe.PipeBlockEntity;
import com.simpleautomation.pipe.PipeSide;
import com.simpleautomation.pipe.filter.ItemFilter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.JadeUI;

/**
 * Côté client : le bras regardé (même calcul que la clé et le filtre), son mode et son filtre.
 * Avec la touche des détails, les autres côtés reliés aussi. Le client a déjà tout : côtés dans l'état du bloc,
 * côtés désactivés et filtres dans le block entity synchronisé.
 */
enum PipeComponentProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof PipeBlockEntity pipe)) return;
        BlockState state = accessor.getBlockState();
        Direction looked = PipeBlock.sideFromHit(accessor.getPosition(), accessor.getHitResult().getLocation(), accessor.getSide());
        addSide(tooltip, pipe, state, looked, ChatFormatting.WHITE);
        addFilter(tooltip, pipe.filter(looked));
        if (!accessor.showDetails()) return;
        for (Direction d : Direction.values()) {
            if (d == looked || (PipeBlock.side(state, d) == PipeSide.NONE && !pipe.isDisabled(d))) continue;
            addSide(tooltip, pipe, state, d, ChatFormatting.GRAY);
            if (pipe.filter(d) != null) tooltip.append(Component.translatable("jade.simple_automation.pipe.filtered").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** « Nord : extraction ». Un côté désactivé à la clé est NONE dans l'état du bloc : on le reconnaît au block entity. */
    private static void addSide(ITooltip tooltip, PipeBlockEntity pipe, BlockState state, Direction d, ChatFormatting style) {
        PipeSide side = PipeBlock.side(state, d);
        String mode = side == PipeSide.NONE && pipe.isDisabled(d) ? "disabled" : side.getSerializedName();
        tooltip.add(Component.translatable("jade.simple_automation.pipe.side",
                Component.translatable("jade.simple_automation.direction." + d.getSerializedName()),
                Component.translatable("jade.simple_automation.pipe.mode." + mode)).withStyle(style));
    }

    /** Le mode du filtre suivi des objets exacts en icônes ; un objet réglé sur un tag a sa ligne, avec le nom du tag. */
    private static void addFilter(ITooltip tooltip, @Nullable ItemFilter filter) {
        if (filter == null) return;
        tooltip.add(Component.translatable(filter.blacklist() ? "jade.simple_automation.pipe.blacklist" : "jade.simple_automation.pipe.whitelist")
                .withStyle(ChatFormatting.AQUA));
        if (filter.isEmpty()) {
            tooltip.append(Component.translatable(filter.blacklist() ? "jade.simple_automation.pipe.empty_blacklist"
                    : "jade.simple_automation.pipe.empty_whitelist").withStyle(ChatFormatting.GRAY));
            return;
        }
        for (ItemFilter.Entry e : filter.entries()) {
            if (!e.isEmpty() && e.tag().isEmpty()) tooltip.append(JadeUI.smallItem(new ItemStack(e.item())));
        }
        for (ItemFilter.Entry e : filter.entries()) {
            if (e.isEmpty() || e.tag().isEmpty()) continue;
            tooltip.add(JadeUI.smallItem(new ItemStack(e.item())));
            tooltip.append(Component.literal("#" + e.tag().get().location()).withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public Identifier getUid() {
        return SAJadePlugin.PIPE;
    }
}
