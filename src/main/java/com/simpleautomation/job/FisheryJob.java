package com.simpleautomation.job;

import com.simpleautomation.SAConfig;
import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.ZoneBounds;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import org.jspecify.annotations.Nullable;

/**
 * Pêcherie : les golems qui tiennent une canne à pêche se postent au bord de l'eau de la zone et lancent leur ligne
 * dans une source d'eau proche, réservée (un golem par source). Chaque prise passe par la table vanilla
 * {@code gameplay/fishing}, avec un {@link FishingHook} construit mais jamais ajouté au monde : Chance de la mer,
 * la chance et Appât comptent comme pour un joueur, les trésors ne mordent qu'en eau libre (règle vanilla 5x5),
 * l'{@link ItemFishedEvent} est envoyé pour les autres mods. L'XP (1 à 6 par objet) va au tampon de la zone,
 * et chaque prise use la canne d'un point.
 */
public final class FisheryJob implements Job {
    private static final int RESCAN_TICKS = 100;
    /** Hauteur des sources d'eau cherchées autour des bornes. */
    private static final int BELOW = 3;
    private static final int ABOVE = 2;
    /** Distance horizontale maximale entre le golem et le bouchon. */
    public static final int CAST_RANGE = 5;
    /** Attente vanilla d'une touche (avant Appât), en ticks. */
    private static final int WAIT_MIN = 100;
    private static final int WAIT_MAX = 600;
    private static final int MIN_TICKS = 20;

    /** Sources d'eau de la zone où un bouchon peut flotter (surface). */
    private final Set<BlockPos> water = new LinkedHashSet<>();
    /** Cases au bord de l'eau où un golem peut se tenir pour pêcher. */
    private final List<BlockPos> banks = new ArrayList<>();
    /** Eau libre ou non, calculée à la demande et oubliée à chaque nouveau relevé. */
    private final Map<BlockPos, Boolean> openCache = new HashMap<>();
    /** Sources réservées par un golem (le bouchon) et cases du bord prises. */
    private final Set<BlockPos> claimed = new HashSet<>();
    private final Set<BlockPos> claimedBanks = new HashSet<>();
    private long nextScan;

    @Override
    public boolean acceptsTool(ItemStack tool) {
        return isRod(tool);
    }

    /** Canne à pêche, ou tout objet qui sait se lancer comme elle (cannes moddées). */
    public static boolean isRod(ItemStack tool) {
        return !tool.isEmpty() && (tool.is(Tags.Items.TOOLS_FISHING_ROD) || tool.canPerformAction(ItemAbilities.FISHING_ROD_CAST));
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
        water.clear();
        banks.clear();
        openCache.clear();
        claimed.clear();
        claimedBanks.clear();
        nextScan = 0;
    }

    @Override
    public void tick(ZoneControllerBlockEntity zone) {
        if (!(zone.getLevel() instanceof ServerLevel level) || level.getGameTime() < nextScan) return;
        nextScan = level.getGameTime() + RESCAN_TICKS;
        rescan(zone, level);
    }

