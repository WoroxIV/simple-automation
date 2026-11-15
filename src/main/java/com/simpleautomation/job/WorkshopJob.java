package com.simpleautomation.job;

import com.simpleautomation.SAConfig;
import com.simpleautomation.registry.ModDataComponents;
import com.simpleautomation.registry.ModItems;
import com.simpleautomation.workshop.RecipeCard;
import com.simpleautomation.workshop.Station;
import com.simpleautomation.workshop.Stations;
import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.ZoneBounds;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Atelier : le golem tient une fiche de recette (une recette sur un type de station) et travaille à la station la plus
 * proche de ce type dans la zone : établi, table de forgeron, tailleur de pierre, alambic.
 * <ul>
 * <li>Établi, forgeron, tailleur : il prend aux coffres un objet qui convient à chaque case (de préférence l'objet de la
 * fiche), applique la recette et range le résultat et les restes (seaux vides...).</li>
 * <li>Alambic : le vrai bloc brasse (20 s) ; le golem le charge, puis le vide. Un alambic est réservé par golem.</li>
 * <li>Les fiches s'enchaînent par les coffres : le résultat d'une fiche est la matière d'une autre.</li>
 * </ul>
 * Sans station du bon type dans la zone, le golem montre un « ? ».
 */
public final class WorkshopJob implements Job {
    private static final int RESCAN_TICKS = 100;
    /** Hauteurs cherchées autour des bornes pour trouver les stations. */
    private static final int BELOW = 2;
    private static final int ABOVE = 3;

    private final Map<Station, List<BlockPos>> stations = new HashMap<>();
    /** Stations en cours d'usage (une par golem) et ce que le golem y fait. */
    private final Set<BlockPos> claimed = new HashSet<>();
    private final Map<BlockPos, Station.Step> steps = new HashMap<>();
    private long nextScan;

    /** La fiche réglée que tient ce golem, ou null. */
    private static @Nullable RecipeCard cardOf(ItemStack tool) {
        return tool.is(ModItems.RECIPE_CARD.get()) ? tool.get(ModDataComponents.RECIPE_CARD.get()) : null;
    }

    @Override
    public boolean acceptsTool(ItemStack tool) {
        return cardOf(tool) != null;
    }

    /** Une fiche dont la recette existe et dont la station est dans la zone. */
    @Override
    public boolean acceptsTool(ZoneControllerBlockEntity zone, ItemStack tool) {
        RecipeCard card = cardOf(tool);
        if (card == null || !(zone.getLevel() instanceof ServerLevel level)) return false;
        if (nextScan == 0) scan(zone, level);
        return !stations.getOrDefault(card.station(), List.of()).isEmpty() && !card.station().preview(level, card).isEmpty();
    }

    @Override
    public boolean showsCrack() {
        return false;
    }

    @Override
    public int budgetCost(Task task) {
        return 0; // aucun bloc cassé
    }

    @Override
    public void reset(@Nullable ZoneControllerBlockEntity zone) {
        stations.clear();
        claimed.clear();
        steps.clear();
        nextScan = 0;
    }

    @Override
    public void tick(ZoneControllerBlockEntity zone) {
        if (!(zone.getLevel() instanceof ServerLevel level) || level.getGameTime() < nextScan) return;
        scan(zone, level);
    }

    private void scan(ZoneControllerBlockEntity zone, ServerLevel level) {
        nextScan = level.getGameTime() + RESCAN_TICKS;
        stations.clear();
        ZoneBounds b = zone.bounds();
        if (b == null) return;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                for (int y = b.markerY() - BELOW; y <= b.markerY() + ABOVE; y++) {
                    p.set(x, y, z);
                    if (!level.isLoaded(p)) continue;
                    Station station = Stations.at(level.getBlockState(p));
                    if (station != null) stations.computeIfAbsent(station, s -> new ArrayList<>()).add(p.immutable());
                }
            }
        }
    }

    @Override
    public @Nullable Task nextTask(ZoneControllerBlockEntity zone, Worker worker) {
        RecipeCard card = cardOf(worker.tool);
        if (card == null || !(zone.getLevel() instanceof ServerLevel level)) return null;
        Station station = card.station();
        List<BlockPos> sorted = new ArrayList<>(stations.getOrDefault(station, List.of()));
        sorted.sort(Comparator.comparingDouble(t -> t.distSqr(worker.stand)));
        for (BlockPos pos : sorted) {
            if (claimed.contains(pos) || !station.matches(level.getBlockState(pos))) continue;
            Station.Step step = station.nextStep(zone, level, card, pos);
            if (step == null) continue;
            BlockPos stand = zone.standNear(level, pos, 2, worker);
            if (stand == null) continue;
            claimed.add(pos);
            steps.put(pos, step);
            return new Task(pos, stand);
        }
        return null;
    }

    @Override
    public int workTicks(ZoneControllerBlockEntity zone, Worker worker, Task task) {
        RecipeCard card = cardOf(worker.tool);
        Station.Step step = steps.getOrDefault(task.target(), Station.Step.CRAFT);
        int base = card != null ? card.station().workTicks(step) : 30;
        double ticks = base * SAConfig.WORK_SPEED_MULTIPLIER.getAsDouble() / Math.max(worker.speedFactor(), 0.05);
        return Math.max(SAConfig.MIN_TICKS_PER_BLOCK.getAsInt(), (int) Math.ceil(ticks));
    }

    @Override
    public boolean isStillValid(ZoneControllerBlockEntity zone, Task task) {
        Level level = zone.getLevel();
        return level != null && level.isLoaded(task.target()) && Stations.at(level.getBlockState(task.target())) != null;
    }

    @Override
    public void complete(ZoneControllerBlockEntity zone, Worker worker, Task task) {
        BlockPos pos = task.target();
        Station.Step step = steps.remove(pos);
        claimed.remove(pos);
        RecipeCard card = cardOf(worker.tool);
        if (step == null || card == null || !(zone.getLevel() instanceof ServerLevel level)) return;
        if (!card.station().matches(level.getBlockState(pos))) return;
        card.station().complete(zone, level, card, pos, step);
    }

    @Override
    public void release(Task task) {
        claimed.remove(task.target());
        steps.remove(task.target());
    }

    @Override
    public @Nullable Component progress(ZoneControllerBlockEntity zone) {
        if (stations.isEmpty()) return null;
        return Component.translatable("message.simple_automation.workshop.stations",
                count(Stations.CRAFTING), count(Stations.SMITHING), count(Stations.STONECUTTER), count(Stations.BREWING));
    }

    private int count(Station station) {
        return stations.getOrDefault(station, List.of()).size();
    }

    @Override
    public void describeTo(ZoneControllerBlockEntity zone, Consumer<Component> lines) {
        if (stations.isEmpty()) {
            lines.accept(Component.translatable("message.simple_automation.workshop.no_station").withStyle(ChatFormatting.YELLOW));
        }
        // Un golem « ? » parce que sa fiche vise une station absente : on dit laquelle.
        Set<Station> missing = new HashSet<>();
        for (Worker w : zone.workers()) {
            RecipeCard card = cardOf(w.tool);
            if (card != null && stations.getOrDefault(card.station(), List.of()).isEmpty()) missing.add(card.station());
        }
        for (Station s : missing) {
            lines.accept(Component.translatable("message.simple_automation.workshop.missing_station", Component.translatable("station.simple_automation." + s.id()))
                    .withStyle(ChatFormatting.YELLOW));
        }
        lines.accept(Component.translatable("message.simple_automation.workshop.tip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void save(ValueOutput out) {
    }

    @Override
    public void load(ValueInput in) {
        reset(null);
    }
}
