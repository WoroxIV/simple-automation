package com.simpleautomation.job;

import com.simpleautomation.SAConfig;
import com.simpleautomation.zone.Claims;
import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.XpCapture;
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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.IShearable;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import org.jspecify.annotations.Nullable;

/**
 * Étable : chaque golem s'occupe des animaux de la zone selon l'objet qu'il tient.
 * <ul>
 * <li>Nourriture (blé, carottes, graines…) : fait se reproduire les animaux qui la mangent, tant que leur espèce est sous
 * le seuil de la config ({@code barnMaxAnimals}), avec la nourriture des coffres.</li>
 * <li>Cisailles : tond tout animal qui se tond (moutons, champimeuhs, animaux moddés via IShearable de NeoForge).</li>
 * <li>Seau : trait les vaches, champimeuhs et chèvres adultes, avec les seaux vides pris dans les coffres.</li>
 * <li>Pinceau : brosse les tatous adultes (une écaille, 16 points d'usure, comme en vanilla).</li>
 * <li>Arme de mêlée (épée, hache, masse, trident…) : abat les adultes en trop au-delà du seuil. Jamais un bébé,
 * un animal nommé, apprivoisé ou tenu en laisse. L'XP de leur mort va dans la zone (cristaux), sans orbe.</li>
 * </ul>
 * Les bébés comptent dans la population (la reproduction s'arrête au seuil). Laine, viande, œufs, écailles et orbes
 * de reproduction tombés dans la zone sont aspirés vers les coffres.
 */
public final class BarnJob implements Job {
    /** Ce que fait un golem, d'après l'objet qu'il tient. */
    private enum Mode { CULL, SHEAR, BRUSH, MILK, BREED }

    private static final int BREED_TICKS = 30;
    private static final int CULL_TICKS = 30;
    private static final int SHEAR_TICKS = 20;
    private static final int MILK_TICKS = 20;
    private static final int BRUSH_TICKS = 20;
    /** Usure d'un pinceau par écaille, comme un joueur (BrushItem sur un tatou). */
    private static final int BRUSH_WEAR = 16;
    /** Vitesse des cisailles vanilla sur la laine : des cisailles moddées plus rapides tondent plus vite. */
    private static final float VANILLA_SHEARS_SPEED = 5.0F;
    /** Les espèces présentes sont relevées au plus toutes les 5 secondes pour vérifier l'objet tenu (pas de scan à chaque tick). */
    private static final int KINDS_REFRESH_TICKS = 100;

    private final Set<UUID> claimed = new HashSet<>();
    private final AnimalJobs.Refusals refused = new AnimalJobs.Refusals();
    /** Un animal par espèce présente dans la zone, pour savoir quelle nourriture sert à quelque chose. */
    private List<Animal> kinds = List.of();
    private long kindsScannedAt = -1L;

    private static Mode mode(ItemStack tool) {
        if (isWeapon(tool)) return Mode.CULL;
        if (isShears(tool)) return Mode.SHEAR;
        if (isBrush(tool)) return Mode.BRUSH;
        if (tool.is(Items.BUCKET)) return Mode.MILK;
        return Mode.BREED;
    }

    /** Épées, armes de mêlée (tag commun : haches, masse, trident), lances, ou tout objet moddé qui sait faire un coup balayé. */
    private static boolean isWeapon(ItemStack tool) {
        return tool.is(ItemTags.SWORDS) || tool.is(Tags.Items.MELEE_WEAPON_TOOLS) || tool.is(Tags.Items.TOOLS_SPEAR)
                || tool.canPerformAction(ItemAbilities.SWORD_SWEEP);
    }

    private static boolean isShears(ItemStack tool) {
        return tool.is(Tags.Items.TOOLS_SHEAR) || tool.is(Items.SHEARS) || tool.canPerformAction(ItemAbilities.SHEARS_HARVEST);
    }

    private static boolean isBrush(ItemStack tool) {
        return tool.is(Tags.Items.TOOLS_BRUSH) || tool.canPerformAction(ItemAbilities.BRUSH_BRUSH);
    }

    private static boolean hasEmptyBucket(ZoneControllerBlockEntity zone) {
        return zone.inventory().count(s -> s.is(Items.BUCKET)) > 0;
    }

    /** Repli sans la zone : n'importe quel objet. */
    @Override
    public boolean acceptsTool(ItemStack tool) {
        return !tool.isEmpty();
    }

