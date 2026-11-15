package com.simpleautomation.test;

import com.simpleautomation.job.FisheryJob;
import com.simpleautomation.job.Task;
import com.simpleautomation.registry.ModBlocks;
import com.simpleautomation.registry.ModItems;
import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Tests de la pêcherie. Enregistrés depuis {@link SATests}. */
public final class FisheryTests {
    /** Objets que seule la partie « trésor » de la table de pêche vanilla donne (la canne, elle, existe aussi en déchet). */
    private static final Set<Item> TREASURE = Set.of(Items.ENCHANTED_BOOK, Items.NAME_TAG, Items.NAUTILUS_SHELL, Items.SADDLE, Items.BOW);

    /**
     * Un bassin de 3x3 dans la zone, deux golems avec une canne Appât III : du butin arrive au coffre, l'XP sort en
     * cristaux sans aucune orbe, les cannes s'usent, et le contrôleur signale qu'il n'y a pas d'eau libre (pas de trésor).
     */
    public static void catchesLootAndXp(GameTestHelper h) {
        ZoneControllerBlockEntity be = TestKit.floorAndZone(h, ModBlocks.FISHERY_CONTROLLER.get());
        for (int x = 2; x <= 4; x++)
            for (int z = 2; z <= 4; z++) {
                h.setBlock(x, 0, z, Blocks.STONE);
                h.setBlock(x, 1, z, Blocks.WATER);
            }
        for (BlockPos at : new BlockPos[] {new BlockPos(3, 2, 1), new BlockPos(3, 2, 5)}) {
            CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, at);
            golem.setItemInHand(InteractionHand.MAIN_HAND, TestKit.enchanted(h, Items.FISHING_ROD, Enchantments.LURE, 3));
        }
        h.onEachTick(() -> h.assertTrue(h.getEntities(EntityType.EXPERIENCE_ORB).isEmpty(), "the fishery spawned orbs"));
        h.succeedWhen(() -> {
            int crystals = TestKit.count(h, TestKit.CHEST, ModItems.XP_CRYSTAL.get());
            int caught = 0;
            ChestBlockEntity chest = TestKit.chest(h, TestKit.CHEST);
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack s = chest.getItem(i);
                if (!s.isEmpty() && !s.is(ModItems.XP_CRYSTAL.get())) caught += s.getCount();
            }
            int wear = 0;
            for (Worker w : be.workers()) wear += w.tool.getDamageValue();
            h.assertTrue(be.workers().size() == 2, "expected 2 workers, got " + be.workers().size());
            h.assertTrue(caught >= 3, "expected at least 3 caught items, got " + caught);
            h.assertTrue(crystals >= 1, "no experience crystal yet");
            h.assertTrue(wear >= 3, "the rods are barely worn: " + wear);
            h.assertTrue(TestKit.hasLine(TestKit.describe(be), "message.simple_automation.fishery.no_open_water"), "no open-water hint");
        });
    }

    /**
     * La règle de l'eau libre : 300 prises avec Chance de la mer III dans un grand bassin (7x7, 2 de profondeur) donnent
     * des trésors, 300 prises dans un bassin de 3x3 aucun. Avec Chance de la mer III, un trésor sort 11 fois sur 97
     * (poids 5 + 2×3, contre 82 poissons et 4 déchets) : aucun trésor en 300 prises a une probabilité de 0,887^300 ≈ 2e-16.
     * Appât III raccourcit l'attente de 300 ticks.
     */
    public static void openWaterTreasure(GameTestHelper h) {
        for (int x = 0; x <= 15; x++)
            for (int z = 0; z <= 15; z++) h.setBlock(x, 0, z, Blocks.STONE);
        // Grand bassin : eau en x,z 1..7 sur y 1..2, murs autour.
        pond(h, 1, 7);
        // Petit bassin : eau en x,z 10..12.
        pond(h, 10, 12);
        h.setBlock(14, 1, 14, ModBlocks.FISHERY_CONTROLLER.get());
        ZoneControllerBlockEntity be = TestKit.zone(h, new BlockPos(14, 1, 14));
        ItemStack rod = TestKit.enchanted(h, Items.FISHING_ROD, Enchantments.LUCK_OF_THE_SEA, 3);
        rod.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        Worker worker = Worker.recruit(UUID.randomUUID(), WeatherState.UNAFFECTED, true, rod, h.absolutePos(new BlockPos(4, 3, 0)), null, ItemStack.EMPTY);
        BlockPos open = h.absolutePos(new BlockPos(4, 2, 4));
        BlockPos closed = h.absolutePos(new BlockPos(11, 2, 11));
        h.runAfterDelay(2, () -> {
            h.assertTrue(FisheryJob.isOpenWater(h.getLevel(), open), "the 7x7 pond is not open water");
            h.assertTrue(!FisheryJob.isOpenWater(h.getLevel(), closed), "the 3x3 pond counts as open water");
            ItemStack lure = TestKit.enchanted(h, Items.FISHING_ROD, Enchantments.LURE, 3);
            int lureTicks = FisheryJob.lureTicks(be, h.getLevel(), lure);
            h.assertTrue(lureTicks == 300, "Lure III should save 300 ticks, got " + lureTicks);

            int closedTreasure = fish(be, worker, closed, 300);
            h.assertTrue(closedTreasure == 0, "treasure caught outside open water: " + closedTreasure);
            int openTreasure = fish(be, worker, open, 300);
            h.assertTrue(openTreasure > 0, "no treasure in open water after 300 catches");
            h.assertTrue(h.getEntities(EntityType.EXPERIENCE_ORB).isEmpty(), "the fishery spawned orbs");
            h.assertTrue(be.xp().stored() >= 600, "expected at least 1 xp per catch, got " + be.xp().stored());
            h.succeed();
        });
    }

    private static void pond(GameTestHelper h, int from, int to) {
        for (int x = from - 1; x <= to + 1; x++)
            for (int z = from - 1; z <= to + 1; z++)
                for (int y = 1; y <= 2; y++) {
                    boolean inside = x >= from && x <= to && z >= from && z <= to;
                    h.setBlock(x, y, z, inside ? Blocks.WATER : Blocks.STONE);
                }
    }

    /** Fait {@code times} prises sur {@code target} et compte les trésors (le contrôleur n'a pas de coffre : tout reste dans son tampon). */
    private static int fish(ZoneControllerBlockEntity be, Worker worker, BlockPos target, int times) {
        be.inventory().buffer().clear();
        for (int i = 0; i < times; i++) be.job().complete(be, worker, new Task(target, worker.stand));
        int treasure = 0;
        for (ItemStack s : be.inventory().buffer()) if (TREASURE.contains(s.getItem())) treasure += s.getCount();
        be.inventory().buffer().clear();
        return treasure;
    }

    private FisheryTests() {
    }
}
