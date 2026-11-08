package top.tasaed.aohx.monohydrate.load;

import static org.junit.jupiter.api.Assertions.*;

import age.of.civilizations2.jakowski.lukasz.Files.FileManager;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Files.FileType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.rainfall.finality.FinalityLogger;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler.Point;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler.Stage;
import top.tasaed.aohx.monohydrate.profiler.MixinFileManagerProfileTest;
import top.tasaed.aohx.monohydrate.profiler.MixinFileManagerProfileTest.Mods;
import top.tasaed.aohx.monohydrate.profiler.MixinFileManagerProfileTest.Resolver;
import top.tasaed.aohx.monohydrate.profiler.MixinFileManagerProfileTest.Workshop;

class ProvinceCandidatesTest {
    private static final String DATA = "map/test/data/provinces/";
    private static final String OCEAN = "map/test/data/scales/provinces/1/";
    private static final String LAND = "map/test/data/scales/provinces/4/";
    private static final String[] LOCAL = {"mods/first/", "mods/second/"};

    @TempDir
    Path directory;

    private Files previousFiles;
    private Backend backend;
    private String[] workshop;
    private FileManager.LoadInterface original;
    private int delegated;
    private OutputStreamWriter previousLog;
    private final ByteArrayOutputStream log = new ByteArrayOutputStream();

    @BeforeEach
    void setup() throws Exception {
        previousFiles = Gdx.files;
        previousLog = FinalityLogger.logStream;
        FinalityLogger.logStream = new OutputStreamWriter(log, StandardCharsets.UTF_8);
        backend = new Backend(directory);
        Gdx.files = backend;
        workshop = new String[] {
            directory.resolve("workshop/first").toString(),
            directory.resolve("workshop/second").toString()
        };
        Mods.sUF = new ArrayList<>(List.of(LOCAL));
        Mods.sUFS = LOCAL.length;
        Mods.sUII = new ArrayList<>(List.of(new Workshop(workshop[0]), new Workshop(workshop[1])));
        Mods.sUIIS = workshop.length;
        useOriginal(false);
    }

    @AfterEach
    void restoreFiles() {
        Gdx.files = previousFiles;
        FinalityLogger.logStream = previousLog;
    }

    @Test
    void desktopKeepsLocalThenWorkshopOrderAndContinuesPastDirectoriesWithoutTheFile() throws Exception {
        backend.put(FileType.Internal, LOCAL[0] + DATA + "7");
        for (int id = 7; id <= 8; id++) backend.put(FileType.Internal, LOCAL[1] + DATA + id);
        for (int id = 7; id <= 9; id++) backend.put(FileType.Absolute, workshop[0] + "/" + DATA + id);
        for (int id = 7; id <= 10; id++) backend.put(FileType.Absolute, workshop[1] + "/" + DATA + id);
        ProvinceCandidates candidates = candidates(false);

        assertResolution(candidates, DATA + "7", FileType.Internal, LOCAL[0] + DATA + "7");
        assertResolution(candidates, DATA + "8", FileType.Internal, LOCAL[1] + DATA + "8");
        assertResolution(candidates, DATA + "9", FileType.Absolute, workshop[0] + "/" + DATA + "9");
        assertResolution(candidates, DATA + "10", FileType.Absolute, workshop[1] + "/" + DATA + "10");
        assertResolution(candidates, DATA + "11", FileType.Internal, DATA + "11");
        assertEquals(0, delegated, "target paths should use the known candidate directories");
    }

    @Test
    void macInterleavesExternalAndInternalPerModBeforeWorkshopAndVanilla() throws Exception {
        useOriginal(true);
        FileType[] types = {
            FileType.External, FileType.Internal, FileType.External, FileType.Internal,
            FileType.Absolute, FileType.Absolute, FileType.External, FileType.Internal
        };
        String[] prefixes = {LOCAL[0], LOCAL[0], LOCAL[1], LOCAL[1], workshop[0] + "/", workshop[1] + "/", "", ""};
        for (int root = 0; root < types.length; root++) {
            for (int id = 0; id <= root; id++) backend.put(types[root], prefixes[root] + DATA + id);
        }
        ProvinceCandidates candidates = candidates(true);
        for (int id = 0; id < types.length; id++) {
            assertResolution(candidates, DATA + id, types[id], prefixes[id] + DATA + id);
        }
        assertResolution(candidates, DATA + "8", FileType.Internal, DATA + "8");
        assertEquals(0, delegated);
    }

