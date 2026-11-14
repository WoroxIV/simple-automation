package com.simpleautomation.test;

import static com.simpleautomation.test.TestKit.zone;

import com.simpleautomation.job.Task;
import com.simpleautomation.registry.ModBlocks;
import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;

/** Tests de l'enclos et de la bergerie : objet tenu, astuce clôture, vitesse de tonte. Enregistrés depuis {@link SATests}. */
public final class AnimalTests {
    private static final BlockPos RANCH = new BlockPos(7, 2, 3);

    /**
     * Enclos avec seulement des poules : un golem qui tient du blé passe en « ? », pas celui qui tient des graines ni celui
     * qui tient une épée. Une vache arrive : le blé redevient utile. Elle repart : le « ? » revient.
     * Tous les animaux partis, n'importe quel objet est accepté (en attendant le troupeau).
     */
    public static void ranchFoodCheck(GameTestHelper h) {
        fencedRanch(h);
        for (int i = 0; i < 2; i++) h.spawn(EntityType.CHICKEN, new BlockPos(2 + i * 2, 2, 4)).setNoAi(true);
        UUID wheat = golem(h, new BlockPos(2, 2, 2), Items.WHEAT).getUUID();
        UUID seeds = golem(h, new BlockPos(3, 2, 2), Items.WHEAT_SEEDS).getUUID();
        UUID sword = golem(h, new BlockPos(4, 2, 2), Items.IRON_SWORD).getUUID();
        Cow[] cow = new Cow[1];

        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(zone(h, RANCH).workers().size() == 3, "golems not recruited");
                    h.assertTrue(worker(h, wheat).status == Worker.Status.NO_TOOL, "wheat with only chickens is not NO_TOOL");
                })
                .thenExecute(() -> {
                    h.assertTrue(worker(h, seeds).status != Worker.Status.NO_TOOL, "seeds with chickens are NO_TOOL");
                    h.assertTrue(worker(h, sword).status != Worker.Status.NO_TOOL, "a sword is NO_TOOL");
                    cow[0] = h.spawn(EntityType.COW, new BlockPos(3, 2, 4));
                    cow[0].setNoAi(true);
                })
                .thenWaitUntil(() -> h.assertTrue(worker(h, wheat).status != Worker.Status.NO_TOOL, "wheat is still NO_TOOL with a cow in the zone"))
                .thenExecute(() -> cow[0].discard())
                .thenWaitUntil(() -> h.assertTrue(worker(h, wheat).status == Worker.Status.NO_TOOL, "wheat is not NO_TOOL again after the cow left"))
                .thenExecute(() -> h.getEntities(EntityType.CHICKEN).forEach(Entity::discard))
                .thenWaitUntil(() -> {
                    ZoneControllerBlockEntity be = zone(h, RANCH);
                    h.assertTrue(worker(h, wheat).status != Worker.Status.NO_TOOL, "wheat is NO_TOOL in an empty zone");
                    h.assertTrue(be.job().acceptsTool(be, new ItemStack(Items.STICK)), "a stick is refused in an empty zone");
                    h.assertFalse(be.job().acceptsTool(be, ItemStack.EMPTY), "an empty hand is accepted in the ranch");
                })
                .thenSucceed();
    }

    /**
     * Enclos sans clôture, une vache juste dehors : le clic sur le contrôleur conseille une clôture.
     * On clôture le pourtour : le conseil disparaît.
     */
    public static void ranchFenceHint(GameTestHelper h) {
        TestKit.floorAndZone(h, ModBlocks.RANCH_CONTROLLER.get());
        h.spawn(EntityType.COW, new BlockPos(7, 2, 7)).setNoAi(true);
        String hint = "message.simple_automation.zone.hint_fence";

        h.startSequence()
                .thenWaitUntil(() -> {
                    ZoneControllerBlockEntity be = zone(h, TestKit.CONTROLLER);
                    h.assertTrue(be.bounds() != null, "zone not found");
                    h.assertTrue(TestKit.hasLine(TestKit.describe(be), hint), "no fence hint with a cow outside an open zone");
                })
                .thenExecute(() -> {
                    for (int i = 0; i <= 6; i++) {
                        fence(h, i, 0);
                        fence(h, i, 6);
                        fence(h, 0, i);
                        fence(h, 6, i);
                    }
                    h.assertFalse(TestKit.hasLine(TestKit.describe(zone(h, TestKit.CONTROLLER)), hint), "the fence hint stays once fenced");
                })
                .thenSucceed();
    }

    /**
     * Bergerie : cisailles Efficacité III → 55 % du temps de base. Un mouton, un champimeuh et un golem de neige dans la zone :
     * le mouton est tondu (laine au coffre), le champimeuh devient une vache (champignons au coffre), le golem de neige
     * garde sa citrouille, et les cisailles perdent un point par tonte.
     */
    public static void shearEfficiencyAndWear(GameTestHelper h) {
        TestKit.floorAndZone(h, ModBlocks.SHEPHERD_CONTROLLER.get());
        h.spawn(EntityType.SHEEP, new BlockPos(2, 2, 4)).setNoAi(true);
        h.spawn(EntityType.MOOSHROOM, new BlockPos(4, 2, 4)).setNoAi(true);
        SnowGolem snow = h.spawn(EntityType.SNOW_GOLEM, new BlockPos(4, 2, 2));
        snow.setNoAi(true);
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(2, 2, 2));
        golem.setItemInHand(InteractionHand.MAIN_HAND, TestKit.enchanted(h, Items.SHEARS, Enchantments.EFFICIENCY, 3));

        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(zone(h, TestKit.CONTROLLER).workers().size() == 1, "golem not recruited"))
                .thenExecute(() -> {
                    ZoneControllerBlockEntity be = zone(h, TestKit.CONTROLLER);
                    Task task = new Task(be.getBlockPos(), be.getBlockPos());
                    Worker w = be.workers().getFirst();
                    int fast = be.job().workTicks(be, w, task);
                    ItemStack own = w.tool;
                    w.tool = new ItemStack(Items.SHEARS);
                    int plain = be.job().workTicks(be, w, task);
                    w.tool = own;
                    h.assertTrue(plain == 20, "vanilla shears should take 20 ticks, got " + plain);
                    h.assertTrue(fast == 11, "Efficiency III shears should take 55% of 20 ticks (11), got " + fast);
                })
                .thenWaitUntil(() -> {
                    h.assertTrue(h.getEntities(EntityType.SHEEP).stream().allMatch(Sheep::isSheared), "the sheep is not sheared");
                    h.assertTrue(h.getEntities(EntityType.MOOSHROOM).isEmpty(), "the mooshroom was not sheared");
                    h.assertTrue(count(h, Items.WHITE_WOOL) >= 1, "no wool in the chest");
                    h.assertTrue(count(h, Items.RED_MUSHROOM) + count(h, Items.BROWN_MUSHROOM) >= 1, "no mushrooms in the chest");
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    h.assertTrue(!h.getEntities(EntityType.COW).isEmpty(), "the mooshroom did not turn into a cow");
                    h.assertTrue(snow.hasPumpkin(), "the snow golem was sheared");
                    int damage = zone(h, TestKit.CONTROLLER).workers().getFirst().tool.getDamageValue();
                    h.assertTrue(damage == 2, "expected 2 durability used (sheep + mooshroom), got " + damage);
                    List<MushroomCow> left = h.getEntities(EntityType.MOOSHROOM);
                    h.assertTrue(left.isEmpty(), "a mooshroom is back");
                })
                .thenSucceed();
    }

    private static void fence(GameTestHelper h, int x, int z) {
        BlockPos p = new BlockPos(x, 2, z);
        if (p.equals(TestKit.CONTROLLER)) return;
        if (h.getBlockState(p).isAir()) h.setBlock(p, Blocks.OAK_FENCE);
    }

    /** Enclos clôturé : bornes (1,2,1)–(5,2,5), clôture tout autour, contrôleur en (7,2,3), coffre en (8,2,3). */
    static void fencedRanch(GameTestHelper h) {
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 6; z++) h.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
        for (int i = 0; i <= 6; i++) {
            h.setBlock(i, 2, 0, Blocks.OAK_FENCE);
            h.setBlock(i, 2, 6, Blocks.OAK_FENCE);
            h.setBlock(0, 2, i, Blocks.OAK_FENCE);
            h.setBlock(6, 2, i, Blocks.OAK_FENCE);
        }
        h.setBlock(1, 2, 1, ModBlocks.BORDER_MARKER.get());
        h.setBlock(5, 2, 5, ModBlocks.BORDER_MARKER.get());
        h.setBlock(RANCH, ModBlocks.RANCH_CONTROLLER.get());
        h.setBlock(8, 2, 3, Blocks.CHEST);
    }

    private static CopperGolem golem(GameTestHelper h, BlockPos pos, Item tool) {
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, pos);
        golem.setNoAi(true);
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
        return golem;
    }

    private static Worker worker(GameTestHelper h, UUID id) {
        return TestKit.worker(h, zone(h, RANCH), w -> w.id.equals(id), id.toString());
    }

    private static int count(GameTestHelper h, Item item) {
        return TestKit.count(h, TestKit.CHEST, item);
    }

    private AnimalTests() {
    }
}
