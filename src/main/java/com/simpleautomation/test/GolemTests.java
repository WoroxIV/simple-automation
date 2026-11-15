package com.simpleautomation.test;

import static com.simpleautomation.test.TestKit.floorAndZone;
import static com.simpleautomation.test.TestKit.zone;

import com.simpleautomation.SAConfig;
import com.simpleautomation.job.GolemTools;
import com.simpleautomation.pipe.PipeBlock;
import com.simpleautomation.pipe.PipeBlockEntity;
import com.simpleautomation.pipe.filter.ItemFilter;
import com.simpleautomation.registry.ModBlocks;
import com.simpleautomation.registry.ModDataComponents;
import com.simpleautomation.registry.ModItems;
import com.simpleautomation.zone.WorkBudget;
import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.ZoneControllerBlock;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import com.simpleautomation.zone.ZoneStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Tests des golems eux-mêmes (oxydation, cire, outil, vitesse) et du contrôleur (pause, rien de perdu, budget).
 * Enregistrés depuis {@link SATests}.
 */
public final class GolemTests {

    /**
     * L'oxydation avance avec le temps : exposé, puis oxydé (statue, plus de travail). Chaque cran ralentit le golem.
     * Un golem ciré ne bouge plus.
     */
    public static void oxidizesOverTime(GameTestHelper h) {
        floorAndZone(h, ModBlocks.FARM_CONTROLLER.get());
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(3, 2, 3));
        golem.setNoAi(true);

        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(zone(h, TestKit.CONTROLLER).workers().size() == 1, "golem not recruited"))
                .thenExecute(() -> {
                    Worker w = zone(h, TestKit.CONTROLLER).workers().getFirst();
                    h.assertTrue(w.weather == WeatherState.UNAFFECTED, "the golem starts oxidized: " + w.weather);
                    w.nextWeatherTick = h.getLevel().getGameTime(); // le prochain stade est dû maintenant
                })
                .thenWaitUntil(() -> h.assertTrue(zone(h, TestKit.CONTROLLER).workers().getFirst().weather == WeatherState.EXPOSED, "not exposed yet"))
                .thenExecute(() -> {
                    Worker w = zone(h, TestKit.CONTROLLER).workers().getFirst();
                    h.assertTrue(w.nextWeatherTick > h.getLevel().getGameTime() + 400000, "the next stage is not days away: " + w.nextWeatherTick);
                    w.weather = WeatherState.WEATHERED;
                    w.nextWeatherTick = h.getLevel().getGameTime();
                })
                .thenWaitUntil(() -> {
                    Worker w = zone(h, TestKit.CONTROLLER).workers().getFirst();
                    h.assertTrue(w.weather == WeatherState.OXIDIZED, "not oxidized yet");
                    h.assertTrue(w.status == Worker.Status.FROZEN, "an oxidized golem is not frozen: " + w.status);
                })
                .thenExecute(() -> {
                    Worker w = zone(h, TestKit.CONTROLLER).workers().getFirst();
                    // Un golem ciré, même dû, ne s'oxyde plus.
                    w.weather = WeatherState.EXPOSED;
                    w.waxed = true;
                    w.nextWeatherTick = h.getLevel().getGameTime();
                })
                .thenIdle(45)
                .thenExecute(() -> {
                    h.assertTrue(zone(h, TestKit.CONTROLLER).workers().getFirst().weather == WeatherState.EXPOSED, "a waxed golem oxidized");
                    // Chaque cran ralentit : temps pour une pierre à la pioche en fer.
                    int fresh = ticks(h, WeatherState.UNAFFECTED, new ItemStack(Items.IRON_PICKAXE), Blocks.OBSIDIAN.defaultBlockState());
                    int exposed = ticks(h, WeatherState.EXPOSED, new ItemStack(Items.IRON_PICKAXE), Blocks.OBSIDIAN.defaultBlockState());
                    int weathered = ticks(h, WeatherState.WEATHERED, new ItemStack(Items.IRON_PICKAXE), Blocks.OBSIDIAN.defaultBlockState());
                    h.assertTrue(fresh < exposed && exposed < weathered, "oxidation does not slow down: " + fresh + " / " + exposed + " / " + weathered);
                })
                .thenSucceed();
    }

    /**
     * Ce que fait le paquet du clic droit sur une marionnette ({@link ZoneControllerBlockEntity#interact}) :
     * cirer au rayon de miel (une seule fois), gratter à la hache accroupi (la cire, puis un cran d'oxydation), donner un outil,
     * le reprendre main vide. Un outil qui ne convient pas met le golem en « ? ».
     */
    public static void waxScrapeAndTool(GameTestHelper h) {
        floorAndZone(h, ModBlocks.FARM_CONTROLLER.get());
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(3, 2, 3));
        golem.setNoAi(true);
        FakePlayer player = TestKit.player(h, new BlockPos(3, 2, 2));
        player.getInventory().clearContent();

        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(zone(h, TestKit.CONTROLLER).workers().size() == 1, "golem not recruited"))
                .thenExecute(() -> {
                    ZoneControllerBlockEntity be = zone(h, TestKit.CONTROLLER);
                    Worker w = be.workers().getFirst();
                    player.snapTo(w.stand.getX() + 0.5, w.stand.getY(), w.stand.getZ() + 1.5, 0.0F, 0.0F);

                    // Cire : un rayon de miel consommé, pas un deuxième.
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.HONEYCOMB, 2));
                    be.interact(player, w.id);
                    h.assertTrue(w.waxed, "the honeycomb did not wax the golem");
                    h.assertTrue(player.getMainHandItem().getCount() == 1, "the honeycomb was not used");
                    be.interact(player, w.id);
                    h.assertTrue(player.getMainHandItem().getCount() == 1, "a second honeycomb was used on a waxed golem");

                    // Hache, accroupi : la cire part, puis un cran d'oxydation ; la hache s'use.
                    player.setShiftKeyDown(true);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
                    be.interact(player, w.id);
                    h.assertFalse(w.waxed, "the axe did not scrape the wax");
                    w.weather = WeatherState.EXPOSED;
                    be.interact(player, w.id);
                    h.assertTrue(w.weather == WeatherState.UNAFFECTED, "the axe did not scrape the oxidation: " + w.weather);
                    h.assertTrue(player.getMainHandItem().getDamageValue() == 2, "the axe did not wear twice: " + player.getMainHandItem().getDamageValue());
                    be.interact(player, w.id);
                    h.assertTrue(player.getMainHandItem().getDamageValue() == 2, "scraping a clean golem wore the axe");
                    h.assertTrue(w.tool.isEmpty(), "scraping gave the axe to the golem");
                    player.setShiftKeyDown(false);

                    // Une pioche dans une ferme : donnée au golem, qui passe en « ? ».
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
                    be.interact(player, w.id);
                    h.assertTrue(w.tool.is(Items.IRON_PICKAXE), "the pickaxe was not given");
                    h.assertTrue(player.getMainHandItem().isEmpty(), "the pickaxe stayed in the hand");
                })
                .thenWaitUntil(() -> h.assertTrue(zone(h, TestKit.CONTROLLER).workers().getFirst().status == Worker.Status.NO_TOOL, "a farm golem with a pickaxe is not NO_TOOL"))
                .thenExecute(() -> {
                    ZoneControllerBlockEntity be = zone(h, TestKit.CONTROLLER);
                    Worker w = be.workers().getFirst();
                    // Une houe à la place : la pioche revient au joueur.
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
                    be.interact(player, w.id);
                    h.assertTrue(w.tool.is(Items.IRON_HOE), "the hoe was not given");
                    h.assertTrue(player.getInventory().contains(new ItemStack(Items.IRON_PICKAXE)), "the old pickaxe did not come back");
                })
                .thenWaitUntil(() -> h.assertTrue(zone(h, TestKit.CONTROLLER).workers().getFirst().status != Worker.Status.NO_TOOL, "a farm golem with a hoe is NO_TOOL"))
                .thenExecute(() -> {
                    ZoneControllerBlockEntity be = zone(h, TestKit.CONTROLLER);
                    Worker w = be.workers().getFirst();
                    // Main vide : la houe est reprise.
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    be.interact(player, w.id);
                    h.assertTrue(w.tool.isEmpty() && player.getMainHandItem().is(Items.IRON_HOE), "the hoe was not taken back");
                    player.getInventory().clearContent();
                })
                .thenSucceed();
    }

    /**
     * Le coffre est plein (27 piles d'un autre objet) et la carrière casse une douzaine de sortes de pierres :
     * le tampon se remplit, la zone passe en pause et plus rien n'est creusé, sans rien jeter au sol.
     * On vide le coffre : le tampon s'y range et la zone repart.
     */
    public static void pausesWhenChestsFull(GameTestHelper h) {
        floorAndZone(h, ModBlocks.QUARRY_CONTROLLER.get());
        Block[] kinds = {Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE, Blocks.TUFF, Blocks.CALCITE, Blocks.DEEPSLATE,
                Blocks.NETHERRACK, Blocks.END_STONE, Blocks.BLACKSTONE, Blocks.SANDSTONE, Blocks.TERRACOTTA, Blocks.DRIPSTONE_BLOCK};
        int i = 0;
        for (int x = 1; x <= 5; x++)
            for (int z = 1; z <= 5; z++) h.setBlock(x, 1, z, kinds[i++ % kinds.length]);
        ChestBlockEntity chest = TestKit.chest(h, TestKit.CHEST);
        for (int s = 0; s < chest.getContainerSize(); s++) chest.setItem(s, new ItemStack(Items.OBSIDIAN, 64));
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(3, 2, 3));
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        int[] dug = new int[1];

        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(status(h) == ZoneStatus.PAUSED, "the zone is not paused: " + status(h)
                        + ", buffer " + zone(h, TestKit.CONTROLLER).inventory().buffer().size()))
                .thenExecute(() -> dug[0] = airInLayer(h))
                .thenIdle(80)
                .thenExecute(() -> {
                    h.assertTrue(status(h) == ZoneStatus.PAUSED, "the pause did not last");
                    h.assertTrue(airInLayer(h) == dug[0], "golems kept digging while paused: " + dug[0] + " -> " + airInLayer(h));
                    h.assertTrue(dug[0] < 25, "the whole layer was dug before the pause");
                    h.assertTrue(h.getEntities(EntityType.ITEM).isEmpty(), "items were dropped");
                    for (Worker w : zone(h, TestKit.CONTROLLER).workers())
                        h.assertFalse(w.status == Worker.Status.MOVING || w.status == Worker.Status.WORKING, "a golem works while paused");
                    chest.clearContent();
                })
                .thenWaitUntil(() -> {
                    h.assertTrue(status(h) != ZoneStatus.PAUSED, "still paused after emptying the chest");
                    h.assertTrue(airInLayer(h) == 25, "the layer was not finished after the pause");
                })
                .thenExecute(() -> {
                    h.assertTrue(h.getEntities(EntityType.ITEM).isEmpty(), "items were dropped");
                    // La cobble (et la cobble de deepslate, du même tag) a pu repartir sous les pieds des golems : on compte les autres pierres.
                    h.assertTrue(zone(h, TestKit.CONTROLLER).inventory().buffer().isEmpty(), "the buffer was not emptied into the chest");
                    StringBuilder missing = new StringBuilder();
                    for (Item item : new Item[] {Items.GRANITE, Items.DIORITE, Items.ANDESITE, Items.TUFF, Items.CALCITE,
                            Items.NETHERRACK, Items.END_STONE, Items.BLACKSTONE, Items.SANDSTONE, Items.TERRACOTTA, Items.DRIPSTONE_BLOCK})
                        if (TestKit.count(h, TestKit.CHEST, item) == 0) missing.append(item).append(' ');
                    h.assertTrue(missing.isEmpty(), "missing from the chest: " + missing);
                })
                .thenSucceed();
    }

    /**
     * Règle blockDrops désactivée : on casse un contrôleur dont le tampon est plein et dont le golem tient une houe,
     * puis une pipe qui porte un filtre. Le tampon, la houe et le filtre tombent quand même au sol ; le golem est libéré.
     */
    public static void breakDropsWithoutBlockDrops(GameTestHelper h) {
        floorAndZone(h, ModBlocks.FARM_CONTROLLER.get());
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(3, 2, 3));
        golem.setNoAi(true);
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
        BlockPos pipePos = new BlockPos(7, 2, 5);
        h.setBlock(7, 2, 6, Blocks.CHEST);
        h.setBlock(pipePos, ModBlocks.PIPE.get());
        PipeBlock.refresh(h.getLevel(), h.absolutePos(pipePos));
        ItemStack filter = new ItemStack(ModItems.FILTER.get());
        filter.set(ModDataComponents.FILTER.get(), new ItemFilter(List.of(new ItemFilter.Entry(Items.DIAMOND, Optional.empty())), false));
        h.getBlockEntity(pipePos, PipeBlockEntity.class).setFilter(Direction.SOUTH, filter);

        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(zone(h, TestKit.CONTROLLER).workers().size() == 1, "golem not recruited"))
                .thenExecute(() -> {
                    ZoneControllerBlockEntity be = zone(h, TestKit.CONTROLLER);
                    // Coffre retiré : le tampon reste dans le contrôleur.
                    h.setBlock(TestKit.CHEST, Blocks.AIR);
                    be.inventory().buffer().add(new ItemStack(Items.WHEAT, 20));
                    be.inventory().buffer().add(new ItemStack(Items.CARROT, 7));
                    h.getLevel().destroyBlock(h.absolutePos(TestKit.CONTROLLER), true);
                    h.getLevel().destroyBlock(h.absolutePos(pipePos), true);
                    h.assertItemEntityCountIs(Items.WHEAT, TestKit.CONTROLLER, 3.0, 20);
                    h.assertItemEntityCountIs(Items.CARROT, TestKit.CONTROLLER, 3.0, 7);
                    h.assertItemEntityPresent(Items.IRON_HOE, TestKit.CONTROLLER, 5.0);
                    h.assertItemEntityPresent(ModItems.FILTER.get(), pipePos, 2.0);
                    h.assertItemEntityNotPresent(ModBlocks.FARM_CONTROLLER.get().asItem(), TestKit.CONTROLLER, 3.0);
                    List<CopperGolem> golems = h.getEntities(EntityType.COPPER_GOLEM);
                    h.assertTrue(golems.size() == 1, "the golem was not released");
                    golems.getFirst().setNoAi(true);
                    h.killAllEntitiesOfClass(ItemEntity.class);
                })
                .thenSucceed();
    }

    /** Efficacité V raccourcit le temps par bloc (plancher compris) ; sur de l'obsidienne, l'écart est net. */
    public static void efficiencySpeedsUp(GameTestHelper h) {
        ItemStack plain = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack fast = TestKit.enchanted(h, Items.DIAMOND_PICKAXE, Enchantments.EFFICIENCY, 5);
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        int slow = ticks(h, WeatherState.UNAFFECTED, plain, obsidian);
        int quick = ticks(h, WeatherState.UNAFFECTED, fast, obsidian);
        h.assertTrue(slow == 188, "a diamond pickaxe should take 188 ticks on obsidian (1500 / 8), got " + slow);
        h.assertTrue(quick == 45, "Efficiency V should take 45 ticks on obsidian (1500 / 34), got " + quick);
        int floor = ticks(h, WeatherState.UNAFFECTED, fast, Blocks.STONE.defaultBlockState());
        h.assertTrue(floor == SAConfig.MIN_TICKS_PER_BLOCK.getAsInt(), "the floor of " + SAConfig.MIN_TICKS_PER_BLOCK.getAsInt() + " ticks was not applied: " + floor);
        h.succeed();
    }

    /**
     * Budget global : une fois les blocs du tick consommés, plus rien ; une tâche plus grosse que tout le budget passe
     * quand même sur un budget plein (un gros arbre ne doit pas rester bloqué).
     */
    public static void workBudgetCap(GameTestHelper h) {
        int max = SAConfig.GLOBAL_BLOCKS_PER_TICK.getAsInt();
        try {
            WorkBudget.reset();
            h.assertTrue(WorkBudget.tryConsume(max - 1), "the budget refused less than its size");
            h.assertTrue(WorkBudget.tryConsume(1), "the last block was refused");
            h.assertFalse(WorkBudget.tryConsume(1), "a block above the budget was accepted");
            h.assertTrue(WorkBudget.tryConsume(0), "a free task was refused");
            WorkBudget.reset();
            h.assertTrue(WorkBudget.tryConsume(max + 100), "a task bigger than the whole budget never passes");
            h.assertFalse(WorkBudget.tryConsume(1), "the oversized task did not use the whole budget");
        } finally {
            WorkBudget.reset(); // les zones des autres tests retrouvent leur budget pour ce tick
        }
        h.succeed();
    }

    /**
     * L'objet tenu est réévalué dès qu'il change, même sans passer par un joueur : des cisailles qui cassent (pile vidée sur place)
     * ou un bâton à leur place mettent le golem de l'étable en « ? » au tick suivant ; de nouvelles cisailles l'en sortent.
     * Une vache dans la zone : sans animal, l'étable accepte n'importe quel objet en attendant le troupeau.
     */
    public static void toolChangeRechecked(GameTestHelper h) {
        floorAndZone(h, ModBlocks.BARN_CONTROLLER.get());
        h.spawn(EntityType.COW, new BlockPos(2, 2, 4)).setNoAi(true);
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, new BlockPos(3, 2, 3));
        golem.setNoAi(true);
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));

        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(zone(h, TestKit.CONTROLLER).workers().size() == 1, "golem not recruited"))
                .thenIdle(30)
                .thenExecute(() -> {
                    Worker w = zone(h, TestKit.CONTROLLER).workers().getFirst();
                    h.assertTrue(w.status == Worker.Status.IDLE, "a barn golem with shears is " + w.status);
                    w.tool.shrink(1); // cassées sur place : la même pile, vidée
                })
                .thenExecuteAfter(2, () -> {
                    Worker w = zone(h, TestKit.CONTROLLER).workers().getFirst();
                    h.assertTrue(w.status == Worker.Status.NO_TOOL, "broken shears were not noticed: " + w.status);
                    w.tool = new ItemStack(Items.SHEARS);
                })
                .thenExecuteAfter(2, () -> {
                    Worker w = zone(h, TestKit.CONTROLLER).workers().getFirst();
                    h.assertTrue(w.status == Worker.Status.IDLE, "new shears were not noticed: " + w.status);
                    w.tool = new ItemStack(Items.STICK);
                })
                .thenExecuteAfter(2, () -> {
                    Worker w = zone(h, TestKit.CONTROLLER).workers().getFirst();
                    h.assertTrue(w.status == Worker.Status.NO_TOOL, "a stick was not noticed: " + w.status);
                })
                .thenSucceed();
    }

    /** Ticks qu'il faut à un golem dans cet état, avec cet outil, pour casser ce bloc. */
    private static int ticks(GameTestHelper h, WeatherState weather, ItemStack tool, BlockState state) {
        Worker w = Worker.recruit(UUID.randomUUID(), weather, false, tool, h.absolutePos(new BlockPos(1, 1, 1)), null, ItemStack.EMPTY);
        return GolemTools.ticksFor(h.getLevel(), w, h.absolutePos(new BlockPos(1, 1, 1)), state);
    }

    private static ZoneStatus status(GameTestHelper h) {
        return h.getBlockState(TestKit.CONTROLLER).getValue(ZoneControllerBlock.STATUS);
    }

    private static int airInLayer(GameTestHelper h) {
        return TestKit.countBlocks(h, Blocks.AIR, new BlockPos(1, 1, 1), new BlockPos(5, 1, 5));
    }

    private GolemTests() {
    }
}
