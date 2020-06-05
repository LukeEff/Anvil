package io.github.lukeeff.anv;

import com.mojang.authlib.GameProfile;
import io.github.lukeeff.anv.text.AnvTextUtil;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitlesPacket;
import net.minecraft.server.level.ServerPlayer;

public class AnvPlayer extends AnvLivingEntity {

    @Getter private GameProfile gameProfile;

    public AnvPlayer(ServerPlayer player) {
        super(player);
    }

    public void sendTitle(String title, String subTitle, int fadeIn, int stay, int fadeOut) {
        Component theTitle = AnvTextUtil.toChatComp(title);
        Component theSubTitle = AnvTextUtil.toChatComp(subTitle);
        ClientboundSetTitlesPacket titlePacket =
                new ClientboundSetTitlesPacket
                        (ClientboundSetTitlesPacket.Type.TITLE, theTitle, fadeIn, stay, fadeOut);

        ClientboundSetTitlesPacket subTitlePacket = new ClientboundSetTitlesPacket
                (ClientboundSetTitlesPacket.Type.SUBTITLE, theSubTitle, fadeIn, stay, fadeOut);

        ServerPlayer player = (ServerPlayer) getHandle();
        player.connection.connection.send(titlePacket);
        player.connection.connection.send(subTitlePacket);

    }



}