    /** Relève les sources d'eau de la zone et les cases du bord d'où l'on peut pêcher. */
    private void rescan(ZoneControllerBlockEntity zone, ServerLevel level) {
        water.clear();
        banks.clear();
        openCache.clear();
        ZoneBounds b = zone.bounds();
        if (b == null) return;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                for (int y = b.markerY() - BELOW; y <= b.markerY() + ABOVE; y++) {
                    p.set(x, y, z);
                    if (level.isLoaded(p) && isFishable(level, p)) water.add(p.immutable());
                }
            }
        }
        Set<BlockPos> found = new LinkedHashSet<>();
        for (BlockPos w : water) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos side = w.relative(d);
                // À hauteur de l'eau sur la berge, ou juste au-dessus, debout sur le bloc du bord.
                for (BlockPos s : new BlockPos[] {side, side.above()}) {
                    if (b.columnDistance(s.getX(), s.getZ()) <= 1 && !s.equals(zone.getBlockPos()) && level.isLoaded(s)
                            && ZoneControllerBlockEntity.canStand(level, s)) found.add(s);
                }
            }
        }
        banks.addAll(found);
    }

    /** Une source d'eau sans bloc solide dedans, avec de l'air (ou un nénuphar) au-dessus : un bouchon y flotte. */
    static boolean isFishable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.getFluidState().is(FluidTags.WATER) || !state.getFluidState().isSource() || !state.getCollisionShape(level, pos).isEmpty()) return false;
        BlockState above = level.getBlockState(pos.above());
        return above.getFluidState().isEmpty() && (above.isAir() || above.is(Blocks.LILY_PAD) || above.getCollisionShape(level, pos.above()).isEmpty());
    }

    /**
     * Règle vanilla de l'eau libre ({@code FishingHook.calculateOpenWater}, privée, refaite ici) : sur 4 couches de 5x5
     * centrées sur le bouchon (de 1 bloc dessous à 2 blocs dessus), chaque couche est toute d'eau ou toute d'air
     * (nénuphars compris), et l'eau ne repasse jamais au-dessus de l'air.
     */
    public static boolean isOpenWater(Level level, BlockPos pos) {
        OpenType previous = OpenType.INVALID;
        for (int i = -1; i <= 2; i++) {
            OpenType layer = layerType(level, pos.offset(-2, i, -2), pos.offset(2, i, 2));
            switch (layer) {
                case ABOVE_WATER -> {
                    if (previous == OpenType.INVALID) return false;
                }
                case INSIDE_WATER -> {
                    if (previous == OpenType.ABOVE_WATER) return false;
                }
                case INVALID -> {
                    return false;
                }
            }
            previous = layer;
        }
        return true;
    }

    private enum OpenType { ABOVE_WATER, INSIDE_WATER, INVALID }

    private static OpenType layerType(Level level, BlockPos from, BlockPos to) {
        OpenType type = null;
        for (BlockPos p : BlockPos.betweenClosed(from, to)) {
            OpenType t = blockType(level, p);
            if (type == null) type = t;
            else if (type != t) return OpenType.INVALID;
        }
        return type == null ? OpenType.INVALID : type;
    }

    private static OpenType blockType(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.is(Blocks.LILY_PAD)) return OpenType.ABOVE_WATER;
        var fluid = state.getFluidState();
        return fluid.is(FluidTags.WATER) && fluid.isSource() && state.getCollisionShape(level, pos).isEmpty()
                ? OpenType.INSIDE_WATER : OpenType.INVALID;
    }

    private boolean open(Level level, BlockPos pos) {
        return openCache.computeIfAbsent(pos, p -> isOpenWater(level, p));
    }

    /**
     * La case du bord libre la plus proche du golem qui a une source libre à portée de ligne, en préférant
     * l'eau libre (seule à donner des trésors). Le bouchon va sur la source libre la plus éloignée du bord à portée,
     * eau libre d'abord.
     */
    @Override
    public @Nullable Task nextTask(ZoneControllerBlockEntity zone, Worker worker) {
        if (!(zone.getLevel() instanceof ServerLevel level) || banks.isEmpty()) return null;
        List<BlockPos> sorted = new ArrayList<>(banks);
        sorted.sort(Comparator.comparingDouble(s -> s.distSqr(worker.stand)));
        Task fallback = null;
        for (BlockPos bank : sorted) {
            if (claimedBanks.contains(bank) || occupied(zone, bank, worker) || !ZoneControllerBlockEntity.canStand(level, bank)) continue;
            BlockPos target = bestTarget(level, bank);
            if (target == null) continue;
            Task task = new Task(target, bank);
            if (open(level, target)) {
                fallback = task;
                break;
            }
            if (fallback == null) fallback = task;
        }
        if (fallback == null) return null;
        claimed.add(fallback.target());
        claimedBanks.add(fallback.stand());
        return fallback;
    }

    private static boolean occupied(ZoneControllerBlockEntity zone, BlockPos pos, Worker self) {
        for (Worker o : zone.workers()) if (o != self && o.stand.equals(pos)) return true;
        return false;
    }

    /** Source libre à portée de ligne depuis {@code bank} : eau libre d'abord, puis la plus loin du bord. */
    private @Nullable BlockPos bestTarget(ServerLevel level, BlockPos bank) {
        BlockPos best = null;
        boolean bestOpen = false;
        double bestDist = -1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = -1; dy <= 0; dy++) {
            for (int dx = -CAST_RANGE; dx <= CAST_RANGE; dx++) {
                for (int dz = -CAST_RANGE; dz <= CAST_RANGE; dz++) {
                    // Portée ronde : le bouchon reste à 5 blocs au plus du bord, même en diagonale.
                    if (dx * dx + dz * dz > CAST_RANGE * CAST_RANGE) continue;
                    p.setWithOffset(bank, dx, dy, dz);
                    if (!water.contains(p) || claimed.contains(p)) continue;
                    BlockPos w = p.immutable();
                    boolean isOpen = open(level, w);
                    double dist = dx * dx + dz * dz;
                    if (best == null || (isOpen && !bestOpen) || (isOpen == bestOpen && dist > bestDist)) {
                        best = w;
                        bestOpen = isOpen;
                        bestDist = dist;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Attente d'une touche : 100 à 600 ticks au hasard comme en vanilla, moins Appât (5 secondes par niveau),
     * multipliée par le réglage de vitesse et ralentie par l'oxydation, jamais moins d'une seconde.
     */
    @Override
    public int workTicks(ZoneControllerBlockEntity zone, Worker worker, Task task) {
        if (!(zone.getLevel() instanceof ServerLevel level)) return MIN_TICKS;
        int wait = level.getRandom().nextIntBetweenInclusive(WAIT_MIN, WAIT_MAX) - lureTicks(zone, level, worker.tool);
        double factor = Math.max(worker.speedFactor(), 0.05);
        double ticks = wait * SAConfig.WORK_SPEED_MULTIPLIER.getAsDouble() / factor;
        return Math.max(MIN_TICKS, (int) Math.ceil(ticks));
    }

    /** Réduction de l'attente due à Appât (et aux enchantements moddés du même effet), en ticks. */
    public static int lureTicks(ZoneControllerBlockEntity zone, ServerLevel level, ItemStack rod) {
        FakePlayer fake = zone.fakePlayer();
        return (int) (EnchantmentHelper.getFishingTimeReduction(level, rod, fake) * 20.0F);
    }

    @Override
    public boolean isStillValid(ZoneControllerBlockEntity zone, Task task) {
        Level level = zone.getLevel();
        return level != null && level.isLoaded(task.target()) && isFishable(level, task.target())
                && ZoneControllerBlockEntity.canStand(level, task.stand());
    }

    @Override
    public void complete(ZoneControllerBlockEntity zone, Worker worker, Task task) {
        release(task);
        if (!(zone.getLevel() instanceof ServerLevel level) || !isRod(worker.tool)) return;
        BlockPos target = task.target();
        FakePlayer fake = zone.fakePlayer();
        fake.setItemInHand(InteractionHand.MAIN_HAND, worker.tool);
        int rodDamage;
        FishingHook hook = null;
        try {
            int luck = EnchantmentHelper.getFishingLuckBonus(level, worker.tool, fake);
            hook = new FishingHook(fake, level, luck, lureTicks(zone, level, worker.tool));
            hook.snapTo(target.getX() + 0.5, target.getY() + 0.9, target.getZ() + 0.5, 0.0F, 0.0F);
            hook.setDeltaMovement(Vec3.ZERO);
            // Recalculée au moment de la prise : l'eau a pu changer depuis le dernier relevé.
            hook.openWater = isOpenWater(level, target);
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, hook.position())
                    .withParameter(LootContextParams.TOOL, worker.tool)
                    .withParameter(LootContextParams.THIS_ENTITY, hook)
                    .withParameter(LootContextParams.ATTACKING_ENTITY, fake)
                    .withLuck(luck + fake.getLuck())
                    .create(LootContextParamSets.FISHING);
            LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
            List<ItemStack> loot = table.getRandomItems(params);
            ItemFishedEvent event = new ItemFishedEvent(loot, 1, hook);
            NeoForge.EVENT_BUS.post(event);
            rodDamage = event.getRodDamage();
            if (!event.isCanceled()) {
                for (ItemStack stack : loot) {
                    if (stack.isEmpty()) continue;
                    zone.inventory().output(stack);
                    zone.xp().add(level.getRandom().nextInt(6) + 1);
                }
            }
        } finally {
            // Le bouchon s'était déclaré comme la ligne du joueur fictif (setOwner) : on la lui retire.
            if (hook != null) hook.discard();
            fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        level.sendParticles(ParticleTypes.SPLASH, target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5, 6, 0.2, 0.0, 0.2, 0.0);
        level.playSound(null, task.stand(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1.0F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (rodDamage > 0 && worker.tool.isDamageableItem()) worker.tool.hurtAndBreak(rodDamage, level, (ServerPlayer) null, item -> {});
    }

    @Override
    public void release(Task task) {
        claimed.remove(task.target());
        claimedBanks.remove(task.stand());
    }

    @Override
    public @Nullable Component progress(ZoneControllerBlockEntity zone) {
        if (!(zone.getLevel() instanceof ServerLevel level)) return null;
        int open = 0;
        for (BlockPos w : water) if (open(level, w)) open++;
        return Component.translatable("message.simple_automation.fishery.water", water.size(), open);
    }

    @Override
    public void describeTo(ZoneControllerBlockEntity zone, Consumer<Component> lines) {
        if (!(zone.getLevel() instanceof ServerLevel level)) return;
        if (banks.isEmpty()) {
            lines.accept(Component.translatable("message.simple_automation.fishery.no_spot").withStyle(ChatFormatting.YELLOW));
            return;
        }
        for (BlockPos w : water) if (open(level, w)) return;
        lines.accept(Component.translatable("message.simple_automation.fishery.no_open_water").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void save(ValueOutput out) {
    }

    @Override
    public void load(ValueInput in) {
        reset(null);
    }
}
