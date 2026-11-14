package com.simpleautomation.job;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simpleautomation.zone.Claims;
import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.ZoneBounds;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FungusBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Scierie : les golems à la hache abattent les arbres entiers d'un coup, le contrôleur ramasse ce qui tombe
 * (bûches, pousses et pommes des feuilles qui se décomposent) et replante une pousse à chaque souche.
 * Les champignons géants du Nether (tiges carmin et biscornues) sont abattus aussi, et replantés sur le nylium assorti ;
 * un champignon ne pousse qu'à la poudre d'os : le contrôleur en prend au coffre pour faire grandir ceux qu'il a plantés.
 * Tout bois moddé du tag minecraft:logs est reconnu.
 */
public final class LumberJob implements Job {
    /** Les souches sont cherchées autour de la hauteur des bornes, pour suivre un terrain un peu irrégulier. */
    private static final int BASE_SEARCH = 4;
    /** Hauteur de travail au-dessus des bornes (ramassage des items). */
    private static final int HEIGHT = 40;
    private static final int COLUMNS_PER_CALL = 128;
    private static final int MAX_REPLANT = 64;

    /** Souche à replanter, avec la pousse préférée (celle de l'arbre coupé) si on la connaît. */
    private record Replant(BlockPos pos, Optional<Identifier> sapling) {
        static final Codec<Replant> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Replant::pos),
                Identifier.CODEC.optionalFieldOf("sapling").forGetter(Replant::sapling)
        ).apply(i, Replant::new));
    }

    private int cursor;
    private long idleUntil;
    private final Set<BlockPos> claimed = new HashSet<>();
    private final List<Replant> replant = new ArrayList<>();
    /** Champignons plantés par la scierie, à faire grandir avec la poudre d'os du coffre. */
    private final List<BlockPos> fungi = new ArrayList<>();

    @Override
    public boolean acceptsTool(ItemStack tool) {
        return GolemTools.isAxe(tool);
    }

    @Override
    public void reset(ZoneControllerBlockEntity zone) {
        cursor = 0;
        idleUntil = 0;
        claimed.clear();
    }

    @Override
    public @Nullable Task nextTask(ZoneControllerBlockEntity zone, Worker worker) {
        ZoneBounds b = zone.bounds();
        if (b == null || !(zone.getLevel() instanceof ServerLevel level) || level.getGameTime() < idleUntil) return null;
        int columns = b.sizeX() * b.sizeZ();
        int toScan = Math.min(columns, COLUMNS_PER_CALL);
        for (int i = 0; i < toScan; i++) {
            if (cursor >= columns) {
                cursor = 0;
                // Un tour complet sans arbre : on souffle un peu avant de rescanner.
                idleUntil = level.getGameTime() + 40;
                return null;
            }
            int x = b.minX() + cursor % b.sizeX();
            int z = b.minZ() + cursor / b.sizeX();
            cursor++;
            BlockPos base = findBase(level, x, z, b.markerY());
            if (base == null || claimed.contains(base) || zone.isProtected(base)) continue;
            List<BlockPos> logs = TreeScanner.collect(level, base, b);
            // Un gros arbre (2×2) a plusieurs souches : un seul golem s'en charge.
            if (logs.isEmpty() || logs.stream().anyMatch(claimed::contains)) continue;
            claimed.add(base);
            return new Task(base, standFor(zone, level, base), List.copyOf(logs.subList(1, logs.size())));
        }
        return null;
    }

    /** La plus basse bûche de la colonne posée sur un sol (terre, racines de palétuvier, boue…), voir {@link TreeScanner#isGround}. */
    private @Nullable BlockPos findBase(ServerLevel level, int x, int z, int markerY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = markerY - BASE_SEARCH; y <= markerY + BASE_SEARCH; y++) {
            pos.set(x, y, z);
            if (!level.getBlockState(pos).is(BlockTags.LOGS)) continue;
            BlockPos below = pos.below();
            if (TreeScanner.isGround(level, below, level.getBlockState(below))) return pos.immutable();
        }
        return null;
    }

    /** Une case avec un sol à côté de la souche (sur des racines, le golem se tient en contrebas plutôt que dans le vide). */
    private BlockPos standFor(ZoneControllerBlockEntity zone, ServerLevel level, BlockPos base) {
        BlockPos spot = zone.standNear(level, base, 2, null);
        if (spot != null) return spot;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = base.relative(d);
            BlockState s = level.getBlockState(n);
            if ((s.isAir() || s.canBeReplaced()) && !s.is(BlockTags.LOGS)) return n;
        }
        return base.relative(Direction.NORTH);
    }

    @Override
    public int workTicks(ZoneControllerBlockEntity zone, Worker worker, Task task) {
        ServerLevel level = (ServerLevel) zone.getLevel();
        int total = GolemTools.ticksFor(level, worker, task.target(), level.getBlockState(task.target()));
        for (BlockPos log : task.extra()) total += GolemTools.ticksFor(level, worker, log, level.getBlockState(log));
        return total;
    }

    @Override
    public boolean isStillValid(ZoneControllerBlockEntity zone, Task task) {
        return zone.getLevel() != null && zone.getLevel().getBlockState(task.target()).is(BlockTags.LOGS);
    }

    @Override
    public void complete(ZoneControllerBlockEntity zone, Worker worker, Task task) {
        claimed.remove(task.target());
        ServerLevel level = (ServerLevel) zone.getLevel();
        ZoneBounds b = zone.bounds();
        if (b == null) return;
        // L'arbre a pu pousser ou être entamé pendant le travail : on le relit.
        List<BlockPos> logs = TreeScanner.collect(level, task.target(), b);
        if (logs.isEmpty()) return;
        BlockPos base = task.target();
        Optional<Identifier> sapling = saplingFor(level.getBlockState(base));
        List<BlockPos> stumps = new ArrayList<>();
        for (BlockPos log : logs) {
            if (log.getY() == base.getY() && b.containsColumn(log.getX(), log.getZ())) stumps.add(log);
        }
        // Du haut vers le bas, la souche en dernier : si l'outil casse en route, l'arbre reste debout.
        logs.sort(Comparator.comparingInt((BlockPos p) -> p.getY()).reversed());
        boolean axeBroke = false;
        for (BlockPos log : logs) {
            if (worker.tool.isEmpty()) {
                axeBroke = true;
                break;
            }
            GolemTools.breakBlock(zone, worker, log, log.equals(base) || log.getY() == base.getY());
        }
        if (axeBroke) return;
        for (BlockPos stump : stumps) {
            // Sur des racines de palétuvier, aucune pousse ne tient : on n'attend pas indéfiniment.
            if (!saplingFits(level, stump, sapling)) continue;
            // Liste pleine (pousses jamais fournies) : on oublie la plus ancienne plutôt que de ne plus rien replanter.
            if (replant.size() >= MAX_REPLANT) replant.removeFirst();
            replant.add(new Replant(stump, sapling));
        }
    }

    /** La pousse de l'arbre (ou, à défaut, une pousse de chêne, un champignon sur le nylium) pourrait-elle tenir à cet endroit ? */
    private static boolean saplingFits(ServerLevel level, BlockPos pos, Optional<Identifier> sapling) {
        if (sapling.isPresent() && BuiltInRegistries.BLOCK.containsKey(sapling.get())
                && BuiltInRegistries.BLOCK.getValue(sapling.get()).defaultBlockState().canSurvive(level, pos)) return true;
        return Blocks.OAK_SAPLING.defaultBlockState().canSurvive(level, pos) || onNylium(level, pos);
    }

    /** chêne → pousse de chêne, palétuvier → propagule, tige carmin → champignon carmin ; les autres pousses servent de secours. */
    private Optional<Identifier> saplingFor(BlockState log) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(log.getBlock());
        String path = id.getPath().replace("stripped_", "");
        String guess = path.endsWith("_log") ? path.substring(0, path.length() - 4)
                : path.endsWith("_wood") ? path.substring(0, path.length() - 5)
                : path.endsWith("_stem") ? path.substring(0, path.length() - 5)
                : path.endsWith("_hyphae") ? path.substring(0, path.length() - 7) : null;
        if (guess == null) return Optional.empty();
        if (path.endsWith("_stem") || path.endsWith("_hyphae")) {
            Identifier fungus = Identifier.fromNamespaceAndPath(id.getNamespace(), guess + "_fungus");
            if (BuiltInRegistries.ITEM.containsKey(fungus)) return Optional.of(fungus);
        }
        Identifier propagule = Identifier.fromNamespaceAndPath(id.getNamespace(), guess + "_propagule");
        if (BuiltInRegistries.ITEM.containsKey(propagule)) return Optional.of(propagule);
        return Optional.of(Identifier.fromNamespaceAndPath(id.getNamespace(), guess + "_sapling"));
    }

    /** Souche posée sur du nylium : seul un champignon y repousse. */
    private static boolean onNylium(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(BlockTags.NYLIUM);
    }

    @Override
    public void tick(ZoneControllerBlockEntity zone) {
        ZoneBounds b = zone.bounds();
        if (b == null || !(zone.getLevel() instanceof ServerLevel level) || level.getGameTime() % 40 != 0) return;
        collectItems(zone, level, b);
        replantStumps(zone, level);
        growFungi(zone, level);
    }

    /**
     * Les champignons du Nether ne poussent pas tout seuls : chaque champignon planté reçoit une poudre d'os du coffre
     * à chaque passage (40 % de chances qu'il grandisse, comme pour un joueur). Sans poudre d'os, il attend.
     */
    private void growFungi(ZoneControllerBlockEntity zone, ServerLevel level) {
        for (int i = fungi.size() - 1; i >= 0; i--) {
            BlockPos pos = fungi.get(i);
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof FungusBlock fungus) || !fungus.isValidBonemealTarget(level, pos, state)) {
                fungi.remove(i);
                continue;
            }
            if (zone.inventory().extract(s -> s.is(Items.BONE_MEAL), 1).isEmpty()) return;
            level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 15);
            if (fungus.isBonemealSuccess(level, level.random, pos, state)) fungus.performBonemeal(level, level.random, pos, state);
            if (!(level.getBlockState(pos).getBlock() instanceof FungusBlock)) fungi.remove(i);
        }
    }

    /**
     * Ramasse ce qui traîne au sol dans la zone : bûches, pousses et pommes des feuilles qui se décomposent.
     * Le feuillage dépasse des bornes comme les branches : on ramasse aussi sur cette marge.
     */
    private void collectItems(ZoneControllerBlockEntity zone, ServerLevel level, ZoneBounds b) {
        int m = TreeScanner.OVERHANG;
        AABB box = new AABB(b.minX() - m, b.markerY() - BASE_SEARCH, b.minZ() - m, b.maxX() + 1 + m, b.markerY() + HEIGHT, b.maxZ() + 1 + m);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && !e.getItem().isEmpty())) {
            zone.inventory().output(item.getItem().copy());
            item.discard();
        }
    }

    private void replantStumps(ZoneControllerBlockEntity zone, ServerLevel level) {
        for (int i = replant.size() - 1; i >= 0; i--) {
            Replant r = replant.get(i);
            if (!level.isLoaded(r.pos())) continue;
            BlockState here = level.getBlockState(r.pos());
            if (!here.isAir() && !here.canBeReplaced()) {
                replant.remove(i);
                continue;
            }
            ItemStack sapling = ItemStack.EMPTY;
            if (r.sapling().isPresent()) {
                Identifier wanted = r.sapling().get();
                sapling = zone.inventory().find(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).equals(wanted) && canPlant(level, r.pos(), s));
            }
            if (sapling.isEmpty()) sapling = zone.inventory().find(s -> isSapling(s) && canPlant(level, r.pos(), s));
            if (sapling.isEmpty()) continue; // On réessaiera quand les feuilles auront lâché des pousses.
            BlockState plant = ((BlockItem) sapling.getItem()).getBlock().defaultBlockState();
            // Une protection refuse la pose : on oublie cette souche, la pousse reste au coffre.
            if (!Claims.canPlace(zone, r.pos(), plant)) {
                replant.remove(i);
                continue;
            }
            Item item = sapling.getItem();
            if (zone.inventory().extract(s -> s.is(item), 1).isEmpty()) continue;
            level.setBlock(r.pos(), plant, Block.UPDATE_ALL);
            replant.remove(i);
            if (plant.getBlock() instanceof FungusBlock && !fungi.contains(r.pos()) && fungi.size() < MAX_REPLANT) fungi.add(r.pos());
        }
    }

    /** Une pousse, ou un champignon du Nether. */
    private static boolean isSapling(ItemStack stack) {
        return stack.is(ItemTags.SAPLINGS) || stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof FungusBlock;
    }

    /**
     * La plante tient ici ; un champignon seulement sur son propre nylium (carmin sur carmin, biscornu sur biscornu) :
     * ailleurs il ne grandirait jamais, même à la poudre d'os.
     */
    private static boolean canPlant(ServerLevel level, BlockPos pos, ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem bi)) return false;
        BlockState plant = bi.getBlock().defaultBlockState();
        return plant.canSurvive(level, pos) && (!(bi.getBlock() instanceof FungusBlock fungus) || fungus.isValidBonemealTarget(level, pos, plant));
    }

    @Override
    public void release(Task task) {
        claimed.remove(task.target());
    }

    @Override
    public void save(ValueOutput out) {
        ValueOutput.TypedOutputList<Replant> list = out.list("replant", Replant.CODEC);
        replant.forEach(list::add);
        ValueOutput.TypedOutputList<BlockPos> growing = out.list("fungi", BlockPos.CODEC);
        fungi.forEach(growing::add);
    }

    @Override
    public void load(ValueInput in) {
        replant.clear();
        in.listOrEmpty("replant", Replant.CODEC).stream().forEach(replant::add);
        fungi.clear();
        in.listOrEmpty("fungi", BlockPos.CODEC).stream().forEach(fungi::add);
        cursor = 0;
        claimed.clear();
    }
}
