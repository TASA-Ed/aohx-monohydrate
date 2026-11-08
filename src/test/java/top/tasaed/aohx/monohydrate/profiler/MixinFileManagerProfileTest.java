package top.tasaed.aohx.monohydrate.profiler;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Files.FileType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import team.rainfall.finality.luminosity2.processor.InjectProcessor;
import team.rainfall.finality.luminosity2.processor.MixinProcessor;
import team.rainfall.finality.luminosity2.processor.NewFieldProcessor;
import top.tasaed.aohx.monohydrate.load.DesktopFileResolver;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler.PathResult;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler.PathSource;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler.Point;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler.Report;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler.Stage;
import top.tasaed.aohx.monohydrate.profiler.mixins.MixinFileManagerDesktop;
import top.tasaed.aohx.monohydrate.profiler.mixins.MixinFileManagerMac;
import top.tasaed.aohx.monohydrate.profiler.mixins.MixinFileManagerMobile;

public class MixinFileManagerProfileTest {
    private static final String FILE = "map/test/data/provinces/7";

    @TempDir
    Path directory;

    private Files previousFiles;
    private Backend backend;
    private long now;
    private final List<Report> reports = new ArrayList<>();
    private final LoadingProfiler profiler = new LoadingProfiler(() -> now, reports::add, () -> null);

    @BeforeEach
    void setup() {
        previousFiles = Gdx.files;
        backend = new Backend(directory);
        Gdx.files = backend;
        Mods.sUF = new ArrayList<>(List.of("mods/first/", "mods/second/"));
        Mods.sUFS = Mods.sUF.size();
        Mods.sUII = new ArrayList<>(List.of(
                new Workshop(directory.resolve("workshop/first").toString()),
                new Workshop(directory.resolve("workshop/second").toString())));
        Mods.sUIIS = Mods.sUII.size();
    }

    @AfterEach
    void restoreFiles() {
        Gdx.files = previousFiles;
    }

    @Test
    void desktopPreservesLocalAndWorkshopPriority() throws Exception {
        backend.put(FileType.Internal, "mods/first/" + FILE);
        backend.put(FileType.Internal, "mods/second/" + FILE);
        backend.put(FileType.Absolute, Mods.sUII.get(0).getFolder() + "/" + FILE);
        FileHandle result = resolve(2);
        assertEquals(FileType.Internal, result.type());
        assertTrue(result.path().endsWith("mods/first/" + FILE));
        assertCounts(finish(), 1, 0, 1, 0, 0, PathSource.LOCAL, "mods/first/");
    }

    @Test
    void desktopTriesEveryMissingLocalBeforeFirstWorkshopHit() throws Exception {
        backend.put(FileType.Absolute, Mods.sUII.get(0).getFolder() + "/" + FILE);
        backend.put(FileType.Absolute, Mods.sUII.get(1).getFolder() + "/" + FILE);
        FileHandle result = resolve(2);
        assertEquals(FileType.Absolute, result.type());
        assertCounts(
                finish(), 2, 1, 0, 1, 0, PathSource.WORKSHOP, Mods.sUII.get(0).getFolder());
    }

    @Test
    void desktopFallbackDoesNotProbeVanillaOrClaimThatItExists() throws Exception {
        FileHandle result = resolve(2);
        assertEquals(FileType.Internal, result.type());
        assertFalse(java.nio.file.Files.exists(result.file().toPath()));
        assertCounts(finish(), 2, 2, 0, 0, 1, PathSource.VANILLA, null);
    }

    @Test
    void desktopHonorsPublishedCountsRatherThanListSizes() throws Exception {
        Mods.sUFS = 1;
        Mods.sUIIS = 0;
        backend.put(FileType.Internal, "mods/second/" + FILE);
        backend.put(FileType.Absolute, Mods.sUII.get(0).getFolder() + "/" + FILE);
        resolve(2);
        assertCounts(finish(), 1, 0, 0, 0, 1, PathSource.VANILLA, null);
    }

    @Test
    void macUsesExternalBeforeInternalWithinEachLocalFolder() throws Exception {
        backend.put(FileType.External, "mods/first/" + FILE);
        backend.put(FileType.Internal, "mods/first/" + FILE);
        backend.put(FileType.External, "mods/second/" + FILE);
        FileHandle result = resolve(1);
        assertEquals(FileType.External, result.type());
        assertCounts(finish(), 1, 0, 1, 0, 0, PathSource.LOCAL, "mods/first/");
    }

