package com.simpleautomation.job;

import com.simpleautomation.registry.ModTags;
import com.simpleautomation.zone.Claims;
import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.ZoneBounds;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.Tags;
import org.jspecify.annotations.Nullable;

/**
 * Quarry : les golems creusent la zone couche par couche, de haut en bas, en partant juste sous les bornes jusqu'à la bedrock.
 * <ul>
 * <li>Pioche, pelle ou mains nues : chacun casse ce que la vanilla lui permet de récolter (à mains nues, la terre,
 * le sable, le gravier… lentement).</li>
 * <li>Le front de taille : à l'entrée d'une couche, ses blocs sont listés une fois, en serpentin. Chaque golem prend,
 * parmi les {@value #WINDOW} premiers qu'il peut récolter, le plus proche, avec un bonus quand son outil est le meilleur
 * de la zone pour ce bloc. Personne n'attend tant qu'il reste un bloc qu'il peut récolter.</li>
 * <li>Un golem se tient sur une case déjà creusée de la couche en cours ; s'il n'y a pas de sol (grotte, lave, eau),
 * on pose de la cobble sous ses pieds, prise dans les coffres ou posée quand même s'il n'y en a pas.</li>
 * <li>Après chaque bloc cassé, les fluides voisins sont bouchés avec de la cobble.</li>
 * <li>Un bloc que personne ne peut récolter (pioche en bois sur du diamant) reste en place. Sans aucune pioche dans
 * l'équipe (pioche cassée, golems à mains nues), la carrière attend sur la couche au lieu de sauter la pierre.</li>
 * <li>Carrière finie : tout le monde remonte près du contrôleur.</li>
 * </ul>
 */
public final class QuarryJob implements Job {
    /** Nombre de blocs restants (récoltables par lui) parmi lesquels un golem choisit sa cible. */
    private static final int WINDOW = 128;
    /** Bonus, en blocs de distance, quand l'outil du golem est le meilleur de la zone pour ce bloc. */
    private static final double BEST_TOOL_BONUS = 8.0;
    /** Couches listées au plus par appel (couches vides ou déjà creusées traversées d'un coup). */
    private static final int LAYERS_PER_CALL = 2;

    private int layerY = Integer.MIN_VALUE;
    private boolean finished;
    /** Blocs à creuser de la couche, en serpentin. Null tant qu'elle n'est pas listée (nouvelle couche, chargement). */
    private @Nullable List<BlockPos> layerTargets;
    /** Index du premier bloc de la liste pas encore creusé : tout ce qui est avant est fait. */
    private int front;
    /** Un bloc a été cassé depuis que la liste a été faite : on la refait une fois avant de changer de couche (sable tombé, cobble posée…). */
    private boolean dugSinceBuild;
    /** Rempli par {@link #pick} : il reste des blocs que d'autres golems peuvent récolter. */
    private boolean leftForOthers;
    /** Rempli par {@link #pick} : il reste des blocs que personne ne peut récolter. */
    private boolean leftForNobody;
    /** La carrière attend une pioche pour continuer (affiché au clic sur le contrôleur). */
    private boolean needsPickaxe;
    private final Set<BlockPos> claimed = new HashSet<>();

    @Override
    public boolean acceptsTool(ItemStack tool) {
        return tool.isEmpty() || GolemTools.isPickaxe(tool) || GolemTools.isShovel(tool);
    }

    @Override
    public void reset(ZoneControllerBlockEntity zone) {
        ZoneBounds b = zone.bounds();
        layerY = b == null ? Integer.MIN_VALUE : b.markerY() - 1;
        finished = false;
        layerTargets = null;
        front = 0;
        dugSinceBuild = false;
        needsPickaxe = false;
        claimed.clear();
    }

