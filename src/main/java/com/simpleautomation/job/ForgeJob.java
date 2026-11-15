package com.simpleautomation.job;

import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.ZoneBounds;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import org.jspecify.annotations.Nullable;

/**
 * Forge : les golems s'occupent des fours vanilla posés dans la zone (four, haut fourneau, fumoir).
 * Ils rapportent les lingots et l'XP au contrôleur, remettent du minerai et du carburant depuis les coffres.
 * À mains nues ça marche ; avec une pelle, le golem attise le four qu'il sert : plus la pelle est bonne, plus le four va vite.
 * Le carburant reste celui de la vanilla par item cuit (un charbon = 8 items), il brûle juste plus vite.
 * Le minerai est réparti entre les fours du même type (part équitable), et chaque four ne reçoit que le carburant
 * qu'il lui faut pour cuire ce qu'il contient.
 */
public final class ForgeJob implements Job {
    private static final int RESCAN_TICKS = 100;
    private static final int SERVICE_TICKS = 30;
    private static final int STOKE_TICKS = 100;
    private static final double MAX_BOOST = 6.0;

    private final List<BlockPos> furnaces = new ArrayList<>();
    private final Set<BlockPos> claimed = new HashSet<>();
    private long nextScan;
    private int roundRobin;

    @Override
    public boolean acceptsTool(ItemStack tool) {
        return true; // mains nues comprises
    }

    @Override
    public boolean showsCrack() {
        return false;
    }

    @Override
    public int budgetCost(Task task) {
        return 0;
    }

    private static boolean stokes(Worker w) {
        return GolemTools.isShovel(w.tool);
    }

    /** Multiplicateur de vitesse du four : bois ×1,4, pierre ×2,1, fer ×2,9, diamant ×3,6, netherite ×4 (plus avec Efficacité). */
    public static double boost(ServerLevel level, Worker w) {
        if (!stokes(w)) return 1.0;
        float speed = GolemTools.speed(level, w.tool, Blocks.DIRT.defaultBlockState());
        return Math.min(MAX_BOOST, 1.0 + (speed - 1.0) * 0.375) * Math.max(w.speedFactor(), 0.05);
    }

    @Override
    public void reset(ZoneControllerBlockEntity zone) {
        furnaces.clear();
        claimed.clear();
        nextScan = 0;
    }

    @Override
    public void tick(ZoneControllerBlockEntity zone) {
        if (!(zone.getLevel() instanceof ServerLevel level) || level.getGameTime() < nextScan) return;
        nextScan = level.getGameTime() + RESCAN_TICKS;
        rescan(zone, level);
    }

