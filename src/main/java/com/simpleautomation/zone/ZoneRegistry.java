package com.simpleautomation.zone;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Contrôleurs chargés côté serveur, par monde. En mémoire seulement : chaque contrôleur s'inscrit en se chargeant. */
public final class ZoneRegistry {
    private static final Map<Level, Set<ZoneControllerBlockEntity>> CONTROLLERS = new WeakHashMap<>();

    public static void add(ZoneControllerBlockEntity be) {
        Level level = be.getLevel();
        if (level == null || level.isClientSide()) return;
        CONTROLLERS.computeIfAbsent(level, l -> Collections.newSetFromMap(new WeakHashMap<>())).add(be);
    }

    public static void remove(ZoneControllerBlockEntity be) {
        Level level = be.getLevel();
        if (level == null) return;
        Set<ZoneControllerBlockEntity> set = CONTROLLERS.get(level);
        if (set != null) set.remove(be);
    }

    public static Set<ZoneControllerBlockEntity> all(Level level) {
        return CONTROLLERS.getOrDefault(level, Set.of());
    }

    public static boolean overlapsOther(Level level, ZoneBounds bounds, ZoneControllerBlockEntity self) {
        for (ZoneControllerBlockEntity other : all(level)) {
            if (other != self && !other.isRemoved() && other.bounds() != null && other.bounds().intersects(bounds)) return true;
        }
        return false;
    }

    public static boolean markerUsedByOther(Level level, BlockPos marker, ZoneControllerBlockEntity self) {
        ZoneControllerBlockEntity owner = findUsingMarker(level, marker);
        return owner != null && owner != self && !owner.isRemoved();
    }

    public static @Nullable ZoneControllerBlockEntity findUsingMarker(Level level, BlockPos marker) {
        for (ZoneControllerBlockEntity c : all(level)) {
            ZoneBounds b = c.bounds();
            if (b != null && (b.markerA().equals(marker) || b.markerB().equals(marker))) return c;
        }
        return null;
    }

    private ZoneRegistry() {
    }
}
