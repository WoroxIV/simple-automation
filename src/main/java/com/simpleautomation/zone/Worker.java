package com.simpleautomation.zone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simpleautomation.job.Task;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import org.jspecify.annotations.Nullable;

/**
 * Un copper golem au travail. Ce n'est pas une entité : le contrôleur stocke ces données et simule le travail,
 * le client dessine une marionnette à partir d'elles.
 */
public final class Worker {
    /** Mêmes délais que le golem vanilla entre deux stades d'oxydation (environ 7 jours de jeu). */
    private static final int WEATHER_FROM = 504000;
    private static final int WEATHER_TO = 552000;

    public enum Status implements StringRepresentable {
        IDLE, MOVING, WORKING, NO_TOOL, FROZEN;

        public static final Codec<Status> CODEC = StringRepresentable.fromEnum(Status::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final Codec<Worker> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(w -> w.id),
            WeatherState.CODEC.optionalFieldOf("weather", WeatherState.UNAFFECTED).forGetter(w -> w.weather),
            Codec.BOOL.optionalFieldOf("waxed", false).forGetter(w -> w.waxed),
            Codec.LONG.optionalFieldOf("next_weather", -1L).forGetter(w -> w.nextWeatherTick),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("tool", ItemStack.EMPTY).forGetter(w -> w.tool),
            BlockPos.CODEC.fieldOf("stand").forGetter(w -> w.stand),
            BlockPos.CODEC.optionalFieldOf("target").forGetter(w -> Optional.ofNullable(w.target)),
            Status.CODEC.optionalFieldOf("status", Status.IDLE).forGetter(w -> w.status),
            ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(w -> Optional.ofNullable(w.customName)),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("antenna", ItemStack.EMPTY).forGetter(w -> w.antenna)
    ).apply(i, Worker::new));

    /**
     * Ce que le client reçoit pour dessiner la marionnette : pas de minuterie d'oxydation,
     * et le statut tel quel (MOVING/WORKING ne sont pas remis à IDLE comme au chargement d'une sauvegarde).
     */
    public static final Codec<Worker> CLIENT_CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(w -> w.id),
            WeatherState.CODEC.optionalFieldOf("weather", WeatherState.UNAFFECTED).forGetter(w -> w.weather),
            Codec.BOOL.optionalFieldOf("waxed", false).forGetter(w -> w.waxed),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("tool", ItemStack.EMPTY).forGetter(w -> w.tool),
            BlockPos.CODEC.fieldOf("stand").forGetter(w -> w.stand),
            BlockPos.CODEC.optionalFieldOf("target").forGetter(w -> Optional.ofNullable(w.target)),
            Status.CODEC.optionalFieldOf("status", Status.IDLE).forGetter(w -> w.status),
            ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(w -> Optional.ofNullable(w.customName)),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("antenna", ItemStack.EMPTY).forGetter(w -> w.antenna)
    ).apply(i, (id, weather, waxed, tool, stand, target, status, name, antenna) -> {
        Worker w = new Worker(id, weather, waxed, -1L, tool, stand, target, Status.IDLE, name, antenna);
        w.status = status;
        return w;
    }));

    public final UUID id;
    public WeatherState weather;
    public boolean waxed;
    public long nextWeatherTick;
    public ItemStack tool;
    /** Bloc où se tiennent les pieds du golem (sa destination pendant qu'il marche). */
    public BlockPos stand;
    /** Bloc travaillé, pour orienter la marionnette. Null quand il n'a rien à faire. */
    public @Nullable BlockPos target;
    public Status status;
    /** Nom donné au golem avec une étiquette, rendu à la libération. */
    public @Nullable Component customName;
    /** Fleur (ou autre) posée sur l'antenne, rendue à la libération. */
    public ItemStack antenna;

    // Simulation côté serveur, non sauvegardée : une tâche interrompue est simplement redemandée.
    public transient @Nullable Task task;
    /** Ticks de marche restants (vers sa tâche, ou vers son coin de repos quand il est inactif). */
    public transient int travelTicks;
    /** Inactif : ticks avant de redemander une tâche. */
    public transient int retryTicks;
    public transient int workTicks;
    public transient int workTotal;
    public transient int lastCrackStage = -1;
    /** Bloc où une fissure est affichée, pour pouvoir l'effacer même quand la tâche a déjà disparu. */
    public transient @Nullable BlockPos crackPos;
    /** Ticks passés sans tâche : au-delà d'un seuil, le golem va se reposer près des autres. */
    public transient int idleTicks;
    /** Fraction de tick d'avance accumulée quand le golem attise un four (multiplicateurs non entiers). */
    public transient double boostCarry;
    /** Dernier verdict du métier sur l'objet tenu, et l'objet jugé (voir ZoneControllerBlockEntity#toolAccepted). */
    public transient boolean toolAccepted;
    public transient @Nullable ItemStack checkedTool;
    public transient boolean checkedEmpty;
    /** Ticks avant de redemander au métier si l'objet tenu convient, même s'il n'a pas changé. */
    public transient int toolCheckTicks;

    private Worker(UUID id, WeatherState weather, boolean waxed, long nextWeatherTick, ItemStack tool, BlockPos stand,
                   Optional<BlockPos> target, Status status, Optional<Component> customName, ItemStack antenna) {
        this.id = id;
        this.weather = weather;
        this.waxed = waxed;
        this.nextWeatherTick = nextWeatherTick;
        this.tool = tool;
        this.stand = stand;
        this.target = target.orElse(null);
        // Un travail en cours au moment de la sauvegarde reprend de zéro.
        this.status = status == Status.MOVING || status == Status.WORKING ? Status.IDLE : status;
        this.customName = customName.orElse(null);
        this.antenna = antenna;
    }

    public static Worker recruit(UUID id, WeatherState weather, boolean waxed, ItemStack tool, BlockPos stand,
                                 @Nullable Component customName, ItemStack antenna) {
        return new Worker(id, weather, waxed, -1L, tool, stand, Optional.empty(), Status.IDLE, Optional.ofNullable(customName), antenna);
    }

    /** Multiplicateur de vitesse selon l'oxydation. Oxydé = statue, il ne travaille plus. */
    public double speedFactor() {
        return switch (weather) {
            case UNAFFECTED -> 1.0;
            case EXPOSED -> 0.85;
            case WEATHERED -> 0.65;
            case OXIDIZED -> 0.0;
        };
    }

    /** Oxydation calquée sur le golem vanilla. Renvoie vrai si l'état a changé. */
    public boolean tickWeathering(long gameTime, RandomSource random) {
        if (waxed || weather == WeatherState.OXIDIZED) return false;
        if (nextWeatherTick < 0) {
            nextWeatherTick = gameTime + random.nextIntBetweenInclusive(WEATHER_FROM, WEATHER_TO);
            return false;
        }
        if (gameTime < nextWeatherTick) return false;
        weather = weather.next();
        nextWeatherTick = weather == WeatherState.OXIDIZED ? -1L : nextWeatherTick + random.nextIntBetweenInclusive(WEATHER_FROM, WEATHER_TO);
        return true;
    }

    public void resetWeatherTimer() {
        nextWeatherTick = -1L;
    }
}
