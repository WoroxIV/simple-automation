package com.simpleautomation.zone;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.simpleautomation.SAConfig;
import com.simpleautomation.job.Job;
import com.simpleautomation.job.Task;
import com.simpleautomation.registry.ModBlockEntities;
import com.simpleautomation.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Cœur d'une zone : trouve ses bornes, recrute les copper golems, simule leur travail et range les items.
 * Les golems recrutés ne sont plus des entités : ce sont des {@link Worker} sauvegardés ici,
 * que le client affiche sous forme de marionnettes. Aucune IA, aucun pathfinding, aucun golem perdu.
 */
public class ZoneControllerBlockEntity extends BlockEntity {
    /** Golem libéré parce que son contrôleur a été cassé : n'importe quelle zone qui le contient le recrute aussitôt. */
    public static final String ORPHAN_TAG = "simple_automation.orphan";
    /** Golem libéré à la main (accroupi, main vide) : il faut une laisse pour le réembaucher. */
    public static final String FREED_TAG = "simple_automation.freed";
    /** Ancien nom de {@link #FREED_TAG} (avant l'itération 2), traité pareil. */
    private static final String LEGACY_RELEASED_TAG = "simple_automation.released";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final UUID FALLBACK_OWNER = UUID.fromString("5a6e3d1c-77b2-4c0e-9d0a-3f4a2b1c9e01");
    /** Un golem construit il y a moins de 10 secondes est recruté sans laisse. */
    private static final int FRESH_GOLEM_TICKS = 200;
    /**
     * Données persistantes d'un copper golem : heure de jeu de son apparition dans le monde (construit, œuf, libéré…).
     * Un golem chargé depuis la sauvegarde sans cette marque n'a jamais été tout juste construit.
     */
    public static final String BIRTH_KEY = "simple_automation.birth";
    /** Marque de naissance d'un golem d'avant la mise à jour passé dans une autre dimension : jamais tout juste construit. */
    private static final long UNKNOWN_BIRTH = Long.MIN_VALUE;
    /** Délai entre deux vérifications de l'objet tenu quand il n'a pas changé (l'étable dépend des animaux présents). */
    private static final int TOOL_CHECK_TICKS = 20;
    private static final int IDLE_RETRY_TICKS = 10;
    /** Après ce nombre de ticks sans tâche, un golem va se reposer à côté des autres. */
    private static final int REST_DELAY_TICKS = 20;
    /** Marge autour du rectangle où les golems sont recrutés (couvre le contrôleur et ses coffres). */
    private static final int RECRUIT_MARGIN = 3;
    /** Rayon autour du contrôleur où réapparaissent les golems libérés (aussi recrutés, voir {@link #recruitGolems}). */
    private static final int RELEASE_RADIUS = 3;
    /** Chute maximale vérifiée par {@link #settle}. */
    private static final int MAX_FALL = 64;
    /** Hauteurs essayées autour d'un point pour trouver où se tenir, de la plus naturelle à la moins. */
    private static final int[] STAND_DY = {0, 1, -1, 2, -2};

    private final JobType jobType;
    private final Job job;
    private final List<Worker> workers = new ArrayList<>();
    private final ZoneInventory inventory = new ZoneInventory(this);
    private final XpBank xp = new XpBank(this);
    private @Nullable ZoneBounds bounds;
    private ZoneBounds.Problem problem = ZoneBounds.Problem.NO_MARKER;
    private @Nullable UUID ownerId;
    private boolean paused;
    private boolean syncDirty;
    private final int tickOffset;