    @Test
    void delegatesUnrelatedMapsAndNonNumericOrMalformedProvincePathsWithoutProbing() throws Exception {
        ProvinceCandidates candidates = candidates(false);
        FileHandle sentinel = backend.local("custom-result");
        List<String> forwarded = new ArrayList<>();
        FileManager.LoadInterface custom = new FileManager.LoadInterface() {
            @Override
            public FileHandle loadFile(String path) {
                forwarded.add(path);
                return sentinel;
            }

            @Override
            public FileHandle getSaveType(String path) {
                throw new AssertionError("Unexpected save lookup");
            }
        };
        String[] paths = {
            "map/test/data/capitals/7",
            "map/other/data/provinces/7",
            "map/test2/data/provinces/7",
            DATA,
            DATA + "-1",
            DATA + "7.json",
            DATA + "7/8",
            DATA + "\u0667",
            "map/test/data/scales/provinces/",
            "map/test/data/scales/provinces/ocean/7",
            "map/test/data/scales/provinces/1/",
            OCEAN + "x",
            OCEAN + "7/8",
            "map/other/army_boxes/7",
            "map/test/civs_template/",
            "map/test/civs_template/.",
            "map/test/civs_template/..",
            "map/test/civs_template/../fra",
            "map/test/formable_civs/nested/fra",
            "map/test/provinces/..\\7.png",
            "map/test/suggested_owners/C:7",
            "map/test/other/army_boxes/7",
            null
        };
        for (String path : paths) assertSame(sentinel, candidates.load(path, custom));
        assertEquals(java.util.Arrays.asList(paths), forwarded);
        assertEquals(0, backend.probes);
    }

    @Test
    void cachesOceanAndEachLandScaleIndependentlyFromProvinceData() throws Exception {
        String otherScale = "map/test/data/scales/provinces/8/";
        backend.put(FileType.Internal, LOCAL[0] + DATA + "7");
        backend.put(FileType.Internal, LOCAL[1] + OCEAN + "7");
        backend.put(FileType.Absolute, workshop[0] + "/" + LAND + "7");
        backend.put(FileType.Absolute, workshop[1] + "/" + otherScale + "7");
        ProvinceCandidates candidates = candidates(false);

        for (int pass = 0; pass < 2; pass++) {
            assertResolution(candidates, OCEAN + "7", FileType.Internal, LOCAL[1] + OCEAN + "7");
            assertResolution(candidates, LAND + "7", FileType.Absolute, workshop[0] + "/" + LAND + "7");
            assertResolution(candidates, otherScale + "7", FileType.Absolute, workshop[1] + "/" + otherScale + "7");
            assertResolution(candidates, DATA + "7", FileType.Internal, LOCAL[0] + DATA + "7");
        }
        assertEquals(0, delegated);
    }

    @Test
    void laterRequestsProbeFewerFilesWithoutCachingIndividualHitsOrMisses() throws Exception {
        backend.put(FileType.Internal, LOCAL[1] + DATA + "99");
        backend.put(FileType.Absolute, workshop[1] + "/" + DATA + "7");
        backend.put(FileType.Absolute, workshop[1] + "/" + DATA + "8");
        ProvinceCandidates candidates = candidates(false);
        candidates.load(DATA + "7", original);

        backend.probes = 0;
        FileHandle expected = original.loadFile(DATA + "8");
        int originalProbes = backend.probes;
        delegated = 0;
        backend.probes = 0;
        assertHandle(expected, candidates.load(DATA + "8", original));
        assertEquals(2, backend.probes, "only directories that exist should cause file existence probes");
        assertTrue(backend.probes < originalProbes);
        assertEquals(0, delegated);

        // New files in an already-known directory must be visible without invalidation.
        assertResolution(candidates, DATA + "9", FileType.Internal, DATA + "9");
        backend.put(FileType.Internal, LOCAL[1] + DATA + "9");
        assertResolution(candidates, DATA + "9", FileType.Internal, LOCAL[1] + DATA + "9");
        java.nio.file.Files.delete(backend.physical(FileType.Internal, LOCAL[1] + DATA + "9"));
        assertResolution(candidates, DATA + "9", FileType.Internal, DATA + "9");
    }

