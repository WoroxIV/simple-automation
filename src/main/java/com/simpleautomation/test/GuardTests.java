package com.simpleautomation.test;

import com.simpleautomation.registry.ModBlocks;
import com.simpleautomation.registry.ModItems;
import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Tests du poste de garde. Enregistrés depuis {@link SATests}. */
public final class GuardTests {
    /**
     * Trois zombies, une vache et un zombie nommé dans la zone, un golem à l'épée en fer. Les trois zombies meurent ; la
     * vache et le zombie nommé ne sont pas touchés. Chaque zombie tenait un lingot de fer (lâché à coup sûr) : 3 lingots
     * au coffre. L'XP (5 par zombie, plus 1 à 3 par objet équipé) n'existe que pour une mise à mort par un joueur : au
     * moins 15 points, dont un cristal, sans aucune orbe. L'épée (6 dégâts, 5,9 après l'armure d'un zombie) frappe
     * 4 fois chaque zombie de 20 PV : 12 points d'usure.
     */
    public static void killsZombies(GameTestHelper h) {
        ZoneControllerBlockEntity be = TestKit.floorAndZone(h, ModBlocks.GUARD_CONTROLLER.get());
        for (BlockPos at : new BlockPos[] {new BlockPos(2, 2, 4), new BlockPos(4, 2, 4), new BlockPos(4, 2, 2)}) zombie(h, at);
        Zombie named = zombie(h, new BlockPos(2, 2, 2));
        named.setCustomName(Component.literal("Bob"));
        Cow cow = h.spawn(EntityType.COW, new BlockPos(3, 2, 4));
        cow.setNoAi(true);
        golem(h, new BlockPos(3, 2, 3));
        h.onEachTick(() -> h.assertTrue(h.getEntities(EntityType.EXPERIENCE_ORB).isEmpty(), "the guard post spawned orbs"));
        h.succeedWhen(() -> {
            h.assertTrue(be.workers().size() == 1, "golem not recruited");
            int zombies = h.getEntities(EntityType.ZOMBIE).size();
            h.assertTrue(zombies == 1 && named.isAlive(), "expected only the named zombie left, got " + zombies + " zombie(s)");
            int ingots = TestKit.count(h, TestKit.CHEST, Items.IRON_INGOT);
            h.assertTrue(ingots == 3, "expected 3 iron ingots in the chest, got " + ingots);
            int crystals = TestKit.count(h, TestKit.CHEST, ModItems.XP_CRYSTAL.get());
            double xp = be.xp().stored() + 10 * crystals;
            h.assertTrue(crystals >= 1 && xp >= 15, "expected at least 15 xp with a crystal, got " + xp + " (" + crystals + " crystals)");
            Worker w = be.workers().getFirst();
            int wear = w.tool.getDamageValue();
            h.assertTrue(wear >= 12 && wear <= 16, "expected about 12 points of wear, got " + wear);
            untouched(h, cow, "the cow");
            untouched(h, named, "the named zombie");
            h.assertTrue(TestKit.hasLine(TestKit.describe(be), "message.simple_automation.guard.targets"), "no target count line");
        });
    }

    /**
     * Exclusions : un Warden (tag guard_blacklist) et un zombie tenu en laisse ne sont jamais frappés ; le zombie libre
     * à côté meurt. Le golem tient une épée : sans épée (un bâton), il attend une arme.
     */
    public static void skipsExcluded(GameTestHelper h) {
        ZoneControllerBlockEntity be = TestKit.floorAndZone(h, ModBlocks.GUARD_CONTROLLER.get());
        Warden warden = h.spawn(EntityType.WARDEN, new BlockPos(2, 2, 4));
        warden.setNoAi(true);
        h.setBlock(4, 2, 2, Blocks.OAK_FENCE);
        Zombie leashed = zombie(h, new BlockPos(4, 2, 3));
        leashed.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(h.getLevel(), h.absolutePos(new BlockPos(4, 2, 2))), true);
        Zombie free = zombie(h, new BlockPos(4, 2, 4));
        golem(h, new BlockPos(3, 2, 3));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(!free.isAlive(), "the free zombie is still alive"))
                .thenIdle(60)
                .thenExecute(() -> {
                    untouched(h, warden, "the warden");
                    untouched(h, leashed, "the leashed zombie");
                    h.assertTrue(leashed.isLeashed(), "the zombie lost its leash");
                    h.assertTrue(be.job().acceptsTool(be, new ItemStack(Items.IRON_AXE)), "an axe is refused");
                    h.assertFalse(be.job().acceptsTool(be, new ItemStack(Items.STICK)), "a stick is accepted");
                })
                .thenSucceed();
    }

    /**
     * Zombie immobile, qui tient un lingot de fer (lâché à coup sûr), avec une citrouille sur la tête pour ne pas brûler
     * au soleil (sans armure en plus).
     */
    private static Zombie zombie(GameTestHelper h, BlockPos at) {
        Zombie z = h.spawn(EntityType.ZOMBIE, at);
        z.setNoAi(true);
        z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Blocks.CARVED_PUMPKIN));
        z.setDropChance(EquipmentSlot.HEAD, 0.0F);
        z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_INGOT));
        z.setGuaranteedDrop(EquipmentSlot.MAINHAND);
        return z;
    }

    private static void golem(GameTestHelper h, BlockPos at) {
        CopperGolem golem = h.spawn(EntityType.COPPER_GOLEM, at);
        golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
    }

    private static void untouched(GameTestHelper h, LivingEntity e, String what) {
        h.assertTrue(e.isAlive() && e.getHealth() >= e.getMaxHealth(), what + " was hit (" + e.getHealth() + "/" + e.getMaxHealth() + ")");
    }

    private GuardTests() {
    }
}
