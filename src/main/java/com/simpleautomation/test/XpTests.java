package com.simpleautomation.test;

import com.simpleautomation.registry.ModBlocks;
import com.simpleautomation.registry.ModItems;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
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

    /** 5 fers bruts fondus (0,7 XP chacun) : 3,5 XP arrivent au tampon, aucune orbe n'apparaît. */
    public static void forgeXpToBank(GameTestHelper h) {
        ZoneControllerBlockEntity be = TestKit.floorAndZone(h, ModBlocks.FORGE_CONTROLLER.get());
        h.setBlock(3, 2, 3, Blocks.FURNACE);
        ChestBlockEntity chest = TestKit.chest(h, TestKit.CHEST);
        chest.setItem(0, new ItemStack(Items.RAW_IRON, 5));
        chest.setItem(1, new ItemStack(Items.COAL, 1));
        h.spawn(EntityType.COPPER_GOLEM, new BlockPos(2, 2, 2));
        h.onEachTick(() -> h.assertTrue(h.getEntities(EntityType.EXPERIENCE_ORB).isEmpty(), "the forge spawned orbs"));
        h.succeedWhen(() -> {
            int ingots = TestKit.count(h, TestKit.CHEST, Items.IRON_INGOT);
            h.assertTrue(ingots == 5, "expected 5 iron ingots, got " + ingots);
            double xp = be.xp().stored() + 10 * TestKit.count(h, TestKit.CHEST, ModItems.XP_CRYSTAL.get());
            h.assertTrue(Math.abs(xp - 3.5) < 1.0E-3, "expected 3.5 xp, got " + xp);
        });
    }

    private XpTests() {
    }
}
