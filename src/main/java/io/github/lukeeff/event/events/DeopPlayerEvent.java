package io.github.lukeeff.event.events;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerPlayer;

/**
 * Event listened for when player gets deopped
 *
 * @author lukeeff
 * @since 1.0
 */
public class DeopPlayerEvent extends OpPlayerEvent {

    public DeopPlayerEvent(ServerPlayer player) {
        super(player);
    }

}
