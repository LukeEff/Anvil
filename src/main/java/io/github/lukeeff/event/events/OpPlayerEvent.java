package io.github.lukeeff.event.events;

import io.github.lukeeff.anv.AnvPlayer;
import io.github.lukeeff.event.Cancellable;
import io.github.lukeeff.event.Event;
import io.github.lukeeff.event.Listener;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.level.ServerPlayer;

/**
 * Event listened for when player gets opped.
 *
 * @author lukeeff
 * @since 1.0
 */
public class OpPlayerEvent implements Event, Listener, Cancellable {

    @Getter private AnvPlayer player;
    @Getter private boolean cancel;
    @Getter @Setter private boolean silent;

    public OpPlayerEvent(ServerPlayer player) {
        this.player = new AnvPlayer(player);
    }

    @Override
    public boolean isCanceled() {
        return cancel;
    }

    @Override
    public void setCanceled(boolean cancel) {
        this.cancel = cancel;
    }

}
