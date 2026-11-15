package com.simpleautomation.workshop;

import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Un type de station de l'atelier (établi, table de forgeron, tailleur de pierre, alambic). Une station sait :
 * reconnaître son bloc, décrire l'interface de la fiche (cases d'entrée, résultat), annoncer le résultat d'une fiche,
 * et dire ce qu'un golem peut y faire maintenant ({@link #nextStep}) puis le faire ({@link #complete}).
 * Ajouter la meule ou le métier à tisser = une classe de plus dans {@link Stations}.
 */
public interface Station {
    /** Ce qu'un golem fait à une station : fabriquer (instantané), ou charger / vider un alambic. */
    enum Step {
        CRAFT, LOAD, UNLOAD
    }

    /** Identifiant stable : clé de langue (« station.simple_automation.<id> ») et sauvegarde des fiches. */
    String id();

    boolean matches(BlockState state);

    /** Nombre de cases d'entrée de la fiche. */
    int inputs();

    /**
     * Position (x, y) des cases dans l'interface de 176 × 166 : une par entrée, puis la case du résultat en dernier.
     * Le haut est libre jusqu'à y = 80 ; l'inventaire du joueur occupe le bas.
     */
    int[][] layout();

    /** Le résultat se choisit parmi plusieurs (tailleur de pierre) : un clic sur la case du résultat passe au suivant. */
    default boolean hasChoices() {
        return false;
    }

    /** Durée de l'étape en ticks, avant réglage de vitesse. */
    int workTicks(Step step);

    /** Résultat annoncé par la fiche pour ces entrées, ou une pile vide s'il n'y a pas de recette. Serveur seulement. */
    ItemStack preview(ServerLevel level, RecipeCard card);

    /** Résultats possibles (seulement si {@link #hasChoices()}), dans l'ordre des recettes. */
    default List<ItemStack> choices(ServerLevel level, RecipeCard card) {
        return List.of();
    }

    /** Ce que la fiche permet de faire à la station {@code pos} maintenant (matière présente, alambic libre…), ou null. */
    @Nullable Step nextStep(ZoneControllerBlockEntity zone, ServerLevel level, RecipeCard card, BlockPos pos);

    /** Fait l'étape : reprend les conditions, prend la matière aux coffres et range le résultat. Ne fait rien si elles ont changé. */
    void complete(ZoneControllerBlockEntity zone, ServerLevel level, RecipeCard card, BlockPos pos, Step step);
}
