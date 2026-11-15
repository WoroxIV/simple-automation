package com.simpleautomation.job;

import com.simpleautomation.SAConfig;
import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.ZoneBounds;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/** Petits outils des métiers animaliers (l'étable). */
final class AnimalJobs {
    /** Distance autour de la zone où l'on cherche des animaux sortis, pour l'astuce « clôture ». */
    private static final int STRAY_RANGE = 8;
    /** Chaque niveau d'Efficacité retire 15 % du temps de travail. */
    private static final double EFFICIENCY_STEP = 0.15;
    /** Durée pendant laquelle un animal protégé (claim) est laissé de côté avant de réessayer : une minute. */
    private static final long REFUSED_TICKS = 1200;

    /**
     * Temps d'une tâche sur un animal : la durée de base, multipliée par la config, ralentie par l'oxydation,
     * raccourcie par Efficacité, jamais sous le plancher de la config (comme les autres métiers).
     */
    static int workTicks(ZoneControllerBlockEntity zone, Worker worker, double baseTicks) {
        double ticks = baseTicks * SAConfig.WORK_SPEED_MULTIPLIER.getAsDouble() / Math.max(worker.speedFactor(), 0.05);
        if (zone.getLevel() instanceof ServerLevel level && !worker.tool.isEmpty()) {
            int efficiency = EnchantmentHelper.getItemEnchantmentLevel(
                    level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY), worker.tool);
            ticks *= Math.max(0.1, 1.0 - EFFICIENCY_STEP * efficiency);
        }
        return Math.max(SAConfig.MIN_TICKS_PER_BLOCK.getAsInt(), (int) Math.ceil(ticks));
    }

    /**
     * Animaux qu'un mod de protection nous a refusés : on les oublie un moment pour que le golem
     * ne tourne pas en boucle dessus. Pas sauvegardé, la liste repart de zéro au rechargement.
     */
    static final class Refusals {
        private final Map<UUID, Long> until = new HashMap<>();

        void add(Level level, UUID id) {
            until.put(id, level.getGameTime() + REFUSED_TICKS);
        }

        boolean contains(Level level, UUID id) {
            Long end = until.get(id);
            if (end == null) return false;
            if (end > level.getGameTime()) return true;
            until.remove(id);
            return false;
        }

        /** Oublie les refus expirés (appelé de temps en temps pour que la table ne grossisse pas). */
        void prune(Level level) {
            long now = level.getGameTime();
            until.values().removeIf(end -> end <= now);
        }

        void clear() {
            until.clear();
        }
    }

    static @Nullable AABB box(ZoneControllerBlockEntity zone) {
        ZoneBounds b = zone.bounds();
        if (b == null) return null;
        return new AABB(b.minX(), b.markerY() - 3, b.minZ(), b.maxX() + 1, b.markerY() + 6, b.maxZ() + 1);
    }

    /**
     * Ramasse la laine, la viande, les œufs, les écailles… tombés dans la zone, et un peu autour (un item lâché au bord peut
     * rebondir dehors), ainsi que les orbes de reproduction, que la vanilla crée directement : elles vont au tampon d'XP.
     */
    static void collectItems(ZoneControllerBlockEntity zone) {
        AABB box = box(zone);
        if (box == null || zone.getLevel() == null || zone.getLevel().getGameTime() % 40 != 0) return;
        AABB around = box.inflate(2, 0, 2);
        GolemTools.collectItems(zone, around);
        GolemTools.collectXp(zone, around);
    }

    /** Une case libre (avec un sol) à côté de l'animal, pour que la marionnette se tienne près de lui. */
    static BlockPos standNear(ZoneControllerBlockEntity zone, ServerLevel level, Entity e) {
        BlockPos at = e.blockPosition();
        BlockPos spot = zone.standNear(level, at, 2, null);
        if (spot != null) return spot;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = at.relative(d);
            if (level.getBlockState(n).isAir() && !level.getBlockState(n.below()).isAir()) return n;
        }
        return at;
    }

    static @Nullable Entity find(ServerLevel level, UUID id, AABB box) {
        Entity e = level.getEntity(id);
        return e != null && e.isAlive() && box.contains(e.position()) ? e : null;
    }

    /**
     * Astuce au clic sur le contrôleur : la zone n'a pas (ou presque pas) de clôture sur son pourtour
     * et des animaux concernés traînent juste à côté. Ils sortent, et les golems ne s'en occupent plus.
     */
    static void fenceHint(ZoneControllerBlockEntity zone, Predicate<Entity> kind, Consumer<Component> lines) {
        ZoneBounds b = zone.bounds();
        AABB box = box(zone);
        if (b == null || box == null || !(zone.getLevel() instanceof ServerLevel level) || fenced(level, b)) return;
        boolean strays = !level.getEntitiesOfClass(LivingEntity.class, box.inflate(STRAY_RANGE, 0, STRAY_RANGE),
                e -> e.isAlive() && kind.test(e) && !box.contains(e.position())).isEmpty();
        if (strays) lines.accept(Component.translatable("message.simple_automation.zone.hint_fence").withStyle(ChatFormatting.YELLOW));
    }

    /**
     * La moitié au moins du pourtour porte une clôture, un muret ou un portillon (sur le bord du rectangle ou juste à côté,
     * à la hauteur des bornes).
     */
    private static boolean fenced(ServerLevel level, ZoneBounds b) {
        int edge = Math.max(1, 2 * (b.sizeX() + b.sizeZ()) - 4);
        int fencedColumns = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = b.minX() - 1; x <= b.maxX() + 1; x++) {
            for (int z = b.minZ() - 1; z <= b.maxZ() + 1; z++) {
                if (x > b.minX() && x < b.maxX() && z > b.minZ() && z < b.maxZ()) continue; // intérieur
                for (int y = b.markerY() - 1; y <= b.markerY() + 2; y++) {
                    p.set(x, y, z);
                    if (!level.isLoaded(p)) break;
                    BlockState s = level.getBlockState(p);
                    if (s.is(BlockTags.FENCES) || s.is(BlockTags.WALLS) || s.is(BlockTags.FENCE_GATES)) {
                        fencedColumns++;
                        break;
                    }
                }
            }
        }
        return fencedColumns * 2 >= edge;
    }

    private AnimalJobs() {
    }
}
