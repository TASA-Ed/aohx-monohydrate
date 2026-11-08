package top.tasaed.aohx.monohydrate.load;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import team.rainfall.finality.luminosity2.processor.MixinProcessor;
import team.rainfall.finality.luminosity2.processor.NewFieldProcessor;
import top.tasaed.aohx.monohydrate.core.MonohydrateConfig;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler.Point;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler.Report;
import top.tasaed.aohx.monohydrate.profiler.mixins.MixinCoreProvinceProfile;

public class MixinCoreCitiesTest {
    private static Class<?> transformedClass;
    private static Field citiesField;
    private CoreFixture receiver;

    @BeforeAll
    static void transformProductionMixin() throws Exception {
        String game = "age/of/civilizations2/jakowski/lukasz/";
        Map<String, String> types = Map.of(
                game + "CFG",
                Type.getInternalName(GlobalsFixture.class),
                game + "City",
                Type.getInternalName(CityFixture.class),
                game + "Province",
                Type.getInternalName(ProvinceFixture.class),
                game + "Core/Core",
                Type.getInternalName(CoreFixture.class),
                game + "Map",
                Type.getInternalName(MapFixture.class),
                game + "MapBG",
                Type.getInternalName(MapBackgroundFixture.class),
                Type.getInternalName(MonohydrateConfig.class),
                Type.getInternalName(ConfigFixture.class));
        ClassNode source = remap(readClass(MixinCoreProvinceProfile.class), types);
        String targetName = "top/tasaed/aohx/monohydrate/load/TransformedCoreCities";
        ClassNode target =
                remap(readClass(TargetFixture.class), Map.of(Type.getInternalName(TargetFixture.class), targetName));
        target.nestHostClass = null;
        target.innerClasses.clear();

        // Run the loader's real processors, not a Java copy of the assignment algorithm.
        new MixinProcessor(source, target).process();
        new NewFieldProcessor(source, target).process();

        ClassWriter writer = new ClassWriter(0);
        target.accept(writer);
        transformedClass = new FixtureLoader().define(targetName.replace('/', '.'), writer.toByteArray());
        citiesField = transformedClass.getDeclaredField("lCities");
        citiesField.setAccessible(true);
    }

    @BeforeEach
    void prepareReceiver() throws Exception {
        receiver = (CoreFixture) transformedClass.getConstructor().newInstance();
        GlobalsFixture.core = receiver;
        GlobalsFixture.map = new MapFixture();
        ConfigFixture.CITY_OPTIMIZATIONS_ENABLED = true;
    }

    @AfterEach
    void releaseGlobals() {
        GlobalsFixture.core = null;
        GlobalsFixture.map = null;
        ConfigFixture.CITY_OPTIMIZATIONS_ENABLED = true;
    }

    @Test
    void overlappingProvincesKeepFirstMatchReverseOrderAndIdentity() throws Exception {
        ProvinceFixture first = province(0, 20, 0, 20, true);
        ProvinceFixture overlap = province(0, 20, 0, 20, true);
        receiver.provinces.addAll(List.of(first, overlap));
        receiver.bound = 2;
        CityFixture earlier = new CityFixture(3, 4);
        CityFixture later = new CityFixture(4, 5);
        CityFixture unmatched = new CityFixture(50, 50);
        CountingCities cities = new CountingCities(List.of(earlier, later, unmatched));

        assign(cities);

        assertEquals(List.of(later, earlier), first.cities);
        assertSame(later, first.cities.get(0));
        assertSame(earlier, first.cities.get(1));
        assertTrue(overlap.cities.isEmpty());
        assertEquals(List.of("0:8:10", "0:6:8"), receiver.polygonCalls);
    }

    @Test
    void includesAllFourScaledBoundingBoxCorners() throws Exception {
        ProvinceFixture province = province(4, 12, 6, 14, true);
        receiver.provinces.add(province);
        receiver.bound = 1;
        List<CityFixture> corners =
                List.of(new CityFixture(2, 3), new CityFixture(6, 7), new CityFixture(2, 7), new CityFixture(6, 3));

        assign(new CountingCities(corners));

        assertEquals(List.of(corners.get(3), corners.get(2), corners.get(1), corners.get(0)), province.cities);
        assertEquals(List.of("0:12:6", "0:4:14", "0:12:14", "0:4:6"), receiver.polygonCalls);
    }

