package com.simpleautomation.job;

import com.simpleautomation.SAConfig;
import com.simpleautomation.registry.ModTags;
import com.simpleautomation.zone.Claims;
import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.ZoneBounds;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jspecify.annotations.Nullable;

/**
 * Broyeur : les golems prennent dans les coffres les blocs de minerai (récupérés en Silk Touch), les posent sur un
 * poste de broyage (une case libre de la zone, au niveau des bornes) puis les cassent à la pioche, fissures comprises.
 * Les drops viennent de la loot table du minerai avec LEUR pioche : on tire plusieurs fois et on garde le meilleur,
 * donc une pioche Fortune III donne presque toujours le maximum. Sans Fortune, un minerai donne ce qu'il donnerait
 * miné normalement : c'est l'enchantement qui fait le tier.
 * <p>Minerais : le tag c:ores, sans les blocs de stockage (blocs de fer brut…) ni le tag simple_automation:crusher_blacklist.
 * <p>Protections (claims) : la pose est demandée aux protections ({@link Claims#canPlace}) et la casse passe par un BreakEvent, au nom du propriétaire.
 * Un poste refusé est évité ; tant qu'un minerai posé ne peut pas être cassé, plus rien n'est pris au coffre.
 * On réessaie chaque minute. Un golem inactif ne se repose jamais sur un poste.
 */
public final class CrusherJob implements Job {
    /** Postes où un minerai est posé (sauvegardés) : retrouvés et cassés en priorité, même après un redémarrage. */
    private final Set<BlockPos> placed = new HashSet<>();
    /** Postes réservés par un golem en route (ou en train de casser). */
    private final Set<BlockPos> claimed = new HashSet<>();
    /** Minerais qui se redonnent eux-mêmes (débris antiques…) : on les laisse au coffre (sauvegardé). */
    private final Set<Item> uncrushable = new HashSet<>();
    /** Postes où une protection a refusé la pose ou la casse : évités jusqu'au prochain essai. */
    private final Set<BlockPos> denied = new HashSet<>();
    /** Une casse a été refusée : on ne pose plus de nouveau minerai tant qu'une casse n'a pas réussi. */
    private boolean breakRefused;
    private long retryAt;
    /** Délai avant de réessayer les postes refusés. */
    private static final int RETRY_TICKS = 1200;

    @Override
    public boolean acceptsTool(ItemStack tool) {
        // Silk Touch est ignoré au broyage (voir complete) : n'importe quelle pioche convient.
        return GolemTools.isPickaxe(tool);
    }

    /**
     * Un minerai à broyer : le tag c:ores, sans les blocs de stockage (un bloc de fer brut n'est pas un minerai, même
     * si un mod le range dans c:ores) ni la liste noire du broyeur.
     */
    private static boolean isOreItem(ItemStack s) {
        return s.is(Tags.Items.ORES) && !s.is(Tags.Items.STORAGE_BLOCKS) && !s.is(ModTags.CRUSHER_BLACKLIST);
    }

    private boolean crushable(Worker w, ItemStack s) {
        return isOreItem(s) && s.getItem() instanceof BlockItem bi && !uncrushable.contains(s.getItem())
                && GolemTools.canHarvest(w.tool, bi.getBlock().defaultBlockState());
    }

    /** Le bloc est-il un minerai (même règle que les items pris au coffre) ? */
    private static boolean isOre(BlockState state) {
        return !state.isAir() && isOreItem(state.getBlock().asItem().getDefaultInstance());
    }

    /** Case vide avec un sol solide, où l'on peut poser un minerai. */
    private static boolean isFreePost(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) return false;
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    /** Un golem (autre que {@code self}) se tient-il sur cette case, ou s'y rend-il ? */
    private static boolean standing(ZoneControllerBlockEntity zone, BlockPos pos, @Nullable Worker self) {
        for (Worker o : zone.workers()) if (o != self && o.stand.equals(pos)) return true;
        return false;
    }

