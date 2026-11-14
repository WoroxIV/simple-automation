package com.simpleautomation.compat.jade;

import com.simpleautomation.zone.ZoneControllerBlock;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import com.simpleautomation.zone.ZoneStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Côté client : l'état de la zone (lu sur le bloc), puis les golems, le tampon et l'avancement envoyés par le serveur. */
enum ControllerComponentProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof ZoneControllerBlockEntity be)) return;
        BlockState state = accessor.getBlockState();
        ZoneStatus status = state.hasProperty(ZoneControllerBlock.STATUS) ? state.getValue(ZoneControllerBlock.STATUS) : ZoneStatus.INVALID;
        tooltip.add(Component.translatable("jade.simple_automation.status." + status.getSerializedName()).withStyle(color(status)));
        if (status == ZoneStatus.INVALID) return;

        ControllerData data = ControllerDataProvider.INSTANCE.decodeFromData(accessor).orElseGet(() -> ControllerData.client(be));
        tooltip.add(Component.translatable("message.simple_automation.zone.workers", data.workers(), data.working()).withStyle(ChatFormatting.GRAY));
        if (data.idle() > 0) tooltip.add(Component.translatable("jade.simple_automation.idle", data.idle()).withStyle(ChatFormatting.GRAY));
        if (data.noTool() > 0) tooltip.add(Component.translatable("jade.simple_automation.no_tool", data.noTool()).withStyle(ChatFormatting.YELLOW));
        if (data.frozen() > 0) tooltip.add(Component.translatable("jade.simple_automation.frozen", data.frozen()).withStyle(ChatFormatting.YELLOW));
        if (data.buffered() > 0) tooltip.add(Component.translatable("jade.simple_automation.buffer", data.buffered()).withStyle(ChatFormatting.GRAY));
        if (!data.hasChest()) tooltip.add(Component.translatable("jade.simple_automation.no_chest").withStyle(ChatFormatting.YELLOW));
        data.progress().ifPresent(p -> tooltip.add(p.copy().withStyle(ChatFormatting.GRAY)));
    }

    private static ChatFormatting color(ZoneStatus status) {
        return switch (status) {
            case WORKING -> ChatFormatting.GREEN;
            case PAUSED, INVALID -> ChatFormatting.RED;
            default -> ChatFormatting.GRAY;
        };
    }

    @Override
    public Identifier getUid() {
        return SAJadePlugin.CONTROLLER;
    }
}