    @Test
    void polygonRejectionContinuesForwardAndUsesReceiverRatherThanGlobalCore() throws Exception {
        ProvinceFixture rejected = province(0, 20, 0, 20, false);
        ProvinceFixture accepted = province(0, 20, 0, 20, true);
        receiver.provinces.addAll(List.of(rejected, accepted));
        GlobalsFixture.core = new CoreFixture();
        GlobalsFixture.core.bound = 2;
        CityFixture city = new CityFixture(3, 4);

        assign(new CountingCities(List.of(city)));

        assertTrue(rejected.cities.isEmpty());
        assertEquals(List.of(city), accepted.cities);
        assertEquals(List.of("0:6:8", "1:6:8"), receiver.polygonCalls);
        assertTrue(GlobalsFixture.core.polygonCalls.isEmpty());
    }

    @Test
    void usesGlobalProvinceCountAndDropsUnmatchedCity() throws Exception {
        receiver.provinces.addAll(List.of(province(0, 2, 0, 2, true), province(0, 20, 0, 20, true)));
        receiver.bound = 2;
        GlobalsFixture.core = new CoreFixture();
        GlobalsFixture.core.bound = 1;

        assign(new CountingCities(List.of(new CityFixture(3, 4))));

        assertTrue(receiver.provinces.stream().allMatch(province -> province.cities.isEmpty()));
        assertTrue(receiver.polygonCalls.isEmpty());
    }

