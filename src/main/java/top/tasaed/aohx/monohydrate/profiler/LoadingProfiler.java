package top.tasaed.aohx.monohydrate.profiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import team.rainfall.finality.FinalityLogger;
import tools.jackson.databind.json.JsonMapper;
import top.tasaed.aohx.monohydrate.core.MonohydrateConfig;

public final class LoadingProfiler {
    public enum Point {
        LOAD_ASSETS("loadAssets"),
        LOAD_COLORS("loadColors"),
        LOAD_GAME_MAP("loadGameMap"),
        LOAD_BACKGROUND("loadBackground"),
        INIT("init"),
        INIT2("init2"),
        LOAD_RELIGIONS("loadReligions"),
        INIT_MAP_CONTINENTS("initMapContinents"),
        INIT_MAP_REGIONS("initMapRegions"),
        LOAD_FONT_ARMY("loadFontArmy"),
        UPDATE_CIV_PERSONALITY_TYPE("updateCivPersonalityType"),
        LOAD_MAP_OVERLAYS("loadMapOverlays"),
        L_O("lO"),
        L_OI("lOI"),
        L_OI2("lOI2"),
        ADD_IMG("addIMG"),
        LOAD_ARMY_BGIMAGES("loadArmyBGImages"),
        LOAD_SPARKS("loadSparks"),
        LOAD_IDEOLOGIES("loadIdeologies"),
        U_DPN("uDPN"),
        LOAD_SHIP_LINES("loadShipLines"),
        READ_SETTINGS("readSettings"),
        LO_CI("loCI"),
        UPDATE_CLOUDS_INTERFACE("updateCloudsInterface"),
        LOAD_PROVINCE("loadProvince"),
        LOAD_PROVINCE_TEXTURE("loadProvinceTexture"),
        INIT_GAME_SCENARIOS("initGameScenarios"),
        LOAD_CHALLENGES("loadChallenges"),
        UPDATE_PROVINCES_SIZE("updateProvincesSize"),
        UPDATE_DRAW_PROVINCES("updateDrawProvinces"),
        CHECK_LAND_BY_SEA_PROVINCES_BORDERS("checkLandBySeaProvincesBorders"),
        CHECK_SEA_BY_SEA_PROVINCES_BORDERS("checkSeaBySeaProvincesBorders"),
        BUILD_PROVINCE_BORDER("buildProvinceBorder"),
        LOAD_FONT_BORDER("loadFontBorder"),
        INIT_GAME_CITIES("initGameCities"),
        LOAD_CITIES("loadCities"),
        BUILD_PROVINCES_NAMES("buildProvincesNames"),
        LOAD_PROVINCE_NAMES_POINTS("loadProvinceNamesPoints"),
        ADD_SIMPLE_TASK("addSimpleTask"),
        LOAD_SCENARIO_1("loadScenario_1"),
        LOAD_SCENARIO_2("loadScenario_2"),
        LOAD_SCENARIO_2_FLAGS("loadScenario_2_Flags"),
        LOAD_SCENARIO_3("loadScenario_3"),
        LOAD_SCENARIO_4("loadScenario_4"),
        LOAD_SCENARIO_5("loadScenario_5"),
        LOAD_SCENARIO_6("loadScenario_6"),
        LOAD_SCENARIO_7("loadScenario_7"),
        LOAD_SCENARIO_8("loadScenario_8"),
        LOAD_SCENARIO_9("loadScenario_9"),
        LOAD_SCENARIO_10("loadScenario_10"),
        LOAD_SCENARIO_11("loadScenario_11"),
        LOAD_SCENARIO_12("loadScenario_12"),
        LOAD_SCENARIO_13("loadScenario_13"),
        LOAD_SCENARIO_14("loadScenario_14"),
        INIT_PLAYERS("initPlayers"),
        BUILD_DRAW_ARMY("buildDrawArmy"),
        BUILD_BASINS_OF_SEA_PROVINCES("buildBasinsOfSeaProvinces"),
        LOAD_PROVINCE_NAMES_ALL("loadProvinceNames_ALL"),
        LOAD_REGIONS("loadRegions"),
        INIT_GAME_MOUNTAINS("initGameMountains"),
        LOAD_MOUNTAINS("loadMountains"),
        ADD_IMG2("addIMG2"),
        LOAD_EVENT_TEMPLATES("loadEventTemplates"),
        LOAD_DIPLOMACY_COLORS_GAME_DATA("loadDiplomacyColors_GameData"),
        LOAD_RANDOM_ALLIANCES_NAMES("loadRandomAlliancesNames"),
        BUILD_COLORS("buildColors"),
        UPDATE_DRAW_MOVE_UNITS_ARMY("updateDrawMoveUnitsArmy"),
        LOAD_IMAGES("loadImages"),
        LOAD_MINIMAP("loadMinimap"),
        LOAD_DELETE_STATUS_FILE("load_DeleteStatusFile"),
        UPDATE_WORLD_MAP_SHADERS("updateWorldMap_Shaders");