    /**
     * Une arme, des cisailles, un pinceau, un seau (s'il reste des seaux vides dans les coffres), ou une nourriture qu'au
     * moins un animal de la zone mange. Tant que la zone est vide d'animaux, tout objet est accepté, pour ne pas marquer
     * les golems « mauvais outil » avant l'arrivée du troupeau.
     */
    @Override
    public boolean acceptsTool(ZoneControllerBlockEntity zone, ItemStack tool) {
        if (tool.isEmpty()) return false;
        switch (mode(tool)) {
            case CULL, SHEAR, BRUSH -> {
                return true;
            }
            case MILK -> {
                return hasEmptyBucket(zone);
            }
            default -> {
                List<Animal> present = kinds(zone);
                if (present.isEmpty()) return true;
                for (Animal a : present) if (a.isFood(tool)) return true;
                return false;
            }
        }
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

    /** Jamais abattu ni utilisé pour la reproduction : bébés, animaux nommés, tenus en laisse ou apprivoisés. */
    private static boolean protectedAnimal(Animal a) {
        return a.isBaby() || a.hasCustomName() || a.isLeashed() || (a instanceof TamableAnimal t && t.isTame());
    }

    /** Un animal qui se tond et qui est prêt (un mouton qui a sa laine…). Pas les golems de neige ni les bogged. */
    private static boolean shearable(Entity e, ItemStack tool, ServerLevel level) {
        return e instanceof Animal && e instanceof IShearable s && s.isShearable(null, tool, level, e.blockPosition());
    }

    /** Vaches, champimeuhs et chèvres adultes : un seau vide donne un seau de lait, comme pour un joueur. */
    private static boolean milkable(Animal a) {
        return (a instanceof AbstractCow || a instanceof Goat) && !a.isBaby();
    }

    private static boolean brushable(Animal a) {
        return a instanceof Armadillo && !a.isBaby();
    }

    private boolean free(ServerLevel level, Animal a) {
        return !claimed.contains(a.getUUID()) && !refused.contains(level, a.getUUID());
    }

    @Override
    public @Nullable Task nextTask(ZoneControllerBlockEntity zone, Worker worker) {
        AABB box = AnimalJobs.box(zone);
        if (box == null || !(zone.getLevel() instanceof ServerLevel level)) return null;
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, box, Animal::isAlive);
        rememberKinds(animals, level.getGameTime());
        if (level.getGameTime() % 200 == 0) refused.prune(level);
        Vec3 from = Vec3.atBottomCenterOf(worker.stand);
        Comparator<Animal> nearest = Comparator.comparingDouble(a -> a.distanceToSqr(from));

        Predicate<Animal> wanted;
        switch (mode(worker.tool)) {
            case BREED -> {
                return breedTask(zone, worker, level, animals);
            }
            case CULL -> {
                Map<EntityType<?>, Integer> population = population(animals);
                int max = SAConfig.BARN_MAX_ANIMALS.getAsInt();
                wanted = a -> population.get(a.getType()) > max && !protectedAnimal(a);
            }
            case SHEAR -> wanted = a -> shearable(a, worker.tool, level);
            case BRUSH -> wanted = BarnJob::brushable;
            default -> {
                if (!hasEmptyBucket(zone)) return null;
                wanted = BarnJob::milkable;
            }
        }
        Animal target = animals.stream().filter(a -> free(level, a) && wanted.test(a)).min(nearest).orElse(null);
        if (target == null) return null;
        claimed.add(target.getUUID());
        return new Task(target.blockPosition(), AnimalJobs.standNear(zone, level, target), List.of(), List.of(target.getUUID()));
    }

    private static Map<EntityType<?>, Integer> population(List<Animal> animals) {
        Map<EntityType<?>, Integer> population = new HashMap<>();
        for (Animal a : animals) population.merge(a.getType(), 1, Integer::sum);
        return population;
    }

    /**
     * Reproduction : deux adultes de la même espèce, prêts, qui mangent ce que tient le golem, et une place sous le seuil.
     * Deux aliments au total dans les coffres, même répartis sur plusieurs piles.
     */
    private @Nullable Task breedTask(ZoneControllerBlockEntity zone, Worker worker, ServerLevel level, List<Animal> animals) {
        if (zone.inventory().count(s -> ItemStack.isSameItemSameComponents(s, worker.tool)) < 2) return null;
        Map<EntityType<?>, Integer> population = population(animals);
        int max = SAConfig.BARN_MAX_ANIMALS.getAsInt();
        Map<EntityType<?>, List<Animal>> ready = new HashMap<>();
        for (Animal a : animals) {
            if (protectedAnimal(a) || !free(level, a) || !a.isFood(worker.tool) || !a.canFallInLove() || a.getAge() != 0) continue;
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
        return switch (mode(worker.tool)) {
            case SHEAR -> {
                // Des cisailles moddées plus rapides sur la laine raccourcissent la tonte ; jamais plus lentes que la vanilla.
                float speed = worker.tool.getDestroySpeed(Blocks.WHITE_WOOL.defaultBlockState());
                yield AnimalJobs.workTicks(zone, worker, SHEAR_TICKS * VANILLA_SHEARS_SPEED / Math.max(VANILLA_SHEARS_SPEED, speed));
            }
            case CULL -> AnimalJobs.workTicks(zone, worker, CULL_TICKS);
            case MILK -> AnimalJobs.workTicks(zone, worker, MILK_TICKS);
            case BRUSH -> AnimalJobs.workTicks(zone, worker, BRUSH_TICKS);
            case BREED -> AnimalJobs.workTicks(zone, worker, BREED_TICKS);
        };
    }

    /** Les animaux visés sont toujours là ; leur état (laine, âge…) est revérifié à la fin de la tâche. */
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
        Mode mode = mode(worker.tool);
        if (mode == Mode.BREED) {
            breed(zone, worker, task, level, box);
            return;
        }
        if (!(AnimalJobs.find(level, task.entities().getFirst(), box) instanceof Animal a)) return;
        switch (mode) {
            case CULL -> cull(zone, worker, level, box, a);
            case SHEAR -> shear(zone, worker, level, a);
            case MILK -> milk(zone, level, a);
            case BRUSH -> brush(zone, worker, level, a);
            default -> {
            }
        }
    }