    @Test
    void macChecksFirstInternalBeforeSecondExternal() throws Exception {
        backend.put(FileType.Internal, "mods/first/" + FILE);
        backend.put(FileType.External, "mods/second/" + FILE);
        FileHandle result = resolve(1);
        assertEquals(FileType.Internal, result.type());
        assertTrue(result.path().endsWith("mods/first/" + FILE));
        assertCounts(finish(), 2, 0, 1, 0, 0, PathSource.LOCAL, "mods/first/");
    }

    @Test
    void macReachesLaterExternalAndCountsBothEarlierChecks() throws Exception {
        backend.put(FileType.External, "mods/second/" + FILE);
        resolve(1);
        assertCounts(finish(), 3, 0, 1, 0, 0, PathSource.LOCAL, "mods/second/");
    }

    @Test
    void macExhaustsLocalPairsBeforeWorkshopInListOrder() throws Exception {
        backend.put(FileType.Absolute, Mods.sUII.get(1).getFolder() + "/" + FILE);
        FileHandle result = resolve(1);
        assertEquals(FileType.Absolute, result.type());
        assertCounts(
                finish(), 4, 2, 0, 1, 0, PathSource.WORKSHOP, Mods.sUII.get(1).getFolder());
    }

    @Test
    void macExternalVanillaHitIsNotALocalModHit() throws Exception {
        backend.put(FileType.External, FILE);
        backend.put(FileType.Internal, FILE);
        FileHandle result = resolve(1);
        assertEquals(FileType.External, result.type());
        assertCounts(finish(), 4, 2, 0, 0, 1, PathSource.VANILLA, null);
    }

    @Test
    void macMissingExternalFallsBackToUncheckedInternal() throws Exception {
        FileHandle result = resolve(1);
        assertEquals(FileType.Internal, result.type());
        assertFalse(java.nio.file.Files.exists(result.file().toPath()));
        assertCounts(finish(), 4, 2, 0, 0, 1, PathSource.VANILLA, null);
    }

    @Test
    void mobileUsesLocalWithoutScanningMods() throws Exception {
        backend.put(FileType.Local, FILE);
        backend.put(FileType.Internal, FILE);
        backend.put(FileType.Internal, "mods/first/" + FILE);
        FileHandle result = resolve(3);
        assertEquals(FileType.Local, result.type());
        assertCounts(finish(), 0, 0, 0, 0, 1, PathSource.VANILLA, null);
    }

    @Test
    void mobileMissingLocalFallsBackToUncheckedInternal() throws Exception {
        FileHandle result = resolve(3);
        assertEquals(FileType.Internal, result.type());
        assertCounts(finish(), 0, 0, 0, 0, 1, PathSource.VANILLA, null);
    }

    @Test
    void saveTypesAreUnchangedByOverwrite() throws Exception {
        for (int platform = 1; platform <= 3; platform++) {
            Resolver original = transform(platform, false);
            Resolver patched = transform(platform, true);
            backend.events.clear();
            FileHandle expected = original.getSaveType(FILE);
            List<String> events = List.copyOf(backend.events);
            backend.events.clear();
            FileHandle actual = patched.getSaveType(FILE);
            assertEquals(expected.type(), actual.type());
            assertEquals(expected.path(), actual.path());
            assertEquals(events, backend.events);
        }
    }

    @Test
    void macPropagatesCreationExistenceAndReturnHandleFailures() throws Exception {
        backend.put(FileType.External, "mods/first/" + FILE);
        for (int failAt = 1; failAt <= 3; failAt++) {
            assertFailure(1, failAt, failAt == 1 ? 0 : 1, 0);
        }
    }

    @Test
    void macPropagatesInternalWorkshopAndVanillaFailuresWithoutExtraChecks() throws Exception {
        Mods.sUFS = 1;
        Mods.sUIIS = 1;
        // external local, internal local, workshop, external vanilla, internal vanilla
        for (int failAt = 3; failAt <= 9; failAt++) {
            assertFailure(1, failAt, failAt == 3 ? 1 : 2, failAt < 6 ? 0 : 1);
        }
    }