        private final String method;

        Point(String method) {
            this.method = method;
        }
    }

    public enum Stage {
        PROVINCE_PATH("province.path", Point.LOAD_PROVINCE),
        PROVINCE_READ("province.read", Point.LOAD_PROVINCE),
        PROVINCE_DESERIALIZE("province.deserialize", Point.LOAD_PROVINCE),
        PROVINCE_CONSTRUCT("province.construct", Point.LOAD_PROVINCE),
        PROVINCE_REBUILD("province.rebuild", Point.LOAD_PROVINCE),
        TEXTURE_PATH("texture.path", Point.LOAD_PROVINCE_TEXTURE),
        TEXTURE_CIM("texture.cimReadDecompress", Point.LOAD_PROVINCE_TEXTURE),
        TEXTURE_UPLOAD("texture.createUpload", Point.LOAD_PROVINCE_TEXTURE),
        TEXTURE_REBUILD("texture.rebuild", Point.LOAD_PROVINCE_TEXTURE),
        CITIES_ASSIGN("cities.assign", Point.LOAD_CITIES);

        private final String name;
        private final Point parent;

        Stage(String name, Point parent) {
            this.name = name;
            this.parent = parent;
        }
    }

    private static final ThreadLocal<LoadingProfiler> CURRENT = new ThreadLocal<>();

    public enum PathSource {
        LOCAL,
        WORKSHOP,
        VANILLA
    }

    public record PathResult(
            long localProbes,
            long workshopProbes,
            long localHits,
            long workshopHits,
            long vanillaFallbacks,
            long candidateFilterSkips,
            long cacheBuildCalls,
            long cacheBuildNanos,
            long cacheBuildSuccesses,
            long cacheBuildFailures,
            long cacheRootsRetained,
            long cacheRootsExcluded,
            long originalResolverCalls,
            Integer maxProvinceId,
            PathSource maxSource,
            String maxSourceFolder) {}

    public record CitiesResult(long cityCount, long candidateProvinceChecks, long aabbPasses, long ptCSCalls) {}

    private static final class CitiesMeasurement {
        private long cityCount;
        private long candidateProvinceChecks;
        private long aabbPasses;
        private long ptCSCalls;

        private CitiesResult result() {
            return new CitiesResult(cityCount, candidateProvinceChecks, aabbPasses, ptCSCalls);
        }
    }

    private static final class PathMeasurement {
        private long localProbes;
        private long workshopProbes;
        private long localHits;
        private long workshopHits;
        private long vanillaFallbacks;
        private long candidateFilterSkips;
        private long cacheBuildNanos;
        private long cacheBuildSuccesses;
        private long cacheBuildFailures;
        private long cacheRootsRetained;
        private long cacheRootsExcluded;
        private long originalResolverCalls;
        private int provinceId = -1;
        private PathSource source;
        private String sourceFolder;
        private int maxProvinceId = -1;
        private PathSource maxSource;
        private String maxSourceFolder;

        private PathResult result() {
            return new PathResult(
                    localProbes,
                    workshopProbes,
                    localHits,
                    workshopHits,
                    vanillaFallbacks,
                    candidateFilterSkips,
                    cacheBuildSuccesses + cacheBuildFailures,
                    cacheBuildNanos,
                    cacheBuildSuccesses,
                    cacheBuildFailures,
                    cacheRootsRetained,
                    cacheRootsExcluded,
                    originalResolverCalls,
                    maxProvinceId < 0 ? null : maxProvinceId,
                    maxSource,
                    maxSourceFolder);
        }
    }