    public ZoneControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ZONE_CONTROLLER.get(), pos, state);
        this.jobType = state.getBlock() instanceof ZoneControllerBlock c ? c.jobType() : JobType.QUARRY;
        this.job = jobType.create();
        this.tickOffset = Math.floorMod(pos.hashCode(), 20);
    }

    // ---------------------------------------------------------------- accès

    public @Nullable ZoneBounds bounds() { return bounds; }
    public List<Worker> workers() { return workers; }
    public ZoneInventory inventory() { return inventory; }
    /** L'XP gagnée par la zone, qui sort en cristaux : voir {@link XpBank}. */
    public XpBank xp() { return xp; }
    public JobType jobType() { return jobType; }
    public Job job() { return job; }

    public void setOwner(Player player) {
        ownerId = player.getUUID();
        setChanged();
    }

    /** Joueur fictif qui casse les blocs au nom du propriétaire, pour que les mods de protection s'appliquent. */
    public FakePlayer fakePlayer() {
        return FakePlayerFactory.get((ServerLevel) level, new GameProfile(ownerId != null ? ownerId : FALLBACK_OWNER, "[SA Golem]"));
    }

    /** Le contrôleur, les blocs collés à lui (coffres, pipes) et les bornes ne sont jamais touchés. */
    public boolean isProtected(BlockPos pos) {
        if (pos.distManhattan(worldPosition) <= 1) return true;
        return bounds != null && (pos.equals(bounds.markerA()) || pos.equals(bounds.markerB()));
    }

    // ---------------------------------------------------------------- ticks

    public static void serverTick(Level level, BlockPos pos, BlockState state, ZoneControllerBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        long time = level.getGameTime();
        if ((time + be.tickOffset) % 20 == 0) {
            be.revalidate(false);
            be.recruitGolems(server);
            be.tickWeathering(server);
        }
        if (be.bounds != null) {
            be.job.tick(be);
            if (time % 10 == 0) {
                be.xp.drain(be.inventory);
                be.paused = be.inventory.flushAndCheckBlocked();
            }
            // En pause (tampon plein), les golems lâchent leur tâche et remontent près du contrôleur.
            for (Worker w : List.copyOf(be.workers)) be.tickWorker(server, w, be.paused);
        }
        if ((time + be.tickOffset) % 10 == 0) be.updateStatusBlockState(state);
        if (be.syncDirty && time % 4 == 0) {
            be.syncDirty = false;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, ZoneControllerBlockEntity be) {
        PuppetBridge.INSTANCE.tick(be);
    }

    private void tickWorker(ServerLevel level, Worker w, boolean paused) {
        if (w.weather == WeatherState.OXIDIZED) {
            if (w.status != Worker.Status.FROZEN) {
                abandon(level, w);
                setStatus(w, Worker.Status.FROZEN);
            }
            return;
        }
        if (!toolAccepted(w)) {
            if (w.status != Worker.Status.NO_TOOL) {
                abandon(level, w);
                w.target = null;
                w.idleTicks = 0;
                setStatus(w, Worker.Status.NO_TOOL);
            }
            if (waitIdle(w)) noTask(level, w);
            return;
        }
        if (paused && (w.status == Worker.Status.MOVING || w.status == Worker.Status.WORKING)) {
            abandon(level, w);
            setStatus(w, Worker.Status.IDLE);
            return;
        }
        switch (w.status) {
            case FROZEN, NO_TOOL, IDLE -> startNextTask(level, w, paused);
            case MOVING -> {
                if (--w.travelTicks > 0) return;
                if (w.task == null || !job.isStillValid(this, w.task)) {
                    abandon(level, w);
                    setStatus(w, Worker.Status.IDLE);
                    return;
                }
                Task task = w.task;
                job.start(this, w, task);
                if (w.task == null || !job.isStillValid(this, w.task)) {
                    if (w.task == null) job.release(task);
                    abandon(level, w);
                    setStatus(w, Worker.Status.IDLE);
                    return;
                }
                w.workTotal = job.workTicks(this, w, w.task);
                w.workTicks = 0;
                setStatus(w, Worker.Status.WORKING);
            }
            case WORKING -> {
                if (w.task == null || !job.isStillValid(this, w.task)) {
                    abandon(level, w);
                    setStatus(w, Worker.Status.IDLE);
                    return;
                }
                if (w.workTicks < w.workTotal) {
                    w.workTicks++;
                    job.workTick(this, w, w.task);
                    if (w.task == null) {
                        clearCrack(level, w);
                        return;
                    }
                    if (job.showsCrack()) updateCrack(level, w);
                }
                if (w.workTicks >= w.workTotal && WorkBudget.tryConsume(job.budgetCost(w.task))) {
                    clearCrack(level, w);
                    Task done = w.task;
                    w.task = null;
                    job.complete(this, w, done);
                    setStatus(w, Worker.Status.IDLE);
                    settleIdle(level, w);
                    syncDirty = true; // l'outil s'est usé
                    setChanged();
                }
            }
        }
    }

    /**
     * Le métier accepte-t-il ce que tient le golem ? Réévalué tout de suite quand l'objet change (donné, repris, cassé),
     * et sinon toutes les secondes tant que le golem ne travaille pas : le « ? » de l'étable apparaît et disparaît
     * au gré des animaux de la zone, sans interrompre une tâche en cours.
     */
    private boolean toolAccepted(Worker w) {
        boolean busy = w.status == Worker.Status.MOVING || w.status == Worker.Status.WORKING;
        if (w.tool != w.checkedTool || w.tool.isEmpty() != w.checkedEmpty || (!busy && --w.toolCheckTicks <= 0)) {
            w.toolAccepted = job.acceptsTool(this, w.tool);
            w.checkedTool = w.tool;
            w.checkedEmpty = w.tool.isEmpty();
            w.toolCheckTicks = TOOL_CHECK_TICKS;
        }
        return w.toolAccepted;
    }

    private void startNextTask(ServerLevel level, Worker w, boolean paused) {
        if (w.status != Worker.Status.IDLE) setStatus(w, Worker.Status.IDLE);
        if (!waitIdle(w)) return;
        // En marche vers son repos, il peut quand même prendre une tâche : il fait demi-tour (w.stand est remplacé).
        Task task = paused ? null : job.nextTask(this, w);
        if (task == null) {
            noTask(level, w);
            return;
        }
        w.idleTicks = 0;
        double distance = Math.sqrt(task.stand().distSqr(w.stand));
        w.task = task;
        w.travelTicks = Math.max(1, (int) Math.ceil(distance / SAConfig.WORKER_WALK_SPEED.getAsDouble()));
        w.stand = task.stand();
        w.target = task.target();
        setStatus(w, Worker.Status.MOVING);
    }

    /**
     * Un tick sans tâche. Renvoie vrai si le golem peut chercher maintenant (délai entre deux essais écoulé).
     * La marche vers le repos avance à part et ne l'empêche pas de chercher.
     */
    private static boolean waitIdle(Worker w) {
        if (w.idleTicks < REST_DELAY_TICKS) w.idleTicks++;
        if (w.travelTicks > 0) w.travelTicks--;
        if (w.retryTicks > 0) {
            w.retryTicks--;
            return false;
        }
        return true;
    }

    /** Rien à faire : on réessaiera un peu plus tard, et passé un moment le golem va se reposer. */
    private void noTask(ServerLevel level, Worker w) {
        w.retryTicks = IDLE_RETRY_TICKS;
        if (w.target != null) {
            w.target = null;
            markSync();
        }
        if (w.idleTicks >= REST_DELAY_TICKS) goRest(level, w);
    }

    /** Le golem inactif rejoint son coin de repos, en prenant le temps de marcher comme pour une tâche. */
    private void goRest(ServerLevel level, Worker w) {
        if (w.travelTicks > 0) return; // déjà en route
        settle(level, w);
        BlockPos dest = restStand(level, w);
        if (dest == null || dest.equals(w.stand)) return;
        double distance = Math.sqrt(dest.distSqr(w.stand));
        w.stand = dest.immutable();
        w.travelTicks = Math.max(IDLE_RETRY_TICKS, (int) Math.ceil(distance / SAConfig.WORKER_WALK_SPEED.getAsDouble()));
        markSync();
    }

    /**
     * Coin de repos d'un golem inactif : celui du métier s'il en propose un, sinon à côté du golem au travail
     * le plus proche (sur une case avec un sol), sinon près du contrôleur. Null s'il n'y a aucune place.
     */
    private @Nullable BlockPos restStand(ServerLevel level, Worker w) {
        BlockPos custom = job.restStand(this, w);
        if (custom != null) return custom;
        Worker nearest = null;
        double best = Double.MAX_VALUE;
        for (Worker o : workers) {
            if (o == w || (o.status != Worker.Status.MOVING && o.status != Worker.Status.WORKING)) continue;
            double d = o.stand.distSqr(w.stand);
            if (d < best) {
                best = d;
                nearest = o;
            }
        }
        if (nearest != null) {
            BlockPos spot = standNear(level, nearest.stand, 2, w);
            if (spot != null) return spot;
        }
        return standNear(level, worldPosition, 3, w);
    }

    /**
     * Case où se tenir à {@code radius} blocs au plus de {@code center}, jamais sur le contrôleur lui-même
     * (en évitant celles des autres golems si possible). Un golem {@code w} déjà bien placé ne bouge pas,
     * pour qu'il ne piétine pas sur place. Null s'il n'y a aucune place.
     */
    public @Nullable BlockPos standNear(ServerLevel level, BlockPos center, int radius, @Nullable Worker w) {
        return standNear(level, center, radius, w, p -> true);
    }

    /** Comme {@link #standNear(ServerLevel, BlockPos, int, Worker)}, seulement parmi les cases que {@code allowed} accepte. */
    public @Nullable BlockPos standNear(ServerLevel level, BlockPos center, int radius, @Nullable Worker w, Predicate<BlockPos> allowed) {
        BlockPos s = w != null ? w.stand : center;
        if (w != null && !s.equals(center) && Math.abs(s.getX() - center.getX()) <= radius && Math.abs(s.getZ() - center.getZ()) <= radius
                && Math.abs(s.getY() - center.getY()) <= 2 && level.isLoaded(s) && allowed.test(s) && canStand(level, s)) return s;
        BlockPos fallback = null;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy : STAND_DY) {
                        p.setWithOffset(center, dx, dy, dz);
                        if (p.equals(center) || p.equals(worldPosition) || !level.isLoaded(p) || !allowed.test(p) || !canStand(level, p)) continue;
                        if (!occupied(p, w)) return p.immutable();
                        if (fallback == null) fallback = p.immutable();
                    }
                }
            }
        }
        return fallback;
    }

    private boolean occupied(BlockPos pos, @Nullable Worker self) {
        for (Worker o : workers) if (o != self && o.stand.equals(pos)) return true;
        return false;
    }

    /** Un golem peut-il se tenir là : un sol solide dessous, et deux blocs libres (sans collision ni fluide) ? */
    public static boolean canStand(Level level, BlockPos pos) {
        return hasFloor(level, pos) && isOpen(level, pos) && isOpen(level, pos.above());
    }

    /** Le bloc sous {@code pos} porte-t-il un golem (il a une collision) ? */
    public static boolean hasFloor(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    private static boolean isOpen(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    /**
     * Gravité : si plus rien ne porte le golem (il se tenait sur le bloc miné), il descend jusqu'au premier sol,
     * sans jamais passer sous le bas du monde. Renvoie vrai s'il a bougé.
     */
    public boolean settle(ServerLevel level, Worker w) {
        BlockPos p = w.stand;
        if (!level.isLoaded(p.below()) || hasFloor(level, p)) return false;
        int min = level.getMinY();
        for (int i = 0; i < MAX_FALL && p.getY() - 1 > min; i++) {
            p = p.below();
            if (hasFloor(level, p)) {
                w.stand = p.immutable();
                markSync();
                return true;
            }
        }
        return false;
    }

    /** Après un bloc cassé : le golem qui l'a cassé et tous ceux qui ne travaillent pas retombent sur un sol. */
    private void settleIdle(ServerLevel level, Worker done) {
        settle(level, done);
        for (Worker o : workers) {
            if (o != done && o.status != Worker.Status.MOVING && o.status != Worker.Status.WORKING) settle(level, o);
        }
    }

    /** Retire sa tâche au golem (et sa fissure, toujours). */
    private void abandon(ServerLevel level, Worker w) {
        if (w.task != null) {
            job.release(w.task);
            w.task = null;
        }
        clearCrack(level, w);
        w.travelTicks = 0;
    }

    private static int crackId(Worker w) {
        return w.id.hashCode() | 0x80000000; // négatif : ne croise jamais l'identifiant d'une vraie entité
    }

    private void updateCrack(ServerLevel level, Worker w) {
        if (w.task == null || w.workTotal <= 0) return;
        int stage = Math.min(9, w.workTicks * 10 / w.workTotal);
        if (stage != w.lastCrackStage) {
            BlockPos target = w.task.target();
            if (w.crackPos != null && !w.crackPos.equals(target)) level.destroyBlockProgress(crackId(w), w.crackPos, -1);
            w.lastCrackStage = stage;
            w.crackPos = target;
            level.destroyBlockProgress(crackId(w), target, stage);
        }
    }

    /** Efface la fissure affichée, même si la tâche a déjà disparu (la position est gardée à part). */
    private void clearCrack(ServerLevel level, Worker w) {
        if (w.crackPos != null) level.destroyBlockProgress(crackId(w), w.crackPos, -1);
        w.crackPos = null;
        w.lastCrackStage = -1;
    }

    private void setStatus(Worker w, Worker.Status status) {
        if (w.status != status) {
            w.status = status;
            markSync();
        }
    }

    private void markSync() {
        syncDirty = true;
        setChanged();
    }

    private void tickWeathering(ServerLevel level) {
        for (Worker w : workers) {
            if (w.tickWeathering(level.getGameTime(), level.getRandom())) markSync();
        }
    }

    private void updateStatusBlockState(BlockState state) {
        ZoneStatus status;
        if (bounds == null) status = ZoneStatus.INVALID;
        else if (paused) status = ZoneStatus.PAUSED;
        else if (workers.stream().anyMatch(w -> w.status == Worker.Status.MOVING || w.status == Worker.Status.WORKING)) status = ZoneStatus.WORKING;
        else status = ZoneStatus.IDLE;
        if (level != null && state.hasProperty(ZoneControllerBlock.STATUS) && state.getValue(ZoneControllerBlock.STATUS) != status) {
            level.setBlock(worldPosition, state.setValue(ZoneControllerBlock.STATUS, status), Block.UPDATE_CLIENTS);
        }
    }

    // ---------------------------------------------------------------- zone

    /** Vérifie que les bornes sont toujours là, et cherche une zone si on n'en a pas (ou si {@code force}). */
    public void revalidate(boolean force) {
        if (!(level instanceof ServerLevel server)) return;
        if (bounds != null) {
            boolean intact = markerIntact(server, bounds.markerA()) && markerIntact(server, bounds.markerB());
            if (intact && !force) return;
            if (!intact) setBounds(server, null);
        }
        ZoneBounds.Result result = ZoneBounds.find(server, worldPosition, SAConfig.MAX_ZONE_SIZE.getAsInt(), this);
        if (result instanceof ZoneBounds.Found found) {
            if (!found.bounds().equals(bounds)) setBounds(server, found.bounds());
        } else if (result instanceof ZoneBounds.Failed failed && bounds == null) {
            problem = failed.problem();
        }
    }

    private static boolean markerIntact(ServerLevel level, BlockPos pos) {
        return !level.isLoaded(pos) || level.getBlockState(pos).is(ModBlocks.BORDER_MARKER.get());
    }

    private void setBounds(ServerLevel level, @Nullable ZoneBounds newBounds) {
        for (Worker w : workers) {
            abandon(level, w);
            if (w.status == Worker.Status.MOVING || w.status == Worker.Status.WORKING) w.status = Worker.Status.IDLE;
        }
        bounds = newBounds;
        problem = newBounds == null ? ZoneBounds.Problem.NO_MARKER : problem;
        job.reset(this);
        markSync();
    }

    // ---------------------------------------------------------------- golems

    /**
     * Convertit en ouvriers les copper golems de la zone (et jusqu'à 3 blocs autour, plus tout le coin où réapparaissent
     * les golems libérés autour du contrôleur) : ceux tout juste construits, ceux amenés en laisse, et les orphelins
     * d'un contrôleur cassé. Un golem libéré à la main demande une laisse.
     */
    private void recruitGolems(ServerLevel level) {
        if (bounds == null) return;
        int max = SAConfig.MAX_WORKERS_PER_ZONE.getAsInt();
        if (max > 0 && workers.size() >= max) return;
        int low = Math.min(bounds.markerY(), worldPosition.getY());
        int high = Math.max(bounds.markerY(), worldPosition.getY());
        // La quarry peut être profonde : on cherche aussi les golems descendus dans le trou.
        int bottom = jobType == JobType.QUARRY ? level.getMinY() : low - 4;
        AABB box = new AABB(bounds.minX() - RECRUIT_MARGIN, bottom, bounds.minZ() - RECRUIT_MARGIN,
                bounds.maxX() + 1 + RECRUIT_MARGIN, high + 8, bounds.maxZ() + 1 + RECRUIT_MARGIN)
                // Le contrôleur peut être 2 blocs hors du rectangle : ses orphelins peuvent réapparaître plus loin encore.
                .minmax(new AABB(worldPosition).inflate(RELEASE_RADIUS + 2, 3, RELEASE_RADIUS + 2));
        for (CopperGolem golem : level.getEntitiesOfClass(CopperGolem.class, box, CopperGolem::isAlive)) {
            boolean leashedByPlayer = golem.getLeashHolder() instanceof Player;
            boolean orphan = golem.getTags().contains(ORPHAN_TAG);
            boolean freed = golem.getTags().contains(FREED_TAG) || golem.getTags().contains(LEGACY_RELEASED_TAG);
            boolean fresh = !freed && isFresh(golem, level.getGameTime());
            if (!leashedByPlayer && !orphan && !fresh) continue;
            if (golem.isLeashed()) golem.dropLeash();
            workers.add(Worker.recruit(golem.getUUID(), golem.getWeatherState(), golem.nextWeatheringTick == -2L,
                    golem.getMainHandItem().copy(), golem.blockPosition(), golem.getCustomName(),
                    golem.getItemBySlot(CopperGolem.EQUIPMENT_SLOT_ANTENNA).copy()));
            level.sendParticles(ParticleTypes.WAX_ON, golem.getX(), golem.getY() + 0.6, golem.getZ(), 15, 0.3, 0.4, 0.3, 0.05);
            level.playSound(null, golem.blockPosition(), SoundEvents.COPPER_GOLEM_SPAWN, SoundSource.NEUTRAL, 1.0F, 1.3F);
            golem.discard();
            markSync();
            if (max > 0 && workers.size() >= max) return;
        }
    }

    /**
     * Un copper golem entre dans le monde sans venir de la sauvegarde (construit, œuf, libéré par un contrôleur…) :
     * on note l'heure, pour le recruter sans laisse pendant {@link #FRESH_GOLEM_TICKS}. Un golem qui a déjà sa marque
     * (changement de dimension) la garde. {@code tickCount} ne convient pas : il repart de zéro à chaque chargement.
     */
    public static void markBorn(CopperGolem golem, long gameTime) {
        CompoundTag data = golem.getPersistentData();
        if (!data.contains(BIRTH_KEY)) data.putLong(BIRTH_KEY, gameTime);
    }

    /**
     * Golem sans marque (d'avant la mise à jour) sur le point de changer de dimension : sa copie dans l'autre dimension
     * entrerait dans le monde comme un golem neuf. On note une naissance inconnue, qui ne rend jamais « tout juste construit ».
     */
    public static void markUnknownBirth(CopperGolem golem) {
        CompoundTag data = golem.getPersistentData();
        if (!data.contains(BIRTH_KEY)) data.putLong(BIRTH_KEY, UNKNOWN_BIRTH);
    }

    /** Golem tout juste construit ? Sans marque (golem de la sauvegarde, d'avant la mise à jour) ou de naissance inconnue, jamais. */
    private static boolean isFresh(CopperGolem golem, long gameTime) {
        return golem.getPersistentData().getLong(BIRTH_KEY)
                .map(birth -> birth != UNKNOWN_BIRTH && gameTime - birth >= 0 && gameTime - birth < FRESH_GOLEM_TICKS).orElse(false);
    }

    /**
     * Rend sa liberté à un ouvrier : il redevient un copper golem vanilla, avec son oxydation, sa cire, son nom et son antenne.
     * Il réapparaît sur une case libre autour du contrôleur (jamais au fond de la carrière).
     * {@code orphan} : le contrôleur est cassé, une autre zone pourra le recruter sans laisse.
     */
    private void release(ServerLevel level, Worker w, @Nullable Player player, boolean orphan) {
        abandon(level, w);
        workers.remove(w);
        BlockPos spot = standNear(level, worldPosition, RELEASE_RADIUS, null);
        if (spot == null) spot = worldPosition.above();
        CopperGolem golem = EntityType.COPPER_GOLEM.create(level, EntitySpawnReason.TRIGGERED);
        if (golem != null) {
            golem.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.0F, 0.0F);
            golem.setWeatherState(w.weather);
            golem.nextWeatheringTick = w.waxed ? -2L : -1L;
            if (w.customName != null) golem.setCustomName(w.customName);
            if (!w.antenna.isEmpty()) golem.setItemSlot(CopperGolem.EQUIPMENT_SLOT_ANTENNA, w.antenna.copy());
            golem.addTag(orphan ? ORPHAN_TAG : FREED_TAG);
            level.addFreshEntity(golem);
        } else if (!w.antenna.isEmpty()) {
            drop(level, spot, w.antenna);
        }
        w.antenna = ItemStack.EMPTY;
        if (!w.tool.isEmpty()) {
            if (player != null) give(player, w.tool);
            else drop(level, spot, w.tool);
            w.tool = ItemStack.EMPTY;
        }
        markSync();
    }

    /** Un éclair est tombé dans la zone : chaque golem perd un cran d'oxydation. */
    public void onLightning() {
        if (!(level instanceof ServerLevel server)) return;
        for (Worker w : workers) {
            if (w.weather == WeatherState.UNAFFECTED) continue;
            w.weather = w.weather.previous();
            w.resetWeatherTimer();
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, w.stand.getX() + 0.5, w.stand.getY() + 0.6, w.stand.getZ() + 0.5, 12, 0.3, 0.4, 0.3, 0.1);
        }
        markSync();
    }

    /** Clic droit d'un joueur sur une marionnette (reçu par paquet). */
    public void interact(ServerPlayer player, UUID workerId) {
        if (!(level instanceof ServerLevel server)) return;
        Worker w = workers.stream().filter(x -> x.id.equals(workerId)).findFirst().orElse(null);
        if (w == null || player.distanceToSqr(Vec3.atBottomCenterOf(w.stand)) > 64) return;
        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack held = player.getItemInHand(hand);

        if (held.isEmpty()) {
            if (player.isShiftKeyDown()) {
                release(server, w, player, false);
            } else if (!w.tool.isEmpty()) {
                abandon(server, w);
                player.setItemInHand(hand, w.tool);
                w.tool = ItemStack.EMPTY;
                markSync();
            }
            return;
        }
        if (held.is(Items.HONEYCOMB)) {
            if (!w.waxed) {
                w.waxed = true;
                held.consume(1, player);
                server.levelEvent(null, 3003, w.stand, 0);
                markSync();
            }
            return;
        }
        if (held.is(ItemTags.AXES) && player.isShiftKeyDown()) {
            if (w.waxed) {
                w.waxed = false;
                server.levelEvent(null, 3004, w.stand, 0);
            } else if (w.weather != WeatherState.UNAFFECTED) {
                w.weather = w.weather.previous();
                server.levelEvent(null, 3005, w.stand, 0);
            } else {
                return;
            }
            w.resetWeatherTimer();
            server.playSound(null, w.stand, SoundEvents.AXE_SCRAPE, SoundSource.PLAYERS, 1.0F, 1.0F);
            held.hurtAndBreak(1, player, hand);
            markSync();
            return;
        }
        // N'importe quel objet va dans la main du golem ; l'ancien revient au joueur.
        abandon(server, w);
        ItemStack old = w.tool;
        w.tool = player.hasInfiniteMaterials() ? held.copyWithCount(1) : held.split(1);
        if (!old.isEmpty()) give(player, old);
        server.playSound(null, w.stand, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1.2F);
        if (!job.acceptsTool(this, w.tool)) {
            player.displayClientMessage(Component.translatable("message.simple_automation.worker.wrong_tool." + jobType.getSerializedName())
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        markSync();
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    /** Jette un objet au sol, comme un coffre cassé : même avec la règle blockDrops désactivée, rien ne disparaît. */
    private static void drop(Level level, BlockPos pos, ItemStack stack) {
        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
    }

    // ---------------------------------------------------------------- infos

    public void describeTo(ServerPlayer player) {
        Component job = Component.translatable("block.simple_automation." + jobType.getSerializedName() + "_controller");
        player.sendSystemMessage(Component.literal("» ").append(job).withStyle(ChatFormatting.GOLD));
        if (bounds == null) {
            player.sendSystemMessage(Component.translatable("message.simple_automation.zone.problem." + problem.name().toLowerCase(java.util.Locale.ROOT),
                    SAConfig.MAX_ZONE_SIZE.getAsInt()).withStyle(ChatFormatting.RED));
            return;
        }
        player.sendSystemMessage(Component.translatable("message.simple_automation.zone.size", bounds.sizeX(), bounds.sizeZ()).withStyle(ChatFormatting.GRAY));
        long working = workers.stream().filter(w -> w.status == Worker.Status.MOVING || w.status == Worker.Status.WORKING).count();
        long noTool = workers.stream().filter(w -> w.status == Worker.Status.NO_TOOL).count();
        long frozen = workers.stream().filter(w -> w.status == Worker.Status.FROZEN).count();
        long idle = workers.stream().filter(w -> w.status == Worker.Status.IDLE).count();
        player.sendSystemMessage(Component.translatable("message.simple_automation.zone.workers", workers.size(), working).withStyle(ChatFormatting.GRAY));
        if (workers.isEmpty()) player.sendSystemMessage(Component.translatable("message.simple_automation.zone.hint_recruit").withStyle(ChatFormatting.YELLOW));
        if (noTool > 0) player.sendSystemMessage(Component.translatable("message.simple_automation.zone.no_tool." + jobType.getSerializedName(), noTool).withStyle(ChatFormatting.YELLOW));
        if (frozen > 0) player.sendSystemMessage(Component.translatable("message.simple_automation.zone.frozen", frozen).withStyle(ChatFormatting.YELLOW));
        if (idle > 0) player.sendSystemMessage(Component.translatable("message.simple_automation.zone.idle", idle).withStyle(ChatFormatting.GRAY));
        if (!inventory.hasContainer()) player.sendSystemMessage(Component.translatable("message.simple_automation.zone.no_chest").withStyle(ChatFormatting.YELLOW));
        if (paused) player.sendSystemMessage(Component.translatable("message.simple_automation.zone.paused").withStyle(ChatFormatting.RED));
        if (xp.stored() >= 0.05) player.sendSystemMessage(Component.translatable("message.simple_automation.zone.xp",
                String.format(java.util.Locale.ROOT, "%.1f", xp.stored()), XpBank.XP_PER_CRYSTAL).withStyle(ChatFormatting.GRAY));
        Component progress = this.job.progress(this);
        if (progress != null) player.sendSystemMessage(progress.copy().withStyle(ChatFormatting.GRAY));
        this.job.describeTo(this, player::sendSystemMessage);
        ZoneOutline.show((ServerLevel) level, player, bounds);
    }

    // ---------------------------------------------------------------- cycle de vie

    @Override
    public void onLoad() {
        super.onLoad();
        ZoneRegistry.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        ZoneRegistry.remove(this);
        if (level != null && level.isClientSide()) PuppetBridge.INSTANCE.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        ZoneRegistry.remove(this);
        if (level != null && level.isClientSide()) PuppetBridge.INSTANCE.remove(this);
    }

    /** Bloc cassé : les golems redeviennent libres, leurs outils et le tampon tombent au sol. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel server) {
            for (Worker w : List.copyOf(workers)) release(server, w, null, true);
            for (ItemStack stack : inventory.buffer()) drop(server, pos, stack);
            inventory.buffer().clear();
            // L'XP en attente (moins d'un cristal) sort en orbes, comme celle d'un four cassé.
            int points = (int) Math.floor(xp.take(xp.stored()));
            if (points > 0) ExperienceOrb.award(server, Vec3.atCenterOf(pos), points);
        }
        super.preRemoveSideEffects(pos, state);
    }

    // ---------------------------------------------------------------- sauvegarde et synchro

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (bounds != null) out.store("bounds", ZoneBounds.CODEC, bounds);
        out.store("workers", Worker.CODEC.listOf(), workers);
        out.store("buffer", ZoneInventory.BUFFER_CODEC, inventory.buffer());
        if (ownerId != null) out.store("owner", UUIDUtil.CODEC, ownerId);
        out.putBoolean("paused", paused);
        out.putDouble("xp", xp.stored());
        job.save(out.child("job"));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        bounds = in.read("bounds", ZoneBounds.CODEC).orElse(null);
        workers.clear();
        if (in.getBooleanOr("client", false)) { // paquet de synchro (côté client) : seulement les marionnettes
            workers.addAll(in.read("workers", Worker.CLIENT_CODEC.listOf()).orElse(List.of()));
            return;
        }
        workers.addAll(in.read("workers", Worker.CODEC.listOf()).orElse(List.of()));
        inventory.load(in.read("buffer", ZoneInventory.BUFFER_CODEC).orElse(List.of()));
        ownerId = in.read("owner", UUIDUtil.CODEC).orElse(null);
        paused = in.getBooleanOr("paused", false);
        xp.load(in.getDoubleOr("xp", 0));
        job.load(in.childOrEmpty("job"));
    }

    /** Ce que le client dessine, et rien d'autre (ni tampon, ni état du métier) : bornes et marionnettes. */
    protected void saveClientData(ValueOutput out) {
        out.putBoolean("client", true);
        if (bounds != null) out.store("bounds", ZoneBounds.CODEC, bounds);
        out.store("workers", Worker.CLIENT_CODEC.listOf(), workers);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(problemPath(), LOGGER)) {
            TagValueOutput out = TagValueOutput.createWithContext(reporter, registries);
            saveClientData(out);
            return out.buildResult();
        }
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
