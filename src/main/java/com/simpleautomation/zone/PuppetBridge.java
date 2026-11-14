package com.simpleautomation.zone;

/**
 * Lien vers l'affichage des marionnettes, branché par le client au démarrage.
 * Le code commun n'importe ainsi aucune classe client, et un serveur dédié ne les charge jamais.
 */
public interface PuppetBridge {
    PuppetBridge NONE = new PuppetBridge() {
        @Override
        public void tick(ZoneControllerBlockEntity controller) {
        }

        @Override
        public void remove(ZoneControllerBlockEntity controller) {
        }
    };

    /** Remplacé par le client dans son initialisation. */
    Holder HOLDER = new Holder();

    PuppetBridge INSTANCE = new PuppetBridge() {
        @Override
        public void tick(ZoneControllerBlockEntity controller) {
            HOLDER.bridge.tick(controller);
        }

        @Override
        public void remove(ZoneControllerBlockEntity controller) {
            HOLDER.bridge.remove(controller);
        }
    };

    void tick(ZoneControllerBlockEntity controller);

    void remove(ZoneControllerBlockEntity controller);

    final class Holder {
        private volatile PuppetBridge bridge = NONE;

        public void set(PuppetBridge bridge) {
            this.bridge = bridge;
        }
    }
}
