package com.simpleautomation.compat.jade;

import com.simpleautomation.zone.ZoneControllerBlockEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.StreamServerDataProvider;

/** Côté serveur : envoie à Jade les compteurs du contrôleur regardé (aucune classe client ici). */
enum ControllerDataProvider implements StreamServerDataProvider<BlockAccessor, ControllerData> {
    INSTANCE;

    @Override
    public @Nullable ControllerData streamData(BlockAccessor accessor) {
        return accessor.getBlockEntity() instanceof ZoneControllerBlockEntity be ? ControllerData.server(be) : null;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, ControllerData> streamCodec() {
        return ControllerData.STREAM_CODEC;
    }

    @Override
    public Identifier getUid() {
        return SAJadePlugin.CONTROLLER;
    }
}