    @Test
    void desktopAndMobileDoNotSwallowBackendFailures() throws Exception {
        backend.put(FileType.Internal, "mods/first/" + FILE);
        backend.put(FileType.Local, FILE);
        for (int failAt = 1; failAt <= 3; failAt++) {
            assertFailure(2, failAt, failAt == 1 ? 0 : 1, 0);
            assertFailure(3, failAt, 0, 0);
        }
    }

    private void assertFailure(int platform, int failAt, long localProbes, long workshopProbes) throws Exception {
        Resolver original = transform(platform, false);
        Resolver patched = transform(platform, true);
        backend.failAt = failAt;
        backend.events.clear();
        assertSame(backend.failure, assertThrows(GdxRuntimeException.class, () -> original.loadFile(FILE)));
        List<String> expected = List.copyOf(backend.events);
        backend.events.clear();
        List<Report> failures = new ArrayList<>();
        LoadingProfiler sample = new LoadingProfiler(() -> now, failures::add, () -> null);
        sample.beginFrame();
        sample.begin(Point.LOAD_PROVINCE);
        LoadingProfiler.beginPathStage(Stage.PROVINCE_PATH, 7);
        assertSame(backend.failure, assertThrows(GdxRuntimeException.class, () -> patched.loadFile(FILE)));
        sample.markComplete();
        sample.endFrame();
        assertEquals(expected, backend.events);
        var stage = failures.get(0).stages().get(0);
        assertEquals(1, stage.unreturnedCalls());
        assertEquals(0, stage.returnedCalls());
        PathResult path = stage.path();
        assertEquals(localProbes, path.localProbes());
        assertEquals(workshopProbes, path.workshopProbes());
        assertEquals(0, path.localHits());
        assertEquals(0, path.workshopHits());
        assertEquals(0, path.vanillaFallbacks());
        assertNull(path.maxSource());
        assertNull(path.maxProvinceId());
        backend.failAt = 0;
    }

    private FileHandle resolve(int platform) throws Exception {
        Resolver original = transform(platform, false);
        Resolver patched = transform(platform, true);
        backend.events.clear();
        FileHandle expected = original.loadFile(FILE);
        List<String> events = List.copyOf(backend.events);
        backend.events.clear();
        profiler.beginFrame();
        profiler.begin(Point.LOAD_PROVINCE);
        LoadingProfiler.beginPathStage(Stage.PROVINCE_PATH, 7);
        FileHandle actual = patched.loadFile(FILE);
        now += 10;
        LoadingProfiler.endStage(Stage.PROVINCE_PATH);
        profiler.end(Point.LOAD_PROVINCE);
        assertEquals(expected.type(), actual.type());
        assertEquals(expected.path(), actual.path());
        assertEquals(events, backend.events, "Handle construction and exists calls must match the original game");
        return actual;
    }

    private Report finish() {
        profiler.markComplete();
        profiler.endFrame();
        return reports.get(0);
    }

    private static void assertCounts(
            Report report,
            long localProbes,
            long workshopProbes,
            long localHits,
            long workshopHits,
            long vanillaFallbacks,
            PathSource source,
            String folder) {
        var stage = report.stages().get(0);
        assertEquals(1, stage.calls());
        assertEquals(1, stage.returnedCalls());
        PathResult path = stage.path();
        assertEquals(localProbes, path.localProbes());
        assertEquals(workshopProbes, path.workshopProbes());
        assertEquals(localHits, path.localHits());
        assertEquals(workshopHits, path.workshopHits());
        assertEquals(vanillaFallbacks, path.vanillaFallbacks());
        assertEquals(Integer.valueOf(7), path.maxProvinceId());
        assertEquals(source, path.maxSource());
        assertEquals(folder, path.maxSourceFolder());
    }