    @Test
    void emptyCandidateSetIsASuccessfulCacheRatherThanPermanentDelegation() throws Exception {
        ProvinceCandidates candidates = candidates(false);
        List<LoadingProfiler.Report> reports = new ArrayList<>();
        LoadingProfiler profiler = profiler(reports);
        profiler.beginFrame();
        profiler.begin(Point.LOAD_PROVINCE);
        for (int id = 7; id <= 8; id++) {
            LoadingProfiler.beginPathStage(Stage.PROVINCE_PATH, id);
            assertHandle(backend.internal(DATA + id), candidates.load(DATA + id, original));
            LoadingProfiler.endStage(Stage.PROVINCE_PATH);
        }
        profiler.end(Point.LOAD_PROVINCE);
        profiler.markComplete();
        profiler.endFrame();
        var path = reports.get(0).stages().get(0).path();
        assertEquals(1, path.cacheBuildCalls());
        assertEquals(1, path.cacheBuildSuccesses());
        assertEquals(0, path.cacheBuildFailures());
        assertEquals(0, path.cacheRootsRetained());
        assertEquals(4, path.cacheRootsExcluded());
        assertEquals(8, path.candidateFilterSkips());
        assertEquals(0, path.localProbes());
        assertEquals(0, path.workshopProbes());
        assertEquals(2, path.vanillaFallbacks());
        assertEquals(0, path.originalResolverCalls());
        assertEquals(0, delegated);
    }

    @Test
    void profilerCountsOnlyFilteredRootsReachedBeforeTheWinningCandidate() throws Exception {
        backend.put(FileType.Internal, LOCAL[1] + DATA + "7");
        ProvinceCandidates candidates = candidates(false);
        List<LoadingProfiler.Report> reports = new ArrayList<>();
        LoadingProfiler profiler = profiler(reports);
        profiler.beginFrame();
        profiler.begin(Point.LOAD_PROVINCE);
        for (int i = 0; i < 2; i++) {
            LoadingProfiler.beginPathStage(Stage.PROVINCE_PATH, 7);
            assertHandle(backend.internal(LOCAL[1] + DATA + "7"), candidates.load(DATA + "7", original));
            LoadingProfiler.endStage(Stage.PROVINCE_PATH);
        }
        profiler.end(Point.LOAD_PROVINCE);
        profiler.markComplete();
        profiler.endFrame();
        var path = reports.get(0).stages().get(0).path();
        assertEquals(2, path.candidateFilterSkips());
        assertEquals(2, path.localProbes());
        assertEquals(2, path.localHits());
        assertEquals(0, path.workshopProbes());
        assertEquals(1, path.cacheBuildCalls());
        assertEquals(1, path.cacheBuildSuccesses());
        assertEquals(0, path.cacheBuildFailures());
        assertEquals(1, path.cacheRootsRetained());
        assertEquals(3, path.cacheRootsExcluded());
        assertEquals(0, path.originalResolverCalls());
    }

    @Test
    void failedBuildsDiscardPartialRootsAndCountEveryDelegationWithoutRepeatedDiagnostics() throws Exception {
        backend.put(FileType.Internal, LOCAL[0] + DATA + "7");
        ProvinceCandidates candidates = candidates(false);
        backend.failingDirectory = LOCAL[1] + DATA;
        List<LoadingProfiler.Report> reports = new ArrayList<>();
        LoadingProfiler profiler = profiler(reports);
        profiler.beginFrame();
        profiler.begin(Point.LOAD_PROVINCE);
        for (int id : new int[] {7, 8}) {
            LoadingProfiler.beginPathStage(Stage.PROVINCE_PATH, id);
            assertHandle(original.loadFile(DATA + id), candidates.load(DATA + id, original));
            LoadingProfiler.endStage(Stage.PROVINCE_PATH);
        }
        backend.failingDirectory = LOCAL[0] + OCEAN;
        LoadingProfiler.beginPathStage(Stage.PROVINCE_PATH, 7);
        assertHandle(original.loadFile(OCEAN + "7"), candidates.load(OCEAN + "7", original));
        LoadingProfiler.endStage(Stage.PROVINCE_PATH);
        profiler.end(Point.LOAD_PROVINCE);
        profiler.markComplete();
        profiler.endFrame();
        var path = reports.get(0).stages().get(0).path();
        assertEquals(2, path.cacheBuildCalls());
        assertEquals(0, path.cacheBuildSuccesses());
        assertEquals(2, path.cacheBuildFailures());
        assertEquals(0, path.cacheRootsRetained());
        assertEquals(0, path.cacheRootsExcluded());
        assertEquals(3, path.originalResolverCalls());
        String diagnostic = log.toString(StandardCharsets.UTF_8);
        assertEquals(
                1, diagnostic.lines().filter(line -> line.startsWith("[Error]")).count());
        assertTrue(diagnostic.contains(LOCAL[1]));
        assertTrue(diagnostic.contains("SecurityException"));
        assertFalse(diagnostic.contains(OCEAN));
    }

