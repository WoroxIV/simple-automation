package com.simpleautomation.zone;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Protections (FTB Chunks, Open Parties and Claims…) : avant de poser un bloc ou de toucher un animal, les golems
 * demandent la permission comme un joueur, au nom du propriétaire de la zone ({@link ZoneControllerBlockEntity#fakePlayer}).
 * La casse, elle, passe par un {@link BlockEvent.BreakEvent} (voir GolemTools et CrusherJob).
 */
public final class Claims {
    private Claims() {
    }

    /**
     * Le golem a-t-il le droit de poser {@code state} en {@code pos} ? À appeler <em>avant</em> de poser le bloc :
     * rien n'est modifié dans le monde. Toujours faux sur le contrôleur, ses voisins et les bornes.
     */
    public static boolean canPlace(ZoneControllerBlockEntity zone, BlockPos pos, BlockState state) {
        if (!(zone.getLevel() instanceof ServerLevel level) || zone.isProtected(pos)) return false;
        FakePlayer fake = zone.fakePlayer();
        // Le joueur fictif tient le bloc posé, au cas où une protection regarde l'objet en main.
        fake.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(state.getBlock().asItem()));
        try {
            BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
            BlockState against = level.getBlockState(pos.below());
            return !NeoForge.EVENT_BUS.post(new PlaceCheck(snapshot, against, fake, state)).isCanceled();
        } finally {
            fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    /**
     * Le golem a-t-il le droit d'interagir avec cette entité (tondre, nourrir…) ? Même événement qu'un clic droit
     * de joueur sur une entité ({@code PlayerInteractEvent.EntityInteract}), celui qu'annulent les mods de claims.
     */
    public static boolean canInteract(ZoneControllerBlockEntity zone, Entity target) {
        if (!(zone.getLevel() instanceof ServerLevel)) return false;
        return CommonHooks.onInteractEntity(zone.fakePlayer(), target, InteractionHand.MAIN_HAND) == null;
    }

    /**
     * Pose demandée avant d'avoir lieu. Pour un joueur, l'événement vanilla lit le bloc posé dans le monde
     * (il est posé avant d'être demandé) : ici il n'y est pas encore, alors on donne le bon bloc aux protections.
     */
    private static final class PlaceCheck extends BlockEvent.EntityPlaceEvent {
        private final BlockState placed;

        PlaceCheck(BlockSnapshot snapshot, BlockState placedAgainst, Player player, BlockState placed) {
            super(snapshot, placedAgainst, player);
            this.placed = placed;
        }

        @Override
        public BlockState getState() {
            return placed;
        }

        @Override
        public BlockState getPlacedBlock() {
            return placed;
        }
    }
}
