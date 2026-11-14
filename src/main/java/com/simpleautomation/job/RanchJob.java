package com.simpleautomation.job;

import com.simpleautomation.SAConfig;
import com.simpleautomation.zone.Claims;
import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import org.jspecify.annotations.Nullable;

/**
 * Enclos : un golem qui tient de la nourriture (blé, carottes, graines…) fait se reproduire les animaux qui la mangent,
 * tant que leur espèce est sous le seuil de la config, en prenant la nourriture dans les coffres.
 * Un golem qui tient une arme de mêlée (épée, hache, masse… vanilla ou moddée) abat les adultes en trop au-delà du seuil.
 * Jamais un bébé, un animal nommé, apprivoisé ou tenu en laisse.
 */
public final class RanchJob implements Job {
    private static final int WORK_TICKS = 30;
    /** Les espèces présentes sont relevées au plus toutes les 5 secondes pour vérifier l'objet tenu (pas de scan à chaque tick). */
    private static final int KINDS_REFRESH_TICKS = 100;
    private final Set<UUID> claimed = new HashSet<>();
    private final AnimalJobs.Refusals refused = new AnimalJobs.Refusals();
    /** Un animal par espèce présente dans la zone, pour savoir quelle nourriture sert à quelque chose. */
    private List<Animal> kinds = List.of();
    private long kindsScannedAt = -1L;

    /** Repli sans la zone : n'importe quel objet. */
    @Override
    public boolean acceptsTool(ItemStack tool) {
        return !tool.isEmpty();
    }

    /**
     * Une arme, ou une nourriture qu'au moins un animal de la zone mange. Tant que la zone est vide d'animaux,
     * tout objet est accepté, pour ne pas marquer les golems « mauvais outil » avant l'arrivée du troupeau.
     */
    @Override
    public boolean acceptsTool(ZoneControllerBlockEntity zone, ItemStack tool) {
        if (tool.isEmpty()) return false;
        if (isWeapon(tool)) return true;
        List<Animal> present = kinds(zone);
        if (present.isEmpty()) return true;
        for (Animal a : present) if (a.isFood(tool)) return true;
        return false;
    }

    private List<Animal> kinds(ZoneControllerBlockEntity zone) {
        if (!(zone.getLevel() instanceof ServerLevel level)) return kinds;
        long now = level.getGameTime();
        if (kindsScannedAt < 0 || now < kindsScannedAt || now - kindsScannedAt >= KINDS_REFRESH_TICKS) {
            AABB box = AnimalJobs.box(zone);
            rememberKinds(box == null ? List.of() : level.getEntitiesOfClass(Animal.class, box, Animal::isAlive), now);
        }
        return kinds;
    }

    private void rememberKinds(List<Animal> animals, long now) {
        Map<EntityType<?>, Animal> byType = new LinkedHashMap<>();
        for (Animal a : animals) byType.putIfAbsent(a.getType(), a);
        kinds = List.copyOf(byType.values());
        kindsScannedAt = now;
    }

    /** Épées, armes de mêlée (tag commun), ou tout objet moddé qui sait faire un coup balayé. */
    private static boolean isWeapon(ItemStack tool) {
        return tool.is(ItemTags.SWORDS) || tool.is(Tags.Items.MELEE_WEAPON_TOOLS) || tool.canPerformAction(ItemAbilities.SWORD_SWEEP);
    }

    @Override
    public boolean showsCrack() {
        return false;
    }

    @Override
    public int budgetCost(Task task) {
        return 0;
    }

    @Override
    public void reset(ZoneControllerBlockEntity zone) {
        claimed.clear();
        refused.clear();
        kinds = List.of();
        kindsScannedAt = -1L;
    }

    private static boolean culls(Worker w) {
        return isWeapon(w.tool);
    }

    private static boolean protectedAnimal(Animal a) {
        return a.isBaby() || a.hasCustomName() || a.isLeashed() || (a instanceof TamableAnimal t && t.isTame());
    }

    @Override
    public @Nullable Task nextTask(ZoneControllerBlockEntity zone, Worker worker) {
        AABB box = AnimalJobs.box(zone);
        if (box == null || !(zone.getLevel() instanceof ServerLevel level)) return null;
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, box, Animal::isAlive);
        rememberKinds(animals, level.getGameTime());
        if (level.getGameTime() % 200 == 0) refused.prune(level);
        Map<EntityType<?>, Integer> population = new HashMap<>();
        for (Animal a : animals) population.merge(a.getType(), 1, Integer::sum);
        int max = SAConfig.RANCH_MAX_ANIMALS.getAsInt();
        Vec3 from = Vec3.atBottomCenterOf(worker.stand);

        if (culls(worker)) {
            Animal target = animals.stream()
                    .filter(a -> population.get(a.getType()) > max && !protectedAnimal(a) && !claimed.contains(a.getUUID())
                            && !refused.contains(level, a.getUUID()))
                    .min(Comparator.comparingDouble(a -> a.distanceToSqr(from))).orElse(null);
            if (target == null) return null;
            claimed.add(target.getUUID());
            return new Task(target.blockPosition(), AnimalJobs.standNear(zone, level, target), List.of(), List.of(target.getUUID()));
        }

