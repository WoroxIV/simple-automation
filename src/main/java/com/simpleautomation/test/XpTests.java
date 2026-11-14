package com.simpleautomation.test;

import com.simpleautomation.registry.ModBlocks;
import com.simpleautomation.registry.ModItems;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.TagValueInput;

/** Tests de l'expérience en cristaux. Enregistrés depuis {@link SATests}. */
public final class XpTests {

    /**
     * 25,5 XP versés à une zone : 2 cristaux arrivent au coffre, 5,5 XP restent en attente, et ce reste survit
     * à une sauvegarde suivie d'un rechargement.
     */
    public static void bankMakesCrystals(GameTestHelper h) {
        ZoneControllerBlockEntity be = TestKit.floorAndZone(h, ModBlocks.FORGE_CONTROLLER.get());
        be.xp().add(25.5);
        h.succeedWhen(() -> {
            int crystals = TestKit.count(h, TestKit.CHEST, ModItems.XP_CRYSTAL.get());
            h.assertTrue(crystals == 2, "expected 2 crystals, got " + crystals);
            var registries = h.getLevel().registryAccess();
            var tag = be.saveCustomOnly(registries);
            be.xp().take(100);
            be.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
            h.assertTrue(Math.abs(be.xp().stored() - 5.5) < 1.0E-6, "expected 5.5 xp left, got " + be.xp().stored());
            h.assertTrue(h.getEntities(EntityType.EXPERIENCE_ORB).isEmpty(), "orbs were spawned");
        });
    }

    private XpTests() {
    }
}