    /** Liste les fours de la zone en parcourant les block entities des chunks chargés. */
    private void rescan(ZoneControllerBlockEntity zone, ServerLevel level) {
        furnaces.clear();
        ZoneBounds b = zone.bounds();
        if (b == null) return;
        for (int cx = SectionPos.blockToSectionCoord(b.minX()); cx <= SectionPos.blockToSectionCoord(b.maxX()); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(b.minZ()); cz <= SectionPos.blockToSectionCoord(b.maxZ()); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos p = be.getBlockPos();
                    if (be instanceof AbstractFurnaceBlockEntity && b.containsColumn(p.getX(), p.getZ())
                            && p.getY() >= b.markerY() - 3 && p.getY() <= b.markerY() + 4) {
                        furnaces.add(p.immutable());
                    }
                }
            }
        }
        furnaces.sort(BlockPos::compareTo);
    }

    /** Le four le plus urgent parmi ceux qu'aucun autre golem n'a pris (tour à tour à urgence égale). */
    @Override
    public @Nullable Task nextTask(ZoneControllerBlockEntity zone, Worker worker) {
        if (!(zone.getLevel() instanceof ServerLevel level) || furnaces.isEmpty()) return null;
        int n = furnaces.size();
        int best = -1;
        int bestPriority = Integer.MAX_VALUE;
        for (int i = 0; i < n && bestPriority > 0; i++) {
            int index = (roundRobin + i) % n;
            BlockPos pos = furnaces.get(index);
            if (claimed.contains(pos) || !level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) continue;
            int priority = priority(zone, level, furnace, worker);
            if (priority >= 0 && priority < bestPriority) {
                best = index;
                bestPriority = priority;
            }
        }
        if (best < 0) return null;
        BlockPos pos = furnaces.get(best);
        roundRobin = (best + 1) % n;
        claimed.add(pos);
        return new Task(pos, standNear(level, pos));
    }

    /**
     * Urgence du service, ou -1 s'il n'y a rien à faire : 0 = four à l'arrêt (vide ou sans carburant) qu'on peut relancer,
     * 1 = produits (ou seau vide) à sortir, 2 = complément de minerai ou de carburant, 3 = juste attiser (pelle).
     */
    private int priority(ZoneControllerBlockEntity zone, ServerLevel level, AbstractFurnaceBlockEntity f, Worker w) {
        ItemStack input = f.getItem(0);
        ItemStack fuelSlot = f.getItem(1);
        ItemStack share = shareFor(zone, level, f);
        ItemStack fuel = fuelFor(zone, level, f, share.isEmpty() ? input : share, input.getCount() + share.getCount());
        boolean load = !share.isEmpty() || !fuel.isEmpty();
        if (load && !cooking(f)) return 0;
        if (!f.getItem(2).isEmpty() || (!fuelSlot.isEmpty() && burn(level, f, fuelSlot) <= 0)) return 1;
        if (load) return 2;
        if (stokes(w) && cooking(f)) return 3;
        return -1;
    }

    /** Le four brûle et a quelque chose à cuire : l'attiser sert à quelque chose. */
    private static boolean cooking(AbstractFurnaceBlockEntity f) {
        BlockState state = f.getBlockState();
        return state.hasProperty(AbstractFurnaceBlock.LIT) && state.getValue(AbstractFurnaceBlock.LIT) && !f.getItem(0).isEmpty();
    }

    /**
     * Part équitable de minerai à ajouter : ce qu'en ont les coffres plus ce qu'il y en a déjà dans les fours du même type,
     * divisé par le nombre de ces fours qui peuvent le prendre (entrée vide ou même item), arrondi au-dessus.
     * Pile vide s'il n'y a rien à ajouter.
     */
    private ItemStack shareFor(ZoneControllerBlockEntity zone, ServerLevel level, AbstractFurnaceBlockEntity f) {
        RecipeType<? extends AbstractCookingRecipe> type = recipeType(f);
        ItemStack current = f.getItem(0);
        ItemStack item = current.isEmpty() ? zone.inventory().find(s -> canSmelt(level, type, s)) : current;
        if (item.isEmpty() || !canSmelt(level, type, item)) return ItemStack.EMPTY;
        int available = zone.inventory().count(s -> ItemStack.isSameItemSameComponents(s, item));
        if (available <= 0) return ItemStack.EMPTY;
        int total = available;
        int sharers = 0;
        for (BlockPos p : furnaces) {
            if (!level.isLoaded(p) || !(level.getBlockEntity(p) instanceof AbstractFurnaceBlockEntity other) || recipeType(other) != type) continue;
            ItemStack in = other.getItem(0);
            if (in.isEmpty()) {
                sharers++;
            } else if (ItemStack.isSameItemSameComponents(in, item)) {
                sharers++;
                total += in.getCount();
            }
        }
        int share = Math.ceilDiv(total, Math.max(1, sharers));
        int room = f.getMaxStackSize(item) - current.getCount();
        int want = Math.min(Math.min(share - current.getCount(), available), room);
        return want > 0 ? item.copyWithCount(want) : ItemStack.EMPTY;
    }

    /**
     * Carburant à ajouter pour cuire {@code inputCount} items, au plus juste :
     * ⌈(items × temps de cuisson − ce qui brûle encore − carburant déjà là) ÷ durée d'un carburant⌉.
     * Pile vide s'il en a assez, ou si les coffres n'ont rien qui convienne.
     */
    private ItemStack fuelFor(ZoneControllerBlockEntity zone, ServerLevel level, AbstractFurnaceBlockEntity f, ItemStack inputItem, int inputCount) {
        RecipeType<? extends AbstractCookingRecipe> type = recipeType(f);
        if (inputCount <= 0 || !canSmelt(level, type, inputItem)) return ItemStack.EMPTY;
        long needed = (long) inputCount * cookTime(level, type, inputItem) - burningTicks(level, f);
        ItemStack slot = f.getItem(1);
        if (!slot.isEmpty()) {
            int slotBurn = burn(level, f, slot);
            if (slotBurn <= 0) return ItemStack.EMPTY; // seau vide : il sortira au prochain service
            needed -= (long) slot.getCount() * slotBurn;
        }
        if (needed <= 0) return ItemStack.EMPTY;
        ItemStack fuel;
        if (!slot.isEmpty()) {
            fuel = zone.inventory().find(s -> ItemStack.isSameItemSameComponents(s, slot));
        } else {
            // En évitant d'y brûler ce qui pourrait cuire.
            fuel = zone.inventory().find(s -> burn(level, f, s) > 0 && !canSmelt(level, type, s));
            if (fuel.isEmpty()) fuel = zone.inventory().find(s -> burn(level, f, s) > 0);
        }
        int perItem = burn(level, f, fuel);
        if (perItem <= 0) return ItemStack.EMPTY;
        int room = f.getMaxStackSize(fuel) - slot.getCount();
        int want = (int) Math.min(room, (needed + perItem - 1) / perItem);
        return want > 0 ? fuel.copyWithCount(want) : ItemStack.EMPTY;
    }

    /** Durée de combustion d'un carburant dans ce four : le haut fourneau et le fumoir le brûlent deux fois plus vite. */
    private static int burn(ServerLevel level, AbstractFurnaceBlockEntity f, ItemStack fuel) {
        if (fuel.isEmpty()) return 0;
        int ticks = fuel.getBurnTime(recipeType(f), level.fuelValues());
        return f instanceof BlastFurnaceBlockEntity || f instanceof SmokerBlockEntity ? ticks / 2 : ticks;
    }

    /** Temps de cuisson d'un item dans ce type de four (minerai de fer : 200 ticks au four, 100 au haut fourneau). */
    private static <T extends AbstractCookingRecipe> int cookTime(ServerLevel level, RecipeType<T> type, ItemStack stack) {
        return level.recipeAccess().getRecipeFor(type, new SingleRecipeInput(stack), level).map(h -> h.value().cookingTime()).orElse(200);
    }

    /**
     * Ticks de cuisson encore couverts par le carburant en train de brûler (plus l'avancée de l'item en cours).
     * Ces compteurs sont privés : on les lit dans la sauvegarde du four.
     */
    private static int burningTicks(ServerLevel level, AbstractFurnaceBlockEntity f) {
        CompoundTag tag = f.saveCustomOnly(level.registryAccess());
        int lit = tag.getIntOr("lit_time_remaining", 0);
        return lit > 0 ? lit + tag.getIntOr("cooking_time_spent", 0) : 0;
    }

    private static RecipeType<? extends AbstractCookingRecipe> recipeType(AbstractFurnaceBlockEntity f) {
        if (f instanceof BlastFurnaceBlockEntity) return RecipeType.BLASTING;
        if (f instanceof SmokerBlockEntity) return RecipeType.SMOKING;
        return RecipeType.SMELTING;
    }

    private static <T extends AbstractCookingRecipe> boolean canSmelt(ServerLevel level, RecipeType<T> type, ItemStack stack) {
        return !stack.isEmpty() && level.recipeAccess().getRecipeFor(type, new SingleRecipeInput(stack), level).isPresent();
    }

    private static BlockPos standNear(ServerLevel level, BlockPos furnace) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = furnace.relative(d);
            if (level.getBlockState(n).isAir() && !level.getBlockState(n.below()).isAir()) return n;
        }
        return furnace.above();
    }

    /** Le golem à la pelle n'attise qu'un four allumé qui a de quoi cuire ; sinon il fait juste le service et passe au suivant. */
    @Override
    public int workTicks(ZoneControllerBlockEntity zone, Worker worker, Task task) {
        boolean stoke = stokes(worker) && zone.getLevel() != null
                && zone.getLevel().getBlockEntity(task.target()) instanceof AbstractFurnaceBlockEntity furnace && cooking(furnace);
        return stoke ? STOKE_TICKS : SERVICE_TICKS;
    }

    /** Le golem à la pelle fait avancer le four de quelques ticks en plus à chaque tick. */
    @Override
    public void workTick(ZoneControllerBlockEntity zone, Worker worker, Task task) {
        if (!stokes(worker) || !(zone.getLevel() instanceof ServerLevel level)) return;
        if (!(level.getBlockEntity(task.target()) instanceof AbstractFurnaceBlockEntity furnace)) return;
        worker.boostCarry += boost(level, worker) - 1.0;
        while (worker.boostCarry >= 1.0) {
            worker.boostCarry -= 1.0;
            AbstractFurnaceBlockEntity.serverTick(level, task.target(), level.getBlockState(task.target()), furnace);
        }
    }

    @Override
    public boolean isStillValid(ZoneControllerBlockEntity zone, Task task) {
        return zone.getLevel() != null && zone.getLevel().getBlockEntity(task.target()) instanceof AbstractFurnaceBlockEntity;
    }

    @Override
    public void complete(ZoneControllerBlockEntity zone, Worker worker, Task task) {
        claimed.remove(task.target());
        if (!(zone.getLevel() instanceof ServerLevel level) || !(level.getBlockEntity(task.target()) instanceof AbstractFurnaceBlockEntity furnace)) return;
        BlockPos pos = task.target();

        // 1. Les produits (et un seau vide) partent au coffre, l'XP va au tampon du contrôleur (en cristaux, sans orbe).
        ResourceHandler<ItemResource> out = level.getCapability(Capabilities.Item.BLOCK, pos, Direction.DOWN);
        if (out != null) {
            ResourceStack<ItemResource> taken;
            while ((taken = ResourceHandlerUtil.extractFirst(out, r -> true, 64, null)) != null) {
                zone.inventory().output(taken.resource().toStack(taken.amount()));
            }
        }
        collectXp(zone, level, furnace);

        // 2. Sa part de minerai (ou de n'importe quoi qui cuit dans ce four).
        ResourceHandler<ItemResource> top = level.getCapability(Capabilities.Item.BLOCK, pos, Direction.UP);
        ItemStack share = shareFor(zone, level, furnace);
        if (top != null && !share.isEmpty()) moveFromChests(zone, top, share);

        // 3. Juste le carburant qu'il faut pour cuire ce qu'il y a maintenant dans le four.
        ResourceHandler<ItemResource> side = level.getCapability(Capabilities.Item.BLOCK, pos, Direction.NORTH);
        ItemStack fuel = fuelFor(zone, level, furnace, furnace.getItem(0), furnace.getItem(0).getCount());
        if (side != null && !fuel.isEmpty()) moveFromChests(zone, side, fuel);
    }

    /**
     * L'XP des cuissons (0,7 par lingot de fer, 0,1 par pierre…) va entière au tampon de la zone : le tampon garde les
     * fractions, là où la vanilla arrondit au hasard en faisant apparaître des orbes.
     */
    private static void collectXp(ZoneControllerBlockEntity zone, ServerLevel level, AbstractFurnaceBlockEntity furnace) {
        if (furnace.recipesUsed.isEmpty()) return;
        double xp = 0;
        for (var entry : furnace.recipesUsed.reference2IntEntrySet()) {
            var holder = level.recipeAccess().byKey(entry.getKey());
            if (holder.isPresent() && holder.get().value() instanceof AbstractCookingRecipe cooking) {
                xp += entry.getIntValue() * (double) cooking.experience();
            }
        }
        furnace.recipesUsed.clear();
        furnace.setChanged();
        if (xp > 0) zone.xp().add(xp);
    }

    /** Déplace des coffres vers le four jusqu'à {@code what.getCount()} items identiques à {@code what}. */
    private static void moveFromChests(ZoneControllerBlockEntity zone, ResourceHandler<ItemResource> to, ItemStack what) {
        int left = what.getCount();
        for (ResourceHandler<ItemResource> chest : zone.inventory().handlers()) {
            left -= ResourceHandlerUtil.move(chest, to, r -> r.matches(what), left, null);
            if (left <= 0) return;
        }
    }

    @Override
    public void release(Task task) {
        claimed.remove(task.target());
    }

    @Override
    public @Nullable Component progress(ZoneControllerBlockEntity zone) {
        return Component.translatable("message.simple_automation.forge.furnaces", furnaces.size());
    }

    @Override
    public void save(ValueOutput out) {
    }

    @Override
    public void load(ValueInput in) {
        reset(null);
    }
}