    private static final class Measurement {
        private final String method;
        // Eight subdivisions per power of two; bounded memory, no per-call samples.
        private final long[] histogram = new long[488];
        private long calls;
        private long returnedCalls;
        private long totalNanos;
        private long minNanos = Long.MAX_VALUE;
        private long maxNanos;
        private long maxCallIndex;
        private long maxFrameIndex;
        private long maxStartedOffsetNanos;
        private PathMeasurement path;
        private CitiesMeasurement cities;

        private Measurement(String method) {
            this.method = method;
        }

        private void record(long duration, long frameIndex, long startedOffsetNanos) {
            returnedCalls++;
            totalNanos += duration;
            minNanos = Math.min(minNanos, duration);
            if (returnedCalls == 1 || duration > maxNanos) {
                maxNanos = duration;
                maxCallIndex = calls;
                maxFrameIndex = frameIndex;
                maxStartedOffsetNanos = startedOffsetNanos;
                if (path != null) {
                    path.maxProvinceId = path.provinceId;
                    path.maxSource = path.source;
                    path.maxSourceFolder = path.sourceFolder;
                }
            }
            int shift = Math.max(0, 60 - Long.numberOfLeadingZeros(duration));
            histogram[(shift * 8) + (int) (duration >>> shift)]++;
        }

        private long percentileUpperBound(int percentile) {
            if (returnedCalls == 0) {
                return 0;
            }
            long rank = returnedCalls - (returnedCalls * (100 - percentile) / 100);
            long count = 0;
            for (int bucket = 0; bucket < histogram.length; bucket++) {
                count += histogram[bucket];
                if (count >= rank) {
                    int shift = Math.max(0, bucket / 8 - 1);
                    long lower = bucket < 8 ? bucket : (long) (8 + bucket % 8) << shift;
                    return Math.min(maxNanos, lower + ((1L << shift) - 1));
                }
            }
            throw new IllegalStateException("Missing loading duration samples");
        }

        private MethodResult result() {
            return new MethodResult(
                    method,
                    calls,
                    returnedCalls,
                    calls - returnedCalls,
                    totalNanos,
                    returnedCalls == 0 ? 0 : minNanos,
                    maxNanos,
                    returnedCalls == 0 ? 0 : (double) totalNanos / returnedCalls,
                    percentileUpperBound(50),
                    percentileUpperBound(95),
                    percentileUpperBound(99),
                    maxCallIndex,
                    maxFrameIndex,
                    maxStartedOffsetNanos,
                    path == null ? null : path.result(),
                    cities == null ? null : cities.result());
        }
    }

    public record MethodResult(
            String method,
            long calls,
            long returnedCalls,
            long unreturnedCalls,
            long totalNanos,
            long minNanos,
            long maxNanos,
            double averageNanos,
            long p50UpperBoundNanos,
            long p95UpperBoundNanos,
            long p99UpperBoundNanos,
            long maxCallIndex,
            long maxFrameIndex,
            long maxStartedOffsetNanos,
            PathResult path,
            CitiesResult cities) {}

    public record Accounting(
            long returnedFrameNanos,
            long recordedInvokeNanosInReturnedFrames,
            long unattributedNanosInReturnedFrames,
            long outsideReturnedFramesNanos,
            Long betweenFramesNanos,
            boolean timingComplete,
            long unreturnedInvokes,
            long unexpectedEndCalls,
            long unreturnedStages,
            long unexpectedStageEndCalls) {}

    public record Report(
            int schemaVersion,
            long startedAtEpochMillis,
            long elapsedNanos,
            LoadingMetadata metadata,
            Accounting accounting,
            MethodResult frames,
            List<MethodResult> methods,
            List<MethodResult> stages) {}

    private enum State {
        BETWEEN_FRAMES,
        IN_FRAME,
        COMPLETING_FRAME,
        FINISHED
    }

    private Measurement[] measurements;
    private Measurement[] stageMeasurements;
    private Measurement activeStage;
    private long stageStartedNanos;
    private long unexpectedStageEndCalls;
    private final LongSupplier nanoClock;
    private final Consumer<Report> reportSink;
    private final Supplier<LoadingMetadata> metadataSupplier;
    private long startedAtEpochMillis;
    private long startedNanos;
    private Measurement activeCall;
    private long callStartedNanos;
    private long frameStartedNanos;
    private long frameInvokeNanos;
    private long recordedInvokeNanosInReturnedFrames;
    private long unexpectedEndCalls;
    private State state = State.BETWEEN_FRAMES;

