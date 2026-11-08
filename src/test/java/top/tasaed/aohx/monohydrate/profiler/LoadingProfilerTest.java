package top.tasaed.aohx.monohydrate.profiler;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler.MethodResult;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler.PathResult;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler.PathSource;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler.Point;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler.Report;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler.Stage;

class LoadingProfilerTest {
    private long now;
    private final List<Report> reports = new ArrayList<>();
    private final LoadingProfiler profiler = new LoadingProfiler(() -> now, reports::add, () -> null);

    @Test
    void capturesMetadataOnlyAfterFinalFrameWithoutChargingCollectionToLoading() {
        List<Long> captures = new ArrayList<>();
        LoadingProfiler sample = new LoadingProfiler(() -> now, reports::add, () -> {
            captures.add(now);
            now += 1_000;
            return null;
        });
        now = 10;
        sample.beginFrame();
        now = 20;
        sample.endFrame();
        assertTrue(captures.isEmpty());
        now = 30;
        sample.beginFrame();
        sample.markComplete();
        assertTrue(captures.isEmpty());
        now = 40;
        sample.endFrame();
        sample.beginFrame();
        sample.markComplete();
        sample.endFrame();
        assertEquals(List.of(40L), captures);
        Report report = reports.get(0);
        assertEquals(30, report.elapsedNanos());
        assertEquals(20, report.frames().totalNanos());
        assertEquals(10L, report.accounting().betweenFramesNanos());
    }

    private Report finish(long at) {
        now = at;
        profiler.markComplete();
        profiler.endFrame();
        return reports.get(0);
    }

