package com.simpleautomation.zone;

import net.minecraft.util.StringRepresentable;

/** État affiché par le contrôleur (texture du bloc et message au clic). */
public enum ZoneStatus implements StringRepresentable {
    INVALID("invalid"),
    IDLE("idle"),
    WORKING("working"),
    PAUSED("paused");

    private final String name;

    ZoneStatus(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