    public LoadingProfiler() {
        this(System::nanoTime, LoadingProfiler::export, LoadingMetadata::capture);
        FinalityLogger.info("[Monohydrate] Loading profile started (Menu_InitGame.loadAssets)");
    }

    LoadingProfiler(LongSupplier nanoClock, Consumer<Report> reportSink, Supplier<LoadingMetadata> metadataSupplier) {
        this.nanoClock = nanoClock;
        this.reportSink = reportSink;
        this.metadataSupplier = metadataSupplier;
        Point[] points = Point.values();
        measurements = new Measurement[points.length];
        for (Point point : points) {
            measurements[point.ordinal()] = new Measurement(point.method);
        }
        Stage[] stages = Stage.values();
        stageMeasurements = new Measurement[stages.length];
        for (Stage stage : stages) {
            stageMeasurements[stage.ordinal()] = new Measurement(stage.name);
            if (stage == Stage.PROVINCE_PATH || stage == Stage.TEXTURE_PATH) {
                stageMeasurements[stage.ordinal()].path = new PathMeasurement();
            }
            if (stage == Stage.CITIES_ASSIGN) {
                stageMeasurements[stage.ordinal()].cities = new CitiesMeasurement();
            }
        }
    }

    public void beginFrame() {
        if (state == State.FINISHED) {
            return;
        }
        long now = nanoClock.getAsLong();
        Measurement frames = measurements[Point.LOAD_ASSETS.ordinal()];
        if (frames.calls == 0) {
            startedAtEpochMillis = System.currentTimeMillis();
            startedNanos = now;
        }
        // A skipped RETURN has no known end time. Never time it through the next frame.
        setActiveCall(null);
        CURRENT.set(this);
        state = State.IN_FRAME;
        frameInvokeNanos = 0;
        frames.calls++;
        frameStartedNanos = now;
    }

    public void begin(Point point) {
        if (!inFrame()) {
            return;
        }
        // Direct invokes are sequential. A stale entry remains counted as unreturned.
        setActiveCall(measurements[point.ordinal()]);
        activeCall.calls++;
        callStartedNanos = nanoClock.getAsLong();
    }

    public void end(Point point) {
        if (state == State.FINISHED) {
            return;
        }
        if (!inFrame() || activeCall != measurements[point.ordinal()]) {
            unexpectedEndCalls++;
            return;
        }
        frameInvokeNanos += record(activeCall, callStartedNanos, nanoClock.getAsLong());
        setActiveCall(null);
    }

    public static void beginStage(Stage stage) {
        beginStage(stage, -1);
    }

    public static void beginPathStage(Stage stage, int provinceId) {
        if (stage == Stage.PROVINCE_PATH || stage == Stage.TEXTURE_PATH) {
            beginStage(stage, provinceId);
        }
    }

    private static void beginStage(Stage stage, int provinceId) {
        LoadingProfiler profiler = current();
        if (profiler == null || !profiler.accepts(stage)) {
            return;
        }
        // A failed invoke skips AFTER; retain its entry but never time it through recovery.
        profiler.activeStage = profiler.stageMeasurements[stage.ordinal()];
        profiler.activeStage.calls++;
        PathMeasurement path = profiler.activeStage.path;
        if (path != null) {
            path.provinceId = provinceId;
            path.source = null;
            path.sourceFolder = null;
        }
        profiler.stageStartedNanos = profiler.nanoClock.getAsLong();
    }

    public static void endStage(Stage stage) {
        LoadingProfiler profiler = current();
        if (profiler == null || !profiler.accepts(stage)) {
            return;
        }
        if (profiler.activeStage != profiler.stageMeasurements[stage.ordinal()]) {
            profiler.unexpectedStageEndCalls++;
            return;
        }
        profiler.record(profiler.activeStage, profiler.stageStartedNanos, profiler.nanoClock.getAsLong());
        profiler.activeStage = null;
    }

    public static void recordCitiesAssignment(
            long cityCount, long candidateProvinceChecks, long aabbPasses, long ptCSCalls) {
        LoadingProfiler profiler = current();
        if (profiler == null || profiler.activeStage == null || profiler.activeStage.cities == null) {
            return;
        }
        CitiesMeasurement cities = profiler.activeStage.cities;
        cities.cityCount += cityCount;
        cities.candidateProvinceChecks += candidateProvinceChecks;
        cities.aabbPasses += aabbPasses;
        cities.ptCSCalls += ptCSCalls;
    }

