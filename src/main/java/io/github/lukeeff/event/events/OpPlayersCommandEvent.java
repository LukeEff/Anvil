package io.github.lukeeff.event.events;

import com.mojang.authlib.GameProfile;
import io.github.lukeeff.event.Cancellable;
import io.github.lukeeff.event.Event;
import io.github.lukeeff.event.Listener;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.commands.CommandSourceStack;

import java.util.Collection;

/**
 * Ran when op command is executed.
 *
 * * %player% - Will replace with name in message.
 *
 * @author lukeeff
 * @since 1.0
 */
public class OpPlayersCommandEvent implements Event, Listener, Cancellable {

    @Getter CommandSourceStack sourceStack;
    @Getter Collection<GameProfile> gameProfiles;
    @Getter @Setter private String customMessage = null;
    @Getter @Setter private boolean silent;
    private boolean cancel;

    public OpPlayersCommandEvent(CommandSourceStack sourceStack, Collection<GameProfile> gameProfiles) {
        this.gameProfiles = gameProfiles;
        this.sourceStack = sourceStack;
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