    private MethodResult method(Report report, String name) {
        return report.methods().stream()
                .filter(result -> result.method().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void separatesFrameBudgetFromIdleTimeAndLocatesSlowestInvocation() {
        now = 1000;
        profiler.beginFrame();
        now = 1010;
        profiler.begin(Point.LOAD_PROVINCE);
        now = 1030;
        profiler.end(Point.LOAD_PROVINCE);
        now = 1050;
        profiler.endFrame();
        now = 1100;
        profiler.beginFrame();
        now = 1105;
        profiler.begin(Point.LOAD_PROVINCE);
        now = 1135;
        profiler.end(Point.LOAD_PROVINCE);
        Report report = finish(1140);

        assertEquals(140, report.elapsedNanos());
        assertEquals(90, report.frames().totalNanos());
        assertEquals(50, report.accounting().recordedInvokeNanosInReturnedFrames());
        assertEquals(40, report.accounting().unattributedNanosInReturnedFrames());
        assertEquals(50L, report.accounting().betweenFramesNanos());
        assertTrue(report.accounting().timingComplete());
        assertFalse(report.methods().stream().anyMatch(result -> result.method().equals("loadAssets")));
        MethodResult result = method(report, "loadProvince");
        assertEquals(2, result.calls());
        assertEquals(50, result.totalNanos());
        assertEquals(25, result.averageNanos());
        assertEquals(2, result.maxCallIndex());
        assertEquals(2, result.maxFrameIndex());
        assertEquals(105, result.maxStartedOffsetNanos());
        assertEquals(1, report.frames().maxFrameIndex());
    }

    @Test
    void doesNotClassifyAnEscapingFrameAsIdleOrSubtractItsChildrenFromOtherFrames() {
        profiler.beginFrame();
        now = 5;
        profiler.begin(Point.LOAD_PROVINCE);
        now = 25;
        profiler.end(Point.LOAD_PROVINCE);
        profiler.markComplete();
        // Escaping exception skips RETURN. Next HEAD must discard the completion signal.
        now = 100;
        profiler.beginFrame();
        now = 110;
        profiler.endFrame();
        assertTrue(reports.isEmpty());
        now = 120;
        profiler.beginFrame();
        Report report = finish(130);

        assertEquals(1, report.frames().unreturnedCalls());
        assertEquals(20, report.frames().totalNanos());
        assertEquals(20, method(report, "loadProvince").totalNanos());
        assertEquals(0, report.accounting().recordedInvokeNanosInReturnedFrames());
        assertEquals(20, report.accounting().unattributedNanosInReturnedFrames());
        assertEquals(110, report.accounting().outsideReturnedFramesNanos());
        assertNull(report.accounting().betweenFramesNanos());
        assertFalse(report.accounting().timingComplete());
    }

    @Test
    void retainsUnreturnedCallsWithoutInventingExceptionDurations() {
        profiler.beginFrame();
        now = 5;
        profiler.begin(Point.LOAD_PROVINCE);
        now = 20;
        profiler.begin(Point.LOAD_PROVINCE); // First invocation threw; next one returns.
        now = 30;
        profiler.end(Point.LOAD_PROVINCE);
        now = 35;
        profiler.begin(Point.LOAD_CITIES); // This AFTER is also skipped, but the frame returns.
        Report report = finish(50);

        MethodResult province = method(report, "loadProvince");
        assertEquals(2, province.calls());
        assertEquals(1, province.returnedCalls());
        assertEquals(1, province.unreturnedCalls());
        assertEquals(10, province.totalNanos());
        assertEquals(2, province.maxCallIndex());
        MethodResult cities = method(report, "loadCities");
        assertEquals(1, cities.unreturnedCalls());
        assertEquals(0, cities.totalNanos());
        assertEquals(0, cities.minNanos());
        assertEquals(0, cities.p99UpperBoundNanos());
        assertEquals(2, report.accounting().unreturnedInvokes());
        assertEquals(40, report.accounting().unattributedNanosInReturnedFrames());
        assertEquals(0L, report.accounting().betweenFramesNanos());
        assertFalse(report.accounting().timingComplete());
    }

    @Test
    void diagnosesUnmatchedReturnsAndIgnoresAllCallbacksAfterCompletion() {
        profiler.beginFrame();
        now = 1;
        profiler.begin(Point.LOAD_PROVINCE);
        profiler.end(Point.LOAD_CITIES);
        now = 4;
        profiler.end(Point.LOAD_PROVINCE);
        Report report = finish(5);
        assertEquals(1, report.accounting().unexpectedEndCalls());
        assertFalse(report.accounting().timingComplete());
        assertEquals(3, method(report, "loadProvince").totalNanos());
        now = 100;
        profiler.beginFrame();
        profiler.begin(Point.LOAD_PROVINCE);
        profiler.end(Point.LOAD_PROVINCE);
        profiler.markComplete();
        profiler.endFrame();
        assertEquals(1, reports.size());
        assertEquals(5, report.elapsedNanos());
    }

    @Test
    void ignoresEntriesAndCompletionRequestsBetweenFramesWithoutReadingClock() {
        List<Long> clockReads = new ArrayList<>();
        LoadingProfiler sample = new LoadingProfiler(
                () -> {
                    clockReads.add(now);
                    return now;
                },
                reports::add,
                () -> null);
        sample.begin(Point.LOAD_PROVINCE);
        sample.markComplete();
        sample.end(Point.LOAD_PROVINCE);
        sample.endFrame();
        assertTrue(clockReads.isEmpty());

        now = 10;
        sample.beginFrame();
        now = 20;
        sample.endFrame();
        sample.markComplete();
        sample.begin(Point.LOAD_PROVINCE);
        LoadingProfiler.beginStage(Stage.PROVINCE_PATH);
        LoadingProfiler.endStage(Stage.PROVINCE_PATH);
        assertTrue(reports.isEmpty());

        now = 30;
        sample.beginFrame();
        sample.markComplete();
        now = 40;
        sample.endFrame();
        assertEquals(List.of(10L, 20L, 30L, 40L), clockReads);
        Report report = reports.get(0);
        assertEquals(2, report.frames().returnedCalls());
        assertEquals(20, report.frames().totalNanos());
        assertEquals(0, method(report, "loadProvince").calls());
        assertEquals(0, stage(report, "province.path").calls());
        assertEquals(2, report.accounting().unexpectedEndCalls());
    }

    @Test
    void keepsFinalFrameOpenUntilReturnAndClosesSessionBeforeExport() {
        RuntimeException exportFailure = new IllegalStateException("export failed");
        LoadingProfiler sample = new LoadingProfiler(
                () -> {
                    if (!reports.isEmpty()) {
                        throw new AssertionError("Completed session read the clock");
                    }
                    return now;
                },
                report -> {
                    reports.add(report);
                    throw exportFailure;
                },
                () -> null);
        sample.beginFrame();
        sample.markComplete();
        sample.markComplete();
        now = 2;
        sample.begin(Point.LOAD_CITIES);
        LoadingProfiler.beginStage(Stage.CITIES_ASSIGN);
        LoadingProfiler.recordCitiesAssignment(1, 2, 1, 1);
        now = 6;
        LoadingProfiler.endStage(Stage.CITIES_ASSIGN);
        sample.end(Point.LOAD_CITIES);
        now = 8;
        assertSame(exportFailure, assertThrows(IllegalStateException.class, sample::endFrame));

        sample.beginFrame();
        sample.begin(Point.LOAD_CITIES);
        LoadingProfiler.beginStage(Stage.CITIES_ASSIGN);
        LoadingProfiler.recordCitiesAssignment(10, 20, 10, 10);
        LoadingProfiler.endStage(Stage.CITIES_ASSIGN);
        sample.end(Point.LOAD_CITIES);
        sample.markComplete();
        sample.endFrame();
        assertEquals(1, reports.size());
        Report report = reports.get(0);
        assertEquals(8, report.frames().totalNanos());
        assertEquals(4, method(report, "loadCities").totalNanos());
        MethodResult cities = stage(report, "cities.assign");
        assertEquals(4, cities.totalNanos());
        assertEquals(1, cities.cities().cityCount());
        assertTrue(report.accounting().timingComplete());
    }

    @Test
    void histogramBoundsContainNearestRankPercentilesWithoutHidingOutliers() {
        profiler.beginFrame();
        for (int duration = 1; duration <= 100; duration++) {
            profiler.begin(Point.LOAD_PROVINCE);
            now += duration;
            profiler.end(Point.LOAD_PROVINCE);
        }
        Report report = finish(now);
        MethodResult result = method(report, "loadProvince");
        assertEquals(5050, result.totalNanos());
        assertEquals(51, result.p50UpperBoundNanos());
        assertEquals(95, result.p95UpperBoundNanos());
        assertEquals(100, result.p99UpperBoundNanos());
        assertEquals(100, result.maxCallIndex());
        assertEquals(4950, result.maxStartedOffsetNanos());
        assertEquals(0, report.accounting().unattributedNanosInReturnedFrames());
    }

    @Test
    void histogramHandlesZeroAndEveryPowerOfTwoBoundary() {
        List<Long> durations = new ArrayList<>();
        durations.add(0L);
        for (int power = 0; power < 63; power++) {
            long boundary = 1L << power;
            durations.add(boundary - 1);
            durations.add(boundary);
            durations.add(boundary + 1);
        }
        durations.add(Long.MAX_VALUE);
        for (long duration : durations) {
            List<Report> single = new ArrayList<>();
            LoadingProfiler sample = new LoadingProfiler(() -> now, single::add, () -> null);
            now = 0;
            sample.beginFrame();
            sample.begin(Point.LOAD_PROVINCE);
            now = duration;
            sample.end(Point.LOAD_PROVINCE);
            sample.markComplete();
            sample.endFrame();
            MethodResult result = method(single.get(0), "loadProvince");
            assertEquals(duration, result.p50UpperBoundNanos(), "duration=" + duration);
            assertEquals(duration, result.p99UpperBoundNanos(), "duration=" + duration);
        }
    }

    @Test
    void separatesStagesAndRebuildAttemptsFromOuterAccounting() {
        profiler.beginFrame();
        profiler.begin(Point.LOAD_PROVINCE_TEXTURE);
        LoadingProfiler.beginStage(Stage.TEXTURE_PATH);
        now = 2;
        LoadingProfiler.endStage(Stage.TEXTURE_PATH);
        LoadingProfiler.beginStage(Stage.TEXTURE_CIM);
        now = 5;
        // Missing/corrupt CIM throws: recovery must not become a successful CIM sample.
        LoadingProfiler.beginStage(Stage.TEXTURE_REBUILD);
        now = 55;
        LoadingProfiler.endStage(Stage.TEXTURE_REBUILD);
        LoadingProfiler.beginStage(Stage.TEXTURE_PATH);
        now = 57;
        LoadingProfiler.endStage(Stage.TEXTURE_PATH);
        LoadingProfiler.beginStage(Stage.TEXTURE_CIM);
        now = 67;
        LoadingProfiler.endStage(Stage.TEXTURE_CIM);
        LoadingProfiler.beginStage(Stage.TEXTURE_UPLOAD);
        now = 72;
        LoadingProfiler.endStage(Stage.TEXTURE_UPLOAD);
        profiler.end(Point.LOAD_PROVINCE_TEXTURE);
        Report report = finish(80);

        assertEquals(72, report.accounting().recordedInvokeNanosInReturnedFrames());
        assertEquals(8, report.accounting().unattributedNanosInReturnedFrames());
        MethodResult cim = stage(report, "texture.cimReadDecompress");
        assertEquals(2, cim.calls());
        assertEquals(1, cim.unreturnedCalls());
        assertEquals(10, cim.totalNanos());
        MethodResult rebuild = stage(report, "texture.rebuild");
        assertEquals(1, rebuild.calls());
        assertEquals(50, rebuild.totalNanos());
        assertEquals(1, report.accounting().unreturnedStages());
        assertFalse(report.accounting().timingComplete());
    }

    @Test
    void confinesStageCallbacksToParentThreadAndFrame() throws InterruptedException {
        LoadingProfiler.beginStage(Stage.PROVINCE_PATH);
        profiler.beginFrame();
        profiler.begin(Point.LOAD_PROVINCE);
        LoadingProfiler.beginStage(Stage.TEXTURE_PATH);
        Thread other = new Thread(() -> {
            LoadingProfiler.beginStage(Stage.PROVINCE_PATH);
            LoadingProfiler.endStage(Stage.PROVINCE_PATH);
        });
        other.start();
        other.join();
        LoadingProfiler.beginStage(Stage.PROVINCE_PATH);
        now = 3;
        LoadingProfiler.endStage(Stage.PROVINCE_PATH);
        profiler.end(Point.LOAD_PROVINCE);
        LoadingProfiler.beginStage(Stage.PROVINCE_READ);
        Report report = finish(5);
        LoadingProfiler.beginStage(Stage.PROVINCE_PATH);
        LoadingProfiler.endStage(Stage.PROVINCE_PATH);
        assertEquals(1, stage(report, "province.path").calls());
        assertEquals(3, stage(report, "province.path").totalNanos());
        assertEquals(0, stage(report, "province.read").calls());
        assertEquals(0, stage(report, "texture.path").calls());
        assertTrue(report.accounting().timingComplete());
    }

    @Test
    void confinesCityCountersToActiveAssignmentAndUsesLongTotals() throws InterruptedException {
        LoadingProfiler.recordCitiesAssignment(1, 1, 1, 1);
        profiler.beginFrame();
        profiler.begin(Point.INIT_GAME_CITIES);
        LoadingProfiler.beginStage(Stage.CITIES_ASSIGN);
        LoadingProfiler.recordCitiesAssignment(1, 1, 1, 1);
        profiler.end(Point.INIT_GAME_CITIES);
        profiler.begin(Point.LOAD_CITIES);
        LoadingProfiler.recordCitiesAssignment(1, 1, 1, 1);
        LoadingProfiler.beginStage(Stage.CITIES_ASSIGN);
        Thread other = new Thread(() -> LoadingProfiler.recordCitiesAssignment(1, 1, 1, 1));
        other.start();
        other.join();
        LoadingProfiler.recordCitiesAssignment(2, Integer.MAX_VALUE, 3, 2);
        now = 5;
        LoadingProfiler.endStage(Stage.CITIES_ASSIGN);
        LoadingProfiler.recordCitiesAssignment(1, 1, 1, 1);
        LoadingProfiler.beginStage(Stage.CITIES_ASSIGN);
        LoadingProfiler.recordCitiesAssignment(1, 2, 1, 1);
        now = 7;
        LoadingProfiler.endStage(Stage.CITIES_ASSIGN);
        profiler.end(Point.LOAD_CITIES);
        Report report = finish(10);
        LoadingProfiler.recordCitiesAssignment(1, 1, 1, 1);
        MethodResult result = stage(report, "cities.assign");
        assertEquals(2, result.returnedCalls());
        assertEquals(7, result.totalNanos());
        assertEquals(3, result.cities().cityCount());
        assertEquals((long) Integer.MAX_VALUE + 2, result.cities().candidateProvinceChecks());
        assertEquals(4, result.cities().aabbPasses());
        assertEquals(3, result.cities().ptCSCalls());
        assertTrue(report.accounting().timingComplete());
    }

    @Test
    void replacingUnreturnedParentDiscardsItsStageButRetainsCompletedWork() {
        profiler.beginFrame();
        profiler.begin(Point.LOAD_CITIES);
        LoadingProfiler.beginStage(Stage.CITIES_ASSIGN);
        LoadingProfiler.recordCitiesAssignment(2, 4, 3, 2);
        now = 10;
        profiler.begin(Point.LOAD_CITIES);
        LoadingProfiler.recordCitiesAssignment(100, 100, 100, 100);
        LoadingProfiler.endStage(Stage.CITIES_ASSIGN);
        now = 12;
        LoadingProfiler.beginStage(Stage.CITIES_ASSIGN);
        LoadingProfiler.recordCitiesAssignment(1, 2, 1, 1);
        now = 15;
        LoadingProfiler.endStage(Stage.CITIES_ASSIGN);
        profiler.end(Point.LOAD_CITIES);
        Report report = finish(20);

        MethodResult calls = method(report, "loadCities");
        assertEquals(2, calls.calls());
        assertEquals(1, calls.unreturnedCalls());
        assertEquals(5, calls.totalNanos());
        MethodResult cities = stage(report, "cities.assign");
        assertEquals(2, cities.calls());
        assertEquals(1, cities.unreturnedCalls());
        assertEquals(3, cities.totalNanos());
        assertEquals(2, cities.maxCallIndex());
        assertEquals(12, cities.maxStartedOffsetNanos());
        assertEquals(3, cities.cities().cityCount());
        assertEquals(6, cities.cities().candidateProvinceChecks());
        assertEquals(4, cities.cities().aabbPasses());
        assertEquals(3, cities.cities().ptCSCalls());
        assertEquals(1, report.accounting().unexpectedStageEndCalls());
        assertEquals(5, report.accounting().recordedInvokeNanosInReturnedFrames());
        assertFalse(report.accounting().timingComplete());
    }

    @Test
    void retainsFailedRebuildCountWithoutTimingThroughNextFrame() {
        profiler.beginFrame();
        profiler.begin(Point.LOAD_PROVINCE);
        LoadingProfiler.beginStage(Stage.PROVINCE_REBUILD);
        now = 100;
        profiler.beginFrame();
        profiler.begin(Point.LOAD_PROVINCE);
        LoadingProfiler.endStage(Stage.PROVINCE_REBUILD);
        LoadingProfiler.beginStage(Stage.PROVINCE_REBUILD);
        now = 110;
        LoadingProfiler.endStage(Stage.PROVINCE_REBUILD);
        profiler.end(Point.LOAD_PROVINCE);
        Report report = finish(120);
        MethodResult rebuild = stage(report, "province.rebuild");
        assertEquals(2, rebuild.calls());
        assertEquals(1, rebuild.unreturnedCalls());
        assertEquals(10, rebuild.totalNanos());
        assertEquals(2, rebuild.maxFrameIndex());
        assertEquals(2, rebuild.maxCallIndex());
        assertEquals(1, report.accounting().unexpectedStageEndCalls());
        assertFalse(report.accounting().timingComplete());
    }

    @Test
    void aggregatesProvinceAndTexturePathSearchesSeparately() {
        profiler.beginFrame();
        profiler.begin(Point.LOAD_PROVINCE);
        LoadingProfiler.beginPathStage(Stage.PROVINCE_PATH, 11);
        LoadingProfiler.pathCacheBuilt(7, true, 2, 3);
        LoadingProfiler.pathCacheBuilt(11, false, 100, 100);
        LoadingProfiler.pathProbe(PathSource.LOCAL);
        LoadingProfiler.pathResolved(PathSource.LOCAL, "province-local");
        now = 2;
        LoadingProfiler.endStage(Stage.PROVINCE_PATH);
        LoadingProfiler.beginPathStage(Stage.PROVINCE_PATH, 12);
        LoadingProfiler.pathProbe(PathSource.LOCAL);
        LoadingProfiler.pathProbe(PathSource.WORKSHOP);
        LoadingProfiler.pathProbe(PathSource.WORKSHOP);
        LoadingProfiler.pathResolved(PathSource.WORKSHOP, "province-workshop");
        now = 7;
        LoadingProfiler.endStage(Stage.PROVINCE_PATH);
        LoadingProfiler.beginPathStage(Stage.PROVINCE_PATH, 13);
        LoadingProfiler.pathProbe(PathSource.LOCAL);
        LoadingProfiler.pathProbe(PathSource.WORKSHOP);
        LoadingProfiler.pathResolved(PathSource.VANILLA, null);
        now = 10;
        LoadingProfiler.endStage(Stage.PROVINCE_PATH);
        profiler.end(Point.LOAD_PROVINCE);

        profiler.begin(Point.LOAD_PROVINCE_TEXTURE);
        LoadingProfiler.beginPathStage(Stage.TEXTURE_PATH, 21);
        LoadingProfiler.pathProbe(PathSource.LOCAL);
        LoadingProfiler.pathProbe(PathSource.LOCAL);
        LoadingProfiler.pathResolved(PathSource.LOCAL, "texture-local");
        now = 14;
        LoadingProfiler.endStage(Stage.TEXTURE_PATH);
        LoadingProfiler.beginPathStage(Stage.TEXTURE_PATH, 22);
        LoadingProfiler.pathProbe(PathSource.LOCAL);
        LoadingProfiler.pathResolved(PathSource.LOCAL, "texture-local");
        now = 17;
        LoadingProfiler.endStage(Stage.TEXTURE_PATH);
        LoadingProfiler.beginPathStage(Stage.TEXTURE_PATH, 23);
        LoadingProfiler.pathProbe(PathSource.LOCAL);
        LoadingProfiler.pathProbe(PathSource.WORKSHOP);
        LoadingProfiler.pathResolved(PathSource.VANILLA, null);
        now = 23;
        LoadingProfiler.endStage(Stage.TEXTURE_PATH);
        profiler.end(Point.LOAD_PROVINCE_TEXTURE);
        Report report = finish(25);

        PathResult province = stage(report, "province.path").path();
        assertEquals(2, province.cacheBuildCalls());
        assertEquals(1, province.cacheBuildSuccesses());
        assertEquals(1, province.cacheBuildFailures());
        assertEquals(18, province.cacheBuildNanos());
        assertEquals(2, province.cacheRootsRetained());
        assertEquals(3, province.cacheRootsExcluded());
        assertEquals(3, province.localProbes());
        assertEquals(3, province.workshopProbes());
        assertEquals(1, province.localHits());
        assertEquals(1, province.workshopHits());
        assertEquals(1, province.vanillaFallbacks());
        assertEquals(Integer.valueOf(12), province.maxProvinceId());
        assertEquals(PathSource.WORKSHOP, province.maxSource());
        assertEquals("province-workshop", province.maxSourceFolder());
        PathResult texture = stage(report, "texture.path").path();
        assertEquals(0, texture.cacheBuildCalls());
        assertEquals(4, texture.localProbes());
        assertEquals(1, texture.workshopProbes());
        assertEquals(2, texture.localHits());
        assertEquals(0, texture.workshopHits());
        assertEquals(1, texture.vanillaFallbacks());
        assertEquals(Integer.valueOf(23), texture.maxProvinceId());
        assertEquals(PathSource.VANILLA, texture.maxSource());
        assertNull(texture.maxSourceFolder());
    }

    @Test
    void keepsPathIdentityOfFirstSlowestReturnedSampleOnTies() {
        profiler.beginFrame();
        profiler.begin(Point.LOAD_PROVINCE);
        LoadingProfiler.beginPathStage(Stage.PROVINCE_PATH, 31);
        LoadingProfiler.pathResolved(PathSource.LOCAL, "first-local");
        now = 3;
        LoadingProfiler.endStage(Stage.PROVINCE_PATH);
        LoadingProfiler.beginPathStage(Stage.PROVINCE_PATH, 32);
        LoadingProfiler.pathResolved(PathSource.WORKSHOP, "slow-workshop");
        now = 11;
        LoadingProfiler.endStage(Stage.PROVINCE_PATH);
        profiler.end(Point.LOAD_PROVINCE);
        profiler.endFrame();

        now = 20;
        profiler.beginFrame();
        profiler.begin(Point.LOAD_PROVINCE);
        LoadingProfiler.beginPathStage(Stage.PROVINCE_PATH, 33);
        LoadingProfiler.pathResolved(PathSource.VANILLA, null);
        now = 28;
        LoadingProfiler.endStage(Stage.PROVINCE_PATH);
        profiler.end(Point.LOAD_PROVINCE);
        MethodResult result = stage(finish(30), "province.path");

        assertEquals(3, result.returnedCalls());
        assertEquals(19, result.totalNanos());
        assertEquals(8, result.maxNanos());
        assertEquals(2, result.maxCallIndex());
        assertEquals(1, result.maxFrameIndex());
        assertEquals(3, result.maxStartedOffsetNanos());
        assertEquals(Integer.valueOf(32), result.path().maxProvinceId());
        assertEquals(PathSource.WORKSHOP, result.path().maxSource());
        assertEquals("slow-workshop", result.path().maxSourceFolder());
    }

    @Test
    void doesNotReplaceReturnedPathMaximumWithAnUnreturnedSearch() {
        profiler.beginFrame();
        profiler.begin(Point.LOAD_PROVINCE);
        LoadingProfiler.beginPathStage(Stage.PROVINCE_PATH, 41);
        LoadingProfiler.pathProbe(PathSource.LOCAL);
        LoadingProfiler.pathResolved(PathSource.LOCAL, "returned-local");
        now = 5;
        LoadingProfiler.endStage(Stage.PROVINCE_PATH);
        LoadingProfiler.beginPathStage(Stage.PROVINCE_PATH, 42);
        LoadingProfiler.pathProbe(PathSource.WORKSHOP);
        LoadingProfiler.pathResolved(PathSource.WORKSHOP, "unreturned-workshop");
        now = 100;
        // The skipped AFTER and frame RETURN must not invent a 95-nanosecond sample.
        profiler.beginFrame();
        profiler.begin(Point.LOAD_PROVINCE);
        LoadingProfiler.beginPathStage(Stage.PROVINCE_PATH, 43);
        LoadingProfiler.pathResolved(PathSource.VANILLA, null);
        now = 102;
        LoadingProfiler.endStage(Stage.PROVINCE_PATH);
        profiler.end(Point.LOAD_PROVINCE);
        MethodResult result = stage(finish(105), "province.path");

        assertEquals(3, result.calls());
        assertEquals(2, result.returnedCalls());
        assertEquals(1, result.unreturnedCalls());
        assertEquals(7, result.totalNanos());
        assertEquals(5, result.maxNanos());
        assertEquals(1, result.maxCallIndex());
        assertEquals(1, result.maxFrameIndex());
        assertEquals(Integer.valueOf(41), result.path().maxProvinceId());
        assertEquals(PathSource.LOCAL, result.path().maxSource());
        assertEquals("returned-local", result.path().maxSourceFolder());
        assertEquals(1, result.path().localProbes());
        assertEquals(1, result.path().workshopProbes());
        assertEquals(1, result.path().localHits());
        assertEquals(1, result.path().workshopHits());
        assertEquals(1, result.path().vanillaFallbacks());
    }

    @Test
    void confinesPathCallbacksToActivePathParentThreadAndFrame() throws InterruptedException {
        LoadingProfiler.pathProbe(PathSource.LOCAL);
        LoadingProfiler.pathResolved(PathSource.LOCAL, "before-frame");
        profiler.beginFrame();
        LoadingProfiler.beginPathStage(Stage.PROVINCE_PATH, 90);
        LoadingProfiler.pathProbe(PathSource.LOCAL);
        LoadingProfiler.pathResolved(PathSource.LOCAL, "without-parent");
        profiler.begin(Point.LOAD_PROVINCE);
        LoadingProfiler.beginPathStage(Stage.TEXTURE_PATH, 91);
        LoadingProfiler.pathProbe(PathSource.WORKSHOP);
        LoadingProfiler.pathResolved(PathSource.WORKSHOP, "wrong-parent");
        LoadingProfiler.beginPathStage(Stage.PROVINCE_PATH, 51);
        Thread other = new Thread(() -> {
            LoadingProfiler.pathProbe(PathSource.LOCAL);
            LoadingProfiler.pathResolved(PathSource.LOCAL, "wrong-thread");
            LoadingProfiler.beginPathStage(Stage.PROVINCE_PATH, 92);
            LoadingProfiler.pathProbe(PathSource.WORKSHOP);
            LoadingProfiler.pathResolved(PathSource.WORKSHOP, "wrong-thread-stage");
            LoadingProfiler.endStage(Stage.PROVINCE_PATH);
        });
        other.start();
        other.join();
        LoadingProfiler.pathProbe(PathSource.LOCAL);
        LoadingProfiler.pathResolved(PathSource.LOCAL, "valid-local");
        now = 4;
        LoadingProfiler.endStage(Stage.PROVINCE_PATH);
        LoadingProfiler.pathProbe(PathSource.WORKSHOP);
        LoadingProfiler.pathResolved(PathSource.WORKSHOP, "after-stage");
        LoadingProfiler.beginStage(Stage.PROVINCE_READ);
        LoadingProfiler.pathProbe(PathSource.LOCAL);
        LoadingProfiler.pathResolved(PathSource.LOCAL, "non-path-stage");
        now = 5;
        LoadingProfiler.endStage(Stage.PROVINCE_READ);
        LoadingProfiler.beginPathStage(Stage.PROVINCE_PATH, 52);
        profiler.end(Point.LOAD_PROVINCE);
        LoadingProfiler.pathProbe(PathSource.WORKSHOP);
        LoadingProfiler.pathResolved(PathSource.WORKSHOP, "after-parent");
        profiler.begin(Point.LOAD_PROVINCE_TEXTURE);
        LoadingProfiler.pathProbe(PathSource.WORKSHOP);
        LoadingProfiler.pathResolved(PathSource.WORKSHOP, "new-parent");
        LoadingProfiler.beginPathStage(Stage.TEXTURE_PATH, 53);
        LoadingProfiler.pathProbe(PathSource.WORKSHOP);
        LoadingProfiler.pathResolved(PathSource.WORKSHOP, "valid-workshop");
        now = 8;
        LoadingProfiler.endStage(Stage.TEXTURE_PATH);
        LoadingProfiler.beginPathStage(Stage.TEXTURE_PATH, 54);
        profiler.endFrame();
        LoadingProfiler.pathProbe(PathSource.LOCAL);
        LoadingProfiler.pathResolved(PathSource.LOCAL, "after-frame");
        now = 10;
        profiler.beginFrame();
        LoadingProfiler.pathProbe(PathSource.WORKSHOP);
        LoadingProfiler.pathResolved(PathSource.WORKSHOP, "new-frame");
        Report report = finish(12);

        MethodResult provinceStage = stage(report, "province.path");
        assertEquals(2, provinceStage.calls());
        assertEquals(1, provinceStage.returnedCalls());
        PathResult province = provinceStage.path();
        assertEquals(1, province.localProbes());
        assertEquals(0, province.workshopProbes());
        assertEquals(1, province.localHits());
        assertEquals(0, province.workshopHits());
        assertEquals(Integer.valueOf(51), province.maxProvinceId());
        assertEquals(PathSource.LOCAL, province.maxSource());
        assertEquals("valid-local", province.maxSourceFolder());
        MethodResult textureStage = stage(report, "texture.path");
        assertEquals(2, textureStage.calls());
        assertEquals(1, textureStage.returnedCalls());
        PathResult texture = textureStage.path();
        assertEquals(0, texture.localProbes());
        assertEquals(1, texture.workshopProbes());
        assertEquals(0, texture.localHits());
        assertEquals(1, texture.workshopHits());
        assertEquals(Integer.valueOf(53), texture.maxProvinceId());
        assertEquals(PathSource.WORKSHOP, texture.maxSource());
        assertEquals("valid-workshop", texture.maxSourceFolder());
    }

    @Test
    void resetsPathIdentityBeforeEachStageIncludingStagesWithoutAnIdOrResolution() {
        profiler.beginFrame();
        profiler.begin(Point.LOAD_PROVINCE);
        LoadingProfiler.beginPathStage(Stage.PROVINCE_PATH, 61);
        LoadingProfiler.pathResolved(PathSource.WORKSHOP, "stale-province-folder");
        now = 2;
        LoadingProfiler.endStage(Stage.PROVINCE_PATH);
        LoadingProfiler.beginStage(Stage.PROVINCE_PATH);
        now = 7;
        LoadingProfiler.endStage(Stage.PROVINCE_PATH);
        profiler.end(Point.LOAD_PROVINCE);
        profiler.begin(Point.LOAD_PROVINCE_TEXTURE);
        LoadingProfiler.beginPathStage(Stage.TEXTURE_PATH, 62);
        LoadingProfiler.pathResolved(PathSource.LOCAL, "stale-texture-folder");
        now = 9;
        LoadingProfiler.endStage(Stage.TEXTURE_PATH);
        LoadingProfiler.beginPathStage(Stage.TEXTURE_PATH, 63);
        LoadingProfiler.pathProbe(PathSource.LOCAL);
        now = 15;
        LoadingProfiler.endStage(Stage.TEXTURE_PATH);
        profiler.end(Point.LOAD_PROVINCE_TEXTURE);
        Report report = finish(16);

        MethodResult province = stage(report, "province.path");
        assertEquals(2, province.maxCallIndex());
        assertEquals(5, province.maxNanos());
        assertEquals(1, province.path().workshopHits());
        assertNull(province.path().maxProvinceId());
        assertNull(province.path().maxSource());
        assertNull(province.path().maxSourceFolder());
        MethodResult texture = stage(report, "texture.path");
        assertEquals(2, texture.maxCallIndex());
        assertEquals(6, texture.maxNanos());
        assertEquals(1, texture.path().localProbes());
        assertEquals(1, texture.path().localHits());
        assertEquals(Integer.valueOf(63), texture.path().maxProvinceId());
        assertNull(texture.path().maxSource());
        assertNull(texture.path().maxSourceFolder());
    }

    private MethodResult stage(Report report, String name) {
        return report.stages().stream()
                .filter(result -> result.method().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