    @Override
    public @Nullable Task nextTask(ZoneControllerBlockEntity zone, Worker worker) {
        ZoneBounds b = zone.bounds();
        if (finished || b == null || !(zone.getLevel() instanceof ServerLevel level)) return null;
        if (layerY == Integer.MIN_VALUE) reset(zone);

        int builds = 0;
        while (true) {
            if (layerTargets == null) {
                if (builds++ >= LAYERS_PER_CALL) return null; // la suite au prochain essai
                buildLayer(zone, level, b);
            }
            BlockPos target = pick(zone, level, worker);
            if (target != null) {
                needsPickaxe = false;
                return claim(zone, level, worker, target);
            }
            // Rien pour lui : la couche continue tant qu'un bloc est en cours ou qu'un autre golem peut en récolter.
            if (!claimed.isEmpty() || leftForOthers) return null;
            if (dugSinceBuild) {
                layerTargets = null; // dernière vérification de la couche avant de descendre
                continue;
            }
            // Il reste des blocs que personne ne peut récolter. On ne les laisse que si une pioche les a « essayés »
            // (pioche en bois sur du diamant) ; sans pioche active (cassée, reprise, mains nues), on attend sur la couche.
            needsPickaxe = leftForNobody && !hasActivePickaxe(zone);
            if (needsPickaxe) return null;
            nextLayer(level);
            if (finished) return null;
        }
    }

