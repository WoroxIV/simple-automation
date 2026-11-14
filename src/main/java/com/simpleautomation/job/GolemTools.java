package com.simpleautomation.job;

import com.simpleautomation.SAConfig;
import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jspecify.annotations.Nullable;

/**
 * Ce que l'outil d'un golem change, en s'appuyant sur la vanilla : la vitesse vient de l'outil et d'Efficacité,
 * les drops passent par les loot tables avec l'outil (Fortune, Silk Touch), la durabilité par hurtAndBreak (Solidité).
 * Les outils moddés marchent donc sans rien ajouter.
 */
public final class GolemTools {

    // Types d'outils : le tag vanilla, ou ce que l'outil sait faire, pour les outils moddés hors tag (paxels, multi-outils…).
    // NeoForge 1.21.11 n'a plus d'ItemAbility « creuser » (les outils sont data-driven) : la pioche se reconnaît à ce
    // qu'elle récolte la pierre, les autres à leur action (écorcer, aplanir, labourer).

    /** Pioche, ou tout outil qui récolte la pierre (paxel, foreuse…). */
    public static boolean isPickaxe(ItemStack tool) {
        return !tool.isEmpty() && (tool.is(ItemTags.PICKAXES) || tool.isCorrectToolForDrops(Blocks.STONE.defaultBlockState()));
    }

    /** Pelle, ou tout outil qui sait aplanir un chemin. */
    public static boolean isShovel(ItemStack tool) {
        return !tool.isEmpty() && (tool.is(ItemTags.SHOVELS) || tool.canPerformAction(ItemAbilities.SHOVEL_FLATTEN));
    }

    /** Hache, ou tout outil qui sait écorcer une bûche. */
    public static boolean isAxe(ItemStack tool) {
        return !tool.isEmpty() && (tool.is(ItemTags.AXES) || tool.canPerformAction(ItemAbilities.AXE_STRIP));
    }

    /** Houe, ou tout outil qui sait labourer. */
    public static boolean isHoe(ItemStack tool) {
        return !tool.isEmpty() && (tool.is(ItemTags.HOES) || tool.canPerformAction(ItemAbilities.HOE_TILL));
    }

    /** Vitesse de minage comme pour un joueur : vitesse de l'outil sur ce bloc, plus le bonus d'Efficacité. */
    public static float speed(ServerLevel level, ItemStack tool, BlockState state) {
        float speed = tool.isEmpty() ? 1.0F : tool.getDestroySpeed(state);
        if (speed > 1.0F) {
            int efficiency = EnchantmentHelper.getItemEnchantmentLevel(
                    level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY), tool);
            if (efficiency > 0) speed += efficiency * efficiency + 1;
        }
        return speed;
    }

    /** Le bloc lâchera-t-il quelque chose avec cet outil ? (une pioche en bois ne récupère pas le diamant) */
    public static boolean canHarvest(ItemStack tool, BlockState state) {
        return !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
    }

    /** Ticks pour casser ce bloc : même formule que le joueur, ralentie par l'oxydation, avec un plancher. */
    public static int ticksFor(ServerLevel level, Worker worker, BlockPos pos, BlockState state) {
        int min = SAConfig.MIN_TICKS_PER_BLOCK.getAsInt();
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness <= 0) return min;
        double factor = Math.max(worker.speedFactor(), 0.05);
        double ticks = Math.ceil(hardness * 30.0 / speed(level, worker.tool, state)) * SAConfig.WORK_SPEED_MULTIPLIER.getAsDouble() / factor;
        return Math.max(min, (int) Math.ceil(ticks));
    }

    /**
     * Casse un bloc au nom du golem : vérifie les protections (FTB Chunks, OPAC… via BreakEvent), calcule les drops
     * avec son outil, use l'outil et range les items. Renvoie faux si le bloc a été protégé.
     */
    public static boolean breakBlock(ZoneControllerBlockEntity zone, Worker worker, BlockPos pos, boolean effects) {
        List<ItemStack> drops = harvest(zone, worker, pos, effects);
        if (drops == null) return false;
        for (ItemStack drop : drops) zone.inventory().output(drop);
        return true;
    }

    /** Comme {@link #breakBlock}, mais rend les drops au lieu de les ranger (la ferme garde une graine pour replanter). Null si protégé. */
    public static @Nullable List<ItemStack> harvest(ZoneControllerBlockEntity zone, Worker worker, BlockPos pos, boolean effects) {
        ServerLevel level = (ServerLevel) zone.getLevel();
        if (level == null) return null;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return null;

        FakePlayer fake = zone.fakePlayer();
        fake.setItemInHand(InteractionHand.MAIN_HAND, worker.tool);
        try {
            if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, fake)).isCanceled()) return null;
            BlockEntity be = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
            List<ItemStack> drops = Block.getDrops(state, level, pos, be, fake, worker.tool);
            float hardness = state.getDestroySpeed(level, pos);
            if (effects) level.levelEvent(2001, pos, Block.getId(state));
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            if (!worker.tool.isEmpty() && worker.tool.isDamageableItem() && hardness != 0) {
                worker.tool.hurtAndBreak(1, level, (ServerPlayer) null, item -> {});
            }
            return new java.util.ArrayList<>(drops);
        } finally {
            fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    /** Ramasse les items au sol dans une boîte et les range (bûches, laine, viande…). */
    public static void collectItems(ZoneControllerBlockEntity zone, net.minecraft.world.phys.AABB box) {
        if (!(zone.getLevel() instanceof ServerLevel level)) return;
        for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box, e -> e.isAlive() && !e.getItem().isEmpty())) {
            zone.inventory().output(item.getItem().copy());
            item.discard();
        }
    }

    /**
     * Aspire les orbes d'XP d'une boîte vers la zone (orbes de reproduction, que la vanilla crée sans événement).
     * Une orbe peut en avoir absorbé d'autres identiques : sa valeur compte autant de fois.
     */
    public static void collectXp(ZoneControllerBlockEntity zone, net.minecraft.world.phys.AABB box) {
        if (!(zone.getLevel() instanceof ServerLevel level)) return;
        for (var orb : level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, box, e -> e.isAlive())) {
            zone.xp().add((double) orb.getValue() * Math.max(1, orb.count));
            orb.discard();
        }
    }

    private GolemTools() {
    }
}
