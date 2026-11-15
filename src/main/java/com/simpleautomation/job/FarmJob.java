package com.simpleautomation.job;

import com.simpleautomation.SAConfig;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.TorchflowerCropBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * Ferme : les golems récoltent ce qui est mûr dans la zone et replantent avec une graine de la récolte.
 * Ils sèment aussi les graines des coffres sur la terre labourée vide (la même culture que les voisines si possible),
 * graines de citrouille et de pastèque comprises ; la terre collée à une tige reste libre pour son fruit.
 * Blé, carottes, patates, betteraves, verrues du Nether, et toute culture moddée qui étend CropBlock ;
 * citrouilles et pastèques (la tige reste) ; canne à sucre (on laisse le pied) ; cacao (replanté sur la même bûche) ;
 * baies sucrées (cueillies comme au clic droit, le buisson reste).
 * Cultures moddées hors CropBlock : le tag simple_automation:farm_crops (récoltées quand leur « age » est au maximum,
 * replantées avec une graine de la récolte) ; graines semées en plus : le tag simple_automation:farm_seeds (c:seeds).
 * Mains nues, ça marche lentement ; une houe va plus vite, et une houe Fortune donne plus.
 */
public final class FarmJob implements Job {
    private static final int SEARCH = 3;
    private static final int COLUMNS_PER_CALL = 256;
    /** Durée pour semer une graine, avant oxydation et multiplicateur de la config. */
    private static final int PLANT_TICKS = 10;
    /** Le cacao pousse sur les troncs de jungle : au-dessus de la fenêtre habituelle, on monte jusqu'à cette hauteur. */
    private static final int COCOA_HEIGHT = 12;
    /** Une case où une protection a refusé la pose n'est plus proposée pendant ce temps (30 secondes). */
    private static final long REFUSED_TICKS = 600;

    private int cursor;
    private long idleUntil;
    private final Set<BlockPos> claimed = new HashSet<>();
    /** Cases à semer (parmi {@link #claimed}) : ces tâches ne cassent rien. */
    private final Set<BlockPos> planting = new HashSet<>();
    /** Terre labourée où une protection refuse de semer → fin du refus (heure de jeu). */
    private final Map<BlockPos, Long> refused = new HashMap<>();

    @Override
    public boolean acceptsTool(ItemStack tool) {
        return tool.isEmpty() || GolemTools.isHoe(tool);
    }

    @Override
    public void reset(ZoneControllerBlockEntity zone) {
        cursor = 0;
        idleUntil = 0;
        claimed.clear();
        planting.clear();
        refused.clear();
    }

    @Override
    public @Nullable Task nextTask(ZoneControllerBlockEntity zone, Worker worker) {
        ZoneBounds b = zone.bounds();
        if (b == null || !(zone.getLevel() instanceof ServerLevel level) || level.getGameTime() < idleUntil) return null;
        int columns = b.sizeX() * b.sizeZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        List<Block> seeds = null; // cultures dont un coffre a des graines, lues une seule fois par appel
        for (int i = 0; i < Math.min(columns, COLUMNS_PER_CALL); i++) {
            if (cursor >= columns) {
                cursor = 0;
                idleUntil = level.getGameTime() + 40;
                long now = level.getGameTime();
                refused.values().removeIf(end -> end <= now);
                return null;
            }
            int x = b.minX() + cursor % b.sizeX();
            int z = b.minZ() + cursor / b.sizeX();
            cursor++;
            for (int y = b.markerY() - SEARCH; y <= b.markerY() + SEARCH; y++) {
                pos.set(x, y, z);
                if (claimed.contains(pos) || zone.isProtected(pos)) continue;
                BlockState state = level.getBlockState(pos);
                if (isRipe(level, pos, state)) {
                    BlockPos target = topOf(level, pos.immutable());
                    if (claimed.contains(target)) continue;
                    claimed.add(target);
                    return new Task(target, standNear(zone, level, target));
                }
                if (isEmptyFarmland(level, pos, state)) {
                    Long until = refused.get(pos);
                    if (until != null && until > level.getGameTime()) continue;
                    if (seeds == null) seeds = seedCrops(zone);
                    if (cropFor(level, pos, seeds) == null) continue;
                    BlockPos target = pos.immutable();
                    claimed.add(target);
                    planting.add(target);
                    return new Task(target, standNear(zone, level, target));
                }
            }
            // Cacao plus haut sur un tronc de jungle collé à cette colonne.
            if (besideJungleLog(level, pos.set(x, b.markerY() + SEARCH, z))) {
                for (int y = b.markerY() + SEARCH + 1; y <= b.markerY() + COCOA_HEIGHT; y++) {
                    pos.set(x, y, z);
                    if (claimed.contains(pos) || zone.isProtected(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof CocoaBlock) || !isRipe(level, pos, state)) continue;
                    BlockPos target = pos.immutable();
                    claimed.add(target);
                    return new Task(target, standNear(zone, level, target));
                }
            }
        }
        return null;
    }