    @Test
    void clearsAndReleasesAnEmptyCityList() throws Exception {
        assign(new CountingCities(List.of()));
        assertEquals(0, receiver.provinceReads);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void includesGridEdgesButStillRejectsAabbMissesInTheSameCell(boolean optimized) throws Exception {
        ConfigFixture.CITY_OPTIMIZATIONS_ENABLED = optimized;
        // The total extent gives 64-unit cells, with (0, 0) exactly on two grid edges.
        receiver.provinces.addAll(List.of(
                province(-128, 0, -128, 0, false),
                province(-128, 0, -128, 0, true),
                province(0, 0, 0, 0, true),
                province(2, 4, 2, 4, true),
                province(1918, 1919, 1918, 1919, true)));
        receiver.bound = receiver.provinces.size();
        CityFixture edge = new CityFixture(0, 0);
        CityFixture corner = new CityFixture(-64, -64);
        CityFixture sameCellMiss = new CityFixture(3, 3);
        CityFixture emptyCell = new CityFixture(256, 256);
        CityFixture outside = new CityFixture(-65, 0);

        assign(new CountingCities(List.of(edge, corner, sameCellMiss, emptyCell, outside)));

        assertEquals(List.of(corner, edge), receiver.provinces.get(1).cities);
        assertEquals(List.of("0:-128:-128", "1:-128:-128", "0:0:0", "1:0:0"), receiver.polygonCalls);
        for (int id : new int[] {0, 2, 3, 4}) {
            assertTrue(receiver.provinces.get(id).cities.isEmpty());
        }
    }

    @Test
    void matchesOriginalScanAcrossNegativeDegenerateAndExtremeBounds() throws Exception {
        java.util.Random random = new java.util.Random(782347L);
        for (int scenario = 0; scenario < 40; scenario++) {
            prepareReceiver();
            receiver.provinces.addAll(List.of(
                    province(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, true),
                    province(Integer.MAX_VALUE - 1, Integer.MAX_VALUE, Integer.MAX_VALUE - 1, Integer.MAX_VALUE, true),
                    province(0, 0, 0, 0, true),
                    province(10, -10, -10, 10, true)));
            for (int id = 0; id < 40; id++) {
                int x = random.nextInt(2048) - 1024;
                int y = random.nextInt(2048) - 1024;
                receiver.provinces.add(province(x, x + random.nextInt(512), y, y + random.nextInt(512), id % 3 != 0));
            }
            receiver.bound = receiver.provinces.size();
            List<CityFixture> cities = new ArrayList<>(List.of(
                    new CityFixture(Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2),
                    new CityFixture(Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2),
                    new CityFixture(0, 0)));
            for (int i = 0; i < 100; i++) {
                cities.add(new CityFixture(random.nextInt(1536) - 768, random.nextInt(1536) - 768));
            }
            ConfigFixture.CITY_OPTIMIZATIONS_ENABLED = false;
            assign(new CountingCities(cities));
            List<String> originalCalls = List.copyOf(receiver.polygonCalls);
            List<List<CityFixture>> originalAssignments = receiver.provinces.stream()
                    .map(province -> List.copyOf(province.cities))
                    .toList();
            receiver.polygonCalls.clear();
            receiver.provinces.forEach(province -> province.cities.clear());

            ConfigFixture.CITY_OPTIMIZATIONS_ENABLED = true;
            assign(new CountingCities(cities));

            assertEquals(originalCalls, receiver.polygonCalls);
            assertEquals(
                    originalAssignments,
                    receiver.provinces.stream().map(province -> province.cities).toList());
        }
    }

    @Test
    void clearsUnmatchedCitiesWhenThereAreNoProvinces() throws Exception {
        assign(new CountingCities(List.of(new CityFixture(1, 2))));
        assertTrue(receiver.polygonCalls.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void profilesAssignmentWithoutReplacingParentAndAccumulatesCalls(boolean optimized) throws Exception {
        ConfigFixture.CITY_OPTIMIZATIONS_ENABLED = optimized;
        receiver.provinces.addAll(List.of(province(0, 20, 0, 20, false), province(0, 20, 0, 20, true)));
        receiver.bound = 2;
        List<Report> reports = new ArrayList<>();
        LoadingProfiler profiler = profiler(reports);
        profiler.beginFrame();
        profiler.begin(Point.LOAD_CITIES);
        assign(new CountingCities(List.of(new CityFixture(3, 4), new CityFixture(50, 50))));
        assign(new CountingCities(List.of(new CityFixture(4, 5))));
        assign(new CountingCities(List.of()));
        profiler.end(Point.LOAD_CITIES);
        profiler.markComplete();
        profiler.endFrame();
        Report report = reports.get(0);
        var stage = report.stages().stream()
                .filter(s -> s.method().equals("cities.assign"))
                .findFirst()
                .orElseThrow();
        assertEquals(3, stage.calls());
        assertEquals(3, stage.returnedCalls());
        assertEquals(3, stage.cities().cityCount());
        assertEquals(optimized ? 4 : 6, stage.cities().candidateProvinceChecks());
        assertEquals(4, stage.cities().aabbPasses());
        assertEquals(4, stage.cities().ptCSCalls());
        assertEquals(2, receiver.provinces.get(1).cities.size());
        var parent = report.methods().stream()
                .filter(m -> m.method().equals("loadCities"))
                .findFirst()
                .orElseThrow();
        assertEquals(1, parent.returnedCalls());
        assertTrue(report.accounting().timingComplete());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void retainsPartialCountersWithoutCompletingFailedAssignment(boolean optimized) throws Exception {
        ConfigFixture.CITY_OPTIMIZATIONS_ENABLED = optimized;
        receiver.provinces.add(province(0, 20, 0, 20, true));
        receiver.bound = 1;
        receiver.polygonFailure = new IllegalStateException("polygon failed");
        CountingCities cities = new CountingCities(List.of(new CityFixture(3, 4), new CityFixture(5, 6)));
        citiesField.set(receiver, cities);
        List<Report> reports = new ArrayList<>();
        LoadingProfiler profiler = profiler(reports);
        profiler.beginFrame();
        profiler.begin(Point.LOAD_CITIES);
        var failure = assertThrows(
                InvocationTargetException.class,
                () -> transformedClass.getMethod("buildCitiesProvinceID").invoke(receiver));
        assertSame(receiver.polygonFailure, failure.getCause());
        assertSame(cities, citiesField.get(receiver));
        assertEquals(0, cities.clears);
        // The parent catches the failure and retries; the abandoned stage must remain unreturned.
        receiver.polygonFailure = null;
        assign(new CountingCities(List.of(new CityFixture(3, 4))));
        profiler.end(Point.LOAD_CITIES);
        profiler.markComplete();
        profiler.endFrame();
        Report report = reports.get(0);
        var stage = report.stages().stream()
                .filter(s -> s.method().equals("cities.assign"))
                .findFirst()
                .orElseThrow();
        assertEquals(2, stage.calls());
        assertEquals(1, stage.returnedCalls());
        assertEquals(1, stage.unreturnedCalls());
        assertEquals(1, stage.totalNanos());
        assertEquals(3, stage.cities().cityCount());
        assertEquals(2, stage.cities().candidateProvinceChecks());
        assertEquals(2, stage.cities().aabbPasses());
        assertEquals(2, stage.cities().ptCSCalls());
        assertEquals(1, report.accounting().unreturnedStages());
        assertEquals(0, report.accounting().unexpectedEndCalls());
        assertEquals(0, report.accounting().unexpectedStageEndCalls());
        assertFalse(report.accounting().timingComplete());
    }

    private static LoadingProfiler profiler(List<Report> reports) throws Exception {
        var constructor =
                LoadingProfiler.class.getDeclaredConstructor(LongSupplier.class, Consumer.class, Supplier.class);
        constructor.setAccessible(true);
        long[] now = {0};
        return constructor.newInstance(
                (LongSupplier) () -> now[0]++, (Consumer<Report>) reports::add, (Supplier<Object>) () -> null);
    }

    public static class ConfigFixture {
        public static boolean CITY_OPTIMIZATIONS_ENABLED = true;
        public static boolean PROFILER_ENABLED = true;
    }

    private void assign(CountingCities cities) throws Exception {
        citiesField.set(receiver, cities);
        transformedClass.getMethod("buildCitiesProvinceID").invoke(receiver);
        assertTrue(cities.isEmpty());
        assertEquals(1, cities.clears);
        assertNull(citiesField.get(receiver));
    }

    private static ProvinceFixture province(int minX, int maxX, int minY, int maxY, boolean contains) {
        return new ProvinceFixture(minX, maxX, minY, maxY, contains);
    }

    private static ClassNode readClass(Class<?> type) throws IOException {
        try (InputStream input = type.getResourceAsStream("/" + Type.getInternalName(type) + ".class")) {
            assertNotNull(input);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static ClassNode remap(ClassNode original, Map<String, String> types) {
        ClassNode result = new ClassNode();
        original.accept(new ClassRemapper(result, new SimpleRemapper(types)));
        return result;
    }

    private static class FixtureLoader extends ClassLoader {
        FixtureLoader() {
            super(MixinCoreCitiesTest.class.getClassLoader());
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    // Only game dependencies are substituted; the production overwrite is transformed unchanged.
    public static class GlobalsFixture {
        public static CoreFixture core;
        public static MapFixture map;
    }

    public static class MapFixture {
        public MapBackgroundFixture getMpB() {
            return BACKGROUND;
        }

        private static final MapBackgroundFixture BACKGROUND = new MapBackgroundFixture();
    }

    public static class MapBackgroundFixture {
        public int getMapSc3() {
            return 2;
        }
    }

    public static class CityFixture {
        final int x;
        final int y;
        int xReads;
        int yReads;

        CityFixture(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getPoX() {
            xReads++;
            return x;
        }

        public int getPosY() {
            yReads++;
            return y;
        }
    }

    public static class ProvinceFixture {
        final int minX;
        final int maxX;
        final int minY;
        final int maxY;
        final boolean contains;
        final List<CityFixture> cities = new ArrayList<>();

        ProvinceFixture(int minX, int maxX, int minY, int maxY, boolean contains) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.contains = contains;
        }

        public int getMiX2() {
            return minX;
        }

        public int getMaX7() {
            return maxX;
        }

        public int getMiY4() {
            return minY;
        }

        public int getMaY6() {
            return maxY;
        }

        public void addCity(CityFixture city) {
            cities.add(city);
        }
    }

    public static class CoreFixture {
        final List<ProvinceFixture> provinces = new ArrayList<>();
        final List<String> polygonCalls = new ArrayList<>();
        int bound;
        int provinceReads;
        RuntimeException polygonFailure;

        public ProvinceFixture getProv(int id) {
            provinceReads++;
            return provinces.get(id);
        }

        public int getProvinSize() {
            return bound;
        }

        public boolean ptCS(int id, int x, int y) {
            polygonCalls.add(id + ":" + x + ":" + y);
            if (polygonFailure != null) {
                throw polygonFailure;
            }
            return provinces.get(id).contains;
        }
    }

    public static class TargetFixture extends CoreFixture {
        private List<CityFixture> lCities;

        public void buildCitiesProvinceID() {
            throw new AssertionError("Overwrite was not installed");
        }

        @Override
        public ProvinceFixture getProv(int id) {
            return super.getProv(id);
        }

        @Override
        public boolean ptCS(int id, int x, int y) {
            return super.ptCS(id, x, y);
        }
    }

    private static class CountingCities extends ArrayList<CityFixture> {
        int reads;
        int clears;

        CountingCities(List<CityFixture> cities) {
            super(cities);
        }

        @Override
        public CityFixture get(int index) {
            reads++;
            return super.get(index);
        }

        @Override
        public void clear() {
            clears++;
            super.clear();
        }
    }
}
