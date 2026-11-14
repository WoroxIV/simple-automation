package com.simpleautomation.compat.jade;

import com.simpleautomation.SimpleAutomation;
import com.simpleautomation.pipe.PipeBlock;
import com.simpleautomation.zone.ZoneControllerBlock;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import net.minecraft.resources.Identifier;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Intégration Jade (optionnelle, découverte par Jade grâce à l'annotation) :
 * état du contrôleur regardé et de ses golems, mode et filtre du bras de pipe regardé.
 */
@WailaPlugin
public final class SAJadePlugin implements IWailaPlugin {
    public static final Identifier CONTROLLER = SimpleAutomation.id("controller");
    public static final Identifier PIPE = SimpleAutomation.id("pipe");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(ControllerDataProvider.INSTANCE, ZoneControllerBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ControllerComponentProvider.INSTANCE, ZoneControllerBlock.class);
        registration.registerBlockComponent(PipeComponentProvider.INSTANCE, PipeBlock.class);
    }
}
