package com.simpleautomation.registry;

import com.simpleautomation.SimpleAutomation;
import com.simpleautomation.pipe.PipeBlockEntity;
import com.simpleautomation.zone.BorderMarkerBlockEntity;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SimpleAutomation.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BorderMarkerBlockEntity>> BORDER_MARKER = BLOCK_ENTITIES.register("border_marker",
            () -> new BlockEntityType<>(BorderMarkerBlockEntity::new, Set.of(ModBlocks.BORDER_MARKER.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ZoneControllerBlockEntity>> ZONE_CONTROLLER = BLOCK_ENTITIES.register("zone_controller",
            () -> new BlockEntityType<>(ZoneControllerBlockEntity::new, Set.of(ModBlocks.QUARRY_CONTROLLER.get(), ModBlocks.LUMBER_CONTROLLER.get(),
                    ModBlocks.FORGE_CONTROLLER.get(), ModBlocks.CRUSHER_CONTROLLER.get(), ModBlocks.FARM_CONTROLLER.get(),
                    ModBlocks.BARN_CONTROLLER.get(), ModBlocks.FISHERY_CONTROLLER.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PipeBlockEntity>> PIPE = BLOCK_ENTITIES.register("pipe",
            () -> new BlockEntityType<>(PipeBlockEntity::new, Set.of(ModBlocks.PIPE.get())));

    private ModBlockEntities() {
    }
}