    private static LoadingProfiler profiler(List<LoadingProfiler.Report> reports) throws Exception {
        var constructor = LoadingProfiler.class.getDeclaredConstructor(
                java.util.function.LongSupplier.class,
                java.util.function.Consumer.class,
                java.util.function.Supplier.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                (java.util.function.LongSupplier) System::nanoTime,
                (java.util.function.Consumer<LoadingProfiler.Report>) reports::add,
                (java.util.function.Supplier<?>) () -> null);
    }

    @Test
    void saveInvalidationStaysDelegatedAcrossReadsBeforeTheHandleIsWritten() throws Exception {
        ProvinceCandidates candidates = candidates(false);
        assertResolution(candidates, DATA + "7", FileType.Internal, DATA + "7");
        String savePath = LOCAL[0] + DATA + "7";
        FileHandle pendingSave = original.getSaveType(savePath);
        candidates.invalidate(savePath);

        delegated = 0;
        assertHandle(backend.internal(DATA + "7"), candidates.load(DATA + "7", original));
        assertEquals(1, delegated, "a read between save-handle acquisition and writing must delegate");
        pendingSave.writeString("saved after the intervening read", false, "UTF-8");
        assertHandle(backend.internal(savePath), candidates.load(DATA + "7", original));
        assertEquals(2, delegated);
        assertEquals(
                "saved after the intervening read",
                candidates.load(DATA + "7", original).readString("UTF-8"));

        candidates.invalidateData();
        candidates.invalidateTextures();
        delegated = 0;
        assertHandle(backend.internal(savePath), candidates.load(DATA + "7", original));
        assertEquals(1, delegated, "rebuild invalidation must not undo a pending-write directory's delegation");
    }

    @Test
    void saveBeforeFirstReadPermanentlyDisablesOnlyItsDirectory() throws Exception {
        ProvinceCandidates candidates = candidates(false);
        for (String parent : List.of(DATA, OCEAN, LAND, "map/test/provinces/")) {
            String path = parent + "7";
            FileHandle pending = original.getSaveType(LOCAL[0] + path);
            candidates.invalidate(LOCAL[0] + path);
            candidates.invalidateData();
            candidates.invalidateTextures();
            delegated = 0;
            assertHandle(backend.internal(path), candidates.load(path, original));
            assertEquals(1, delegated);
            pending.writeString("delayed write", false, "UTF-8");
            assertEquals("delayed write", candidates.load(path, original).readString("UTF-8"));
            assertEquals(2, delegated);
        }
        assertResolution(candidates, "map/test/army_boxes/7", FileType.Internal, "map/test/army_boxes/7");
    }