    public static void pathProbe(PathSource source) {
        PathMeasurement path = activePath();
        if (path == null) {
            return;
        }
        if (source == PathSource.LOCAL) {
            path.localProbes++;
        } else if (source == PathSource.WORKSHOP) {
            path.workshopProbes++;
        }
    }

    public static void pathResolved(PathSource source, String folder) {
        PathMeasurement path = activePath();
        if (path == null) {
            return;
        }
        switch (source) {
            case LOCAL -> path.localHits++;
            case WORKSHOP -> path.workshopHits++;
            case VANILLA -> path.vanillaFallbacks++;
        }
        path.source = source;
        path.sourceFolder = folder;
    }

    public static void pathFilterSkips(int count) {
        PathMeasurement path = activePath();
        if (path != null) path.candidateFilterSkips += count;
    }

    public static void pathCacheBuilt(long nanos, boolean success, int retained, int excluded) {
        PathMeasurement path = activePath();
        if (path != null) {
            path.cacheBuildNanos += nanos;
            if (success) {
                path.cacheBuildSuccesses++;
                path.cacheRootsRetained += retained;
                path.cacheRootsExcluded += excluded;
            } else {
                path.cacheBuildFailures++;
            }
        }
    }

    public static void pathOriginalResolverCall() {
        PathMeasurement path = activePath();
        if (path != null) path.originalResolverCalls++;
    }

    private static LoadingProfiler current() {
        return MonohydrateConfig.PROFILER_ENABLED ? CURRENT.get() : null;
    }

    private static PathMeasurement activePath() {
        LoadingProfiler profiler = current();
        return profiler == null || profiler.activeStage == null ? null : profiler.activeStage.path;
    }

    private boolean inFrame() {
        return state == State.IN_FRAME || state == State.COMPLETING_FRAME;
    }

    private void setActiveCall(Measurement call) {
        // Replacing or closing a parent always abandons its unreturned stage.
        activeCall = call;
        activeStage = null;
    }

    private boolean accepts(Stage stage) {
        return inFrame() && activeCall == measurements[stage.parent.ordinal()];
    }

    private long record(Measurement measurement, long started, long now) {
        long duration = now - started;
        measurement.record(duration, measurements[Point.LOAD_ASSETS.ordinal()].calls, started - startedNanos);
        return duration;
    }

    public void markComplete() {
        if (state == State.IN_FRAME) {
            state = State.COMPLETING_FRAME;
        }
    }

    public void endFrame() {
        if (state == State.FINISHED) {
            return;
        }
        if (!inFrame()) {
            unexpectedEndCalls++;
            return;
        }
        long now = nanoClock.getAsLong();
        Measurement frames = measurements[Point.LOAD_ASSETS.ordinal()];
        record(frames, frameStartedNanos, now);
        recordedInvokeNanosInReturnedFrames += frameInvokeNanos;
        setActiveCall(null);
        if (CURRENT.get() == this) {
            CURRENT.remove();
        }
        state = state == State.COMPLETING_FRAME ? State.FINISHED : State.BETWEEN_FRAMES;
        if (state == State.FINISHED) {
            Report report = report(now - startedNanos);
            measurements = null;
            stageMeasurements = null;
            reportSink.accept(report);
        }
    }

    private Report report(long elapsedNanos) {
        List<MethodResult> results = new ArrayList<>(measurements.length - 1);
        long unreturnedInvokes = 0;
        for (int i = 1; i < measurements.length; i++) {
            MethodResult result = measurements[i].result();
            results.add(result);
            unreturnedInvokes += result.unreturnedCalls();
        }
        results.sort(Comparator.comparingLong(MethodResult::totalNanos).reversed());
        List<MethodResult> stages = new ArrayList<>(stageMeasurements.length);
        long unreturnedStages = 0;
        for (Measurement measurement : stageMeasurements) {
            MethodResult result = measurement.result();
            stages.add(result);
            unreturnedStages += result.unreturnedCalls();
        }
        MethodResult frames = measurements[Point.LOAD_ASSETS.ordinal()].result();
        long outsideFrames = elapsedNanos - frames.totalNanos();
        Accounting accounting = new Accounting(
                frames.totalNanos(),
                recordedInvokeNanosInReturnedFrames,
                frames.totalNanos() - recordedInvokeNanosInReturnedFrames,
                outsideFrames,
                frames.unreturnedCalls() == 0 ? outsideFrames : null,
                frames.unreturnedCalls() == 0
                        && unreturnedInvokes == 0
                        && unexpectedEndCalls == 0
                        && unreturnedStages == 0
                        && unexpectedStageEndCalls == 0,
                unreturnedInvokes,
                unexpectedEndCalls,
                unreturnedStages,
                unexpectedStageEndCalls);
        return new Report(
                2,
                startedAtEpochMillis,
                elapsedNanos,
                metadataSupplier.get(),
                accounting,
                frames,
                List.copyOf(results),
                List.copyOf(stages));
    }

