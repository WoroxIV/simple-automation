package com.simpleautomation.test;

import static com.simpleautomation.test.TestKit.floorAndZone;
import static com.simpleautomation.test.TestKit.zone;

import com.simpleautomation.registry.ModBlocks;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Tests du recrutement : un golem tout juste construit est embauché seul, un golem de la sauvegarde ou libéré à la main
 * demande une laisse, l'orphelin d'un contrôleur cassé est repris sans laisse. Enregistrés depuis {@link SATests}.
 */
public final class RecruitTests {

    /**
     * Deux golems dans la zone : l'un sans marque de naissance (chargé d'une vieille sauvegarde), l'autre né il y a longtemps.
     * Aucun n'est recruté ; on passe une laisse au premier : il l'est.
     */
    public static void skipsOldGolem(GameTestHelper h) {
        floorAndZone(h, ModBlocks.FARM_CONTROLLER.get());
        CopperGolem untagged = still(h, new BlockPos(2, 2, 2));
        TestKit.forgetBirth(untagged);
        CopperGolem old = still(h, new BlockPos(4, 2, 4));
        old.getPersistentData().putLong(ZoneControllerBlockEntity.BIRTH_KEY, h.getLevel().getGameTime() - 1000);

        h.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    ZoneControllerBlockEntity be = zone(h, TestKit.CONTROLLER);
                    h.assertTrue(be.bounds() != null, "zone not found");
                    h.assertTrue(be.workers().isEmpty(), "an old golem was recruited without a lead: " + be.workers().size());
                    h.assertTrue(untagged.isAlive() && old.isAlive(), "a golem disappeared");
                    leash(h, untagged);
                })
                .thenWaitUntil(() -> {
                    ZoneControllerBlockEntity be = zone(h, TestKit.CONTROLLER);
                    h.assertTrue(be.workers().size() == 1, "the leashed golem was not recruited: " + be.workers().size());
                    h.assertTrue(old.isAlive(), "the old golem was recruited too");
                })
                .thenSucceed();
    }

    /**
     * Un golem qui apparaît reçoit sa marque de naissance et il est recruté sans laisse ; l'orphelin d'un contrôleur cassé
     * aussi, même sans marque.
     */
    public static void freshAndOrphan(GameTestHelper h) {
        floorAndZone(h, ModBlocks.FARM_CONTROLLER.get());
        CopperGolem fresh = still(h, new BlockPos(2, 2, 2));
        h.assertTrue(fresh.getPersistentData().getLong(ZoneControllerBlockEntity.BIRTH_KEY).isPresent(), "a spawned golem has no birth mark");
        CopperGolem orphan = still(h, new BlockPos(4, 2, 4));
        TestKit.forgetBirth(orphan);
        orphan.addTag(ZoneControllerBlockEntity.ORPHAN_TAG);

        h.succeedWhen(() -> {
            ZoneControllerBlockEntity be = zone(h, TestKit.CONTROLLER);
            h.assertTrue(be.workers().size() == 2, "expected the fresh golem and the orphan, got " + be.workers().size() + " workers");
            h.assertTrue(h.getEntities(EntityType.COPPER_GOLEM).isEmpty(), "a vanilla golem is still there");
        });
    }

    /** Un golem tout juste construit mais marqué « libéré à la main » attend une laisse. */
    public static void freedNeedsLead(GameTestHelper h) {
        floorAndZone(h, ModBlocks.FARM_CONTROLLER.get());
        CopperGolem golem = still(h, new BlockPos(3, 2, 3));
        golem.addTag(ZoneControllerBlockEntity.FREED_TAG);

        h.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    h.assertTrue(zone(h, TestKit.CONTROLLER).workers().isEmpty(), "a freed golem was recruited without a lead");
                    leash(h, golem);
                })
                .thenWaitUntil(() -> h.assertTrue(zone(h, TestKit.CONTROLLER).workers().size() == 1, "the leashed freed golem was not recruited"))
                .thenSucceed();
    }

    /**
     * Un ouvrier à la houe ; un joueur accroupi, main vide, le libère : la houe lui revient, un golem vanilla marqué
     * « libéré » réapparaît, et il n'est pas réembauché sans laisse.
     */
    public static void releaseByHandNeedsLead(GameTestHelper h) {
        floorAndZone(h, ModBlocks.FARM_CONTROLLER.get());
        CopperGolem golem = still(h, new BlockPos(3, 2, 3));
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
        FakePlayer player = TestKit.player(h, new BlockPos(3, 2, 2));

        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(zone(h, TestKit.CONTROLLER).workers().size() == 1, "golem not recruited"))
                .thenExecute(() -> {
                    ZoneControllerBlockEntity be = zone(h, TestKit.CONTROLLER);
                    player.setShiftKeyDown(true);
                    be.interact(player, be.workers().getFirst().id);
                    h.assertTrue(be.workers().isEmpty(), "the worker was not released");
                    h.assertTrue(player.getInventory().contains(new ItemStack(Items.IRON_HOE)), "the hoe was not given back");
                    List<CopperGolem> golems = h.getEntities(EntityType.COPPER_GOLEM);
                    h.assertTrue(golems.size() == 1, "expected one released golem, got " + golems.size());
                    h.assertTrue(golems.getFirst().getTags().contains(ZoneControllerBlockEntity.FREED_TAG), "the released golem is not tagged freed");
                    golems.getFirst().setNoAi(true);
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    h.assertTrue(zone(h, TestKit.CONTROLLER).workers().isEmpty(), "the released golem was recruited again without a lead");
                    h.assertTrue(h.getEntities(EntityType.COPPER_GOLEM).size() == 1, "the released golem disappeared");
                })
                .thenSucceed();
    }

    /** Un changement de dimension refait apparaître le golem : sa marque de naissance ne doit pas rajeunir. */
    public static void markBornKeepsFirst(GameTestHelper h) {
        CopperGolem golem = still(h, new BlockPos(3, 2, 3));
        TestKit.forgetBirth(golem);
        ZoneControllerBlockEntity.markBorn(golem, 100L);
        ZoneControllerBlockEntity.markBorn(golem, 5000L);
        long birth = golem.getPersistentData().getLong(ZoneControllerBlockEntity.BIRTH_KEY).orElse(-1L);
        h.assertTrue(birth == 100L, "the birth mark changed: " + birth);
        golem.discard();
        h.succeed();
    }

    /**
     * Un golem d'avant la mise à jour (sans marque) passe un portail : sa copie entre dans le monde comme un golem neuf.
     * Elle ne doit pas être recrutée sans laisse.
     */
    public static void oldGolemThroughPortal(GameTestHelper h) {
        floorAndZone(h, ModBlocks.FARM_CONTROLLER.get());
        CopperGolem golem = still(h, new BlockPos(3, 2, 3));
        TestKit.forgetBirth(golem);
        // Ce que fait Entity.teleport vers une autre dimension : l'événement, puis la copie qui rejoint le monde (markBorn).
        h.assertTrue(net.neoforged.neoforge.common.CommonHooks.onTravelToDimension(golem, net.minecraft.world.level.Level.NETHER), "travel cancelled");
        ZoneControllerBlockEntity.markBorn(golem, h.getLevel().getGameTime());

        h.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    h.assertTrue(zone(h, TestKit.CONTROLLER).workers().isEmpty(), "an old golem was recruited without a lead after a portal");
                    h.assertTrue(golem.isAlive(), "the golem disappeared");
                })
                .thenSucceed();
    }

    /** Un golem immobile (pas d'IA : il ne sort pas de la zone en se promenant). */
    private static CopperGolem still(GameTestHelper h, BlockPos pos) {
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, pos);
        golem.setNoAi(true);
        return golem;
    }

    /** Un joueur, juste à côté, tient le golem en laisse. */
    private static void leash(GameTestHelper h, CopperGolem golem) {
        FakePlayer player = TestKit.player(h, h.relativePos(golem.blockPosition()));
        golem.setLeashedTo(player, true);
        h.assertTrue(golem.getLeashHolder() == player, "the lead did not hold");
    }

    private RecruitTests() {
    }
}