    @Test
    void dataAndTextureInvalidationsRebuildOnlyTheirOwnPreviouslyMissingDirectories() throws Exception {
        ProvinceCandidates candidates = candidates(false);
        for (String parent : List.of(DATA, OCEAN, LAND)) {
            assertResolution(candidates, parent + "7", FileType.Internal, parent + "7");
            backend.put(FileType.Internal, LOCAL[0] + parent + "7");
        }

        candidates.invalidateData();
        assertResolution(candidates, DATA + "7", FileType.Internal, LOCAL[0] + DATA + "7");
        assertHandle(backend.internal(OCEAN + "7"), candidates.load(OCEAN + "7", original));
        assertHandle(backend.internal(LAND + "7"), candidates.load(LAND + "7", original));
        backend.put(FileType.Internal, LOCAL[1] + DATA + "8");
        candidates.invalidateTextures();
        assertResolution(candidates, OCEAN + "7", FileType.Internal, LOCAL[0] + OCEAN + "7");
        assertResolution(candidates, LAND + "7", FileType.Internal, LOCAL[0] + LAND + "7");
        assertHandle(backend.internal(DATA + "8"), candidates.load(DATA + "8", original));
        candidates.invalidateData();
        assertResolution(candidates, DATA + "8", FileType.Internal, LOCAL[1] + DATA + "8");
    }

    @Test
    void classpathFindsJarResourcesWithoutDirectoryEntriesAndFollowsManifestChains() throws Exception {
        String resource = "mods/first/" + DATA + "7";
        String mixedCase = "Mods/Second/" + OCEAN + "8";
        Path leaf = jar("leaf.jar", null, Map.of(resource, "province", mixedCase, "ocean"));
        Path middle = jar("middle.jar", leaf.getFileName().toString(), Map.of());
        Path root = jar("root.jar", middle.getFileName().toString(), Map.of());
        try (URLClassLoader loader = new URLClassLoader(new URL[] {root.toUri().toURL()}, null)) {
            ClasspathDirectories captured = ClasspathDirectories.capture(loader);
            assertNotNull(captured);
            assertNotNull(loader.getResource(resource));
            assertNotNull(loader.getResource(mixedCase));
            assertNull(loader.getResource("mods/second/" + OCEAN + "8"));
            assertNull(loader.getResource("mods/missing/" + DATA + "7"));
            assertTrue(captured.contains("mods/first/" + DATA));
            assertTrue(captured.contains("Mods/Second/" + OCEAN));
            assertFalse(captured.contains("mods/second/" + OCEAN));
            assertFalse(captured.contains("mods/missing/" + DATA));
            assertFalse(captured.contains("mods/first/" + LAND));
        }
    }

    @Test
    void classpathDirectoryRootsObserveResourcesCreatedAfterCapture() throws Exception {
        Path root = java.nio.file.Files.createDirectory(directory.resolve("classpath"));
        try (URLClassLoader loader = new URLClassLoader(new URL[] {root.toUri().toURL()}, null)) {
            ClasspathDirectories captured = ClasspathDirectories.capture(loader);
            assertNotNull(captured);
            assertFalse(captured.contains(DATA));
            Path resource = root.resolve(DATA + "7");
            java.nio.file.Files.createDirectories(resource.getParent());
            java.nio.file.Files.writeString(resource, "province");
            assertNotNull(loader.getResource(DATA + "7"));
            assertTrue(captured.contains(DATA));
            assertFalse(captured.contains(OCEAN));
        }
    }

    @Test
    void unknownResourceLoadersAndUnknownParentsCannotProveClasspathAbsence() throws Exception {
        ClassLoader unknown = new ClassLoader(null) {};
        IOException failure = assertThrows(IOException.class, () -> ClasspathDirectories.capture(unknown));
        assertTrue(failure.getMessage().contains(unknown.getClass().getName()));
        try (URLClassLoader loader = new URLClassLoader(new URL[0], unknown)) {
            assertThrows(IOException.class, () -> ClasspathDirectories.capture(loader));
        }
        try (URLClassLoader custom = new URLClassLoader(new URL[0], null) {}) {
            assertThrows(IOException.class, () -> ClasspathDirectories.capture(custom));
        }
    }

