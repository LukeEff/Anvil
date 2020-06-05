package io.github.lukeeff.anv;

import io.github.lukeeff.anv.text.AnvTextUtil;
import lombok.Getter;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/**
 * An AnvilLivingEntity. Keeping things simple for developers.
 *
 * @author lukeeff
 * @since 1.0
 */
public class AnvLivingEntity {

    @Getter private final LivingEntity handle;

    public AnvLivingEntity(LivingEntity livingEntity) {
        this.handle = livingEntity;
    }

    /**
     * Sends a message to the entity.
     *
     * @param message the message being sent to the entity.
     */
    public void sendMessage(String message) {

        handle.sendMessage(AnvTextUtil.toChatComp(message), handle.getUUID());
    }

    /**
     * Sets the health of the entity.
     *
     * @param newHealth the new health of the entity.
     */
    public void setHealth(float newHealth) {
        if(newHealth <= 0) {
            kill();
        } else {
            handle.setHealth(newHealth);
        }
    }

    /**
     * Kills the entity.
     */
    public void kill() {
        handle.kill();
    }

    /**
     * Gets the UUID of the entity.
     *
     * @return the UUID of the entity.
     */
    public UUID getUUID() {
        return handle.getUUID();
    }



}
