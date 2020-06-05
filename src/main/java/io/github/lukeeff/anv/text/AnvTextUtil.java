package io.github.lukeeff.anv.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;

public class AnvTextUtil {

    public static Component toChatComp(String msg) {
        return new TextComponent(msg);
    }

}