    @Test
    void missingManifestDependenciesDoNotHideResourcesInTheRemainingClasspath() throws Exception {
        String local = LOCAL[0] + DATA + "7";
        String dependency = LOCAL[1] + OCEAN + "8";
        Path leaf = jar("leaf.jar", null, Map.of(dependency, "ocean"));
        Path root = jar("parent.jar", "game.jar " + leaf.getFileName() + " loader.jar", Map.of(local, "province"));
        try (URLClassLoader loader = new URLClassLoader(new URL[] {root.toUri().toURL()}, null)) {
            ClasspathDirectories captured = ClasspathDirectories.capture(loader);
            try (var province = loader.getResourceAsStream(local);
                    var ocean = loader.getResourceAsStream(dependency)) {
                assertNotNull(province);
                assertNotNull(ocean);
                assertEquals("province", new String(province.readAllBytes(), StandardCharsets.UTF_8));
                assertEquals("ocean", new String(ocean.readAllBytes(), StandardCharsets.UTF_8));
            }
            assertTrue(captured.contains(LOCAL[0] + DATA));
            assertTrue(captured.contains(LOCAL[1] + OCEAN));
            assertFalse(captured.contains(LOCAL[0] + LAND));
            assertNull(loader.getResource(LOCAL[0] + LAND + "7"));
        }
    }

    @Test
    void manifestDependencyAppearingAfterCapturePreventsNegativeDirectoryAnswers() throws Exception {
        Path root = jar("parent.jar", "late.jar", Map.of());
        try (URLClassLoader loader = new URLClassLoader(new URL[] {root.toUri().toURL()}, null)) {
            ClasspathDirectories captured = ClasspathDirectories.capture(loader);
            assertFalse(captured.contains(LOCAL[0] + DATA));
            jar("late.jar", null, Map.of(LOCAL[0] + DATA + "7", "late province"));
            assertNotNull(loader.getResource(LOCAL[0] + DATA + "7"));
            assertTrue(captured.contains(LOCAL[0] + DATA));
            assertTrue(captured.contains(LOCAL[1] + OCEAN), "the old snapshot cannot inspect the new archive");
            ClasspathDirectories refreshed = ClasspathDirectories.capture(loader);
            assertTrue(refreshed.contains(LOCAL[0] + DATA));
            assertFalse(refreshed.contains(LOCAL[1] + OCEAN));
        }
    }

    @Test
    void existingInvalidManifestArchiveStillPreventsNegativeDirectoryAnswers() throws Exception {
        java.nio.file.Files.writeString(directory.resolve("invalid.jar"), "not an archive");
        Path root = jar("parent.jar", "invalid.jar", Map.of());
        try (URLClassLoader loader = new URLClassLoader(new URL[] {root.toUri().toURL()}, null)) {
            assertThrows(IOException.class, () -> ClasspathDirectories.capture(loader));
        }
    }

    @Test
    void unavailableExplicitClasspathRootStillFailsCapture() throws Exception {
        try (URLClassLoader loader = new URLClassLoader(
                new URL[] {directory.resolve("missing.jar").toUri().toURL()}, null)) {
            assertThrows(IOException.class, () -> ClasspathDirectories.capture(loader));
        }
    }

    @Test
    void jarIndexHandlingMatchesRuntimeResourceLookup() throws Exception {
        String resource = LOCAL[0] + DATA + "7";
        String dependency = LOCAL[1] + OCEAN + "8";
        Path leaf = jar("leaf.jar", null, Map.of(dependency, "ocean"));
        Path root = jar(
                "indexed.jar",
                leaf.getFileName().toString(),
                Map.of(resource, "province", "META-INF/INDEX.LIST", "JarIndex-Version: 1.0\n\nmissing.jar\nmods\n"));
        try (URLClassLoader loader = new URLClassLoader(new URL[] {root.toUri().toURL()}, null)) {
            if (Runtime.version().feature() < 21) {
                assertThrows(IOException.class, () -> ClasspathDirectories.capture(loader));
            } else {
                ClasspathDirectories captured = ClasspathDirectories.capture(loader);
                assertNotNull(loader.getResource(resource));
                assertNotNull(loader.getResource(dependency));
                assertTrue(captured.contains(LOCAL[0] + DATA));
                assertTrue(captured.contains(LOCAL[1] + OCEAN));
                assertFalse(captured.contains(LOCAL[0] + LAND));
            }
        }
    }

    private void useOriginal(boolean mac) throws Exception {
        // Reuse the real game's resolver bytecode with only Steam-owning statics remapped.
        Resolver resolver = MixinFileManagerProfileTest.transform(mac ? 1 : 2, false);
        original = new FileManager.LoadInterface() {
            @Override
            public FileHandle loadFile(String path) {
                delegated++;
                return resolver.loadFile(path);
            }

            @Override
            public FileHandle getSaveType(String path) {
                return resolver.getSaveType(path);
            }
        };
    }

