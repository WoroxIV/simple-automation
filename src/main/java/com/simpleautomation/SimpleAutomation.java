package com.simpleautomation;

import com.mojang.logging.LogUtils;
import com.simpleautomation.network.WorkerInteractPayload;
import com.simpleautomation.pipe.PipeBlockEntity;
import com.simpleautomation.registry.ModBlockEntities;
import com.simpleautomation.registry.ModBlocks;
import com.simpleautomation.registry.ModDataComponents;
import com.simpleautomation.registry.ModItems;
import com.simpleautomation.registry.ModMenus;
import com.simpleautomation.zone.WorkBudget;
import com.simpleautomation.zone.ZoneBounds;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import com.simpleautomation.zone.ZoneRegistry;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

/** Point d'entrée du mod : branche les registres, la config, le réseau et les événements. */
@Mod(SimpleAutomation.MODID)
public final class SimpleAutomation {
    public static final String MODID = "simple_automation";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SimpleAutomation(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModItems.TABS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModDataComponents.COMPONENTS.register(modBus);
        ModMenus.MENUS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, SAConfig.SPEC);

        modBus.addListener(SimpleAutomation::registerPayloads);
        modBus.addListener(SimpleAutomation::registerCapabilities);
        com.simpleautomation.test.SATests.register(modBus);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre e) -> WorkBudget.reset());
        NeoForge.EVENT_BUS.addListener(SimpleAutomation::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(com.simpleautomation.zone.XpCapture::onExperienceDrop);
        // Un golem sans marque (vieille sauvegarde) qui change de dimension reçoit une marque « jamais tout juste construit » :
        // sa copie dans l'autre dimension entre dans le monde comme un golem neuf (loadedFromDisk faux) et la garde.
        NeoForge.EVENT_BUS.addListener((EntityTravelToDimensionEvent e) -> {
            if (e.getEntity() instanceof CopperGolem golem && !golem.level().isClientSide()) ZoneControllerBlockEntity.markUnknownBirth(golem);
        });
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(WorkerInteractPayload.TYPE, WorkerInteractPayload.STREAM_CODEC, WorkerInteractPayload::handle);
    }

    /**
     * Une pipe se présente comme un inventaire où l'on peut déposer : ce qui y entre part dans le réseau.
     * Un contrôleur expose son tampon, en extraction seule : une pipe s'y raccorde et peut le vider.
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.PIPE.get(), PipeBlockEntity::inserter);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.ZONE_CONTROLLER.get(), (be, side) -> be.inventory().extractor());
    }

    /**
     * Un copper golem qui apparaît (pas chargé depuis la sauvegarde) est marqué comme tout juste construit.
     * Un éclair qui tombe dans une zone désoxyde d'un cran tous ses golems, comme sur un bloc de cuivre.
     */
    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof CopperGolem golem) {
            if (!event.loadedFromDisk()) ZoneControllerBlockEntity.markBorn(golem, event.getLevel().getGameTime());
            return;
        }
        if (!(event.getEntity() instanceof LightningBolt bolt)) return;
        for (ZoneControllerBlockEntity zone : ZoneRegistry.all(event.getLevel())) {
            ZoneBounds b = zone.bounds();
            if (b != null && b.columnDistance(bolt.getBlockX(), bolt.getBlockZ()) <= 2) zone.onLightning();
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
