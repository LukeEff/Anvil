package io.github.lukeeff.anv;

import io.github.lukeeff.anv.text.AnvTextUtil;
import lombok.Getter;
import net.minecraft.Util;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import java.util.UUID;

public class Anvil {

    @Getter private static MinecraftServer server;

    public static void init(MinecraftServer mcServer) {
        server = mcServer;
    }

    public static void broadcastMessage(String message) {
        Component msg = AnvTextUtil.toChatComp(message);
        server.getPlayerList().broadcastMessage(msg, ChatType.CHAT, Util.NIL_UUID);
    }

    public static PlayerList getPlayers() {
        return server.getPlayerList();
    }

}
