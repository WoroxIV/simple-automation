package com.simpleautomation.job;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simpleautomation.SAConfig;
import com.simpleautomation.registry.ModTags;
import com.simpleautomation.zone.Claims;
import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.ZoneBounds;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FungusBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jspecify.annotations.Nullable;

/**
 * Scierie : les golems à la hache abattent les arbres entiers d'un coup, feuilles comprises (leurs pousses arrivent
 * tout de suite au coffre), et le contrôleur replante l'arbre avec les pousses des coffres (voir {@link #replantStumps}).
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

    /** Rayon autour de la souche où l'on cherche une place pour la propagule d'un palétuvier poussé sur des racines. */
    private static final int MANGROVE_SEARCH = 3;

    /**
     * Arbre abattu à replanter : une case, ou les 4 cases d'un gros arbre (2×2), avec la pousse de l'arbre coupé
     * si on la connaît, et l'heure de la coupe (pour savoir quand se rabattre sur une autre espèce).
     */
    private record Replant(List<BlockPos> spots, Optional<Identifier> sapling, long since) {
        static final Codec<Replant> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.listOf().fieldOf("spots").forGetter(Replant::spots),
                Identifier.CODEC.optionalFieldOf("sapling").forGetter(Replant::sapling),
                Codec.LONG.optionalFieldOf("since", -1L).forGetter(Replant::since)
        ).apply(i, Replant::new));

        boolean mega() {
            return spots.size() == 4;
        }
    }

    private int cursor;
    private long idleUntil;
    private final Set<BlockPos> claimed = new HashSet<>();
    private final List<Replant> replant = new ArrayList<>();
    /** Champignons plantés par la scierie, à faire grandir avec la poudre d'os du coffre. */
    private final List<BlockPos> fungi = new ArrayList<>();
    /** Arbres abattus qui attendaient encore des pousses au dernier passage. */
    private int starved;

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
        if (waitsForSaplings()) return null;
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
        // Les feuilles tombent avec l'arbre : leurs pousses arrivent tout de suite au coffre pour replanter.
        List<BlockPos> leaves = TreeScanner.collectLeaves(level, logs, b);
        // Nids d'abord : ils ne dépendent pas de la hache, et ne restent pas en l'air si elle casse en route.
        for (BlockPos nest : nestsOn(level, logs, leaves)) takeNest(zone, level, worker, nest);
        // Du haut vers le bas, la souche en dernier : si l'outil casse en route, l'arbre reste debout.
        logs.sort(Comparator.comparingInt((BlockPos p) -> p.getY()).reversed());
        for (BlockPos log : logs) {
            if (worker.tool.isEmpty()) return;
            GolemTools.breakBlock(zone, worker, log, log.equals(base) || log.getY() == base.getY());
        }
        for (BlockPos leaf : leaves) {
            List<ItemStack> drops = GolemTools.harvest(zone, worker, leaf, false, false);
            if (drops != null) drops.forEach(zone.inventory()::output);
        }
        List<BlockPos> spots = replantSpots(level, base, stumps, sapling);
        if (spots.isEmpty()) return;
        // Liste pleine (pousses jamais fournies) : on oublie la plus ancienne plutôt que de ne plus rien replanter.
        if (replant.size() >= MAX_REPLANT) replant.removeFirst();
        replant.add(new Replant(spots, sapling, level.getGameTime()));
        replantStumps(zone, level); // Les pousses des feuilles sont déjà au coffre : on replante sans attendre.
    }

    /**
     * Les nids d'abeilles accrochés à l'arbre (contre le tronc ou sous les feuilles, là où la génération vanilla les
     * pose) : ils resteraient en l'air une fois l'arbre abattu. Les ruches fabriquées par un joueur ne sont pas concernées.
     */
    private static List<BlockPos> nestsOn(ServerLevel level, List<BlockPos> logs, List<BlockPos> leaves) {
        Set<BlockPos> nests = new HashSet<>();
        for (List<BlockPos> part : List.of(logs, leaves)) {
            for (BlockPos pos : part) {
                for (Direction d : Direction.values()) {
                    BlockPos n = pos.relative(d);
                    BlockState state = level.getBlockState(n);
                    if (state.is(BlockTags.BEEHIVES) && !state.is(Blocks.BEEHIVE)) nests.add(n.immutable());
                }
            }
        }
        return new ArrayList<>(nests);
    }

    /**
     * Le nid tombe avec l'arbre et arrive au coffre entier, avec ses abeilles et son miel (comme cueilli en Silk Touch) :
     * le joueur le replace où il veut, sans abeilles énervées ni perdues. Un nid moddé qui ne se ramasse pas ainsi
     * relâche d'abord ses abeilles.
     */
    private static void takeNest(ZoneControllerBlockEntity zone, ServerLevel level, Worker worker, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        FakePlayer fake = zone.fakePlayer();
        fake.setItemInHand(InteractionHand.MAIN_HAND, worker.tool);
        try {
            if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, fake)).isCanceled()) return;
            // Outil à part : la hache du golem peut être cassée (vide), et ne doit pas gagner Silk Touch.
            ItemStack silk = new ItemStack(Items.WOODEN_AXE);
            silk.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
            BlockEntity be = level.getBlockEntity(pos);
            List<ItemStack> drops = Block.getDrops(state, level, pos, be, fake, silk);
            if (drops.stream().noneMatch(st -> st.is(state.getBlock().asItem())) && be instanceof BeehiveBlockEntity hive) {
                hive.emptyAllLivingFromHive(null, state, BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
            }
            level.levelEvent(2001, pos, Block.getId(state));
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            drops.forEach(zone.inventory()::output);
        } finally {
            fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    /**
     * Où replanter : les 4 souches d'un gros arbre (carré 2×2), sinon la souche principale. Quand la souche ne convient
     * plus, on cherche une place libre à côté :
     * <ul>
     * <li>un palétuvier pousse sur des racines où aucune propagule ne tient : place sur la boue ou la terre voisine ;</li>
     * <li>sous un champignon géant, le nylium recouvert par la tige redevient du netherrack (règle vanilla) : place sur
     * le nylium assorti voisin.</li>
     * </ul>
     */
    private static List<BlockPos> replantSpots(ServerLevel level, BlockPos base, List<BlockPos> stumps, Optional<Identifier> sapling) {
        if (stumps.size() == 4 && isSquare(stumps) && stumps.stream().allMatch(p -> saplingFits(level, p, sapling))) {
            return stumps.stream().sorted(Comparator.comparingInt((BlockPos p) -> p.getX()).thenComparingInt(p -> p.getZ())).toList();
        }
        if (saplingFits(level, base, sapling)) return List.of(base);
        Block block = sapling.filter(BuiltInRegistries.BLOCK::containsKey).map(BuiltInRegistries.BLOCK::getValue).orElse(null);
        boolean fungus = block instanceof FungusBlock;
        if (block == null || !(fungus || BuiltInRegistries.BLOCK.getKey(block).getPath().endsWith("_propagule"))) return List.of();
        BlockState plant = block.defaultBlockState();
        BlockPos best = null;
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-MANGROVE_SEARCH, -3, -MANGROVE_SEARCH), base.offset(MANGROVE_SEARCH, 1, MANGROVE_SEARCH))) {
            BlockState here = level.getBlockState(p);
            if (!(here.isAir() || here.canBeReplaced()) || here.is(BlockTags.LOGS) || !plant.canSurvive(level, p)) continue;
            if (fungus && !((FungusBlock) block).isValidBonemealTarget(level, p, plant)) continue; // il ne grandirait jamais
            if (best == null || p.distSqr(base) < best.distSqr(base)) best = p.immutable();
        }
        return best == null ? List.of() : List.of(best);
    }

    private static boolean isSquare(List<BlockPos> four) {
        int minX = four.stream().mapToInt(BlockPos::getX).min().orElse(0);
        int minZ = four.stream().mapToInt(BlockPos::getZ).min().orElse(0);
        return four.stream().allMatch(p -> p.getX() - minX <= 1 && p.getZ() - minZ <= 1)
                && four.stream().distinct().count() == 4;
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

    /**
     * Replante les arbres abattus avec les pousses des coffres :
     * <ul>
     * <li>d'abord l'espèce de l'arbre coupé ; un gros arbre (2×2) est replanté en carré dès qu'on a 4 pousses identiques ;</li>
     * <li>le chêne noir et le chêne pâle ne poussent qu'en carré : on attend d'en avoir 4 ;</li>
     * <li>sans pousse de la bonne espèce au bout de {@code lumberFallbackSeconds}, une autre espèce (celle qu'on a le plus)
     * est plantée seule ; un épicéa ou un acajou géant sans 4 pousses est replanté avec une seule.</li>
     * </ul>
     */
    private void replantStumps(ZoneControllerBlockEntity zone, ServerLevel level) {
        int fallback = SAConfig.LUMBER_FALLBACK_SECONDS.getAsInt();
        starved = 0;
        for (int i = replant.size() - 1; i >= 0; i--) {
            Replant r = replant.get(i);
            if (r.spots().stream().anyMatch(p -> !level.isLoaded(p))) continue;
            List<BlockPos> free = r.spots().stream().filter(p -> {
                BlockState here = level.getBlockState(p);
                return here.isAir() || here.canBeReplaced();
            }).toList();
            if (free.isEmpty()) {
                replant.remove(i);
                continue;
            }
            if (r.since() < 0) { // Sauvegarde d'avant ce champ : le délai part de maintenant, pas de la création du monde.
                r = new Replant(r.spots(), r.sapling(), level.getGameTime());
                replant.set(i, r);
            }
            boolean late = fallback > 0 && level.getGameTime() - r.since() >= fallback * 20L;
            Item wanted = r.sapling().filter(BuiltInRegistries.ITEM::containsKey).map(BuiltInRegistries.ITEM::getValue).orElse(null);
            List<BlockPos> plantAt = null;
            Item use = null;
            if (wanted != null && wanted != Items.AIR) {
                Item w = wanted;
                boolean megaOnly = new ItemStack(w).is(ModTags.MEGA_ONLY_SAPLINGS);
                int have = zone.inventory().count(st -> st.is(w));
                if (r.mega() && free.size() == 4 && have >= 4 && free.stream().allMatch(p -> canPlant(level, p, new ItemStack(w)))) {
                    plantAt = free;
                    use = w;
                } else if (megaOnly && !r.mega()) {
                    wanted = null; // Chêne noir/pâle à cheval sur le bord de zone : seul, il ne pousserait jamais.
                } else if (!megaOnly && (!r.mega() || late) && have >= 1 && canPlant(level, free.getFirst(), new ItemStack(w))) {
                    plantAt = List.of(free.getFirst());
                    use = w;
                }
            } else {
                wanted = null;
            }
            if (use == null && (wanted == null || late)) {
                BlockPos at = free.getFirst();
                use = otherSapling(zone, level, at, wanted);
                if (use != null) plantAt = List.of(at);
            }
            if (use == null) {
                starved++;
                continue; // On réessaiera quand des pousses arriveront.
            }
            BlockState plant = ((BlockItem) use).getBlock().defaultBlockState();
            // Une protection refuse la pose : on oublie cet arbre, les pousses restent au coffre.
            if (plantAt.stream().anyMatch(p -> !Claims.canPlace(zone, p, plant))) {
                replant.remove(i);
                continue;
            }
            Item item = use;
            ItemStack taken = zone.inventory().extract(st -> st.is(item), plantAt.size());
            if (taken.getCount() < plantAt.size()) {
                zone.inventory().output(taken); // Pas assez finalement (une pipe a été plus rapide) : on rend et on réessaiera.
                starved++;
                continue;
            }
            for (BlockPos p : plantAt) {
                level.setBlock(p, plant, Block.UPDATE_ALL);
                if (plant.getBlock() instanceof FungusBlock && !fungi.contains(p) && fungi.size() < MAX_REPLANT) fungi.add(p);
            }
            replant.remove(i);
        }
    }

    /** Pousse d'une autre espèce pour se rabattre : celle qu'on a le plus, qui pousse seule à cet endroit. */
    private static @Nullable Item otherSapling(ZoneControllerBlockEntity zone, ServerLevel level, BlockPos at, @Nullable Item not) {
        Map<Item, Integer> counts = new HashMap<>();
        Set<Item> tried = new HashSet<>();
        while (true) {
            ItemStack found = zone.inventory().find(st -> isSapling(st) && st.getItem() != not && !tried.contains(st.getItem()));
            if (found.isEmpty()) break;
            Item item = found.getItem();
            tried.add(item);
            if (found.is(ModTags.MEGA_ONLY_SAPLINGS) || !canPlant(level, at, found)) continue;
            counts.put(item, zone.inventory().count(st -> st.is(item)));
        }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    /** Réglage {@code lumberWaitForSaplings} : pas de nouvel arbre tant qu'un arbre abattu attend ses pousses. */
    private boolean waitsForSaplings() {
        return starved > 0 && SAConfig.LUMBER_WAIT_FOR_SAPLINGS.getAsBoolean();
    }

    @Override
    public void describeTo(ZoneControllerBlockEntity zone, Consumer<Component> lines) {
        if (starved > 0) lines.accept(Component.translatable("message.simple_automation.lumber.needs_saplings", starved).withStyle(ChatFormatting.YELLOW));
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
