package com.simpleautomation.test;

import com.mojang.authlib.GameProfile;
import com.simpleautomation.registry.ModBlocks;
import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Petits outils partagés par les tests. */
final class TestKit {
    /** Contrôleur et coffre de {@link #floorAndZone}. */
    static final BlockPos CONTROLLER = new BlockPos(6, 2, 3);
    static final BlockPos CHEST = new BlockPos(7, 2, 3);

    /** Sol de pierre 9×9, deux bornes (1,2,1)–(5,2,5), contrôleur en (6,2,3) et coffre collé en (7,2,3). */
    static ZoneControllerBlockEntity floorAndZone(GameTestHelper h, Block controller) {
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 8; z++) h.setBlock(x, 1, z, Blocks.STONE);
        h.setBlock(1, 2, 1, ModBlocks.BORDER_MARKER.get());
        h.setBlock(5, 2, 5, ModBlocks.BORDER_MARKER.get());
        h.setBlock(CONTROLLER, controller);
        h.setBlock(CHEST, Blocks.CHEST);
        return zone(h, CONTROLLER);
    }

    static ZoneControllerBlockEntity zone(GameTestHelper h, BlockPos pos) {
        return h.getBlockEntity(pos, ZoneControllerBlockEntity.class);
    }

    static ChestBlockEntity chest(GameTestHelper h, BlockPos pos) {
        return h.getBlockEntity(pos, ChestBlockEntity.class);
    }

    static int count(GameTestHelper h, BlockPos chestPos, Item item) {
        ChestBlockEntity chest = chest(h, chestPos);
        int n = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack s = chest.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    /** Blocs {@code block} entre deux coins (relatifs, inclus). */
    static int countBlocks(GameTestHelper h, Block block, BlockPos from, BlockPos to) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(from, to)) if (h.getBlockState(p).is(block)) n++;
        return n;
    }

    /** L'ouvrier qui passe le filtre (par exemple « tient une pioche »), ou un échec. */
    static Worker worker(GameTestHelper h, ZoneControllerBlockEntity be, Predicate<Worker> which, String what) {
        for (Worker w : be.workers()) if (which.test(w)) return w;
        throw h.assertionException("no worker " + what + " among " + be.workers().size());
    }

    /** Un golem tel que le recrutement le voit après un chargement depuis une sauvegarde d'avant la marque de naissance. */
    static void forgetBirth(CopperGolem golem) {
        golem.getPersistentData().remove(ZoneControllerBlockEntity.BIRTH_KEY);
    }

    /** Joueur fictif (un ServerPlayer hors du monde), placé sur la case relative {@code at}. */
    static FakePlayer player(GameTestHelper h, BlockPos at) {
        FakePlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "sa-test"));
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
        p.snapTo(v.x, v.y, v.z, 0.0F, 0.0F);
        return p;
    }

    static ItemStack enchanted(GameTestHelper h, Item item, ResourceKey<Enchantment> enchantment, int level) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment), level);
        return stack;
    }

    /** Lignes que le métier ajoute au clic sur le contrôleur. */
    static List<Component> describe(ZoneControllerBlockEntity be) {
        List<Component> lines = new ArrayList<>();
        be.job().describeTo(be, lines::add);
        return lines;
    }

    static boolean hasLine(List<Component> lines, String key) {
        return lines.stream().anyMatch(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals(key));
    }

    /**
     * Lance {@code cleanup} quand le test se termine, réussi ou raté (échec d'une vérification, délai dépassé) :
     * ce qu'un test change hors de son arène ne doit pas lui survivre.
     */
    static void onFinish(GameTestHelper h, Runnable cleanup) {
        h.testInfo.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo info) {
            }

            @Override
            public void testPassed(GameTestInfo info, GameTestRunner runner) {
                cleanup.run();
            }

            @Override
            public void testFailed(GameTestInfo info, GameTestRunner runner) {
                cleanup.run();
            }

            @Override
            public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            }
        });
    }

    /**
     * Remplace le contenu d'un tag en cours de partie (comme un datapack rechargé), pour tester les tags vides par défaut.
     * L'ancien contenu est remis à la fin du test, même raté ; il est aussi renvoyé, pour le remettre plus tôt.
     */
    static <T> List<Holder<T>> setTag(GameTestHelper h, Registry<T> registry, TagKey<T> tag, List<Holder<T>> contents) {
        List<Holder<T>> old = setTag(registry, tag, contents);
        onFinish(h, () -> setTag(registry, tag, old));
        return old;
    }

    /** Remplace le contenu d'un tag ; renvoie l'ancien. */
    static <T> List<Holder<T>> setTag(Registry<T> registry, TagKey<T> tag, List<Holder<T>> contents) {
        Map<TagKey<T>, List<Holder<T>>> all = new HashMap<>();
        registry.getTags().forEach(named -> all.put(named.key(), named.stream().toList()));
        List<Holder<T>> old = all.getOrDefault(tag, List.of());
        all.put(tag, List.copyOf(contents));
        registry.prepareTagReload(new TagLoader.LoadResult<>(registry.key(), all)).apply();
        return old;
    }

    private TestKit() {
    }
}