    private void cull(ZoneControllerBlockEntity zone, Worker worker, ServerLevel level, AABB box, Animal a) {
        if (protectedAnimal(a)) return;
        long sameKind = level.getEntitiesOfClass(Animal.class, box, x -> x.isAlive() && x.getType() == a.getType()).size();
        if (sameKind <= SAConfig.BARN_MAX_ANIMALS.getAsInt()) return;
        FakePlayer fake = zone.fakePlayer();
        fake.setItemInHand(InteractionHand.MAIN_HAND, worker.tool);
        try {
            // Les mods de protection peuvent refuser l'attaque, comme pour un joueur.
            if (NeoForge.EVENT_BUS.post(new AttackEntityEvent(fake, a)).isCanceled()) {
                refused.add(level, a.getUUID());
                return;
            }
            // L'XP de la mort (règles vanilla « tué par un joueur ») va au tampon de la zone, sans orbe.
            XpCapture.during(zone, () -> a.hurtServer(level, level.damageSources().playerAttack(fake), Float.MAX_VALUE));
            worker.tool.hurtAndBreak(1, level, (ServerPlayer) null, item -> {});
        } finally {
            fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    private void shear(ZoneControllerBlockEntity zone, Worker worker, ServerLevel level, Animal a) {
        if (!(a instanceof IShearable s) || !shearable(a, worker.tool, level)) return;
        // Un mod de protection peut refuser qu'on touche à cet animal : on le laisse tranquille un moment.
        if (!Claims.canInteract(zone, a)) {
            refused.add(level, a.getUUID());
            return;
        }
        // Comme les cisailles de NeoForge (ShearsItem#interactLivingEntity), mais la laine va droit dans les coffres.
        for (ItemStack drop : s.onSheared(null, worker.tool, level, a.blockPosition())) zone.inventory().output(drop);
        a.gameEvent(GameEvent.SHEAR);
        worker.tool.hurtAndBreak(1, level, (ServerPlayer) null, item -> {});
    }

    /** Un seau vide pris dans les coffres devient un seau de lait (le seau tenu par le golem ne sert que d'outil). */
    private void milk(ZoneControllerBlockEntity zone, ServerLevel level, Animal a) {
        if (!milkable(a)) return;
        if (!Claims.canInteract(zone, a)) {
            refused.add(level, a.getUUID());
            return;
        }
        if (zone.inventory().extract(s -> s.is(Items.BUCKET), 1).isEmpty()) return;
        var sound = a instanceof Goat g ? (g.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_MILK : SoundEvents.GOAT_MILK) : SoundEvents.COW_MILK;
        level.playSound(null, a.blockPosition(), sound, SoundSource.NEUTRAL, 1.0F, 1.0F);
        zone.inventory().output(new ItemStack(Items.MILK_BUCKET));
    }

    /** Comme un joueur au pinceau : l'écaille tombe au sol (aspirée ensuite vers les coffres), le pinceau perd 16 points. */
    private void brush(ZoneControllerBlockEntity zone, Worker worker, ServerLevel level, Animal a) {
        if (!(a instanceof Armadillo armadillo) || !brushable(a)) return;
        if (!Claims.canInteract(zone, a)) {
            refused.add(level, a.getUUID());
            return;
        }
        FakePlayer fake = zone.fakePlayer();
        fake.setItemInHand(InteractionHand.MAIN_HAND, worker.tool);
        try {
            if (armadillo.brushOffScute(fake, worker.tool)) worker.tool.hurtAndBreak(BRUSH_WEAR, level, (ServerPlayer) null, item -> {});
        } finally {
            fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    private void breed(ZoneControllerBlockEntity zone, Worker worker, Task task, ServerLevel level, AABB box) {
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
        boolean milker = zone.workers().stream().anyMatch(w -> !w.tool.isEmpty() && mode(w.tool) == Mode.MILK);
        if (milker && !hasEmptyBucket(zone)) {
            lines.accept(Component.translatable("message.simple_automation.zone.barn_no_buckets").withStyle(ChatFormatting.YELLOW));
        }
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
