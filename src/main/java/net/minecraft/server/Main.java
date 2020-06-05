//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package net.minecraft.server;

import com.google.common.collect.ImmutableSet;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Lifecycle;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.net.Proxy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

import io.github.lukeeff.event.EventRegistry;
import joptsimple.AbstractOptionSpec;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.NonOptionArgumentSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;
import net.minecraft.CrashReport;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.Util;
import net.minecraft.commands.Commands.CommandSelection;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistryAccess.RegistryHolder;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryReadOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.dedicated.DedicatedServerSettings;
import net.minecraft.server.level.progress.LoggerChunkProgressListener;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.worldupdate.WorldUpgrader;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {
    private static final Logger LOGGER = LogManager.getLogger();

    public Main() {
    }

    public static void main(String[] var0) {
        OptionParser var1 = new OptionParser();
        OptionSpecBuilder var2 = var1.accepts("nogui");
        OptionSpecBuilder var3 = var1.accepts("initSettings", "Initializes 'server.properties' and 'eula.txt', then quits");
        OptionSpecBuilder var4 = var1.accepts("demo");
        OptionSpecBuilder var5 = var1.accepts("bonusChest");
        OptionSpecBuilder var6 = var1.accepts("forceUpgrade");
        OptionSpecBuilder var7 = var1.accepts("eraseCache");
        OptionSpecBuilder var8 = var1.accepts("safeMode", "Loads level with vanilla datapack only");
        AbstractOptionSpec var9 = var1.accepts("help").forHelp();
        ArgumentAcceptingOptionSpec var10 = var1.accepts("singleplayer").withRequiredArg();
        ArgumentAcceptingOptionSpec var11 = var1.accepts("universe").withRequiredArg().defaultsTo(".");
        ArgumentAcceptingOptionSpec var12 = var1.accepts("world").withRequiredArg();
        ArgumentAcceptingOptionSpec var13 = var1.accepts("port").withRequiredArg().ofType(Integer.class).defaultsTo(-1);
        ArgumentAcceptingOptionSpec var14 = var1.accepts("serverId").withRequiredArg();
        NonOptionArgumentSpec var15 = var1.nonOptions();

        try {
            OptionSet var16 = var1.parse(var0);
            if (var16.has(var9)) {
                var1.printHelpOn(System.err);
                return;
            }

            CrashReport.preload();
            Bootstrap.bootStrap();
            Bootstrap.validate();
            Util.startTimerHackThread();
            Path path = Paths.get("server.properties");
            DedicatedServerSettings var18 = new DedicatedServerSettings(path);
            var18.forceSave();
            Path var19 = Paths.get("eula.txt");
            Eula var20 = new Eula(var19);
            if (var16.has(var3)) {
                LOGGER.info("Initialized '{}' and '{}'", path.toAbsolutePath(), var19.toAbsolutePath());
                return;
            }

            if (!var20.hasAgreedToEULA()) {
                LOGGER.info("You need to agree to the EULA in order to run the server. Go to eula.txt for more info.");
                return;
            }

            File var21 = new File((String)var16.valueOf(var11));
            YggdrasilAuthenticationService var22 = new YggdrasilAuthenticationService(Proxy.NO_PROXY, UUID.randomUUID().toString());
            MinecraftSessionService var23 = var22.createMinecraftSessionService();
            GameProfileRepository var24 = var22.createProfileRepository();
            GameProfileCache var25 = new GameProfileCache(var24, new File(var21, MinecraftServer.USERID_CACHE_FILE.getName()));
            String var26 = (String)Optional.ofNullable(var16.valueOf(var12)).orElse(var18.getProperties().levelName);
            LevelStorageSource var27 = LevelStorageSource.createDefault(var21.toPath());
            LevelStorageAccess var28 = var27.createAccess(var26);
            MinecraftServer.convertFromRegionFormatIfNeeded(var28);
            DataPackConfig var29 = var28.getDataPacks();
            boolean var30 = var16.has(var8);
            if (var30) {
                LOGGER.warn("Safe mode active, only vanilla datapack will be loaded");
            }

            PackRepository var31 = new PackRepository(Pack::new, new ServerPacksSource(), new FolderRepositorySource(var28.getLevelPath(LevelResource.DATAPACK_DIR).toFile(), PackSource.WORLD));
            DataPackConfig var32 = MinecraftServer.configurePackRepository(var31, var29 == null ? DataPackConfig.DEFAULT : var29, var30);
            CompletableFuture var33 = ServerResources.loadResources(var31.openAllSelected(), CommandSelection.DEDICATED, var18.getProperties().functionPermissionLevel, Util.backgroundExecutor(), Runnable::run);

            ServerResources var34;
            try {
                var34 = (ServerResources)var33.get();
            } catch (Exception var41) {
                LOGGER.warn("Failed to load datapacks, can't proceed with server load. You can either fix your datapacks or reset to vanilla with --safeMode", var41);
                var31.close();
                return;
            }

            var34.updateGlobals();
            RegistryHolder var35 = RegistryAccess.builtin();
            RegistryReadOps var36 = RegistryReadOps.create(NbtOps.INSTANCE, var34.getResourceManager(), var35);
            WorldData var37 = var28.getDataTag(var36, var32);
            if (var37 == null) {
                LevelSettings var38;
                WorldGenSettings var39;
                if (var16.has(var4)) {
                    var38 = MinecraftServer.DEMO_SETTINGS;
                    var39 = WorldGenSettings.DEMO_SETTINGS;
                } else {
                    DedicatedServerProperties var40 = var18.getProperties();
                    var38 = new LevelSettings(var40.levelName, var40.gamemode, var40.hardcore, var40.difficulty, false, new GameRules(), var32);
                    var39 = var16.has(var5) ? var40.worldGenSettings.withBonusChest() : var40.worldGenSettings;
                }

                var37 = new PrimaryLevelData(var38, var39, Lifecycle.stable());
            }

            if (var16.has(var6)) {
                forceUpgrade(var28, DataFixers.getDataFixer(), var16.has(var7), () -> true, var37.worldGenSettings().levels());
            }

            new EventRegistry();
            var28.saveDataTag(var35, var37);
            WorldData finalVar3 = var37;
            final DedicatedServer var43 = MinecraftServer.spin((var16x) -> {
                DedicatedServer dedicatedServer = new DedicatedServer(var16x, var35, var28, var31, var34, finalVar3, var18, DataFixers.getDataFixer(), var23, var24, var25, LoggerChunkProgressListener::new);
                dedicatedServer.setSingleplayerName((String)var16.valueOf(var10));
                dedicatedServer.setPort((Integer)var16.valueOf(var13));
                dedicatedServer.setDemo(var16.has(var4));
                dedicatedServer.setId((String)var16.valueOf(var14));
                boolean var18x = !var16.has(var2) && !var16.valuesOf(var15).contains("nogui");
                if (var18x && !GraphicsEnvironment.isHeadless()) {
                    dedicatedServer.showGui();
                }

                return dedicatedServer;
            });
            Thread var44 = new Thread("Server Shutdown Thread") {
                public void run() {
                    var43.halt(true);
                }
            };
            var44.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
            Runtime.getRuntime().addShutdownHook(var44);
        } catch (Exception var42) {
            LOGGER.fatal("Failed to start the minecraft server", var42);
        }

    }

    private static void forceUpgrade(LevelStorageAccess var0, DataFixer var1, boolean var2, BooleanSupplier var3, ImmutableSet<ResourceKey<Level>> var4) {
        LOGGER.info("Forcing world upgrade!");
        WorldUpgrader var5 = new WorldUpgrader(var0, var1, var4, var2);
        Component var6 = null;

        while(!var5.isFinished()) {
            Component var7 = var5.getStatus();
            if (var6 != var7) {
                var6 = var7;
                LOGGER.info(var5.getStatus().getString());
            }

            int var8 = var5.getTotalChunks();
            if (var8 > 0) {
                int var9 = var5.getConverted() + var5.getSkipped();
                LOGGER.info("{}% completed ({} / {} chunks)...", Mth.floor((float)var9 / (float)var8 * 100.0F), var9, var8);
            }

            if (!var3.getAsBoolean()) {
                var5.cancel();
            } else {
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException var10) {
                }
            }
        }

    }
}

