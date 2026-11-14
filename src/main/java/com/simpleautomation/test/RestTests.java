package com.simpleautomation.test;

import static com.simpleautomation.test.TestKit.floorAndZone;
import static com.simpleautomation.test.TestKit.zone;

import com.simpleautomation.job.QuarryJob;
import com.simpleautomation.registry.ModBlocks;
import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Tests du repos : un golem sans travail rejoint un golem occupé, ou revient près du contrôleur. Enregistrés depuis {@link SATests}. */
public final class RestTests {

    /**
     * Forge à un seul four, qui cuit longtemps, et deux golems à la pelle : un seul peut s'en occuper à la fois.
     * L'autre, parti du coin opposé, doit venir se reposer à côté de celui qui travaille.
     */
    public static void idleRestsNearBusy(GameTestHelper h) {
        floorAndZone(h, ModBlocks.FORGE_CONTROLLER.get());
        BlockPos furnace = new BlockPos(5, 2, 1);
        h.setBlock(furnace, Blocks.FURNACE);
        TestKit.chest(h, TestKit.CHEST).setItem(0, new ItemStack(Items.RAW_IRON, 64));
        TestKit.chest(h, TestKit.CHEST).setItem(1, new ItemStack(Items.COAL, 16));
        golem(h, new BlockPos(4, 2, 1), Items.IRON_SHOVEL);
        golem(h, new BlockPos(1, 2, 5), Items.IRON_SHOVEL);

        h.succeedWhen(() -> {
            ZoneControllerBlockEntity be = zone(h, TestKit.CONTROLLER);
            h.assertTrue(be.workers().size() == 2, "expected 2 workers, got " + be.workers().size());
            Worker busy = null, idle = null;
            for (Worker w : be.workers()) {
                if (w.status == Worker.Status.MOVING || w.status == Worker.Status.WORKING) busy = w;
                else if (w.status == Worker.Status.IDLE && w.travelTicks == 0) idle = w;
            }
            h.assertTrue(busy != null && idle != null, "need one busy and one resting golem");
            int dx = Math.abs(busy.stand.getX() - idle.stand.getX());
            int dz = Math.abs(busy.stand.getZ() - idle.stand.getZ());
            h.assertTrue(Math.max(dx, dz) <= 2 && Math.abs(busy.stand.getY() - idle.stand.getY()) <= 2,
                    "the idle golem rests at " + h.relativePos(idle.stand) + ", far from the busy one at " + h.relativePos(busy.stand));
        });
    }

    /** Une ferme sans rien à faire : le golem du coin opposé revient près du contrôleur (jamais dessus). */
    public static void idleReturnsToController(GameTestHelper h) {
        floorAndZone(h, ModBlocks.FARM_CONTROLLER.get());
        golem(h, new BlockPos(1, 2, 5), null);

        h.succeedWhen(() -> {
            ZoneControllerBlockEntity be = zone(h, TestKit.CONTROLLER);
            h.assertTrue(be.workers().size() == 1, "golem not recruited");
            Worker w = be.workers().getFirst();
            h.assertTrue(w.travelTicks == 0, "still walking");
            assertNearController(h, be, w, 3);
        });
    }

    /**
     * Carrière 2×2 sur une couche de pierre posée au-dessus du sol du monde : le golem creuse jusqu'à la bedrock,
     * la carrière se dit finie et il remonte près du contrôleur au lieu de rester au fond du trou.
     */
    public static void quarryFinishedReturns(GameTestHelper h) {
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 6; z++) h.setBlock(x, 1, z, Blocks.STONE);
        h.setBlock(2, 2, 2, ModBlocks.BORDER_MARKER.get());
        h.setBlock(3, 2, 3, ModBlocks.BORDER_MARKER.get());
        BlockPos controller = new BlockPos(5, 2, 2);
        h.setBlock(controller, ModBlocks.QUARRY_CONTROLLER.get());
        h.setBlock(6, 2, 2, Blocks.CHEST);
        golem(h, new BlockPos(1, 2, 1), Items.DIAMOND_PICKAXE);

        h.succeedWhen(() -> {
            ZoneControllerBlockEntity be = zone(h, controller);
            h.assertTrue(be.workers().size() == 1, "golem not recruited");
            h.assertTrue(((QuarryJob) be.job()).isFinished(), "quarry not finished yet, layer " + ((QuarryJob) be.job()).layerY());
            Worker w = be.workers().getFirst();
            h.assertTrue(w.status == Worker.Status.IDLE && w.travelTicks == 0, "the golem is not resting yet: " + w.status);
            assertNearController(h, be, w, 3);
        });
    }

    private static void assertNearController(GameTestHelper h, ZoneControllerBlockEntity be, Worker w, int radius) {
        BlockPos c = be.getBlockPos();
        h.assertFalse(w.stand.equals(c), "the golem stands on the controller");
        int d = Math.max(Math.abs(w.stand.getX() - c.getX()), Math.abs(w.stand.getZ() - c.getZ()));
        h.assertTrue(d <= radius && Math.abs(w.stand.getY() - c.getY()) <= 2,
                "the golem rests at " + h.relativePos(w.stand) + ", not next to the controller at " + h.relativePos(c));
    }

    private static CopperGolem golem(GameTestHelper h, BlockPos pos, net.minecraft.world.item.@org.jspecify.annotations.Nullable Item tool) {
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, pos);
        golem.setNoAi(true);
        if (tool != null) golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
        return golem;
    }

    private RestTests() {
    }
}
