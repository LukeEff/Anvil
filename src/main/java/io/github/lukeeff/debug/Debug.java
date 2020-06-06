package io.github.lukeeff.debug;

import com.mojang.authlib.GameProfile;
import io.github.lukeeff.anv.AnvLivingEntity;
import io.github.lukeeff.anv.AnvPlayer;
import io.github.lukeeff.anv.Anvil;
import io.github.lukeeff.event.EventHandler;
import io.github.lukeeff.event.Listener;
import io.github.lukeeff.event.events.DeopPlayerEvent;
import io.github.lukeeff.event.events.EffectAddedEvent;
import io.github.lukeeff.event.events.OpPlayerEvent;
import io.github.lukeeff.event.events.OpPlayersCommandEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;

public class Debug implements Listener {

    public Debug() {
    }

    @EventHandler
    public void onEffectAdd(EffectAddedEvent event) {
        AnvLivingEntity livingEntity = event.getLivingEntity();
        livingEntity.kill();
        livingEntity.sendMessage(ChatFormatting.RED + "You died! Wait, you already knew that...");
    }

    @EventHandler
    public void onOp(OpPlayerEvent event) {
        ServerPlayer player = (ServerPlayer) event.getPlayer().getHandle();
        MinecraftServer server = player.getServer();
        EntityType.LIGHTNING_BOLT.spawn(player.getLevel(),  null, player, player.blockPosition(), MobSpawnType.COMMAND, true, true );
        server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
        event.getPlayer().getHandle().die(DamageSource.LIGHTNING_BOLT);
        server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(false, server);
        event.getPlayer().sendTitle(ChatFormatting.YELLOW + "DEAD!", "Or not?", 80, 100, 80);
    }

    @EventHandler
    public void onDeop(DeopPlayerEvent event) {
        event.getPlayer().sendMessage("Nice job");
    }

}
