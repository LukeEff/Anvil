package io.github.lukeeff.debug;

import io.github.lukeeff.event.EventHandler;
import io.github.lukeeff.event.Listener;
import io.github.lukeeff.event.events.EffectAddedEvent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.entity.LivingEntity;


public class Debug implements Listener {

    public Debug() {
    }

    @EventHandler
    public void onEffectAdd(EffectAddedEvent event) {
        LivingEntity livingEntity = event.getLivingEntity();
        livingEntity.kill();
        livingEntity.sendMessage(new TextComponent("Nice one lol"), livingEntity.getUUID());
    }

}
