package com.simpleautomation.job;

import com.simpleautomation.SAConfig;
import com.simpleautomation.registry.ModItems;
import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.XpBank;
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
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;

/**
 * Enchanteur : le golem tient un livre et se sert d'une vraie table d'enchantement de la zone.
 * <ul>
 * <li>La puissance vient des bibliothèques autour de la table, selon les règles vanilla ({@link EnchantingTableBlock#BOOKSHELF_OFFSETS},
 * {@link EnchantingTableBlock#isValidBookShelf}), plafonnée à 15 par le calcul du coût.</li>
 * <li>Le golem prend toujours la 3e option (la meilleure) : coût et enchantements comme au clic du joueur, au hasard et sans choix.</li>
 * <li>Prix : 3 lapis-lazulis des coffres, plus l'XP des 3 niveaux qui mènent au niveau requis (à 30 : 97 + 102 + 107 = 306 XP).
 * L'XP est prise d'abord dans le tampon de la zone, puis en cristaux des coffres ; ce qui dépasse retourne au tampon.</li>
 * <li>Matière : tout objet enchantable non enchanté des coffres, et tout livre, est enchanté (un à la fois).</li>
 * </ul>
 * Aucune orbe ni niveau de joueur : le golem ne fait que dépenser l'XP de la zone.
 */
public final class EnchanterJob implements Job {
    private static final int RESCAN_TICKS = 100;
    /** Hauteurs cherchées autour des bornes pour trouver les tables. */
    private static final int BELOW = 2;
    private static final int ABOVE = 3;
    /** Durée d'un enchantement, avant réglage de vitesse. */
    private static final int WORK_TICKS = 60;
    private static final int LAPIS_COST = 3;
    /** Option choisie : la 3e (indice 2). */
    private static final int OPTION = 2;

    /** Ce qui entre dans la table : un livre, ou un objet enchantable sans enchantement. */
    public static final Predicate<ItemStack> INPUT = s -> !s.isEmpty() && (s.is(Items.BOOK) || s.isEnchantable());
    private static final Predicate<ItemStack> LAPIS = s -> s.is(Tags.Items.GEMS_LAPIS);
    private static final Predicate<ItemStack> CRYSTAL = s -> s.is(ModItems.XP_CRYSTAL.get());

    /** Plan d'un enchantement : puissance de la table au départ et coût tiré (niveau requis). */
    private record Plan(int power, int cost) {
    }

    private final List<BlockPos> tables = new ArrayList<>();
    private final Set<BlockPos> claimed = new HashSet<>();
    private final Map<BlockPos, Plan> plans = new HashMap<>();
    private long nextScan;
    /**
     * Graine d'enchantement de la zone, comme celle d'un joueur : tant qu'elle ne change pas, la table propose le même
     * coût et les mêmes enchantements pour le même objet. Elle change après chaque enchantement réussi, pas à chaque
     * essai : un golem ne peut pas « relancer » jusqu'à tomber sur un coût bas.
     */
    private int seed = RandomSource.create().nextInt();

    @Override
    public boolean acceptsTool(ItemStack tool) {
        return tool.is(Items.BOOK);
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
        tables.clear();
        claimed.clear();
        plans.clear();
        nextScan = 0;
    }

