package top.tasaed.aohx.monohydrate.profiler;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Z_Other.ST.sUM;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import team.rainfall.finality.FinalityLogger;
import team.rainfall.finality.loader.Main;
import team.rainfall.finality.loader.plugin.PluginManager;
import top.tasaed.aohx.monohydrate.core.MonohydrateConfig;

/** Immutable completion-time snapshot; collected only after the final timing sample. */
public record LoadingMetadata(
        Versions versions,
        Configuration configuration,
        MapContext map,
        ScenarioContext scenario,
        List<Mod> mods,
        List<Plugin> plugins,
        Jvm jvm) {
    private static final String PLUGIN_ID = "top.tasaed.aohx.monohydrate";
    private static final String[] HEAP_ARGUMENT_PREFIXES = {
        "-Xms",
        "-Xmx",
        "-Xmn",
        "-XX:InitialHeapSize=",
        "-XX:MinHeapSize=",
        "-XX:MaxHeapSize=",
        "-XX:NewSize=",
        "-XX:MaxNewSize=",
        "-XX:NewRatio=",
        "-XX:SurvivorRatio=",
        "-XX:InitialRAMPercentage=",
        "-XX:MinRAMPercentage=",
        "-XX:MaxRAMPercentage=",
        "-XX:MaxRAM=",
        "-XX:MinHeapFreeRatio=",
        "-XX:MaxHeapFreeRatio="
    };

    public LoadingMetadata {
        mods = mods == null ? null : List.copyOf(mods);
        plugins = plugins == null ? null : List.copyOf(plugins);
    }

    public record Versions(String plugin, String game, String loader) {}

    public record Configuration(
            boolean cityOptimizationsEnabled,
            boolean provinceCandidateFilterEnabled,
            boolean profilerEnabled,
            String analysisMode) {}

    public record MapContext(int index, String folder, int assetScale, float extraScale, Float viewportZoom) {}

    public record ScenarioContext(int index, String tag, String name) {}

    public record Mod(String source, String folder) {}

    public record Plugin(String id, String name, String version) {}

    public record Jvm(
            String javaVersion,
            String runtimeVersion,
            String vendor,
            String vmName,
            String vmVersion,
            List<String> heapArguments,
            long initialHeapBytes,
            long maxHeapBytes,
            long committedHeapBytes,
            List<String> garbageCollectors) {
        public Jvm {
            heapArguments = List.copyOf(heapArguments);
            garbageCollectors = List.copyOf(garbageCollectors);
        }
    }

    static LoadingMetadata capture() {
        List<Plugin> plugins = captureSection("plugins", LoadingMetadata::capturePlugins);
        String pluginVersion = null;
        if (plugins != null) {
            for (Plugin plugin : plugins) {
                if (PLUGIN_ID.equals(plugin.id())) {
                    pluginVersion = plugin.version();
                    break;
                }
            }
        }
        return new LoadingMetadata(
                new Versions(pluginVersion, runtimeVersion(CFG.class), runtimeVersion(Main.class)),
                new Configuration(
                        MonohydrateConfig.CITY_OPTIMIZATIONS_ENABLED,
                        MonohydrateConfig.PROVINCE_CANDIDATE_FILTER_ENABLED,
                        MonohydrateConfig.PROFILER_ENABLED,
                        MonohydrateConfig.PROFILER_ENABLED ? "loading-frames-invokes-stages" : "disabled"),
                captureSection("map", LoadingMetadata::captureMap),
                captureSection("scenario", LoadingMetadata::captureScenario),
                captureSection("mods", LoadingMetadata::captureMods),
                plugins,
                captureSection("jvm", LoadingMetadata::captureJvm));
    }

    // VERSION is a constant variable in both dependencies. Direct access would inline the build-time value.
    static String runtimeVersion(Class<?> type) {
        try {
            return (String) type.getField("VERSION").get(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            FinalityLogger.error("[Monohydrate] Failed to read runtime version: " + type.getName(), ex);
            return null;
        }
    }

    private static MapContext captureMap() {
        var map = CFG.map;
        if (map == null) {
            return null;
        }
        int index = map.getActiveMapIDN();
        var zoom = map.getMpS();
        return new MapContext(
                index,
                map.getFile_ActiveMap_Path2(),
                map.getMapScale(index),
                map.getMapExtraScale(index),
                zoom == null ? null : zoom.getCurrSc());
    }

    private static ScenarioContext captureScenario() {
        var core = CFG.core;
        if (core == null || core.getGameScenars() == null) {
            return null;
        }
        var scenarios = core.getGameScenars();
        int index = core.getScenarioID();
        if (index < 0 || index >= scenarios.lScenarios_TagsList.size()) {
            return null;
        }
        return new ScenarioContext(index, scenarios.getScenarioTagID(index), scenarios.getScenarioNameID(index));
    }

    private static List<Mod> captureMods() {
        // FileManager checks active local folders first, then active Workshop installs. Never sort/deduplicate.
        List<Mod> mods = new ArrayList<>(sUM.sUFS + sUM.sUIIS);
        for (int i = 0; i < sUM.sUFS; i++) {
            mods.add(new Mod("local", sUM.sUF.get(i)));
        }
        for (int i = 0; i < sUM.sUIIS; i++) {
            mods.add(new Mod("workshop", sUM.sUII.get(i).getFolder()));
        }
        return mods;
    }

    static List<Plugin> capturePlugins() {
        var data = PluginManager.INSTANCE.pluginDataList;
        List<Plugin> plugins = new ArrayList<>(data.size());
        for (var entry : data) {
            var manifest = entry.manifest;
            plugins.add(new Plugin(manifest.id, manifest.name, manifest.version));
        }
        return plugins;
    }

    static Jvm captureJvm() {
        List<String> heapArguments = new ArrayList<>();
        for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            for (String prefix : HEAP_ARGUMENT_PREFIXES) {
                if (argument.startsWith(prefix)) {
                    heapArguments.add(argument);
                    break;
                }
            }
        }
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        List<String> collectors = new ArrayList<>();
        for (var collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            collectors.add(collector.getName());
        }
        return new Jvm(
                System.getProperty("java.version"),
                System.getProperty("java.runtime.version"),
                System.getProperty("java.vendor"),
                System.getProperty("java.vm.name"),
                System.getProperty("java.vm.version"),
                heapArguments,
                heap.getInit(),
                heap.getMax(),
                heap.getCommitted(),
                collectors);
    }

    private static <T> T captureSection(String section, Supplier<T> capture) {
        try {
            return capture.get();
        } catch (RuntimeException | LinkageError ex) {
            // Metadata failure must not discard timings or prevent the game's final RETURN.
            FinalityLogger.error("[Monohydrate] Failed to capture loading metadata: " + section, ex);
            return null;
        }
    }
}
