package com.simpleautomation.registry;

import com.simpleautomation.SimpleAutomation;
import com.simpleautomation.pipe.PipeBlock;
import com.simpleautomation.zone.BorderMarkerBlock;
import com.simpleautomation.zone.JobType;
import com.simpleautomation.zone.ZoneControllerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SimpleAutomation.MODID);

    public static final DeferredBlock<BorderMarkerBlock> BORDER_MARKER = BLOCKS.registerBlock("border_marker", BorderMarkerBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(0.5F).sound(SoundType.COPPER).noOcclusion().lightLevel(s -> 7));

    public static final DeferredBlock<ZoneControllerBlock> QUARRY_CONTROLLER = controller("quarry_controller", JobType.QUARRY);
    public static final DeferredBlock<ZoneControllerBlock> LUMBER_CONTROLLER = controller("lumber_controller", JobType.LUMBER);
    public static final DeferredBlock<ZoneControllerBlock> FORGE_CONTROLLER = controller("forge_controller", JobType.FORGE);
    public static final DeferredBlock<ZoneControllerBlock> CRUSHER_CONTROLLER = controller("crusher_controller", JobType.CRUSHER);
    public static final DeferredBlock<ZoneControllerBlock> FARM_CONTROLLER = controller("farm_controller", JobType.FARM);
    public static final DeferredBlock<ZoneControllerBlock> SHEPHERD_CONTROLLER = controller("shepherd_controller", JobType.SHEPHERD);
    public static final DeferredBlock<ZoneControllerBlock> RANCH_CONTROLLER = controller("ranch_controller", JobType.RANCH);

    public static final DeferredBlock<PipeBlock> PIPE = BLOCKS.registerBlock("pipe", PipeBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(0.8F).sound(SoundType.COPPER).noOcclusion());

    private static DeferredBlock<ZoneControllerBlock> controller(String name, JobType job) {
        return BLOCKS.registerBlock(name, p -> new ZoneControllerBlock(job, p), ModBlocks::controllerProperties);
    }

    private static BlockBehaviour.Properties controllerProperties(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.COLOR_ORANGE).strength(3.0F, 6.0F).sound(SoundType.COPPER).requiresCorrectToolForDrops();
    }

    private ModBlocks() {
    }
}
