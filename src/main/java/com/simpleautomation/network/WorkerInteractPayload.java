package com.simpleautomation.network;

import com.simpleautomation.SimpleAutomation;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Le joueur a fait un clic droit sur une marionnette : le serveur applique (outil, cire, libération…). */
public record WorkerInteractPayload(BlockPos controller, UUID worker) implements CustomPacketPayload {
    public static final Type<WorkerInteractPayload> TYPE = new Type<>(SimpleAutomation.id("worker_interact"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkerInteractPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, WorkerInteractPayload::controller,
            UUIDUtil.STREAM_CODEC, WorkerInteractPayload::worker,
            WorkerInteractPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WorkerInteractPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var level = player.level();
            if (!level.isLoaded(payload.controller())) return;
            if (level.getBlockEntity(payload.controller()) instanceof ZoneControllerBlockEntity be) be.interact(player, payload.worker());
        });
    }
}
