package io.github.lukeeff.event.events;

import io.github.lukeeff.anv.AnvLivingEntity;
import io.github.lukeeff.event.Event;
import io.github.lukeeff.event.Listener;
import lombok.Getter;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Event called when a living entity receives a status effect.
 *
 * @author lukeeff
 * @since 1.0
 */
public class EffectAddedEvent implements Event, Listener {

    @Getter private final MobEffectInstance mobEffectInstance;
    @Getter private final AnvLivingEntity livingEntity;

    public EffectAddedEvent(MobEffectInstance mobEffectInstance, final LivingEntity livingEntity) {
        this.mobEffectInstance = mobEffectInstance;
        this.livingEntity = new AnvLivingEntity(livingEntity);
    }


}
