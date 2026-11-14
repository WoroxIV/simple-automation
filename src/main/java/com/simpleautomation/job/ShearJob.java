package com.simpleautomation.job;

import com.simpleautomation.zone.Claims;
import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
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
import net.neoforged.neoforge.common.Tags;
import org.jspecify.annotations.Nullable;

/**
 * Bergerie : les golems aux cisailles tondent les moutons de la zone, et tout animal qui se tond
 * (champimeuh, animaux moddés qui passent par IShearable de NeoForge).
 * La laine est rangée directement dans les coffres.
 */
public final class ShearJob implements Job {
    private static final int WORK_TICKS = 20;
    /** Vitesse des cisailles vanilla sur la laine : des cisailles moddées plus rapides tondent plus vite. */
    private static final float VANILLA_SHEARS_SPEED = 5.0F;
    private final Set<UUID> claimed = new HashSet<>();
    private final AnimalJobs.Refusals refused = new AnimalJobs.Refusals();

    @Override
    public boolean acceptsTool(ItemStack tool) {
        return tool.is(Tags.Items.TOOLS_SHEAR) || tool.is(Items.SHEARS) || tool.canPerformAction(ItemAbilities.SHEARS_HARVEST);
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
    }

    /** Un animal qui se tond et qui est prêt (un mouton qui a sa laine…). Pas les golems de neige ni les bogged. */
    private static boolean ready(Entity e, ItemStack tool, ServerLevel level) {
        return e instanceof Animal && e instanceof IShearable s && s.isShearable(null, tool, level, e.blockPosition());
    }

    @Override
    public @Nullable Task nextTask(ZoneControllerBlockEntity zone, Worker worker) {
        AABB box = AnimalJobs.box(zone);
        if (box == null || !(zone.getLevel() instanceof ServerLevel level)) return null;
        if (level.getGameTime() % 200 == 0) refused.prune(level);
        Vec3 from = Vec3.atBottomCenterOf(worker.stand);
        List<Animal> candidates = level.getEntitiesOfClass(Animal.class, box,
                e -> !claimed.contains(e.getUUID()) && !refused.contains(level, e.getUUID()) && ready(e, worker.tool, level));
        Animal target = candidates.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(from))).orElse(null);
        if (target == null) return null;
        claimed.add(target.getUUID());
        return new Task(target.blockPosition(), AnimalJobs.standNear(zone, level, target), List.of(), List.of(target.getUUID()));
    }

    @Override
    public int workTicks(ZoneControllerBlockEntity zone, Worker worker, Task task) {
        // Des cisailles moddées plus rapides sur la laine raccourcissent la tonte ; jamais plus lentes que la vanilla.
        float speed = worker.tool.getDestroySpeed(Blocks.WHITE_WOOL.defaultBlockState());
        return AnimalJobs.workTicks(zone, worker, WORK_TICKS * VANILLA_SHEARS_SPEED / Math.max(VANILLA_SHEARS_SPEED, speed));
    }

    @Override
    public boolean isStillValid(ZoneControllerBlockEntity zone, Task task) {
        AABB box = AnimalJobs.box(zone);
        if (box == null || !(zone.getLevel() instanceof ServerLevel level)) return false;
        Entity e = AnimalJobs.find(level, task.entities().getFirst(), box);
        return e != null && ready(e, Items.SHEARS.getDefaultInstance(), level);
    }

    @Override
    public void complete(ZoneControllerBlockEntity zone, Worker worker, Task task) {
        claimed.removeAll(task.entities());
        AABB box = AnimalJobs.box(zone);
        if (box == null || !(zone.getLevel() instanceof ServerLevel level)) return;
        Entity e = AnimalJobs.find(level, task.entities().getFirst(), box);
        if (!(e instanceof IShearable s) || !ready(e, worker.tool, level)) return;
        // Un mod de protection peut refuser qu'on touche à cet animal : on le laisse tranquille un moment.
        if (!Claims.canInteract(zone, e)) {
            refused.add(level, e.getUUID());
            return;
        }
        // Comme les cisailles de NeoForge (ShearsItem#interactLivingEntity), mais la laine va droit dans les coffres.
        for (ItemStack drop : s.onSheared(null, worker.tool, level, e.blockPosition())) zone.inventory().output(drop);
        e.gameEvent(GameEvent.SHEAR);
        worker.tool.hurtAndBreak(1, level, (ServerPlayer) null, item -> {});
    }

    @Override
    public void tick(ZoneControllerBlockEntity zone) {
        AnimalJobs.collectItems(zone);
    }

    @Override
    public void describeTo(ZoneControllerBlockEntity zone, Consumer<Component> lines) {
        AnimalJobs.fenceHint(zone, e -> e instanceof Animal && e instanceof IShearable, lines);
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
