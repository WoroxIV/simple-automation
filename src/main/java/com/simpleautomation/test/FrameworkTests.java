package com.simpleautomation.test;

import com.simpleautomation.pipe.PipeBlock;
import com.simpleautomation.pipe.PipeSide;
import com.simpleautomation.registry.ModBlocks;
import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Tests du socle commun : recrutement, libération, tampon du contrôleur. Enregistrés depuis {@link SATests}. */
public final class FrameworkTests {
    private static final BlockPos CONTROLLER = new BlockPos(6, 2, 3);

    /**
     * Un golem nommé, une fleur sur l'antenne, recruté ; on casse le contrôleur puis on le repose :
     * le golem libéré (orphelin) doit être recruté de nouveau, avec son nom et sa fleur, sans rien laisser au sol.
     */
    public static void recruitAfterControllerReplaced(GameTestHelper h) {
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 8; z++) h.setBlock(x, 1, z, Blocks.STONE);
        h.setBlock(1, 2, 1, ModBlocks.BORDER_MARKER.get());
        h.setBlock(5, 2, 5, ModBlocks.BORDER_MARKER.get());
        h.setBlock(CONTROLLER, ModBlocks.FARM_CONTROLLER.get());
        h.setBlock(7, 2, 3, Blocks.CHEST);
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(3, 2, 3));
        golem.setCustomName(Component.literal("Bob"));
        golem.setItemSlot(CopperGolem.EQUIPMENT_SLOT_ANTENNA, new ItemStack(Items.POPPY));
        AtomicBoolean replaced = new AtomicBoolean();

        h.runAfterDelay(45, () -> {
            h.assertTrue(controller(h).workers().size() == 1, "golem not recruited before the break");
            h.setBlock(CONTROLLER, Blocks.AIR);
            List<CopperGolem> golems = h.getEntities(EntityType.COPPER_GOLEM);
            h.assertTrue(golems.size() == 1, "expected one released golem, got " + golems.size());
            CopperGolem freed = golems.getFirst();
            h.assertTrue(freed.getTags().contains(ZoneControllerBlockEntity.ORPHAN_TAG), "released golem is not tagged orphan");
            h.assertTrue(freed.getCustomName() != null && freed.getCustomName().getString().equals("Bob"), "name lost on release");
            h.assertTrue(freed.getItemBySlot(CopperGolem.EQUIPMENT_SLOT_ANTENNA).is(Items.POPPY), "antenna lost on release");
            h.setBlock(CONTROLLER, ModBlocks.FARM_CONTROLLER.get());
            replaced.set(true);
        });
        h.succeedWhen(() -> {
            h.assertTrue(replaced.get(), "controller not replaced yet");
            ZoneControllerBlockEntity be = controller(h);
            h.assertTrue(be.workers().size() == 1, "golem not recruited again: " + be.workers().size() + " workers, bounds=" + be.bounds());
            Worker w = be.workers().getFirst();
            h.assertTrue(w.customName != null && w.customName.getString().equals("Bob"), "worker lost its name");
            h.assertTrue(w.antenna.is(Items.POPPY), "worker lost its antenna");
            h.assertTrue(h.getEntities(EntityType.COPPER_GOLEM).isEmpty(), "a vanilla golem is still there");
            h.assertTrue(h.getEntities(EntityType.ITEM).isEmpty(), "items were dropped");
        });
    }

    /** Contrôleur sans zone (rien ne vide son tampon), pipe en Extraction collée, coffre au bout : le tampon doit finir au coffre. */
    public static void controllerBufferExtractable(GameTestHelper h) {
        // En hauteur : hors de portée verticale des bornes des tests voisins, sinon ce contrôleur sans zone pourrait les prendre.
        final int Y = 14;
        h.setBlock(1, Y, 1, ModBlocks.QUARRY_CONTROLLER.get());
        h.setBlock(3, Y, 1, Blocks.CHEST);
        h.setBlock(2, Y, 1, ModBlocks.PIPE.get());
        BlockPos pipe = h.absolutePos(new BlockPos(2, Y, 1));
        PipeBlock.refresh(h.getLevel(), pipe);
        BlockState state = h.getLevel().getBlockState(pipe);
        h.assertTrue(PipeBlock.side(state, Direction.WEST) != PipeSide.NONE, "pipe does not connect to the controller");
        h.getLevel().setBlock(pipe, state.setValue(PipeBlock.PROPERTIES.get(Direction.WEST), PipeSide.EXTRACT), 3);
        ZoneControllerBlockEntity be = h.getBlockEntity(new BlockPos(1, Y, 1), ZoneControllerBlockEntity.class);
        be.inventory().buffer().add(new ItemStack(Items.COBBLESTONE, 32));
        be.inventory().buffer().add(new ItemStack(Items.IRON_INGOT, 5));

        h.succeedWhen(() -> {
            h.assertTrue(count(h, new BlockPos(3, Y, 1), Items.COBBLESTONE) == 32, "cobblestone did not reach the chest");
            h.assertTrue(count(h, new BlockPos(3, Y, 1), Items.IRON_INGOT) == 5, "iron did not reach the chest");
            h.assertTrue(be.inventory().buffer().isEmpty(), "controller buffer is not empty: " + be.inventory().buffer());
        });
    }

    private static ZoneControllerBlockEntity controller(GameTestHelper h) {
        return h.getBlockEntity(CONTROLLER, ZoneControllerBlockEntity.class);
    }

    private static int count(GameTestHelper h, BlockPos chestPos, Item item) {
        ChestBlockEntity chest = h.getBlockEntity(chestPos, ChestBlockEntity.class);
        int n = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack s = chest.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private FrameworkTests() {
    }
}