    private static void export(Report report) {
        FinalityLogger.info(String.format(
                Locale.ROOT,
                "[Monohydrate] Loading complete: elapsed=%.3f ms, loadAssets=%.3f ms, unattributed=%.3f ms, timingComplete=%s",
                report.elapsedNanos() / 1_000_000.0,
                report.frames().totalNanos() / 1_000_000.0,
                report.accounting().unattributedNanosInReturnedFrames() / 1_000_000.0,
                report.accounting().timingComplete()));
        MethodResult dataRebuild = report.stages().get(Stage.PROVINCE_REBUILD.ordinal());
        MethodResult textureRebuild = report.stages().get(Stage.TEXTURE_REBUILD.ordinal());
        FinalityLogger.info(String.format(
                Locale.ROOT,
                "[Monohydrate] Rebuild branches: province=%d (unreturned=%d, returnedTotal=%.3f ms), texture=%d (unreturned=%d, returnedTotal=%.3f ms)",
                dataRebuild.calls(),
                dataRebuild.unreturnedCalls(),
                dataRebuild.totalNanos() / 1_000_000.0,
                textureRebuild.calls(),
                textureRebuild.unreturnedCalls(),
                textureRebuild.totalNanos() / 1_000_000.0));
        for (MethodResult stage : report.stages()) {
            PathResult path = stage.path();
            if (path == null) {
                continue;
            }
            FinalityLogger.info(String.format(
                    Locale.ROOT,
                    "[Monohydrate] %s: probes(local=%d, workshop=%d), hits(local=%d, workshop=%d), vanillaFallbacks=%d, filterSkips=%d, cacheBuilds=%d (%.3f ms, successes=%d, failures=%d), cacheRoots(retained=%d, excluded=%d), originalResolverCalls=%d, max=%.3f ms (province=%s, source=%s, folder=%s)",
                    stage.method(),
                    path.localProbes(),
                    path.workshopProbes(),
                    path.localHits(),
                    path.workshopHits(),
                    path.vanillaFallbacks(),
                    path.candidateFilterSkips(),
                    path.cacheBuildCalls(),
                    path.cacheBuildNanos() / 1_000_000.0,
                    path.cacheBuildSuccesses(),
                    path.cacheBuildFailures(),
                    path.cacheRootsRetained(),
                    path.cacheRootsExcluded(),
                    path.originalResolverCalls(),
                    stage.maxNanos() / 1_000_000.0,
                    path.maxProvinceId(),
                    path.maxSource(),
                    path.maxSourceFolder()));
        }
        // The JSON retains all points; keep synchronous completion logging bounded.
        int logged = 0;
        for (MethodResult result : report.methods()) {
            if (logged == 10) {
                break;
            }
            if (result.calls() == 0) {
                continue;
            }
            FinalityLogger.info(String.format(
                    Locale.ROOT,
                    "[Monohydrate] %s: calls=%d, unreturned=%d, total=%.3f ms, p95<=%.3f ms, max=%.3f ms (frame=%d, call=%d)",
                    result.method(),
                    result.calls(),
                    result.unreturnedCalls(),
                    result.totalNanos() / 1_000_000.0,
                    result.p95UpperBoundNanos() / 1_000_000.0,
                    result.maxNanos() / 1_000_000.0,
                    result.maxFrameIndex(),
                    result.maxCallIndex()));
            logged++;
        }
        try {
            Path directory = Path.of("logs", "monohydrate");
            Files.createDirectories(directory);
            Path output = Files.createTempFile(directory, "loading-" + report.startedAtEpochMillis() + "-", ".json");
            JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
            FinalityLogger.info("[Monohydrate] Loading profile exported: " + output.toAbsolutePath());
        } catch (IOException | RuntimeException ex) {
            FinalityLogger.error("[Monohydrate] Failed to export loading profile", ex);
        }
    }
}
