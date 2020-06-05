package io.github.lukeeff.event;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class EventManager {

    private static final List<Listener> registered = new ArrayList<>();

    public static void register(Listener listener) {
        if (!registered.contains(listener)) {
            registered.add(listener);
        }
    }

    public static void unregister(Listener listener) {
        registered.remove(listener);
    }

    public static List<Listener> getRegistered() {
        return registered;
    }

    public static void callEvent(final Event event) {
        new Thread(() -> call(event)).start();
    }

    private static void call(final Event event) {
        for (Listener registered : getRegistered()) {
            Method[] methods = registered.getClass().getMethods();

            for (Method method : methods) {
                EventHandler eventHandler = method.getAnnotation(EventHandler.class);
                if (eventHandler != null) {
                    Class<?>[] methodParams = method.getParameterTypes();

                    if (methodParams.length < 1) {
                        continue;
                    }

                    if (!event.getClass().getSimpleName().equals(methodParams[0].getSimpleName())) {
                        continue;
                    }

                    try {
                        method.invoke(registered.getClass().newInstance(), event);
                    } catch (Exception exception) {
                        System.err.println(exception);
                    }
                }
            }

        }
    }



}
