package io.github.lukeeff.event;

import io.github.lukeeff.debug.Debug;

public class EventRegistry {

    public EventRegistry() {
        EventManager.register(new Debug());
    }

}
