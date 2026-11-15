package com.simpleautomation.workshop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Les stations connues de l'atelier. */
public final class Stations {
    public static final CraftingStation CRAFTING = new CraftingStation();
    public static final SmithingStation SMITHING = new SmithingStation();
    public static final StonecutterStation STONECUTTER = new StonecutterStation();
    public static final BrewingStation BREWING = new BrewingStation();

    public static final List<Station> ALL = List.of(CRAFTING, SMITHING, STONECUTTER, BREWING);

    public static final Codec<Station> CODEC = Codec.STRING.comapFlatMap(
            id -> byId(id) != null ? DataResult.success(byId(id)) : DataResult.error(() -> "Unknown station: " + id), Station::id);
    public static final StreamCodec<RegistryFriendlyByteBuf, Station> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(id -> {
        Station s = byId(id);
        return s != null ? s : CRAFTING;
    }, Station::id).cast();

    public static @Nullable Station byId(String id) {
        for (Station s : ALL) if (s.id().equals(id)) return s;
        return null;
    }

    /** La station que ce bloc est, ou null. */
    public static @Nullable Station at(BlockState state) {
        for (Station s : ALL) if (s.matches(state)) return s;
        return null;
    }

    private Stations() {
    }
}