    /** Liste les blocs à creuser de la couche ; un fluide devient de la cobble, que les golems minent ensuite. */
    private void buildLayer(ZoneControllerBlockEntity zone, ServerLevel level, ZoneBounds b) {
        List<BlockPos> list = new ArrayList<>();
        int layerSize = b.sizeX() * b.sizeZ();
        for (int i = 0; i < layerSize; i++) {
            BlockPos pos = layerPos(b, i);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof LiquidBlock && !zone.isProtected(pos)) {
                placeCobble(zone, level, pos);
                state = level.getBlockState(pos);
            }
            if (isTarget(zone, level, pos, state)) list.add(pos);
        }
        layerTargets = list;
        front = 0;
        dugSinceBuild = false;
    }

    private void nextLayer(ServerLevel level) {
        layerTargets = null;
        front = 0;
        layerY--;
        if (layerY < level.getMinY()) finished = true;
    }

    /**
     * Le meilleur bloc pour ce golem parmi les {@value #WINDOW} premiers restants qu'il peut récolter :
     * le plus proche, moins un bonus si son outil est le meilleur de la zone pour ce bloc. Avance le front au passage.
     */
    private @Nullable BlockPos pick(ZoneControllerBlockEntity zone, ServerLevel level, Worker worker) {
        List<BlockPos> targets = layerTargets;
        leftForOthers = false;
        leftForNobody = false;
        if (targets == null) return null;
        Map<BlockState, Float> bestSpeeds = new HashMap<>();
        Map<BlockState, Boolean> minable = new HashMap<>();
        BlockPos chosen = null;
        double chosenScore = Double.MAX_VALUE;
        int candidates = 0;
        boolean prefix = true;
        for (int i = front; i < targets.size() && candidates < WINDOW; i++) {
            BlockPos pos = targets.get(i);
            BlockState state = level.getBlockState(pos);
            if (!isTarget(zone, level, pos, state)) {
                if (prefix) front = i + 1;
                continue;
            }
            prefix = false;
            if (claimed.contains(pos)) continue;
            if (!canMine(worker, state)) {
                if (minable.computeIfAbsent(state, s -> anyoneCanMine(zone, s))) leftForOthers = true;
                else leftForNobody = true;
                continue;
            }
            candidates++;
            double score = Math.sqrt(pos.distSqr(worker.stand));
            float best = bestSpeeds.computeIfAbsent(state, s -> bestSpeed(zone, level, s));
            if (GolemTools.speed(level, worker.tool, state) >= best) score -= BEST_TOOL_BONUS;
            if (score < chosenScore) {
                chosenScore = score;
                chosen = pos;
            }
        }
        return chosen;
    }

    /** Vitesse du meilleur outil de la zone sur ce bloc, parmi les golems qui peuvent le récolter. */
    private float bestSpeed(ZoneControllerBlockEntity zone, ServerLevel level, BlockState state) {
        float best = 0;
        for (Worker w : zone.workers()) {
            if (canMine(w, state)) best = Math.max(best, GolemTools.speed(level, w.tool, state));
        }
        return best;
    }

    /** Parcours en serpentin : chaque bloc est voisin du précédent, les golems avancent en ligne. */
    private BlockPos layerPos(ZoneBounds b, int index) {
        int row = index / b.sizeX();
        int col = index % b.sizeX();
        if ((row & 1) == 1) col = b.sizeX() - 1 - col;
        return new BlockPos(b.minX() + col, layerY, b.minZ() + row);
    }

    private Task claim(ZoneControllerBlockEntity zone, ServerLevel level, Worker worker, BlockPos pos) {
        claimed.add(pos);
        BlockPos stand = standFor(zone, level, worker, pos);
        // Debout sur la cible, il retombera dans sa case une fois le bloc cassé : c'est sous la cible qu'il faut un sol.
        supportFloor(zone, level, worker, stand.equals(pos.above()) ? pos.below() : stand.below());
        return new Task(pos, stand);
    }

    /** Une case déjà creusée à côté de la cible ; sinon, debout sur la cible elle-même (il retombe une fois le bloc cassé). */
    private BlockPos standFor(ZoneControllerBlockEntity zone, ServerLevel level, Worker worker, BlockPos target) {
        ZoneBounds b = zone.bounds();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = target.relative(d);
            if (b == null || !b.containsColumn(n.getX(), n.getZ()) || !level.getBlockState(n).isAir()) continue;
            double dist = n.distSqr(worker.stand);
            if (dist < bestDist) {
                bestDist = dist;
                best = n;
            }
        }
        return best != null ? best : target.above();
    }

    /**
     * Pose de la cobble sous les pieds du golem s'il n'y a pas de sol (grotte, lave, eau). Une plante ou un bloc sans
     * collision (herbe, algue…) est d'abord cassé par le golem, drops au coffre ; un bloc que la carrière ne mine pas
     * (coffre, bloc de la liste noire…) n'est jamais écrasé.
     */
    private void supportFloor(ZoneControllerBlockEntity zone, ServerLevel level, Worker worker, BlockPos floor) {
        if (floor.getY() < level.getMinY() || zone.isProtected(floor)) return;
        BlockState state = level.getBlockState(floor);
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
            placeCobble(zone, level, floor);
            return;
        }
        if (!state.getCollisionShape(level, floor).isEmpty() || !isTarget(zone, level, floor, state)
                || !GolemTools.canHarvest(worker.tool, state)) return;
        if (GolemTools.breakBlock(zone, worker, floor, false) && level.getBlockState(floor).isAir()) placeCobble(zone, level, floor);
    }

    /**
     * Cobble prise dans les coffres si possible, posée quand même sinon. Rien n'est posé (ni pris au coffre)
     * si une protection refuse la pose.
     */
    static void placeCobble(ZoneControllerBlockEntity zone, ServerLevel level, BlockPos pos) {
        ItemStack inChest = zone.inventory().find(s -> s.is(Tags.Items.COBBLESTONES) && s.getItem() instanceof BlockItem);
        BlockState toPlace = inChest.getItem() instanceof BlockItem bi ? bi.getBlock().defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
        if (!Claims.canPlace(zone, pos, toPlace)) return;
        if (!inChest.isEmpty()) {
            Item item = inChest.getItem();
            zone.inventory().extract(s -> s.is(item), 1);
        }
        level.setBlock(pos, toPlace, Block.UPDATE_ALL);
    }

    private boolean isTarget(ZoneControllerBlockEntity zone, ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) return false;
        if (state.hasBlockEntity() || state.is(ModTags.GOLEM_BLACKLIST)) return false;
        if (state.getDestroySpeed(level, pos) < 0) return false;
        return !zone.isProtected(pos);
    }

    /** Le golem peut-il récolter ce bloc ? Règles vanilla : à mains nues, seulement ce qui ne demande pas d'outil. */
    private boolean canMine(Worker w, BlockState state) {
        return w.status != Worker.Status.FROZEN && acceptsTool(w.tool) && GolemTools.canHarvest(w.tool, state);
    }

    private boolean anyoneCanMine(ZoneControllerBlockEntity zone, BlockState state) {
        for (Worker w : zone.workers()) if (canMine(w, state)) return true;
        return false;
    }

    /** Un golem en état de travailler tient-il une pioche ? */
    private static boolean hasActivePickaxe(ZoneControllerBlockEntity zone) {
        for (Worker w : zone.workers()) if (w.status != Worker.Status.FROZEN && GolemTools.isPickaxe(w.tool)) return true;
        return false;
    }

    @Override
    public int workTicks(ZoneControllerBlockEntity zone, Worker worker, Task task) {
        ServerLevel level = (ServerLevel) zone.getLevel();
        return GolemTools.ticksFor(level, worker, task.target(), level.getBlockState(task.target()));
    }

    @Override
    public boolean isStillValid(ZoneControllerBlockEntity zone, Task task) {
        ServerLevel level = (ServerLevel) zone.getLevel();
        return isTarget(zone, level, task.target(), level.getBlockState(task.target()));
    }

    @Override
    public void complete(ZoneControllerBlockEntity zone, Worker worker, Task task) {
        claimed.remove(task.target());
        ServerLevel level = (ServerLevel) zone.getLevel();
        BlockPos pos = task.target();
        if (!isTarget(zone, level, pos, level.getBlockState(pos))) return;
        if (GolemTools.breakBlock(zone, worker, pos, true)) {
            dugSinceBuild = true;
            sealFluids(zone, level, pos);
        }
    }

    /** Bouche avec de la cobble les fluides autour du bloc cassé, pour que la lave n'envahisse pas la couche. */
    private void sealFluids(ZoneControllerBlockEntity zone, ServerLevel level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (zone.isProtected(n)) continue;
            if (level.getBlockState(n).getBlock() instanceof LiquidBlock) placeCobble(zone, level, n);
        }
    }

    @Override
    public void release(Task task) {
        claimed.remove(task.target());
    }

    /** Carrière finie : tout le monde remonte près du contrôleur au lieu de rester au fond du trou. Sinon, choix du contrôleur. */
    @Override
    public @Nullable BlockPos restStand(ZoneControllerBlockEntity zone, Worker worker) {
        if (!finished || !(zone.getLevel() instanceof ServerLevel level)) return null;
        return zone.standNear(level, zone.getBlockPos(), 3, worker);
    }

    @Override
    public @Nullable Component progress(ZoneControllerBlockEntity zone) {
        if (finished) return Component.translatable("message.simple_automation.quarry.finished");
        if (layerY == Integer.MIN_VALUE) return null;
        return Component.translatable("message.simple_automation.quarry.layer", layerY);
    }

    @Override
    public void describeTo(ZoneControllerBlockEntity zone, Consumer<Component> lines) {
        if (needsPickaxe && !finished) {
            lines.accept(Component.translatable("message.simple_automation.quarry.needs_pickaxe").withStyle(ChatFormatting.YELLOW));
        }
    }

    /** Pour les tests : la couche en cours, et si la carrière est finie. */
    public int layerY() { return layerY; }
    public boolean isFinished() { return finished; }

    @Override
    public void save(ValueOutput out) {
        out.putInt("layer", layerY);
        out.putBoolean("finished", finished);
    }

    @Override
    public void load(ValueInput in) {
        layerY = in.getIntOr("layer", Integer.MIN_VALUE);
        finished = in.getBooleanOr("finished", false);
        // La liste de la couche est refaite au premier besoin : les blocs déjà creusés n'y figurent plus.
        layerTargets = null;
        front = 0;
        dugSinceBuild = false;
        needsPickaxe = false;
        claimed.clear();
    }
}
