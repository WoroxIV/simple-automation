package com.simpleautomation.job;

import com.simpleautomation.SAConfig;
import com.simpleautomation.registry.ModTags;
import com.simpleautomation.zone.Worker;
import com.simpleautomation.zone.XpCapture;
import com.simpleautomation.zone.ZoneControllerBlockEntity;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import org.jspecify.annotations.Nullable;

/**
 * Poste de garde : les golems armés (épée, hache, masse, trident…) frappent les monstres de la zone comme un joueur.
 * <ul>
 * <li>Une tâche = un coup. Sa durée suit la vitesse d'attaque de l'arme (20 / vitesse, en ticks), comme le temps de
 * recharge d'un joueur : chaque coup est donc à pleine puissance.</li>
 * <li>Dégâts : ceux de l'arme (ses modificateurs d'attaque, sur la base 1 d'un joueur), plus les enchantements
 * (Tranchant, Châtiment…). Puis les effets d'après-coup (Aura de feu…) et le Recul. Usure : 1 point par coup.</li>
 * <li>Le coup vient du faux joueur de la zone, qui tient l'arme : une mise à mort « par joueur », donc Pillage s'applique,
 * les objets rares sont possibles, et l'XP (règles vanilla) va au tampon de la zone via {@link XpCapture}, sans orbe.</li>
 * </ul>
 * Jamais visés : les boss ({@code c:bosses}), les monstres nommés ou tenus en laisse, et le tag
 * {@code simple_automation:guard_blacklist} (le Warden par défaut). Les objets tombés sont aspirés vers les coffres.
 * Les ouvriers étant des données, les monstres ne peuvent ni les viser ni les blesser.
 */
public final class GuardJob implements Job {
    /** Base des attributs d'un joueur : 1 point de dégâts à mains nues, 4 coups par seconde. */
    private static final double PLAYER_ATTACK_DAMAGE = 1.0;
    private static final double PLAYER_ATTACK_SPEED = 4.0;

    private final Set<UUID> claimed = new HashSet<>();
    private final AnimalJobs.Refusals refused = new AnimalJobs.Refusals();

    @Override
    public boolean acceptsTool(ItemStack tool) {
        return GolemTools.isWeapon(tool);
    }

    @Override
    public boolean showsCrack() {
        return false;
    }

    @Override
    public int budgetCost(Task task) {
        return 0;
    }

    /** Un monstre vivant que le poste peut attaquer : ni boss, ni nommé, ni en laisse, ni dans la liste noire. */
    static boolean target(LivingEntity e) {
        return e instanceof Enemy && e.isAlive() && !e.hasCustomName() && !(e instanceof Leashable l && l.isLeashed())
                && !e.getType().is(Tags.EntityTypes.BOSSES) && !e.getType().is(ModTags.GUARD_BLACKLIST);
    }

    private List<LivingEntity> targets(ServerLevel level, AABB box) {
        return level.getEntitiesOfClass(LivingEntity.class, box, GuardJob::target);
    }

