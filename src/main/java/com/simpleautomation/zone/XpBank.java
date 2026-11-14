package com.simpleautomation.zone;

import com.simpleautomation.registry.ModItems;
import net.minecraft.world.item.ItemStack;

/**
 * L'expérience gagnée par une zone (fonte, minerais, animaux, pêche…). Elle n'apparaît jamais en orbes : elle s'accumule
 * ici, au dixième près, et sort en cristaux d'expérience ({@link com.simpleautomation.item.XpCrystalItem}) dans les coffres
 * dès qu'elle atteint la valeur d'un cristal. Un cristal rend exactement l'XP qu'il contient : ni perte, ni bonus.
 */
public final class XpBank {
    /** XP contenue dans un cristal. Fixe : changer cette valeur changerait celle des cristaux déjà fabriqués. */
    public static final int XP_PER_CRYSTAL = 10;

    private final ZoneControllerBlockEntity owner;
    private double stored;

    XpBank(ZoneControllerBlockEntity owner) {
        this.owner = owner;
    }

    public double stored() {
        return stored;
    }

    public void add(double xp) {
        if (!(xp > 0)) return;
        stored += xp;
        owner.setChanged();
    }

    /**
     * Retire de l'XP en attente (pour l'Enchanteur). Renvoie ce qui a vraiment été retiré.
     */
    public double take(double xp) {
        double taken = Math.min(Math.max(xp, 0), stored);
        if (taken > 0) {
            stored -= taken;
            owner.setChanged();
        }
        return taken;
    }

    /** Transforme l'XP en attente en cristaux, rangés comme n'importe quel butin (coffres, sinon tampon). */
    public void drain(ZoneInventory inventory) {
        int crystals = (int) Math.floor(stored / XP_PER_CRYSTAL);
        if (crystals <= 0) return;
        stored -= crystals * (double) XP_PER_CRYSTAL;
        // Les erreurs d'arrondi du double ne doivent jamais faire passer le reste sous zéro.
        if (stored < 1.0E-6) stored = 0;
        int max = ModItems.XP_CRYSTAL.get().getDefaultMaxStackSize();
        while (crystals > 0) {
            int n = Math.min(crystals, max);
            inventory.output(new ItemStack(ModItems.XP_CRYSTAL.get(), n));
            crystals -= n;
        }
        owner.setChanged();
    }

    void load(double value) {
        stored = Math.max(0, value);
    }
}
