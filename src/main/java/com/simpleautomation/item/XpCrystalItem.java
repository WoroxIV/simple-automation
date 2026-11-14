package com.simpleautomation.item;

import com.simpleautomation.zone.XpBank;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Cristal d'expérience : l'XP produite par les golems, en objet solide qui s'empile et passe dans les pipes.
 * Clic droit : un cristal ; accroupi : toute la pile. L'XP apparaît en orbes aux pieds du joueur, qui les ramasse
 * aussitôt : Mending et les mods qui réagissent aux orbes marchent comme pour de l'XP ramassée à la main.
 */
public class XpCrystalItem extends Item {
    public XpCrystalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int count = player.isShiftKeyDown() ? stack.getCount() : 1;
        if (level instanceof ServerLevel server) {
            ExperienceOrb.award(server, player.position(), count * XpBank.XP_PER_CRYSTAL);
            level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS,
                    0.6F, 0.8F + level.random.nextFloat() * 0.4F);
            if (!player.hasInfiniteMaterials()) stack.shrink(count);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