    @Override
    public @Nullable Task nextTask(ZoneControllerBlockEntity zone, Worker worker) {
        AABB box = AnimalJobs.box(zone);
        if (box == null || !(zone.getLevel() instanceof ServerLevel level)) return null;
        if (level.getGameTime() % 200 == 0) refused.prune(level);
        Vec3 from = Vec3.atBottomCenterOf(worker.stand);
        LivingEntity mob = targets(level, box).stream()
                .filter(e -> !claimed.contains(e.getUUID()) && !refused.contains(level, e.getUUID()))
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(from))).orElse(null);
        if (mob == null) return null;
        claimed.add(mob.getUUID());
        return new Task(mob.blockPosition(), AnimalJobs.standNear(zone, level, mob), List.of(), List.of(mob.getUUID()));
    }

    /**
     * Temps de recharge d'un joueur avec cette arme (20 / vitesse d'attaque), multiplié par la config et ralenti par
     * l'oxydation. Jamais sous le plancher de la config. Efficacité ne compte pas : ce n'est pas du minage.
     */
    @Override
    public int workTicks(ZoneControllerBlockEntity zone, Worker worker, Task task) {
        double speed = worker.tool.getAttributeModifiers().compute(Attributes.ATTACK_SPEED, PLAYER_ATTACK_SPEED, EquipmentSlot.MAINHAND);
        double ticks = 20.0 / Math.max(speed, 0.05) * SAConfig.WORK_SPEED_MULTIPLIER.getAsDouble() / Math.max(worker.speedFactor(), 0.05);
        return Math.max(SAConfig.MIN_TICKS_PER_BLOCK.getAsInt(), (int) Math.ceil(ticks));
    }

    @Override
    public boolean isStillValid(ZoneControllerBlockEntity zone, Task task) {
        AABB box = AnimalJobs.box(zone);
        if (box == null || !(zone.getLevel() instanceof ServerLevel level)) return false;
        return AnimalJobs.find(level, task.entities().getFirst(), box) instanceof LivingEntity e && target(e);
    }

    @Override
    public void complete(ZoneControllerBlockEntity zone, Worker worker, Task task) {
        claimed.removeAll(task.entities());
        AABB box = AnimalJobs.box(zone);
        if (box == null || !(zone.getLevel() instanceof ServerLevel level)) return;
        if (!(AnimalJobs.find(level, task.entities().getFirst(), box) instanceof LivingEntity mob) || !target(mob)) return;
        FakePlayer fake = zone.fakePlayer();
        ItemStack weapon = worker.tool;
        fake.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        try {
            // Les mods de protection peuvent refuser l'attaque, comme pour un joueur : on laisse ce monstre un moment.
            if (NeoForge.EVENT_BUS.post(new AttackEntityEvent(fake, mob)).isCanceled()) {
                refused.add(level, mob.getUUID());
                return;
            }
            hit(zone, level, fake, weapon, mob, Vec3.atBottomCenterOf(task.stand()));
        } finally {
            fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    /** Un coup à pleine puissance, comme {@code Player#attack} sans coup critique ni coup balayé. */
    private static void hit(ZoneControllerBlockEntity zone, ServerLevel level, FakePlayer fake, ItemStack weapon, LivingEntity mob, Vec3 from) {
        DamageSource source = weapon.getDamageSource(fake, () -> level.damageSources().playerAttack(fake));
        float base = (float) weapon.getAttributeModifiers().compute(Attributes.ATTACK_DAMAGE, PLAYER_ATTACK_DAMAGE, EquipmentSlot.MAINHAND);
        float damage = EnchantmentHelper.modifyDamage(level, weapon, mob, source, base);
        damage += weapon.getItem().getAttackDamageBonus(mob, base, source);
        float knockbackBase = (float) weapon.getAttributeModifiers().compute(Attributes.ATTACK_KNOCKBACK, 0.0, EquipmentSlot.MAINHAND);
        float knockback = EnchantmentHelper.modifyKnockback(level, weapon, mob, source, knockbackBase) / 2.0F;
        float finalDamage = damage;
        boolean[] hurt = new boolean[1];
        // L'XP d'une mort (règles vanilla « tué par un joueur ») va au tampon de la zone, sans orbe.
        XpCapture.during(zone, () -> {
            hurt[0] = mob.hurtServer(level, source, finalDamage);
            if (!hurt[0]) return;
            if (knockback > 0.0F) {
                Vec3 push = mob.position().subtract(from);
                if (push.horizontalDistanceSqr() > 1.0E-4) mob.knockback(knockback, -push.x, -push.z);
            }
            // Aura de feu et autres effets d'après-coup ; une mort qu'ils causent compte encore pour la zone.
            EnchantmentHelper.doPostAttackEffectsWithItemSource(level, mob, source, weapon);
        });
        if (hurt[0]) weapon.hurtAndBreak(1, level, (ServerPlayer) null, item -> {});
    }

    @Override
    public void tick(ZoneControllerBlockEntity zone) {
        AnimalJobs.collectItems(zone);
    }

    @Override
    public void describeTo(ZoneControllerBlockEntity zone, Consumer<Component> lines) {
        AABB box = AnimalJobs.box(zone);
        if (box == null || !(zone.getLevel() instanceof ServerLevel level)) return;
        lines.accept(Component.translatable("message.simple_automation.guard.targets", targets(level, box).size()).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void release(Task task) {
        claimed.removeAll(task.entities());
    }

    @Override
    public void reset(ZoneControllerBlockEntity zone) {
        claimed.clear();
        refused.clear();
    }

    @Override
    public void save(ValueOutput out) {
    }

    @Override
    public void load(ValueInput in) {
        reset(null);
    }
}
