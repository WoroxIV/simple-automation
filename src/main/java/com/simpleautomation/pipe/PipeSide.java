package com.simpleautomation.pipe;

import net.minecraft.util.StringRepresentable;

/** Ce qu'il y a d'un côté d'une pipe. Un côté désactivé à la clé s'affiche comme NONE (le choix est gardé dans le block entity). */
public enum PipeSide implements StringRepresentable {
    NONE("none"),
    PIPE("pipe"),
    INSERT("insert"),
    EXTRACT("extract");

    private final String name;

    PipeSide(String name) {
        this.name = name;
    }

    public boolean isContainer() {
        return this == INSERT || this == EXTRACT;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