    /** Loads real anonymous resolver bytecode without initializing the game's Steam-owning statics. */
    public static Resolver transform(int platform, boolean overwrite) throws Exception {
        String game = "age/of/civilizations2/jakowski/lukasz/";
        String originalName = game + "Files/FileManager$" + platform;
        String targetName = "top/tasaed/aohx/monohydrate/profiler/ResolverFixture" + platform;
        String helperName = "top/tasaed/aohx/monohydrate/profiler/DesktopResolverFixture";
        Map<String, String> mappings = Map.of(
                Type.getInternalName(DesktopFileResolver.class),
                helperName,
                originalName,
                targetName,
                game + "Files/FileManager$LoadInterface",
                Type.getInternalName(Resolver.class),
                game + "Z_Other/ST/sUM",
                Type.getInternalName(Mods.class),
                "com/codedisaster/steamworks/SteamUGC$ItemInstallInfo",
                Type.getInternalName(Workshop.class));
        ClassNode target = remap(node(originalName), mappings);
        target.nestHostClass = null;
        target.outerClass = null;
        target.outerMethod = null;
        target.outerMethodDesc = null;
        target.innerClasses.clear();
        if (overwrite) {
            Class<?> mixin =
                    switch (platform) {
                        case 1 -> MixinFileManagerMac.class;
                        case 2 -> MixinFileManagerDesktop.class;
                        case 3 -> MixinFileManagerMobile.class;
                        default -> throw new IllegalArgumentException("platform " + platform);
                    };
            ClassNode source = remap(node(Type.getInternalName(mixin)), mappings);
            new MixinProcessor(source, target).process();
            new NewFieldProcessor(source, target).process();
            new InjectProcessor(target).process();
            // A later plugin causes Luminosity to rerun InjectProcessor on the entire target.
            ClassNode later = node(Type.getInternalName(LaterMixin.class));
            new MixinProcessor(later, target).process();
            new NewFieldProcessor(later, target).process();
            new InjectProcessor(target).process();
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        target.accept(writer);
        byte[] bytes = writer.toByteArray();
        ClassWriter helperWriter = new ClassWriter(0);
        remap(node(Type.getInternalName(DesktopFileResolver.class)), mappings).accept(helperWriter);
        byte[] helperBytes = helperWriter.toByteArray();
        Class<?> type = new ClassLoader(MixinFileManagerProfileTest.class.getClassLoader()) {
            Class<?> define() {
                defineClass(helperName.replace('/', '.'), helperBytes, 0, helperBytes.length);
                return defineClass(targetName.replace('/', '.'), bytes, 0, bytes.length);
            }
        }.define();
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return (Resolver) constructor.newInstance();
    }

    private static ClassNode node(String name) throws IOException {
        try (InputStream input = MixinFileManagerProfileTest.class.getResourceAsStream("/" + name + ".class")) {
            if (input == null) throw new IOException("Missing class resource: " + name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static ClassNode remap(ClassNode source, Map<String, String> mappings) {
        ClassNode target = new ClassNode();
        source.accept(new ClassRemapper(target, new SimpleRemapper(mappings)));
        return target;
    }

    public interface Resolver {
        FileHandle loadFile(String path);

        FileHandle getSaveType(String path);
    }

    public static class LaterMixin {
        public int otherPlugin() {
            return 17;
        }
    }

    public static class Mods {
        public static List<String> sUF;
        public static int sUFS;
        public static List<Workshop> sUII;
        public static int sUIIS;
    }

    public static class Workshop {
        private final String folder;

        public Workshop(String folder) {
            this.folder = folder;
        }

        public String getFolder() {
            return folder;
        }
    }

    public static class Backend implements Files {
        private final Path root;
        final List<String> events = new ArrayList<>();
        final GdxRuntimeException failure = new GdxRuntimeException("injected filesystem failure");
        int failAt;

        public Backend(Path root) {
            this.root = root;
        }

        private Path physical(FileType type, String path) {
            return type == FileType.Absolute
                    ? Path.of(path)
                    : root.resolve(type.name()).resolve(path);
        }

        public void put(FileType type, String path) throws IOException {
            Path file = physical(type, path);
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, "real fixture data");
        }

        private void event(String value) {
            events.add(value);
            if (events.size() == failAt) throw failure;
        }

        @Override
        public FileHandle getFileHandle(String path, FileType type) {
            event("make " + type + " " + path);
            return new FileHandle(physical(type, path).toFile(), type) {
                @Override
                public java.io.File file() {
                    // physical() already rooted this handle; do not prefix External a second time.
                    return file;
                }

                @Override
                public boolean exists() {
                    event("exists " + type + " " + path);
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
            return root.resolve(FileType.Local.name()).toString();
        }

        @Override
        public boolean isLocalStorageAvailable() {
            return true;
        }
    }
}