    private ProvinceCandidates candidates(boolean mac) throws Exception {
        ProvinceCandidates result =
                new ProvinceCandidates(backend, mac, "test", LOCAL, workshop, new ProvinceCandidates.Diagnostics());
        // Gradle's custom loader is deliberately not a production whitelist entry. These filesystem
        // fixtures have a known empty resource classpath; resource discovery is tested separately above.
        try (URLClassLoader loader = new URLClassLoader(new URL[0], null)) {
            ClasspathDirectories empty = ClasspathDirectories.capture(loader);
            assertNotNull(empty);
            var classpath = ProvinceCandidates.class.getDeclaredField("classpath");
            classpath.setAccessible(true);
            classpath.set(result, empty);
        }
        return result;
    }

    private void assertResolution(ProvinceCandidates candidates, String path, FileType type, String resolved) {
        FileHandle expected = original.loadFile(path);
        assertHandle(backend.getFileHandle(resolved, type), expected);
        delegated = 0;
        FileHandle actual = candidates.load(path, original);
        assertHandle(expected, actual);
        assertEquals(0, delegated, "known target directories should not delegate");
        if (java.nio.file.Files.exists(expected.file().toPath())) {
            assertEquals(expected.readString("UTF-8"), actual.readString("UTF-8"));
        }
    }

    private static void assertHandle(FileHandle expected, FileHandle actual) {
        assertEquals(expected.type(), actual.type());
        assertEquals(expected.path(), actual.path());
        assertEquals(expected.file(), actual.file());
    }

    private Path jar(String name, String classpath, Map<String, String> resources) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (classpath != null) manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, classpath);
        Path path = directory.resolve(name);
        try (JarOutputStream output = new JarOutputStream(java.nio.file.Files.newOutputStream(path), manifest)) {
            for (var resource : resources.entrySet()) {
                // Deliberately omit directory entries, as many game/mod archives do.
                output.putNextEntry(new JarEntry(resource.getKey()));
                output.write(resource.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return path;
    }

    private static final class Backend implements Files {
        private final Path root;
        private int probes;
        private String failingDirectory;

        Backend(Path root) {
            this.root = root;
        }

        Path physical(FileType type, String path) {
            // Desktop local saves and internal reads share the working-directory filesystem.
            FileType storage = type == FileType.Local ? FileType.Internal : type;
            return type == FileType.Absolute
                    ? Path.of(path)
                    : root.resolve(storage.name()).resolve(path);
        }

        void put(FileType type, String path) throws IOException {
            Path target = physical(type, path);
            java.nio.file.Files.createDirectories(target.getParent());
            java.nio.file.Files.writeString(target, type + " " + path);
        }

        @Override
        public FileHandle getFileHandle(String path, FileType type) {
            if (path.equals(failingDirectory)) throw new SecurityException("Directory access denied");
            return new FileHandle(physical(type, path).toFile(), type) {
                @Override
                public File file() {
                    // The fixture has already resolved the external root, unlike a plain FileHandle.
                    return file;
                }

                @Override
                public boolean exists() {
                    probes++;
                    return java.nio.file.Files.exists(file().toPath());
                }
            };
        }

        @Override
        public FileHandle classpath(String path) {
            return getFileHandle(path, FileType.Classpath);
        }

        @Override
        public FileHandle internal(String path) {
            return getFileHandle(path, FileType.Internal);
        }

        @Override
        public FileHandle external(String path) {
            return getFileHandle(path, FileType.External);
        }

        @Override
        public FileHandle absolute(String path) {
            return getFileHandle(path, FileType.Absolute);
        }

        @Override
        public FileHandle local(String path) {
            return getFileHandle(path, FileType.Local);
        }

        @Override
        public String getExternalStoragePath() {
            return root.resolve(FileType.External.name()).toString();
        }

        @Override
        public boolean isExternalStorageAvailable() {
            return true;
        }

        @Override
        public String getLocalStoragePath() {
            return root.resolve(FileType.Internal.name()).toString();
        }

        @Override
        public boolean isLocalStorageAvailable() {
            return true;
        }
    }
}