    @Override
    public void reset(ZoneControllerBlockEntity zone) {
        // Les minerais déjà posés restent à casser, même si la zone change : on ne les oublie pas.
        claimed.clear();
        clearDenied();
    }

    private void clearDenied() {
        denied.clear();
        breakRefused = false;
    }

    private void deny(ServerLevel level, BlockPos post) {
        denied.add(post);
        retryAt = level.getGameTime() + RETRY_TICKS;
    }

    @Override
    public @Nullable Task nextTask(ZoneControllerBlockEntity zone, Worker worker) {
        if (!(zone.getLevel() instanceof ServerLevel level)) return null;
        if (!denied.isEmpty() && level.getGameTime() >= retryAt) denied.clear(); // on réessaie
        // 1. Un minerai déjà posé (golem retiré en pleine tâche, serveur redémarré) passe avant tout.
        for (Iterator<BlockPos> it = placed.iterator(); it.hasNext(); ) {
            BlockPos pos = it.next();
            if (claimed.contains(pos) || denied.contains(pos) || !level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!isOre(state)) { // un joueur l'a pris : le poste est libéré
                it.remove();
                zone.setChanged();
                continue;
            }
            if (!GolemTools.canHarvest(worker.tool, state)) continue;
            BlockPos stand = standFor(level, zone, pos, worker);
            claimed.add(pos);
            return new Task(pos, stand != null ? stand : pos.above());
        }
        // 2. Sinon un nouveau minerai du coffre, sur le poste libre le plus proche (il sera posé à l'arrivée du golem).
        // Pas tant qu'une casse est refusée : chaque nouveau minerai posé resterait coincé.
        if (placed.isEmpty()) breakRefused = false; // plus rien de coincé (le joueur a pu le récupérer)
        if (breakRefused || zone.inventory().find(s -> crushable(worker, s)).isEmpty()) return null;
        BlockPos post = findPost(level, zone, worker.stand, worker);
        if (post == null) return null;
        BlockPos stand = standFor(level, zone, post, worker);
        if (stand == null) return null;
        claimed.add(post);
        return new Task(post, stand);
    }

