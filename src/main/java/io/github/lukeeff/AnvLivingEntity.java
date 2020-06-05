package io.github.lukeeff;

import lombok.Getter;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.entity.LivingEntity;

/**
 * An AnvilLivingEntity. Keeping things simple for developers.
 *
 * @author lukeeff
 * @since 1.0
 */
public class AnvLivingEntity {

    @Getter private final LivingEntity livingEntity;

    public AnvLivingEntity(LivingEntity livingEntity) {
        this.livingEntity = livingEntity;
    }

    /**
     * Sends a message to the entity.
     *
     * @param message the message being sent to the entity.
     */
    public void sendMessage(String message) {
        livingEntity.sendMessage(new TextComponent(message), livingEntity.getUUID());
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
            livingEntity.setHealth(newHealth);
        }
    }

    /**
     * Kills the entity.
     */
    public void kill() {
        livingEntity.kill();
    }


}
