//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package net.minecraft.server;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.datafixers.DataFixer;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.net.Proxy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import joptsimple.AbstractOptionSpec;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.NonOptionArgumentSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;
import net.minecraft.CrashReport;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.dedicated.DedicatedServerSettings;
import net.minecraft.server.level.progress.LoggerChunkProgressListener;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.worldupdate.WorldUpgrader;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelSettings;
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

    public static void main(String[] args) {
        OptionParser optionParser = new OptionParser();
        OptionSpecBuilder noGui = optionParser.accepts("nogui");
        OptionSpecBuilder initSettings = optionParser.accepts("initSettings", "Initializes 'server.properties' and 'eula.txt', then quits");
        OptionSpecBuilder demo = optionParser.accepts("demo");
        OptionSpecBuilder bonusChest = optionParser.accepts("bonusChest");
        OptionSpecBuilder forceUpgrade = optionParser.accepts("forceUpgrade");
        OptionSpecBuilder eraseCache = optionParser.accepts("eraseCache");
        OptionSpecBuilder safeMode = optionParser.accepts("safeMode", "Loads level with vanilla datapack only");
        AbstractOptionSpec help = optionParser.accepts("help").forHelp();
        ArgumentAcceptingOptionSpec singlePlayer = optionParser.accepts("singleplayer").withRequiredArg();
        ArgumentAcceptingOptionSpec universe = optionParser.accepts("universe").withRequiredArg().defaultsTo(".");
        ArgumentAcceptingOptionSpec world = optionParser.accepts("world").withRequiredArg();
        ArgumentAcceptingOptionSpec port = optionParser.accepts("port").withRequiredArg().ofType(Integer.class).defaultsTo(-1);
        ArgumentAcceptingOptionSpec serverId = optionParser.accepts("serverId").withRequiredArg();
        NonOptionArgumentSpec nonOptions = optionParser.nonOptions();

        try {
            OptionSet optionSet = optionParser.parse(args);
            if (optionSet.has(help)) {
                optionParser.printHelpOn(System.err);
                return;
            }

            CrashReport.preload();
            Bootstrap.bootStrap();
            Bootstrap.validate();
            Util.startTimerHackThread();
            Path var17 = Paths.get("server.properties");
            DedicatedServerSettings var18 = new DedicatedServerSettings(var17);
            var18.forceSave();
            Path path = Paths.get("eula.txt");
            Eula eula = new Eula(path);
            if (optionSet.has(initSettings)) {
                LOGGER.info("Initialized '{}' and '{}'", var17.toAbsolutePath(), path.toAbsolutePath());
                return;
            }

            if (!eula.hasAgreedToEULA()) {
                LOGGER.info("You need to agree to the EULA in order to run the server. Go to eula.txt for more info.");
                return;
            }

            File file = new File((String)optionSet.valueOf(universe));
            YggdrasilAuthenticationService service = new YggdrasilAuthenticationService(Proxy.NO_PROXY, UUID.randomUUID().toString());
            MinecraftSessionService sessionService = service.createMinecraftSessionService();
            GameProfileRepository repository = service.createProfileRepository();
            GameProfileCache var25 = new GameProfileCache(repository, new File(file, MinecraftServer.USERID_CACHE_FILE.getName()));
            String var26 = (String)Optional.ofNullable(optionSet.valueOf(world)).orElse(var18.getProperties().levelName);
            LevelStorageSource var27 = LevelStorageSource.createDefault(file.toPath());
            LevelStorageAccess var28 = var27.createAccess(var26);
            MinecraftServer.convertFromRegionFormatIfNeeded(var28);
            if (optionSet.has(forceUpgrade)) {
                forceUpgrade(var28, DataFixers.getDataFixer(), optionSet.has(eraseCache), () -> true);
            }

            Object var29 = var28.getDataTag();
            if (var29 == null) {
                LevelSettings var30;
                if (optionSet.has(demo)) {
                    var30 = MinecraftServer.DEMO_SETTINGS;
                } else {
                    DedicatedServerProperties var31 = var18.getProperties();
                    var30 = new LevelSettings(var31.levelName, var31.gamemode, var31.hardcore, var31.difficulty, false, new GameRules(), optionSet.has(bonusChest) ? var31.worldGenSettings.withBonusChest() : var31.worldGenSettings);
                }

                var29 = new PrimaryLevelData(var30);
            }

            boolean var39 = optionSet.has(safeMode);
            if (var39) {
                LOGGER.warn("Safe mode active, only vanilla datapack will be loaded");
            }

            PackRepository var40 = MinecraftServer.createPackRepository(var28.getLevelPath(LevelResource.DATAPACK_DIR), (WorldData)var29, var39);
            CompletableFuture var32 = ServerResources.loadResources(var40.openAllSelected(), true, var18.getProperties().functionPermissionLevel, Util.backgroundExecutor(), Runnable::run);

            ServerResources var33;
            try {
                var33 = (ServerResources)var32.get();
            } catch (Exception var37) {
                LOGGER.warn("Failed to load datapacks, can't proceed with server load. You can either fix your datapacks or reset to vanilla with --safeMode", var37);
                var40.close();
                return;
            }

            var33.updateGlobals();
            final DedicatedServer var34 = new DedicatedServer(var28, var40, var33, (WorldData)var29, var18, DataFixers.getDataFixer(), sessionService, repository, var25, LoggerChunkProgressListener::new);
            var34.setSingleplayerName((String)optionSet.valueOf(singlePlayer));
            var34.setPort((Integer)optionSet.valueOf(port));
            var34.setDemo(optionSet.has(demo));
            var34.setId((String)optionSet.valueOf(serverId));
            boolean var35 = !optionSet.has(noGui) && !optionSet.valuesOf(nonOptions).contains("nogui");
            if (var35 && !GraphicsEnvironment.isHeadless()) {
                var34.showGui();
            }

            var34.forkAndRun();
            Thread var36 = new Thread("Server Shutdown Thread") {
                public void run() {
                    var34.halt(true);
                }
            };
            var36.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
            Runtime.getRuntime().addShutdownHook(var36);
        } catch (Exception var38) {
            LOGGER.fatal("Failed to start the minecraft server", var38);
        }

    }

    private static void forceUpgrade(LevelStorageAccess var0, DataFixer var1, boolean var2, BooleanSupplier var3) {
        LOGGER.info("Forcing world upgrade!");
        WorldData var4 = var0.getDataTag();
        if (var4 != null) {
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
}
