//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package net.minecraft.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.ResultConsumer;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class CommandSourceStack implements SharedSuggestionProvider {
    public static final SimpleCommandExceptionType ERROR_NOT_PLAYER = new SimpleCommandExceptionType(new TranslatableComponent("permissions.requires.player"));
    public static final SimpleCommandExceptionType ERROR_NOT_ENTITY = new SimpleCommandExceptionType(new TranslatableComponent("permissions.requires.entity"));
    private final CommandSource source;
    private final Vec3 worldPosition;
    private final ServerLevel level;
    private final int permissionLevel;
    private final String textName;
    private final Component displayName;
    private final MinecraftServer server;
    private final boolean silent;
    @Nullable
    private final Entity entity;
    private final ResultConsumer<CommandSourceStack> consumer;
    private final Anchor anchor;
    private final Vec2 rotation;

    public CommandSourceStack(CommandSource source, Vec3 worldPosition, Vec2 rotation, ServerLevel level, int permissionLevel, String textName, Component displayName, MinecraftServer server, @Nullable Entity entity) {
        this(source, worldPosition, rotation, level, permissionLevel, textName, displayName, server, entity, false, (commandContext, b, i) -> {
        }, Anchor.FEET);
    }

    protected CommandSourceStack(CommandSource source, Vec3 worldPosition, Vec2 rotation, ServerLevel level, int permissionLevel, String textName, Component displayName, MinecraftServer server, @Nullable Entity entity, boolean silent, ResultConsumer<CommandSourceStack> consumer, Anchor anchor) {
        this.source = source;
        this.worldPosition = worldPosition;
        this.level = level;
        this.silent = silent;
        this.entity = entity;
        this.permissionLevel = permissionLevel;
        this.textName = textName;
        this.displayName = displayName;
        this.server = server;
        this.consumer = consumer;
        this.anchor = anchor;
        this.rotation = rotation;
    }

    public CommandSourceStack withEntity(Entity entity) {
        return this.entity == entity ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, this.permissionLevel, entity.getName().getString(), entity.getDisplayName(), this.server, entity, this.silent, this.consumer, this.anchor);
    }

    public CommandSourceStack withPosition(Vec3 worldPosition) {
        return this.worldPosition.equals(worldPosition) ? this : new CommandSourceStack(this.source, worldPosition, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor);
    }

    public CommandSourceStack withRotation(Vec2 rotation) {
        return this.rotation.equals(rotation) ? this : new CommandSourceStack(this.source, this.worldPosition, rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor);
    }

    public CommandSourceStack withCallback(ResultConsumer<CommandSourceStack> consumer) {
        return this.consumer.equals(consumer) ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, consumer, this.anchor);
    }

    public CommandSourceStack withCallback(ResultConsumer<CommandSourceStack> var1, BinaryOperator<ResultConsumer<CommandSourceStack>> var2) {
        ResultConsumer<CommandSourceStack> commandSourceStack = var2.apply(this.consumer, var1);
        return this.withCallback(commandSourceStack);
    }

    public CommandSourceStack withSuppressedOutput() {
        return this.silent ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, true, this.consumer, this.anchor);
    }

    public CommandSourceStack withPermission(int permissionLevel) {
        return permissionLevel == this.permissionLevel ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor);
    }

    public CommandSourceStack withMaximumPermission(int permissionLevel) {
        return permissionLevel <= this.permissionLevel ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor);
    }

    public CommandSourceStack withAnchor(Anchor anchor) {
        return anchor == this.anchor ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, anchor);
    }

    public CommandSourceStack withLevel(ServerLevel level) {
        return level == this.level ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor);
    }

    public CommandSourceStack facing(Entity entity, Anchor anchor) throws CommandSyntaxException {
        return this.facing(anchor.apply(entity));
    }

    public CommandSourceStack facing(Vec3 worldPosition) throws CommandSyntaxException {
        Vec3 vec3 = this.anchor.apply(this);
        double var3 = worldPosition.x - vec3.x;
        double var5 = worldPosition.y - vec3.y;
        double var7 = worldPosition.z - vec3.z;
        double var9 = Mth.sqrt(var3 * var3 + var7 * var7);
        float var11 = Mth.wrapDegrees((float)(-(Mth.atan2(var5, var9) * 57.2957763671875D)));
        float var12 = Mth.wrapDegrees((float)(Mth.atan2(var7, var3) * 57.2957763671875D) - 90.0F);
        return this.withRotation(new Vec2(var11, var12));
    }

    public Component getDisplayName() {
        return this.displayName;
    }

    public String getTextName() {
        return this.textName;
    }

    public boolean hasPermission(int var1) {
        return this.permissionLevel >= var1;
    }

    public Vec3 getPosition() {
        return this.worldPosition;
    }

    public ServerLevel getLevel() {
        return this.level;
    }

    @Nullable
    public Entity getEntity() {
        return this.entity;
    }

    public Entity getEntityOrException() throws CommandSyntaxException {
        if (this.entity == null) {
            throw ERROR_NOT_ENTITY.create();
        } else {
            return this.entity;
        }
    }

    public ServerPlayer getPlayerOrException() throws CommandSyntaxException {
        if (!(this.entity instanceof ServerPlayer)) {
            throw ERROR_NOT_PLAYER.create();
        } else {
            return (ServerPlayer)this.entity;
        }
    }

    public Vec2 getRotation() {
        return this.rotation;
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    public Anchor getAnchor() {
        return this.anchor;
    }

    public void sendSuccess(Component message, boolean shouldBroadcast) {
        if (this.source.acceptsSuccess() && !this.silent) {
            this.source.sendMessage(message, Util.NIL_UUID);
        }

        if (shouldBroadcast && this.source.shouldInformAdmins() && !this.silent) {
            this.broadcastToAdmins(message);
        }

    }

    private void broadcastToAdmins(Component component) {
        MutableComponent var2 = (new TranslatableComponent("chat.type.admin", this.getDisplayName(), component)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        if (this.server.getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK)) {
            Iterator var3 = this.server.getPlayerList().getPlayers().iterator();

            while(var3.hasNext()) {
                ServerPlayer var4 = (ServerPlayer)var3.next();
                if (var4 != this.source && this.server.getPlayerList().isOp(var4.getGameProfile())) {
                    var4.sendMessage(var2, Util.NIL_UUID);
                }
            }
        }

        if (this.source != this.server && this.server.getGameRules().getBoolean(GameRules.RULE_LOGADMINCOMMANDS)) {
            this.server.sendMessage(var2, Util.NIL_UUID);
        }

    }

    public void sendFailure(Component var1) {
        if (this.source.acceptsFailure() && !this.silent) {
            this.source.sendMessage((new TextComponent("")).append(var1).withStyle(ChatFormatting.RED), Util.NIL_UUID);
        }

    }

    public void onCommandComplete(CommandContext<CommandSourceStack> var1, boolean var2, int var3) {
        if (this.consumer != null) {
            this.consumer.onCommandComplete(var1, var2, var3);
        }

    }

    public Collection<String> getOnlinePlayerNames() {
        return Lists.newArrayList(this.server.getPlayerNames());
    }

    public Collection<String> getAllTeams() {
        return this.server.getScoreboard().getTeamNames();
    }

    public Collection<ResourceLocation> getAvailableSoundEvents() {
        return Registry.SOUND_EVENT.keySet();
    }

    public Stream<ResourceLocation> getRecipeNames() {
        return this.server.getRecipeManager().getRecipeIds();
    }

    public CompletableFuture<Suggestions> customSuggestion(CommandContext<SharedSuggestionProvider> var1, SuggestionsBuilder var2) {
        return null;
    }

    public Set<ResourceKey<Level>> levels() {
        return this.server.levelKeys();
    }
}