        // Reproduction : deux adultes de la même espèce, prêts, qui mangent ce que tient le golem.
        // Deux aliments au total dans les coffres, même répartis sur plusieurs piles.
        if (zone.inventory().count(s -> ItemStack.isSameItemSameComponents(s, worker.tool)) < 2) return null;
        Map<EntityType<?>, List<Animal>> ready = new HashMap<>();
        for (Animal a : animals) {
            if (protectedAnimal(a) || claimed.contains(a.getUUID()) || refused.contains(level, a.getUUID())
                    || !a.isFood(worker.tool) || !a.canFallInLove() || a.getAge() != 0) continue;
            if (population.get(a.getType()) + 1 > max) continue;
            ready.computeIfAbsent(a.getType(), t -> new ArrayList<>()).add(a);
        }
        for (List<Animal> pair : ready.values()) {
            if (pair.size() < 2) continue;
            Animal a = pair.get(0);
            Animal b = pair.get(1);
            claimed.add(a.getUUID());
            claimed.add(b.getUUID());
            return new Task(a.blockPosition(), AnimalJobs.standNear(zone, level, a), List.of(), List.of(a.getUUID(), b.getUUID()));
        }
        return null;
    }

    @Override
    public int workTicks(ZoneControllerBlockEntity zone, Worker worker, Task task) {
        return AnimalJobs.workTicks(zone, worker, WORK_TICKS);
    }

    @Override
    public boolean isStillValid(ZoneControllerBlockEntity zone, Task task) {
        AABB box = AnimalJobs.box(zone);
        if (box == null || !(zone.getLevel() instanceof ServerLevel level)) return false;
        for (UUID id : task.entities()) if (AnimalJobs.find(level, id, box) == null) return false;
        return true;
    }

    @Override
    public void complete(ZoneControllerBlockEntity zone, Worker worker, Task task) {
        claimed.removeAll(task.entities());
        AABB box = AnimalJobs.box(zone);
        if (box == null || !(zone.getLevel() instanceof ServerLevel level)) return;
        FakePlayer fake = zone.fakePlayer();

        if (culls(worker)) {
            Entity e = AnimalJobs.find(level, task.entities().getFirst(), box);
            if (!(e instanceof Animal a) || protectedAnimal(a)) return;
            long sameKind = level.getEntitiesOfClass(Animal.class, box, x -> x.isAlive() && x.getType() == a.getType()).size();
            if (sameKind <= SAConfig.RANCH_MAX_ANIMALS.getAsInt()) return;
            fake.setItemInHand(InteractionHand.MAIN_HAND, worker.tool);
            try {
                // Les mods de protection peuvent refuser l'attaque, comme pour un joueur.
                if (NeoForge.EVENT_BUS.post(new AttackEntityEvent(fake, a)).isCanceled()) {
                    refused.add(level, a.getUUID());
                    return;
                }
                a.hurtServer(level, level.damageSources().playerAttack(fake), Float.MAX_VALUE);
                worker.tool.hurtAndBreak(1, level, (ServerPlayer) null, item -> {});
            } finally {
                fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
            return;
        }

        List<Animal> pair = new ArrayList<>();
        for (UUID id : task.entities()) {
            if (AnimalJobs.find(level, id, box) instanceof Animal a && a.canFallInLove() && a.getAge() == 0 && !protectedAnimal(a)) pair.add(a);
        }
        if (pair.size() < 2) return;
        // Un mod de protection peut refuser qu'on touche à ces animaux : on les laisse de côté un moment, sans prendre la nourriture.
        boolean allowed = true;
        for (Animal a : pair) {
            if (!Claims.canInteract(zone, a)) {
                refused.add(level, a.getUUID());
                allowed = false;
            }
        }
        if (!allowed || !takeFood(zone, worker.tool)) return;
        for (Animal a : pair) a.setInLove(null);
    }

    /** Retire deux aliments des coffres, pris sur une ou plusieurs piles ; s'il en manque, ce qui a été pris y retourne. */
    private static boolean takeFood(ZoneControllerBlockEntity zone, ItemStack like) {
        Predicate<ItemStack> same = s -> ItemStack.isSameItemSameComponents(s, like);
        ItemStack food = zone.inventory().extract(same, 2);
        if (food.getCount() < 2) {
            ItemStack more = zone.inventory().extract(same, 2 - food.getCount());
            if (!more.isEmpty()) food = more.copyWithCount(food.getCount() + more.getCount());
        }
        if (food.getCount() >= 2) return true;
        zone.inventory().output(food);
        return false;
    }

    @Override
    public void tick(ZoneControllerBlockEntity zone) {
        AnimalJobs.collectItems(zone);
    }

    @Override
    public void describeTo(ZoneControllerBlockEntity zone, Consumer<Component> lines) {
        AnimalJobs.fenceHint(zone, e -> e instanceof Animal, lines);
    }

    @Override
    public void release(Task task) {
        claimed.removeAll(task.entities());
    }

    @Override
    public void save(ValueOutput out) {
    }

    @Override
    public void load(ValueInput in) {
        claimed.clear();
        refused.clear();
    }
}
