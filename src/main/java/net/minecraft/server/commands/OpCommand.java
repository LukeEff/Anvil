//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package net.minecraft.server.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import java.util.Iterator;

import io.github.lukeeff.anv.text.AnvTextUtil;
import io.github.lukeeff.event.EventManager;
import io.github.lukeeff.event.events.OpPlayersCommandEvent;
import lombok.SneakyThrows;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.players.PlayerList;

public class OpCommand {
    private static final SimpleCommandExceptionType ERROR_ALREADY_OP = new SimpleCommandExceptionType(new TranslatableComponent("commands.op.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher) {
        commandDispatcher.register((Commands.literal("op").requires((sourceStack) -> sourceStack.hasPermission(3))).then(Commands.argument("targets", GameProfileArgument.gameProfile()).suggests((context, builder) -> {
            PlayerList var2 = context.getSource().getServer().getPlayerList();
            return SharedSuggestionProvider.suggest(var2.getPlayers().stream().filter((var1x) -> !var2.isOp(var1x.getGameProfile())).map((var0) -> var0.getGameProfile().getName()), builder);
        }).executes((context) -> opPlayers(context.getSource(), GameProfileArgument.getGameProfiles(context, "targets")))));
    }

    /**
     * Executes logic for op command
     *
     * @see OpPlayersCommandEvent
     * @param sender source stack.
     * @param gameProfiles the game profile's of players who will be granted operator.
     * @return the number of players to be granted operator.
     * @throws CommandSyntaxException command syntax wrong.
     */
    private static int opPlayers(CommandSourceStack sender, Collection<GameProfile> gameProfiles) throws CommandSyntaxException {
        final OpPlayersCommandEvent event = new OpPlayersCommandEvent(sender, gameProfiles);
        EventManager.callEvent(event);
        if(event.isCanceled()) {
            return 0;
        }
        PlayerList playerList = sender.getServer().getPlayerList();
        int playersOpped = 0;

        for (GameProfile gameProfile : gameProfiles) {
            if (!playerList.isOp(gameProfile)) {
                playerList.op(gameProfile);
                ++playersOpped;
                if(event.isSilent()) {
                    continue;
                }
                String name = gameProfiles.iterator().next().getName();
                Component message = (event.getCustomMessage() == null) ?
                        new TranslatableComponent("commands.op.success", name) :
                        AnvTextUtil.toChatComp(event.getCustomMessage().replaceAll("%player%", name));
                sender.sendSuccess(message, true);
            }
        }
        if (playersOpped == 0) {
            throw ERROR_ALREADY_OP.create();

        } else {
            return playersOpped;
        }
    }
}