    @Override
    public void tick(ZoneControllerBlockEntity zone) {
        if (!(zone.getLevel() instanceof ServerLevel level) || level.getGameTime() < nextScan) return;
        nextScan = level.getGameTime() + RESCAN_TICKS;
        tables.clear();
        ZoneBounds b = zone.bounds();
        if (b == null) return;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                for (int y = b.markerY() - BELOW; y <= b.markerY() + ABOVE; y++) {
                    p.set(x, y, z);
                    if (level.isLoaded(p) && level.getBlockState(p).is(Blocks.ENCHANTING_TABLE)) tables.add(p.immutable());
                }
            }
        }
    }

    /** Puissance d'une table : la somme des bonus des bibliothèques valides, comme dans le menu vanilla (entier). */
    public static int power(ServerLevel level, BlockPos table) {
        float power = 0;
        for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
            if (EnchantingTableBlock.isValidBookShelf(level, table, offset)) {
                BlockPos at = table.offset(offset);
                power += level.getBlockState(at).getEnchantPowerBonus(level, at);
            }
        }
        return (int) power;
    }

    /** XP pour monter d'un niveau à partir du niveau donné (courbe vanilla). */
    public static int xpToNextLevel(int level) {
        if (level >= 30) return 112 + (level - 30) * 9;
        if (level >= 15) return 37 + (level - 15) * 5;
        return 7 + level * 2;
    }

    /** XP des niveaux {@code cost - 3} à {@code cost} : ce que paie un joueur qui dépense 3 niveaux en étant au niveau requis. */
    public static int xpPrice(int cost) {
        int total = 0;
        for (int l = Math.max(0, cost - LAPIS_COST); l < cost; l++) total += xpToNextLevel(l);
        return total;
    }

    /**
     * Coût (niveau requis) de la 3e option pour cet objet à cette puissance, 0 si elle n'existe pas. Même tirage que
     * {@code EnchantmentMenu.slotsChanged} : graine fixée, puis les 3 options dans l'ordre (la 3e dépend des deux autres).
     */
    private int optionCost(ServerLevel level, BlockPos table, int power, ItemStack sample) {
        RandomSource random = RandomSource.create(seed);
        int cost = 0;
        for (int option = 0; option <= OPTION; option++) {
            cost = EnchantmentHelper.getEnchantmentCost(random, option, power, sample);
            if (cost < option + 1) cost = 0;
            cost = EventHooks.onEnchantmentLevelSet(level, table, option, power, sample, cost);
        }
        return cost;
    }

    /** XP disponible pour payer : le tampon, plus les cristaux des coffres. */
    private static double availableXp(ZoneControllerBlockEntity zone) {
        return zone.xp().stored() + (double) XpBank.XP_PER_CRYSTAL * zone.inventory().count(CRYSTAL);
    }

    /** Un enchantement faisable maintenant à cette table (matière, lapis, XP), ou null. */
    private @Nullable Plan plan(ZoneControllerBlockEntity zone, ServerLevel level, BlockPos table) {
        if (zone.inventory().count(LAPIS) < LAPIS_COST) return null;
        ItemStack sample = zone.inventory().find(INPUT);
        if (sample.isEmpty()) return null;
        int power = power(level, table);
        int cost = optionCost(level, table, power, sample);
        if (cost <= 0 || availableXp(zone) + 1.0E-6 < xpPrice(cost)) return null;
        return new Plan(power, cost);
    }

    @Override
    public @Nullable Task nextTask(ZoneControllerBlockEntity zone, Worker worker) {
        if (!(zone.getLevel() instanceof ServerLevel level) || tables.isEmpty()) return null;
        List<BlockPos> sorted = new ArrayList<>(tables);
        sorted.sort(Comparator.comparingDouble(t -> t.distSqr(worker.stand)));
        for (BlockPos table : sorted) {
            if (claimed.contains(table) || !level.getBlockState(table).is(Blocks.ENCHANTING_TABLE)) continue;
            Plan plan = plan(zone, level, table);
            if (plan == null) continue;
            BlockPos stand = zone.standNear(level, table, 2, worker);
            if (stand == null) continue;
            claimed.add(table);
            plans.put(table, plan);
            return new Task(table, stand);
        }
        return null;
    }

    @Override
    public int workTicks(ZoneControllerBlockEntity zone, Worker worker, Task task) {
        double ticks = WORK_TICKS * SAConfig.WORK_SPEED_MULTIPLIER.getAsDouble() / Math.max(worker.speedFactor(), 0.05);
        return Math.max(SAConfig.MIN_TICKS_PER_BLOCK.getAsInt(), (int) Math.ceil(ticks));
    }

    @Override
    public boolean isStillValid(ZoneControllerBlockEntity zone, Task task) {
        Level level = zone.getLevel();
        return level != null && level.isLoaded(task.target()) && level.getBlockState(task.target()).is(Blocks.ENCHANTING_TABLE);
    }

    @Override
    public void workTick(ZoneControllerBlockEntity zone, Worker worker, Task task) {
        // Les glyphes s'élèvent de la table pendant que le golem travaille.
        if (zone.getLevel() instanceof ServerLevel level && level.getGameTime() % 10 == 0) {
            BlockPos t = task.target();
            level.sendParticles(ParticleTypes.ENCHANT, t.getX() + 0.5, t.getY() + 1.6, t.getZ() + 0.5, 6, 0.4, 0.2, 0.4, 0.5);
        }
    }

    @Override
    public void complete(ZoneControllerBlockEntity zone, Worker worker, Task task) {
        BlockPos table = task.target();
        Plan plan = plans.remove(table);
        claimed.remove(table);
        if (plan == null || !(zone.getLevel() instanceof ServerLevel level) || !level.getBlockState(table).is(Blocks.ENCHANTING_TABLE)) return;
        // Une bibliothèque retirée en route : le prix et le résultat annoncés ne tiennent plus.
        if (power(level, table) < plan.power()) return;
        if (zone.inventory().count(LAPIS) < LAPIS_COST) return;

        List<ItemStack> taken = new ArrayList<>();
        ItemStack input = take(zone, INPUT, 1, taken);
        if (input.isEmpty()) return;
        // Le coût de l'objet vraiment pris (les coffres ont pu changer depuis le plan), puis ce qu'il donnerait :
        // trop cher ou sans résultat, rien n'est dépensé.
        int cost = optionCost(level, table, power(level, table), input);
        int xp = xpPrice(cost);
        List<EnchantmentInstance> chosen = cost > 0 && availableXp(zone) + 1.0E-6 >= xp ? choose(level, input, cost) : List.of();
        if (chosen.isEmpty()) {
            zone.inventory().output(input);
            return;
        }
        // Prix : lapis, puis XP du tampon d'abord, le reste en cristaux (arrondi au cristal, le surplus retourne au tampon).
        ItemStack lapis = take(zone, LAPIS, LAPIS_COST, taken);
        double fromBank = Math.min(zone.xp().stored(), xp);
        double rest = xp - fromBank;
        int crystals = (int) Math.ceil(rest / XpBank.XP_PER_CRYSTAL - 1.0E-9);
        ItemStack paid = crystals > 0 ? take(zone, CRYSTAL, crystals, taken) : ItemStack.EMPTY;
        if (lapis.getCount() < LAPIS_COST || paid.getCount() < crystals) {
            for (ItemStack s : taken) zone.inventory().output(s);
            return;
        }
        zone.xp().take(fromBank);
        zone.xp().add(crystals * (double) XpBank.XP_PER_CRYSTAL - rest);

        seed = level.getRandom().nextInt(); // Comme un joueur après un enchantement : nouvelle offre.
        ItemStack result = input.getItem().applyEnchantments(input, chosen);
        CommonHooks.onPlayerEnchantItem(zone.fakePlayer(), result, chosen);
        zone.inventory().output(result);
        level.playSound(null, table, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
    }

    /**
     * Les enchantements de la 3e option, tirés comme dans le menu vanilla ({@code getEnchantmentList} : graine + option ;
     * un livre en perd un au hasard s'il en a plusieurs).
     */
    private List<EnchantmentInstance> choose(ServerLevel level, ItemStack stack, int cost) {
        RandomSource random = RandomSource.create(seed + OPTION);
        Optional<HolderSet.Named<Enchantment>> possible = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.IN_ENCHANTING_TABLE);
        if (possible.isEmpty()) return List.of();
        List<EnchantmentInstance> list = EnchantmentHelper.selectEnchantment(random, stack, cost, possible.get().stream());
        if (stack.is(Items.BOOK) && list.size() > 1) list.remove(random.nextInt(list.size()));
        return list;
    }

    /** Retire jusqu'à {@code count} objets (de plusieurs piles au besoin), les ajoute à {@code taken} et renvoie leur somme. */
    private static ItemStack take(ZoneControllerBlockEntity zone, Predicate<ItemStack> filter, int count, List<ItemStack> taken) {
        ItemStack total = ItemStack.EMPTY;
        while (total.getCount() < count) {
            ItemStack got = zone.inventory().extract(filter, count - total.getCount());
            if (got.isEmpty()) break;
            taken.add(got.copy());
            if (total.isEmpty()) total = got;
            else total.grow(got.getCount());
        }
        return total;
    }

    @Override
    public void release(Task task) {
        claimed.remove(task.target());
        plans.remove(task.target());
    }

    @Override
    public @Nullable Component progress(ZoneControllerBlockEntity zone) {
        if (!(zone.getLevel() instanceof ServerLevel level) || tables.isEmpty()) return null;
        int best = 0;
        for (BlockPos t : tables) if (level.getBlockState(t).is(Blocks.ENCHANTING_TABLE)) best = Math.max(best, Math.min(power(level, t), 15));
        return Component.translatable("message.simple_automation.enchanter.tables", tables.size(), best);
    }

    @Override
    public void describeTo(ZoneControllerBlockEntity zone, Consumer<Component> lines) {
        if (tables.isEmpty()) {
            lines.accept(Component.translatable("message.simple_automation.enchanter.no_table").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (zone.inventory().count(LAPIS) < LAPIS_COST) {
            lines.accept(Component.translatable("message.simple_automation.enchanter.no_lapis").withStyle(ChatFormatting.GRAY));
        }
        lines.accept(Component.translatable("message.simple_automation.enchanter.tip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void save(ValueOutput out) {
        out.putInt("seed", seed);
    }

    @Override
    public void load(ValueInput in) {
        reset(null);
        seed = in.getIntOr("seed", seed);
    }
}