    private static boolean besideJungleLog(ServerLevel level, BlockPos pos) {
        for (Direction d : Direction.Plane.HORIZONTAL) if (level.getBlockState(pos.relative(d)).is(BlockTags.JUNGLE_LOGS)) return true;
        return false;
    }

    static boolean isRipe(ServerLevel level, BlockPos pos, BlockState state) {
        if (neverHarvest(state)) return false;
        if (state.getBlock() instanceof CropBlock crop) return crop.isMaxAge(state);
        if (state.getBlock() instanceof NetherWartBlock) return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
        if (state.getBlock() instanceof SweetBerryBushBlock) return state.getValue(SweetBerryBushBlock.AGE) >= SweetBerryBushBlock.MAX_AGE;
        if (state.getBlock() instanceof CocoaBlock) return state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
        if (state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON)) return true;
        if (isTagCrop(state)) {
            IntegerProperty age = ageOf(state);
            return age != null && state.getValue(age) >= age.getPossibleValues().getLast();
        }
        // Canne à sucre : chaque morceau posé sur une autre canne (le pied reste pour repousser).
        return state.is(Blocks.SUGAR_CANE) && level.getBlockState(pos.below()).is(Blocks.SUGAR_CANE);
    }

    /**
     * Culture moddée hors CropBlock : du tag farm_crops, ou plante avec un « age » semée d'une graine du tag farm_seeds
     * (voir {@link #cropOf}).
     */
    private static boolean isTagCrop(BlockState state) {
        if (neverHarvest(state)) return false;
        if (state.is(ModTags.FARM_CROPS)) return true;
        return !(state.getBlock() instanceof DoublePlantBlock) && ageOf(state) != null && new ItemStack(state.getBlock().asItem()).is(ModTags.FARM_SEEDS);
    }

    /**
     * Plantes qui ne se récoltent jamais, même mûres en apparence ou listées dans farm_crops : les tiges de melon et de
     * citrouille (leur graine est dans c:seeds, et une tige à l'âge 7 passait pour mûre : on la cassait), la jeune
     * plante de sarracénie et la pousse de torchiflamme (ce sont leurs fleurs qu'on veut, pas leur graine).
     */
    private static boolean neverHarvest(BlockState state) {
        Block block = state.getBlock();
        return isStem(state) || block instanceof PitcherCropBlock || block instanceof TorchflowerCropBlock;
    }

    /** La propriété « age » d'une culture moddée du tag farm_crops, si elle en a une. */
    private static @Nullable IntegerProperty ageOf(BlockState state) {
        return state.getBlock().getStateDefinition().getProperty("age") instanceof IntegerProperty age ? age : null;
    }

    /**
     * La jeune pousse à replanter après la récolte, ou null si la plante ne se replante pas (citrouille, canne…).
     * Le cacao garde sa face, contre la même bûche.
     */
    private static @Nullable BlockState youngOf(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock || block instanceof NetherWartBlock) return block.defaultBlockState();
        if (block instanceof CocoaBlock) return state.setValue(CocoaBlock.AGE, 0);
        if (isTagCrop(state)) {
            IntegerProperty age = ageOf(state);
            return age != null ? state.setValue(age, age.getPossibleValues().getFirst()) : null;
        }
        return null;
    }

    /**
     * Canne à sucre : on coupe du haut vers le bas. Couper un morceau du milieu ferait tomber ceux du dessus
     * en items au sol, que personne ne ramasse.
     */
    private static BlockPos topOf(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(Blocks.SUGAR_CANE)) return pos;
        BlockPos top = pos;
        while (level.getBlockState(top.above()).is(Blocks.SUGAR_CANE)) top = top.above();
        return top;
    }

    private static boolean isEmptyFarmland(ServerLevel level, BlockPos pos, BlockState state) {
        return state.isAir() && level.getBlockState(pos.below()).getBlock() instanceof FarmBlock;
    }

    /**
     * Une graine qui se plante sur la terre labourée : un objet-bloc dont le bloc est une culture (blé, carottes,
     * betteraves…, cultures moddées du tag farm_crops) ou une tige (citrouille, pastèque) ; ou une graine du tag
     * farm_seeds dont la plante a un « age » (récoltée à l'âge maximal, voir {@link #isTagCrop}), sauf les plantes hautes
     * (le pichet) : la ferme ne sème que ce qu'elle sait récolter ensuite.
     */
    private static @Nullable Block cropOf(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem bi)) return null;
        Block block = bi.getBlock();
        if (block instanceof CropBlock || block instanceof StemBlock || block.defaultBlockState().is(ModTags.FARM_CROPS)) return block;
        return stack.is(ModTags.FARM_SEEDS) && isTagCrop(block.defaultBlockState()) ? block : null;
    }

    private static boolean isStem(BlockState state) {
        return state.getBlock() instanceof StemBlock || state.getBlock() instanceof AttachedStemBlock;
    }

    /** Les cultures dont les coffres contiennent des graines, sans doublon. */
    private static List<Block> seedCrops(ZoneControllerBlockEntity zone) {
        List<Block> crops = new ArrayList<>();
        for (ResourceHandler<ItemResource> h : zone.inventory().handlers()) {
            for (int i = 0; i < h.size(); i++) {
                ItemResource r = h.getResource(i);
                if (r.isEmpty() || h.getAmountAsInt(i) <= 0) continue;
                Block crop = cropOf(r.toStack());
                if (crop != null && !crops.contains(crop)) crops.add(crop);
            }
        }
        return crops;
    }

    /**
     * La culture à semer ici : celle d'une case voisine si on en a, sinon la première qui pousse à cet endroit.
     * Rien à côté d'une tige : c'est là que pousse sa citrouille ou sa pastèque.
     */
    private static @Nullable Block cropFor(ServerLevel level, BlockPos pos, List<Block> crops) {
        for (Direction d : Direction.Plane.HORIZONTAL) if (isStem(level.getBlockState(pos.relative(d)))) return null;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Block n = level.getBlockState(pos.relative(d)).getBlock();
            if (crops.contains(n) && n.defaultBlockState().canSurvive(level, pos)) return n;
        }
        for (Block crop : crops) if (crop.defaultBlockState().canSurvive(level, pos)) return crop;
        return null;
    }

    /** Une case où se tenir à côté de la culture, avec un sol (jamais dans l'eau d'irrigation). */
    private static BlockPos standNear(ZoneControllerBlockEntity zone, ServerLevel level, BlockPos crop) {
        BlockPos spot = zone.standNear(level, crop, 2, null);
        if (spot != null) return spot;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = crop.relative(d);
            BlockState s = level.getBlockState(n);
            if ((s.isAir() || s.getBlock() instanceof CropBlock || s.canBeReplaced()) && !level.getBlockState(n.below()).isAir()) return n;
        }
        return crop.above();
    }

    @Override
    public int budgetCost(Task task) {
        return planting.contains(task.target()) ? 0 : 1;
    }

    @Override
    public int workTicks(ZoneControllerBlockEntity zone, Worker worker, Task task) {
        ServerLevel level = (ServerLevel) zone.getLevel();
        if (planting.contains(task.target())) {
            double ticks = PLANT_TICKS * SAConfig.WORK_SPEED_MULTIPLIER.getAsDouble() / Math.max(worker.speedFactor(), 0.05);
            return Math.max(SAConfig.MIN_TICKS_PER_BLOCK.getAsInt(), (int) Math.ceil(ticks));
        }
        // Mains nues 40 ticks, houe en bois 20, fer ~7, netherite ~5 (la vitesse de la houe sur une botte de foin).
        float speed = GolemTools.speed(level, worker.tool, Blocks.HAY_BLOCK.defaultBlockState());
        double ticks = Math.ceil(40.0 / speed) * SAConfig.WORK_SPEED_MULTIPLIER.getAsDouble() / Math.max(worker.speedFactor(), 0.05);
        return Math.max(SAConfig.MIN_TICKS_PER_BLOCK.getAsInt(), (int) Math.ceil(ticks));
    }

    @Override
    public boolean isStillValid(ZoneControllerBlockEntity zone, Task task) {
        ServerLevel level = (ServerLevel) zone.getLevel();
        BlockState state = level.getBlockState(task.target());
        return planting.contains(task.target()) ? isEmptyFarmland(level, task.target(), state) : isRipe(level, task.target(), state);
    }

    @Override
    public void complete(ZoneControllerBlockEntity zone, Worker worker, Task task) {
        claimed.remove(task.target());
        ServerLevel level = (ServerLevel) zone.getLevel();
        BlockPos pos = task.target();
        if (planting.remove(pos)) {
            if (!plant(zone, level, pos)) refused.put(pos, level.getGameTime() + REFUSED_TICKS);
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!isRipe(level, pos, state)) return;
        if (state.getBlock() instanceof SweetBerryBushBlock) {
            pickBerries(zone, level, pos, state);
            return;
        }
        BlockState young = youngOf(state);
        ItemStack seedItem = new ItemStack(state.getBlock().asItem());
        // Culture moddée dont la graine n'est pas l'objet du bloc : celle que donne le clic molette.
        if (young != null && seedItem.isEmpty()) seedItem = state.getCloneItemStack(level, pos, false);
        ItemStack seed = seedItem;
        List<ItemStack> drops = GolemTools.harvest(zone, worker, pos, true);
        if (drops == null) return;
        // On ne prend une graine que si la jeune pousse peut tenir (lumière, sol) et que les protections
        // autorisent la pose : sinon elle serait perdue.
        if (young != null && !seed.isEmpty() && young.canSurvive(level, pos) && Claims.canPlace(zone, pos, young)) {
            boolean planted = false;
            for (ItemStack drop : drops) {
                if (ItemStack.isSameItem(drop, seed)) {
                    drop.shrink(1);
                    planted = true;
                    break;
                }
            }
            if (!planted) planted = !zone.inventory().extract(s -> ItemStack.isSameItem(s, seed), 1).isEmpty();
            if (planted) level.setBlock(pos, young, Block.UPDATE_ALL);
        }
        for (ItemStack drop : drops) if (!drop.isEmpty()) zone.inventory().output(drop);
    }

    /**
     * Baies sucrées : cueillies comme au clic droit d'un joueur (même loot table, même son), le buisson reste.
     * Les protections sont consultées comme pour un joueur qui clique sur le bloc.
     */
    private static void pickBerries(ZoneControllerBlockEntity zone, ServerLevel level, BlockPos pos, BlockState state) {
        FakePlayer fake = zone.fakePlayer();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        PlayerInteractEvent.RightClickBlock event = CommonHooks.onRightClickBlock(fake, InteractionHand.MAIN_HAND, pos, hit);
        if (event.isCanceled() || event.getUseBlock() == TriState.FALSE) return;
        state.useWithoutItem(level, fake, hit);
        // Les baies tombent autour du buisson : on les range.
        GolemTools.collectItems(zone, new AABB(pos).inflate(1));
    }

    /**
     * Sème une graine du coffre ; si elle ne peut finalement pas tenir, elle y retourne (une pose refusée n'en prend aucune).
     * Renvoie false si une protection refuse la pose.
     */
    private static boolean plant(ZoneControllerBlockEntity zone, ServerLevel level, BlockPos pos) {
        if (!isEmptyFarmland(level, pos, level.getBlockState(pos))) return true;
        Block crop = cropFor(level, pos, seedCrops(zone));
        if (crop == null) return true;
        if (!Claims.canPlace(zone, pos, crop.defaultBlockState())) return false;
        ItemStack seed = zone.inventory().extract(s -> cropOf(s) == crop, 1);
        if (seed.isEmpty()) return true;
        BlockState young = crop.defaultBlockState();
        if (young.canSurvive(level, pos)) {
            level.setBlock(pos, young, Block.UPDATE_ALL);
            level.playSound(null, pos, young.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        } else {
            zone.inventory().output(seed);
        }
        return true;
    }

    @Override
    public void release(Task task) {
        claimed.remove(task.target());
        planting.remove(task.target());
    }

    @Override
    public void save(ValueOutput out) {
    }

    @Override
    public void load(ValueInput in) {
        reset(null);
    }
}
