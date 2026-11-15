package com.simpleautomation.client;

import com.simpleautomation.SimpleAutomation;
import com.simpleautomation.network.WorkerInteractPayload;
import com.simpleautomation.registry.ModBlockEntities;
import com.simpleautomation.registry.ModMenus;
import com.simpleautomation.zone.PuppetBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = SimpleAutomation.MODID, dist = Dist.CLIENT)
public final class SimpleAutomationClient {
    public SimpleAutomationClient(IEventBus modBus) {
        PuppetBridge.HOLDER.set(new PuppetManager());
        modBus.addListener((RegisterMenuScreensEvent e) -> e.register(ModMenus.FILTER.get(), FilterScreen::new));
        modBus.addListener((RegisterMenuScreensEvent e) -> e.register(ModMenus.RECIPE_CARD.get(), RecipeCardScreen::new));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerBlockEntityRenderer(ModBlockEntities.PIPE.get(), PipeFilterRenderer::new));
        NeoForge.EVENT_BUS.addListener(SimpleAutomationClient::onInteract);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> PuppetManager.clear());
    }

    /**
     * Les marionnettes n'existent pas sur le serveur : on intercepte les clics dessus
     * et on envoie notre propre paquet à la place de l'interaction vanilla.
     */
    private static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof PuppetGolem puppet)) return;
        event.setCanceled(true);
        if (event.isUseItem()) {
            if (event.getHand() == InteractionHand.MAIN_HAND) {
                ClientPacketDistributor.sendToServer(new WorkerInteractPayload(puppet.controller, puppet.workerId));
                event.setSwingHand(true);
            } else {
                event.setSwingHand(false);
            }
        } else {
            event.setSwingHand(event.isAttack());
        }
    }
}
