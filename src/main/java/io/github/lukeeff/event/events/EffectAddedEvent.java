package io.github.lukeeff.event.events;

import io.github.lukeeff.event.Event;
import io.github.lukeeff.event.Listener;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public class EffectAddedEvent implements Event, Listener {

    @Getter @Setter
    private MobEffectInstance mobEffectInstance;
    @Getter private final LivingEntity livingEntity;

    public EffectAddedEvent(MobEffectInstance mobEffectInstance, final LivingEntity livingEntity) {
        this.mobEffectInstance = mobEffectInstance;
        this.livingEntity = livingEntity;
    }


}
