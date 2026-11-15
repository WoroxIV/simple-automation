package com.simpleautomation.job;

import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Un métier. Le contrôleur fait marcher les golems et compte le temps ; le métier choisit les cibles
 * et applique le résultat. Tout se passe côté serveur.
 */
public interface Job {
    /** Le golem peut-il faire ce métier avec ce qu'il a en main ? */
    boolean acceptsTool(ItemStack tool);

    /**
     * Comme {@link #acceptsTool(ItemStack)}, en connaissant la zone (l'étable regarde si un animal présent mange l'objet).
     * C'est celle-ci que le contrôleur appelle : à l'arrivée d'un objet, puis régulièrement tant que le golem ne travaille pas.
     */
    default boolean acceptsTool(ZoneControllerBlockEntity zone, ItemStack tool) {
        return acceptsTool(tool);
    }

    /** Prochaine tâche pour ce golem, ou null s'il n'y a rien à faire pour lui maintenant. */
    @Nullable Task nextTask(ZoneControllerBlockEntity zone, Worker worker);

    /**
     * Le golem vient d'arriver à son poste (passage MOVING → WORKING), juste avant {@link #workTicks}.
     * Le broyeur y pose son minerai. Si la tâche n'est plus valable ensuite ({@code worker.task = null}
     * ou {@link #isStillValid} faux), le contrôleur appelle {@link #release} et le golem redevient inactif.
     */
    default void start(ZoneControllerBlockEntity zone, Worker worker, Task task) {
    }

    /** Durée de la tâche en ticks, une fois le golem arrivé. */
    int workTicks(ZoneControllerBlockEntity zone, Worker worker, Task task);

    /** La cible existe-t-elle toujours ? (un joueur a pu la casser entre-temps) */
    boolean isStillValid(ZoneControllerBlockEntity zone, Task task);

    /** Appelé à chaque tick pendant que le golem travaille (la forge attise ses fours ici). */
    default void workTick(ZoneControllerBlockEntity zone, Worker worker, Task task) {
    }

    /** Afficher les fissures de minage sur la cible ? (pas sur un four) */
    default boolean showsCrack() {
        return true;
    }

    /** Nombre de blocs que la tâche casse, pour le budget global. */
    default int budgetCost(Task task) {
        return 1 + task.extra().size();
    }

    /** Termine la tâche : casse, récolte, range les items. */
    void complete(ZoneControllerBlockEntity zone, Worker worker, Task task);

    /** La tâche est abandonnée (golem retiré, outil repris…) : libérer sa cible. */
    void release(Task task);

    /**
     * Où un golem inactif va se reposer, ou null pour le choix du contrôleur
     * (à côté du golem au travail le plus proche, sinon près du contrôleur).
     */
    default @Nullable BlockPos restStand(ZoneControllerBlockEntity zone, Worker worker) {
        return null;
    }

    /** Appelé une fois par tick avant les golems (ramassage, replantation…). */
    default void tick(ZoneControllerBlockEntity zone) {
    }

    /** La zone a changé de taille ou de bornes : repartir de zéro. */
    void reset(ZoneControllerBlockEntity zone);

    /** Ligne d'avancement affichée au clic sur le contrôleur, ou null. */
    default net.minecraft.network.chat.@Nullable Component progress(ZoneControllerBlockEntity zone) {
        return null;
    }

    /** Lignes en plus au clic sur le contrôleur (astuces, avertissements), déjà mises en forme par le métier. */
    default void describeTo(ZoneControllerBlockEntity zone, Consumer<Component> lines) {
    }

    void save(ValueOutput out);

    void load(ValueInput in);
}