    /** Poste libre de la zone le plus proche de {@code origin} : au niveau des bornes, sol solide, jamais contre le contrôleur ni sur un golem. */
    private @Nullable BlockPos findPost(ServerLevel level, ZoneControllerBlockEntity zone, BlockPos origin, @Nullable Worker worker) {
        ZoneBounds b = zone.bounds();
        if (b == null) return null;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                pos.set(x, b.markerY(), z);
                double d = pos.distSqr(origin);
                if (d >= bestDistance || claimed.contains(pos) || placed.contains(pos) || denied.contains(pos) || zone.isProtected(pos)) continue;
                if (!isFreePost(level, pos) || standing(zone, pos, null) || standFor(level, zone, pos, worker) == null) continue;
                best = pos.immutable();
                bestDistance = d;
            }
        }
        return best;
    }

    /** Où se tenir pour travailler sur un poste : là où le golem est s'il y touche déjà, sinon une case à côté, libre. */
    private @Nullable BlockPos standFor(ServerLevel level, ZoneControllerBlockEntity zone, BlockPos post, @Nullable Worker worker) {
        if (worker != null && worker.stand.getY() == post.getY() && worker.stand.distManhattan(post) == 1
                && ZoneControllerBlockEntity.canStand(level, worker.stand)) return worker.stand;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = post.relative(d);
            if (claimed.contains(n) || placed.contains(n) || !level.isLoaded(n) || standing(zone, n, worker)) continue;
            if (ZoneControllerBlockEntity.canStand(level, n)) return n;
        }
        return zone.standNear(level, post, 1, worker);
    }

    /** Le golem arrive : on prend le minerai au coffre et on le pose sur le poste (rien n'est posé s'il a été retiré en route). */
    @Override
    public void start(ZoneControllerBlockEntity zone, Worker worker, Task task) {
        if (!(zone.getLevel() instanceof ServerLevel level)) return;
        BlockPos post = task.target();
        if (placed.contains(post)) return; // déjà posé : il n'y a plus qu'à le casser
        if (!isFreePost(level, post) || standing(zone, post, null)) {
            worker.task = null;
            return;
        }
        ItemStack ore = zone.inventory().extract(s -> crushable(worker, s), 1);
        BlockState state = ore.getItem() instanceof BlockItem bi ? bi.getBlock().defaultBlockState() : null;
        if (state == null || !level.isUnobstructed(state, post, CollisionContext.empty())) {
            zone.inventory().output(ore); // rien de perdu : retour au coffre
            worker.task = null;
            return;
        }
        // On demande aux protections avant de poser ; refusé, le minerai retourne au coffre et le poste est évité.
        if (!Claims.canPlace(zone, post, state)) {
            zone.inventory().output(ore);
            deny(level, post);
            worker.task = null;
            return;
        }
        if (!level.setBlock(post, state, Block.UPDATE_ALL)) {
            zone.inventory().output(ore);
            worker.task = null;
            return;
        }
        placed.add(post);
        zone.setChanged();
        SoundType sound = state.getSoundType(level, post, null);
        level.playSound(null, post, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
    }

    @Override
    public int workTicks(ZoneControllerBlockEntity zone, Worker worker, Task task) {
        ServerLevel level = (ServerLevel) zone.getLevel();
        return GolemTools.ticksFor(level, worker, task.target(), level.getBlockState(task.target()));
    }

    @Override
    public boolean isStillValid(ZoneControllerBlockEntity zone, Task task) {
        ServerLevel level = (ServerLevel) zone.getLevel();
        BlockPos pos = task.target();
        return placed.contains(pos) ? isOre(level.getBlockState(pos)) : isFreePost(level, pos);
    }

    @Override
    public void complete(ZoneControllerBlockEntity zone, Worker worker, Task task) {
        BlockPos pos = task.target();
        claimed.remove(pos);
        if (!(zone.getLevel() instanceof ServerLevel level) || !placed.contains(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (!isOre(state)) {
            placed.remove(pos);
            return;
        }
        if (!GolemTools.canHarvest(worker.tool, state)) return; // il reste posé, un autre golem le cassera
        FakePlayer fake = zone.fakePlayer();
        fake.setItemInHand(InteractionHand.MAIN_HAND, worker.tool);
        try {
            if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, fake)).isCanceled()) {
                // Protégé : le minerai reste posé et suivi (réessayé plus tard), et plus rien n'est pris au coffre d'ici là.
                deny(level, pos);
                breakRefused = true;
                return;
            }
            breakRefused = false;
            BlockEntity be = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
            // Un seul tirage vanilla (Fortune compris) avec la pioche privée de Silk Touch, puis le bonus du broyeur.
            ItemStack rollTool = withoutSilkTouch(worker.tool);
            List<ItemStack> drops = Block.getDrops(state, level, pos, be, fake, rollTool);
            int xp = GolemTools.blockXp(level, pos, state, be, fake, rollTool); // compté une fois, pas multiplié
            float hardness = state.getDestroySpeed(level, pos);
            level.levelEvent(2001, pos, Block.getId(state));
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            placed.remove(pos);
            if (xp > 0) zone.xp().add(xp);
            // Un minerai qui se redonne lui-même retourne au coffre (avec les drops) et ne sera plus posé.
            Item oreItem = state.getBlock().asItem();
            if (drops.stream().anyMatch(s -> s.is(oreItem))) uncrushable.add(oreItem);
            double multiplier = SAConfig.CRUSHER_MULTIPLIER.getAsDouble();
            for (ItemStack drop : drops) {
                if (drop.is(oreItem)) zone.inventory().output(drop);
                else multiply(drop, multiplier, level, zone.inventory()::output);
            }
            if (worker.tool.isDamageableItem() && hardness != 0) worker.tool.hurtAndBreak(1, level, (ServerPlayer) null, item -> {});
        } finally {
            fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    /** Copie de l'outil sans Silk Touch : le broyeur broie toujours, même avec la pioche de la carrière. */
    static ItemStack withoutSilkTouch(ItemStack tool) {
        if (tool.getEnchantments().keySet().stream().noneMatch(e -> e.is(Enchantments.SILK_TOUCH))) return tool;
        ItemStack copy = tool.copy();
        EnchantmentHelper.updateEnchantments(copy, m -> m.removeIf(e -> e.is(Enchantments.SILK_TOUCH)));
        return copy;
    }

    /**
     * {@code count × m} items : la partie entière, plus un avec une probabilité égale à la partie décimale (×1,5 sur un
     * fer brut : 1 ou 2, 1,5 en moyenne). Découpé en piles de taille normale.
     */
    static void multiply(ItemStack drop, double m, ServerLevel level, Consumer<ItemStack> out) {
        double exact = drop.getCount() * m;
        int total = (int) Math.floor(exact);
        if (level.random.nextDouble() < exact - total) total++;
        int max = drop.getMaxStackSize();
        while (total > 0) {
            int n = Math.min(total, max);
            out.accept(drop.copyWithCount(n));
            total -= n;
        }
    }

    @Override
    public void release(Task task) {
        claimed.remove(task.target());
    }

    @Override
    public void describeTo(ZoneControllerBlockEntity zone, Consumer<Component> lines) {
        if (!(zone.getLevel() instanceof ServerLevel level)) return;
        if (!uncrushable.isEmpty()) {
            lines.accept(Component.translatable("message.simple_automation.crusher.uncrushable",
                    ComponentUtils.formatList(uncrushable, i -> i.getDefaultInstance().getHoverName())).withStyle(ChatFormatting.GRAY));
        }
        if (breakRefused || !denied.isEmpty()) {
            lines.accept(Component.translatable("message.simple_automation.crusher.denied").withStyle(ChatFormatting.YELLOW));
        }
        boolean hasOre = !zone.inventory().find(s -> isOreItem(s) && s.getItem() instanceof BlockItem && !uncrushable.contains(s.getItem())).isEmpty();
        if (hasOre && placed.isEmpty() && claimed.isEmpty() && findPost(level, zone, zone.getBlockPos(), null) == null) {
            lines.accept(Component.translatable("message.simple_automation.crusher.no_post").withStyle(ChatFormatting.YELLOW));
        }
    }

    /** Un golem inactif se repose près du contrôleur, jamais sur un poste (ni juste au-dessus) : il le bloquerait. */
    @Override
    public @Nullable BlockPos restStand(ZoneControllerBlockEntity zone, Worker worker) {
        ZoneBounds b = zone.bounds();
        if (b == null || !(zone.getLevel() instanceof ServerLevel level)) return null;
        return zone.standNear(level, zone.getBlockPos(), 4, worker,
                p -> !b.containsColumn(p.getX(), p.getZ()) || (p.getY() != b.markerY() && p.getY() != b.markerY() + 1));
    }

    @Override
    public void save(ValueOutput out) {
        out.store("placed", BlockPos.CODEC.listOf(), List.copyOf(placed));
        out.store("uncrushable", BuiltInRegistries.ITEM.byNameCodec().listOf(), List.copyOf(uncrushable));
    }

    @Override
    public void load(ValueInput in) {
        placed.clear();
        claimed.clear();
        uncrushable.clear();
        clearDenied();
        placed.addAll(in.read("placed", BlockPos.CODEC.listOf()).orElse(List.of()));
        uncrushable.addAll(in.read("uncrushable", BuiltInRegistries.ITEM.byNameCodec().listOf()).orElse(List.of()));
    }
}
