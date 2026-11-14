package com.simpleautomation.client;

import com.simpleautomation.SAConfig;
import com.simpleautomation.zone.Worker;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.CopperGolemState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Copper golem qui n'existe que côté client : il rejoue le travail simulé par le contrôleur.
 * Le jeu se charge du rendu, de l'animation de marche, de l'outil en main et du ciblage au viseur.
 */
public class PuppetGolem extends CopperGolem {
    /** Au-delà, la marionnette se téléporte au lieu de marcher (golem qui vient d'être recruté loin, par exemple). */
    private static final double TELEPORT_DISTANCE = 24.0;

    public final BlockPos controller;
    public final UUID workerId;
    private Vec3 destination;
    private @Nullable BlockPos lookAt;
    private boolean working;
    private boolean frozen;

    public PuppetGolem(Level level, BlockPos controller, UUID workerId, Vec3 start) {
        super(EntityType.COPPER_GOLEM, level);
        this.controller = controller;
        this.workerId = workerId;
        this.destination = start;
        setNoGravity(true);
        this.noPhysics = true;
        setInvulnerable(true);
        setPos(start);
    }

    public void apply(Worker w) {
        destination = Vec3.atBottomCenterOf(w.stand);
        lookAt = w.target;
        working = w.status == Worker.Status.WORKING;
        frozen = w.status == Worker.Status.FROZEN;
        if (getWeatherState() != w.weather) setWeatherState(w.weather);
        if (!ItemStack.matches(getMainHandItem(), w.tool)) setItemSlot(EquipmentSlot.MAINHAND, w.tool.copy());
        if (!ItemStack.matches(getItemBySlot(EQUIPMENT_SLOT_ANTENNA), w.antenna)) setItemSlot(EQUIPMENT_SLOT_ANTENNA, w.antenna.copy());
        setNoAi(frozen); // oxydé : figé comme une statue, plus d'animation
        // Objet en main refusé par le métier : un « ? » toujours visible au-dessus de la tête (à côté du nom s'il en a un).
        boolean noTool = w.status == Worker.Status.NO_TOOL;
        Component name = w.customName;
        if (noTool) {
            Component mark = Component.literal("?").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
            name = name == null ? mark : name.copy().append(" ").append(mark);
        }
        if (!Objects.equals(getCustomName(), name)) setCustomName(name);
        if (isCustomNameVisible() != noTool) setCustomNameVisible(noTool);
    }

    @Override
    public void tick() {
        Vec3 pos = position();
        Vec3 delta = destination.subtract(pos);
        double distance = delta.length();
        if (distance > TELEPORT_DISTANCE) {
            setPos(destination);
        } else if (distance > 1.0E-3 && (!frozen || delta.horizontalDistanceSqr() < 1.0E-6)) { // une statue peut encore tomber
            double speed = SAConfig.WORKER_WALK_SPEED.getAsDouble();
            Vec3 step = distance <= speed ? delta : delta.scale(speed / distance);
            setPos(pos.add(step));
            if (step.horizontalDistanceSqr() > 1.0E-6) face(step.x, step.z);
        } else if (lookAt != null) {
            Vec3 target = Vec3.atCenterOf(lookAt);
            face(target.x - getX(), target.z - getZ());
        }
        if (working) {
            // L'animation « prendre un objet » rejouée en boucle ressemble à un coup d'outil.
            int phase = tickCount % 16;
            setState(phase == 0 ? CopperGolemState.IDLE : CopperGolemState.GETTING_ITEM);
        } else if (getState() != CopperGolemState.IDLE) {
            setState(CopperGolemState.IDLE);
        }
        super.tick();
    }

    private void face(double dx, double dz) {
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity entity) {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
