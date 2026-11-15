package com.simpleautomation.registry;

import com.simpleautomation.SimpleAutomation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    /** Blocs que les golems ne cassent jamais (les modpacks peuvent le remplir). */
    public static final TagKey<Block> GOLEM_BLACKLIST = TagKey.create(Registries.BLOCK, SimpleAutomation.id("golem_blacklist"));
    /** Cultures moddées que la ferme récolte quand leur propriété « age » est au maximum, puis replante (vide par défaut). */
    public static final TagKey<Block> FARM_CROPS = TagKey.create(Registries.BLOCK, SimpleAutomation.id("farm_crops"));
    /** Graines que la ferme sème sur la terre labourée, en plus des graines de cultures reconnues (contient c:seeds). */
    public static final TagKey<Item> FARM_SEEDS = TagKey.create(Registries.ITEM, SimpleAutomation.id("farm_seeds"));
    /** Minerais que le broyeur ne prend jamais au coffre (vide par défaut). */
    public static final TagKey<Item> CRUSHER_BLACKLIST = TagKey.create(Registries.ITEM, SimpleAutomation.id("crusher_blacklist"));

    /** Pousses qui ne donnent un arbre qu'en carré de 4 (chêne noir, chêne pâle) : la scierie attend d'en avoir 4. */
    public static final TagKey<Item> MEGA_ONLY_SAPLINGS = TagKey.create(Registries.ITEM, SimpleAutomation.id("mega_only_saplings"));

    private ModTags() {
    }
}
