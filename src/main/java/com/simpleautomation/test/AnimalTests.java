package com.simpleautomation.test;

import static com.simpleautomation.test.TestKit.zone;

import com.simpleautomation.job.Task;
import com.simpleautomation.registry.ModBlocks;
import com.simpleautomation.registry.ModItems;
import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.chicken.ChickenVariants;
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

/** Tests de l'étable : objet tenu, astuce clôture, tonte, traite, pinceau, œufs, XP d'abattage. Enregistrés depuis {@link SATests}. */
public final class AnimalTests {
    private static final BlockPos BARN = new BlockPos(7, 2, 3);

    /**
     * Étable avec seulement des poules : un golem qui tient du blé passe en « ? », pas celui qui tient des graines ni celui
     * qui tient une épée. Une vache arrive : le blé redevient utile. Elle repart : le « ? » revient.
     * Tous les animaux partis, n'importe quel objet est accepté (en attendant le troupeau).
     */
    public static void barnFoodCheck(GameTestHelper h) {
        fencedBarn(h);
        for (int i = 0; i < 2; i++) h.spawn(EntityType.CHICKEN, new BlockPos(2 + i * 2, 2, 4)).setNoAi(true);
        UUID wheat = golem(h, new BlockPos(2, 2, 2), Items.WHEAT).getUUID();
        UUID seeds = golem(h, new BlockPos(3, 2, 2), Items.WHEAT_SEEDS).getUUID();
        UUID sword = golem(h, new BlockPos(4, 2, 2), Items.IRON_SWORD).getUUID();
        Cow[] cow = new Cow[1];

        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(zone(h, BARN).workers().size() == 3, "golems not recruited");
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
                    ZoneControllerBlockEntity be = zone(h, BARN);
                    h.assertTrue(worker(h, wheat).status != Worker.Status.NO_TOOL, "wheat is NO_TOOL in an empty zone");
                    h.assertTrue(be.job().acceptsTool(be, new ItemStack(Items.STICK)), "a stick is refused in an empty zone");
                    h.assertFalse(be.job().acceptsTool(be, ItemStack.EMPTY), "an empty hand is accepted in the barn");
                })
                .thenSucceed();
    }

    /**
     * Étable sans clôture, une vache juste dehors : le clic sur le contrôleur conseille une clôture.
     * On clôture le pourtour : le conseil disparaît.
     */
    public static void barnFenceHint(GameTestHelper h) {
        TestKit.floorAndZone(h, ModBlocks.BARN_CONTROLLER.get());
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
     * Étable aux cisailles : cisailles Efficacité III → 55 % du temps de base. Un mouton, un champimeuh et un golem de neige dans la zone :
     * le mouton est tondu (laine au coffre), le champimeuh devient une vache (champignons au coffre), le golem de neige
     * garde sa citrouille, et les cisailles perdent un point par tonte.
     */
    public static void shearEfficiencyAndWear(GameTestHelper h) {
        TestKit.floorAndZone(h, ModBlocks.BARN_CONTROLLER.get());
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

    /**
     * Traite : 3 seaux vides au coffre, un golem qui tient un seau. Avec seulement un veau, rien ne se passe. Une chèvre
     * adulte arrive : les 3 seaux deviennent du lait, puis le golem passe en « ? » et le contrôleur réclame des seaux.
     */
    public static void barnMilks(GameTestHelper h) {
        TestKit.floorAndZone(h, ModBlocks.BARN_CONTROLLER.get());
        TestKit.chest(h, TestKit.CHEST).setItem(0, new ItemStack(Items.BUCKET, 3));
        Cow calf = h.spawn(EntityType.COW, new BlockPos(2, 2, 4));
        calf.setNoAi(true);
        calf.setBaby(true);
        golem(h, new BlockPos(2, 2, 2), Items.BUCKET);

        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(zone(h, TestKit.CONTROLLER).workers().size() == 1, "golem not recruited"))
                .thenIdle(100)
                .thenExecute(() -> {
                    h.assertTrue(count(h, Items.MILK_BUCKET) == 0, "a calf was milked");
                    h.assertTrue(count(h, Items.BUCKET) == 3, "buckets were used without an adult");
                    h.spawn(EntityType.GOAT, new BlockPos(4, 2, 4)).setNoAi(true);
                })
                .thenWaitUntil(() -> {
                    h.assertTrue(count(h, Items.MILK_BUCKET) == 3, "expected 3 milk buckets, got " + count(h, Items.MILK_BUCKET));
                    h.assertTrue(count(h, Items.BUCKET) == 0, "empty buckets left: " + count(h, Items.BUCKET));
                })
                .thenWaitUntil(() -> {
                    ZoneControllerBlockEntity be = zone(h, TestKit.CONTROLLER);
                    Worker w = be.workers().getFirst();
                    h.assertTrue(w.tool.is(Items.BUCKET), "the golem lost its bucket");
                    h.assertTrue(w.status == Worker.Status.NO_TOOL, "a bucket golem without empty buckets is " + w.status);
                    h.assertTrue(TestKit.hasLine(TestKit.describe(be), "message.simple_automation.zone.barn_no_buckets"), "no hint about empty buckets");
                })
                .thenSucceed();
    }

    /**
     * Pinceau : un bébé tatou n'est pas brossé. Un adulte arrive : 4 écailles au coffre (16 points d'usure chacune, le
     * pinceau de 64 points casse à la 4e), puis le golem n'a plus rien en main.
     */
    public static void barnBrushesArmadillo(GameTestHelper h) {
        TestKit.floorAndZone(h, ModBlocks.BARN_CONTROLLER.get());
        Armadillo baby = h.spawn(EntityType.ARMADILLO, new BlockPos(2, 2, 4));
        baby.setNoAi(true);
        baby.setBaby(true);
        golem(h, new BlockPos(2, 2, 2), Items.BRUSH);

        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(zone(h, TestKit.CONTROLLER).workers().size() == 1, "golem not recruited"))
                .thenIdle(100)
                .thenExecute(() -> {
                    h.assertTrue(count(h, Items.ARMADILLO_SCUTE) == 0, "a baby armadillo was brushed");
                    h.assertTrue(zone(h, TestKit.CONTROLLER).workers().getFirst().tool.getDamageValue() == 0, "the brush wore on a baby");
                    h.spawn(EntityType.ARMADILLO, new BlockPos(4, 2, 4)).setNoAi(true);
                })
                .thenWaitUntil(() -> {
                    h.assertTrue(count(h, Items.ARMADILLO_SCUTE) == 4, "expected 4 scutes, got " + count(h, Items.ARMADILLO_SCUTE));
                    Worker w = zone(h, TestKit.CONTROLLER).workers().getFirst();
                    h.assertTrue(w.tool.isEmpty(), "the brush did not break after 4 scutes");
                })
                .thenIdle(60)
                .thenExecute(() -> h.assertTrue(count(h, Items.ARMADILLO_SCUTE) == 4, "more scutes after the brush broke"))
                .thenSucceed();
    }

    /** Trois poules (tempérée, chaude, froide) sur le point de pondre : l'œuf, l'œuf brun et l'œuf bleu arrivent au coffre. */
    public static void barnCollectsEggs(GameTestHelper h) {
        TestKit.floorAndZone(h, ModBlocks.BARN_CONTROLLER.get());
        var variants = h.getLevel().registryAccess().lookupOrThrow(Registries.CHICKEN_VARIANT);
        int x = 2;
        for (var key : List.of(ChickenVariants.TEMPERATE, ChickenVariants.WARM, ChickenVariants.COLD)) {
            Chicken chicken = h.spawn(EntityType.CHICKEN, new BlockPos(x, 2, 3));
            x++;
            chicken.setNoAi(true);
            chicken.setVariant(variants.getOrThrow(key));
            chicken.eggTime = 5;
        }
        h.succeedWhen(() -> {
            h.assertTrue(count(h, Items.EGG) >= 1, "no egg in the chest");
            h.assertTrue(count(h, Items.BROWN_EGG) >= 1, "no brown egg in the chest");
            h.assertTrue(count(h, Items.BLUE_EGG) >= 1, "no blue egg in the chest");
            h.assertTrue(h.getEntities(EntityType.ITEM).isEmpty(), "eggs left on the ground");
        });
    }

    /**
     * Abattage : 20 poules (seuil 10), un golem à l'épée. Il en reste 10, et l'XP des 10 mortes (1 à 3 chacune, donc au
     * moins 10) arrive en cristaux, sans qu'aucune orbe n'apparaisse.
     */
    public static void barnCullXpToCrystals(GameTestHelper h) {
        fencedBarn(h);
        int n = 0;
        for (int px = 1; px <= 5 && n < 20; px++)
            for (int pz = 1; pz <= 5 && n < 20; pz++) {
                if ((px == 1 && pz == 1) || (px == 5 && pz == 5)) continue; // bornes
                h.spawn(EntityType.CHICKEN, new BlockPos(px, 2, pz)).setNoAi(true);
                n++;
            }
        golem(h, new BlockPos(3, 2, 3), Items.IRON_SWORD);
        BlockPos chest = new BlockPos(8, 2, 3);
        h.onEachTick(() -> h.assertTrue(h.getEntities(EntityType.EXPERIENCE_ORB).isEmpty(), "culling spawned orbs"));
        h.succeedWhen(() -> {
            int left = h.getEntities(EntityType.CHICKEN).size();
            h.assertTrue(left == 10, "expected 10 chickens left, got " + left);
            int crystals = TestKit.count(h, chest, ModItems.XP_CRYSTAL.get());
            double xp = zone(h, BARN).xp().stored() + 10 * crystals;
            h.assertTrue(crystals >= 1 && xp >= 10, "expected at least 10 xp with a crystal, got " + xp + " (" + crystals + " crystals)");
        });
    }

    private static void fence(GameTestHelper h, int x, int z) {
        BlockPos p = new BlockPos(x, 2, z);
        if (p.equals(TestKit.CONTROLLER)) return;
        if (h.getBlockState(p).isAir()) h.setBlock(p, Blocks.OAK_FENCE);
    }

    /** Étable clôturée : bornes (1,2,1)–(5,2,5), clôture tout autour, contrôleur en (7,2,3), coffre en (8,2,3). */
    static void fencedBarn(GameTestHelper h) {
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
        h.setBlock(BARN, ModBlocks.BARN_CONTROLLER.get());
        h.setBlock(8, 2, 3, Blocks.CHEST);
    }

    private static CopperGolem golem(GameTestHelper h, BlockPos pos, Item tool) {
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, pos);
        golem.setNoAi(true);
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
        return golem;
    }

    private static Worker worker(GameTestHelper h, UUID id) {
        return TestKit.worker(h, zone(h, BARN), w -> w.id.equals(id), id.toString());
    }

    private static int count(GameTestHelper h, Item item) {
        return TestKit.count(h, TestKit.CHEST, item);
    }

    private AnimalTests() {
    }
}
