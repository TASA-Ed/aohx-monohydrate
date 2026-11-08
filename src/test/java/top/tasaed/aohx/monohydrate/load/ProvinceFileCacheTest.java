package top.tasaed.aohx.monohydrate.load;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Files.FileType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import team.rainfall.finality.FinalityLogger;
import team.rainfall.finality.luminosity2.processor.InjectProcessor;
import team.rainfall.finality.luminosity2.processor.MixinProcessor;
import team.rainfall.finality.luminosity2.processor.NewFieldProcessor;
import top.tasaed.aohx.monohydrate.load.mixins.MixinFileManagerCandidates;

/** Real anonymous resolver and cache bytecode, with only native-owning globals/backend types isolated. */
public class ProvinceFileCacheTest {
    static final String FILE = "map/test/data/provinces/7";
    static final String SCALE_ONE = "map/test/data/scales/provinces/1/7";
    static final String SCALE_TWO = "map/test/data/scales/provinces/2/7";

    @TempDir
    Path directory;

    private Files previousFiles;
    Backend backend;
    private OutputStreamWriter previousLog;
    private final ByteArrayOutputStream log = new ByteArrayOutputStream();

    @BeforeEach
    void setup() {
        previousFiles = Gdx.files;
        previousLog = FinalityLogger.logStream;
        FinalityLogger.logStream = new OutputStreamWriter(log, StandardCharsets.UTF_8);
        backend = new Backend(directory);
        Gdx.files = backend;
        Config.map = new MapState("test");
        Switch.enabled = true;
        Mods.sUF = new ArrayList<>();
        Mods.sUFS = 0;
        Mods.sUII = new ArrayList<>(List.of(
                new Workshop(directory.resolve("workshop/first").toString()),
                new Workshop(directory.resolve("workshop/second").toString())));
        Mods.sUIIS = Mods.sUII.size();
    }

    @AfterEach
    void restoreFiles() {
        Gdx.files = previousFiles;
        FinalityLogger.logStream = previousLog;
        Config.map = null;
        Mods.sUF = null;
        Mods.sUII = null;
    }

    @Test
    void installsBothDefaultResolversOnlyOnceAndPreservesTheirPriority() throws Exception {
        localMods();
        backend.put(FileType.Internal, "mods/first/" + FILE, "first internal");
        backend.put(FileType.External, "mods/first/" + FILE, "first external");
        backend.put(FileType.Internal, "mods/second/" + FILE, "second internal");
        workshop(0, FILE, "workshop");
        for (int platform : new int[] {1, 2}) {
            Harness h = new Harness(platform);
            assertNotSame(h.original, h.resolver());
            Resolver installed = h.resolver();
            h.call("install");
            assertSame(installed, h.resolver());
            h.begin(new Object());
            h.knownClasspath();
            assertEquals(
                    platform == 1 ? "first external" : "first internal",
                    h.load(FILE).readString());
            assertTrue(h.load(FILE).path().contains("mods/first/"));
        }
    }

    @Test
    void leavesUnknownResolversNullAndDisabledInstallationUntouched() throws Exception {
        Harness h = new Harness(2);
        FileHandle sentinel = backend.internal("sentinel");
        Resolver unknown = new Resolver() {
            public FileHandle loadFile(String path) {
                return sentinel;
            }

            public FileHandle getSaveType(String path) {
                return sentinel;
            }
        };
        h.setResolver(unknown);
        h.call("install");
        h.begin(new Object());
        assertSame(unknown, h.resolver());
        assertSame(sentinel, h.load(FILE));
        assertSame(sentinel, h.resolver().getSaveType(FILE));
        h.setResolver(null);
        h.call("install");
        assertNull(h.resolver());
        Switch.enabled = false;
        h.setResolver(h.original);
        h.call("install");
        assertSame(h.original, h.resolver());
    }

    @Test
    void rejectsDefaultNamedResolverFromAnotherClassLoader() throws Exception {
        Harness first = new Harness(2);
        Harness second = new Harness(2);
        first.setResolver(second.original);
        first.call("install");
        assertSame(second.original, first.resolver());
    }

    @Test
    void delegatesOutsideFramesAndReleasesCompletedSession() throws Exception {
        workshop(1, FILE, "second");
        Harness h = new Harness(2);
        assertEquals("second", h.load(FILE).readString());
        assertNull(h.state("candidates"));
        Object owner = new Object();
        h.begin(owner);
        assertEquals("second", h.load(FILE).readString());
        assertNotNull(h.state("candidates"));
        h.call("endFrame", owner);
        workshop(0, FILE, "first created between frames");
        assertEquals("first created between frames", h.load(FILE).readString());
        h.call("endSession", owner);
        for (String field : List.of("session", "thread", "files", "mapIdentity", "candidates")) {
            assertNull(h.state(field), field);
        }
        assertEquals("first created between frames", h.load(FILE).readString());
        assertNull(h.state("candidates"));
    }

    @Test
    void unrelatedOwnersCannotEndTheActiveSession() throws Exception {
        workshop(1, FILE, "second");
        Harness h = new Harness(2);
        Object owner = new Object();
        h.begin(owner);
        assertEquals("second", h.load(FILE).readString());
        h.call("endFrame", new Object());
        h.call("endSession", new Object());
        assertSame(owner, h.state("session"));
        assertEquals(Boolean.TRUE, h.state("inFrame"));
        backend.probes.clear();
        assertEquals("second", h.load(FILE).readString());
        assertEquals(1, backend.probes.size(), "the absent first workshop directory stays pruned");
    }

    @Test
    void otherThreadsDelegateUntilTheyBeginTheirOwnFrame() throws Exception {
        workshop(1, FILE, "second");
        Harness h = new Harness(2);
        Object owner = new Object();
        h.begin(owner);
        assertEquals("second", h.load(FILE).readString());
        workshop(0, FILE, "new first");
        var outsideFrame =
                new java.util.concurrent.FutureTask<>(() -> h.load(FILE).readString());
        new Thread(outsideFrame).start();
        assertEquals("new first", outsideFrame.get());
        assertEquals("second", h.load(FILE).readString(), "another thread must not consume the cached snapshot");

        h.call("endFrame", owner);
        var nextFrame = new java.util.concurrent.FutureTask<>(() -> {
            h.begin(owner);
            return h.load(FILE).readString();
        });
        new Thread(nextFrame).start();
        assertEquals("new first", nextFrame.get(), "changing the loading thread resets its directory snapshot");
        h.call("endSession", owner);
    }

    @Test
    void newOwnerOnTheSameMapRebuildsMissingDirectories() throws Exception {
        workshop(1, FILE, "second");
        Harness h = active();
        assertEquals("second", h.load(FILE).readString());
        workshop(0, FILE, "new first");
        h.begin(new Object());
        assertEquals("new first", h.load(FILE).readString());
    }

    @Test
    void reportsFirstClasspathFailureOnceAcrossSnapshotsButAgainInANewSession() throws Exception {
        localMods();
        Harness h = new Harness(2);
        Object owner = new Object();
        h.begin(owner);
        h.load(FILE);
        h.load(FILE.replace("/7", "/8"));
        h.load(SCALE_ONE);
        h.call("endFrame", owner);
        h.begin(owner);
        Config.map = new MapState("test");
        h.load(FILE);
        String first = log.toString(StandardCharsets.UTF_8);
        assertEquals(1, first.lines().filter(line -> line.startsWith("[Error]")).count());
        assertTrue(first.contains(FileHandle.class.getClassLoader().getClass().getName()));
        assertTrue(first.contains("mods/first/"));
        h.call("endSession", owner);
        h.begin(new Object());
        h.load(FILE);
        assertEquals(
                2,
                log.toString(StandardCharsets.UTF_8)
                        .lines()
                        .filter(line -> line.startsWith("[Error]"))
                        .count());
    }

    @Test
    void replacingMapObjectWithSameTagInvalidatesSnapshot() throws Exception {
        workshop(1, FILE, "second");
        Harness h = active();
        assertEquals("second", h.load(FILE).readString());
        workshop(0, FILE, "new first");
        Config.map = new MapState("test");
        assertEquals("new first", h.load(FILE).readString());
    }

    @Test
    void changingTagOnTheSameMapObjectSelectsTheNewMap() throws Exception {
        workshop(1, FILE, "old map");
        String other = "map/other/data/provinces/7";
        workshop(0, other, "other map");
        Harness h = active();
        assertEquals("old map", h.load(FILE).readString());
        Config.map.tag = "other";
        assertEquals("other map", h.load(other).readString());
        workshop(0, FILE, "new old map");
        Config.map.tag = "test";
        assertEquals("new old map", h.load(FILE).readString());
    }

    @Test
    void localReorderingWithUnchangedCountChangesTheWinner() throws Exception {
        localMods();
        backend.put(FileType.Internal, "mods/first/" + FILE, "first");
        backend.put(FileType.Internal, "mods/second/" + FILE, "second");
        Harness h = active();
        h.knownClasspath();
        assertEquals("first", h.load(FILE).readString());
        Collections.swap(Mods.sUF, 0, 1);
        assertEquals("second", h.load(FILE).readString());
    }

    @Test
    void workshopReorderingAndFolderMutationWithUnchangedCountChangeTheWinner() throws Exception {
        workshop(0, FILE, "first");
        workshop(1, FILE, "second");
        Harness h = active();
        assertEquals("first", h.load(FILE).readString());
        Collections.swap(Mods.sUII, 0, 1);
        assertEquals("second", h.load(FILE).readString());
        Mods.sUII.get(0).folder = directory.resolve("workshop/replacement").toString();
        workshop(0, FILE, "replacement");
        assertEquals("replacement", h.load(FILE).readString());
    }

    @Test
    void publishedCountsDetermineWhichModsParticipate() throws Exception {
        workshop(1, FILE, "second");
        backend.put(FileType.Internal, FILE, "vanilla");
        Mods.sUIIS = 1;
        Harness h = active();
        assertEquals("vanilla", h.load(FILE).readString());
        Mods.sUIIS = 2;
        assertEquals("second", h.load(FILE).readString());
        Mods.sUIIS = 0;
        assertEquals("vanilla", h.load(FILE).readString());
        localMods();
        backend.put(FileType.Internal, "mods/second/" + FILE, "local second");
        Mods.sUFS = 1;
        h.knownClasspath();
        assertEquals("vanilla", h.load(FILE).readString());
        Mods.sUFS = 2;
        assertEquals("local second", h.load(FILE).readString());
    }

    @Test
    void replacingGdxFilesRebuildsEvenWithTheSameMapAndMods() throws Exception {
        backend.put(FileType.Internal, FILE, "old vanilla");
        Harness h = active();
        assertEquals("old vanilla", h.load(FILE).readString());
        workshop(0, FILE, "new workshop");
        Gdx.files = new Backend(directory.resolve("replacement"));
        assertEquals("new workshop", h.load(FILE).readString());
    }

    @Test
    void unknownFilesBackendDelegatesWithoutSkippingMissingDirectories() throws Exception {
        workshop(1, FILE, "second");
        Harness h = active();
        assertEquals("second", h.load(FILE).readString());
        Backend unknown = new Backend(directory) {};
        Gdx.files = unknown;
        assertEquals("second", h.load(FILE).readString());
        assertEquals(2, unknown.probes.size());
        workshop(0, FILE, "new first");
        assertEquals("new first", h.load(FILE).readString());
        assertNull(h.state("candidates"));
    }

    @Test
    void saveReturnsTheOriginalHandleAndDelayedWritesStayVisibleForBothPlatforms() throws Exception {
        localMods();
        for (int platform : new int[] {1, 2}) {
            String path = "map/test/data/scales/provinces/" + platform + "/7";
            backend.put(FileType.Internal, path, "vanilla");
            Harness h = new Harness(platform);
            h.begin(new Object());
            h.knownClasspath();
            assertEquals("vanilla", h.load(path).readString());
            FileHandle saved = h.resolver().getSaveType("mods/first/" + path);
            assertSame(backend.lastHandle, saved, "the exact handle returned by the original resolver");
            assertEquals(platform == 1 ? FileType.External : FileType.Local, saved.type());
            assertEquals("vanilla", h.load(path).readString(), "read before delayed write");
            saved.writeString("saved mod", false);
            assertEquals("saved mod", h.load(path).readString());
            String neighbor = path.substring(0, path.length() - 1) + "8";
            backend.put(platform == 1 ? FileType.External : FileType.Internal, "mods/second/" + neighbor, "later mod");
            assertEquals("later mod", h.load(neighbor).readString(), "the whole writable directory delegates");
        }
    }

    @Test
    void separateScaleDirectoriesAndExplicitInvalidationRevealNewRoots() throws Exception {
        workshop(1, FILE, "data second");
        workshop(1, SCALE_ONE, "scale one second");
        workshop(0, SCALE_TWO, "scale two first");
        Harness h = active();
        assertEquals("data second", h.load(FILE).readString());
        assertEquals("scale one second", h.load(SCALE_ONE).readString());
        assertEquals("scale two first", h.load(SCALE_TWO).readString());
        workshop(0, FILE, "data first");
        workshop(0, SCALE_ONE, "scale one first");
        h.call("invalidateData");
        assertEquals("data first", h.load(FILE).readString());
        h.call("invalidateTextures");
        assertEquals("scale one first", h.load(SCALE_ONE).readString());
        assertEquals("scale two first", h.load(SCALE_TWO).readString());
    }

    @Test
    void mapResourceDirectoriesKeepIndependentCandidateRoots() throws Exception {
        String[] paths = {
            "map/test/army_boxes/7",
            "map/test/civs_template/fra",
            "map/test/suggested_owners/7",
            "map/test/formable_civs/Age_of_Civilizations",
            "map/test/provinces/7.png"
        };
        for (int i = 0; i < paths.length; i++) workshop(i % 2, paths[i], paths[i]);
        Harness h = active();
        for (String path : paths) assertEquals(path, h.load(path).readString());
        for (String path : paths) {
            backend.probes.clear();
            assertEquals(path, h.load(path).readString());
            assertEquals(1, backend.probes.size(), "each directory excludes its own absent roots");
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"army_boxes/7", "civs_template/fra", "suggested_owners/7", "formable_civs/fra", "provinces/7.png"
            })
    void mapResourceDelayedSavesRevealPreviouslyMissingModDirectories(String resource) throws Exception {
        localMods();
        for (int platform : new int[] {1, 2}) {
            String path = "map/test/" + resource + platform;
            backend.put(FileType.Internal, path, "vanilla");
            Harness h = new Harness(platform);
            h.begin(new Object());
            h.knownClasspath();
            assertEquals("vanilla", h.load(path).readString());
            FileHandle saved = h.resolver().getSaveType("mods/first/" + path);
            assertEquals("vanilla", h.load(path).readString());
            saved.writeString("saved mod", false);
            assertEquals("saved mod", h.load(path).readString());
        }
    }

    @Test
    void nonProvincePathsKeepOriginalResolutionDuringAFrame() throws Exception {
        Harness h = active();
        for (String path : List.of(
                "map/test/data/provinces/metadata.json", "map/test/data/scales/provinces/x/7", "settings.txt")) {
            workshop(0, path, path);
            FileHandle expected = h.original.loadFile(path);
            FileHandle actual = h.load(path);
            assertEquals(expected.path(), actual.path());
            assertEquals(path, actual.readString());
        }
    }

    Harness active() throws Exception {
        Harness h = new Harness(2);
        h.begin(new Object());
        return h;
    }

    void localMods() {
        Mods.sUF = new ArrayList<>(List.of("mods/first/", "mods/second/"));
        Mods.sUFS = Mods.sUF.size();
    }

    void workshop(int index, String path, String content) throws IOException {
        backend.put(FileType.Absolute, Mods.sUII.get(index).getFolder() + "/" + path, content);
    }

    public interface Resolver {
        FileHandle loadFile(String path);

        FileHandle getSaveType(String path);
    }

    public static class Config {
        public static MapState map;

        public static boolean getIsDesktop() {
            return true;
        }
    }

    public static class Switch {
        public static boolean enabled;
        public static final boolean PROFILER_ENABLED = false;
    }

    public static class MapState {
        String tag;

        MapState(String tag) {
            this.tag = tag;
        }

        public String getFile_ActiveMap_Path2() {
            return tag;
        }
    }

    public static class Mods {
        public static List<String> sUF;
        public static int sUFS;
        public static List<Workshop> sUII;
        public static int sUIIS;
    }

    public static class Workshop {
        String folder;

        Workshop(String folder) {
            this.folder = folder;
        }

        public String getFolder() {
            return folder;
        }
    }

    /** Mirrors desktop Internal/Local sharing and keeps External away from the real user home. */
    public static class Backend implements Files {
        private final Path root;
        final List<String> probes = new ArrayList<>();
        FileHandle lastHandle;

        Backend(Path root) {
            this.root = root;
        }

        private Path physical(FileType type, String path) {
            if (type == FileType.Absolute) return Path.of(path);
            return root.resolve(type == FileType.External ? "external" : "internal")
                    .resolve(path);
        }

        void put(FileType type, String path, String content) throws IOException {
            Path target = physical(type, path);
            java.nio.file.Files.createDirectories(target.getParent());
            java.nio.file.Files.writeString(target, content);
        }

        public FileHandle getFileHandle(String path, FileType type) {
            lastHandle = new FileHandle(physical(type, path).toFile(), type) {
                @Override
                public java.io.File file() {
                    return file;
                }

                @Override
                public FileHandle parent() {
                    return new FileHandle(file.getParentFile());
                }

                @Override
                public boolean exists() {
                    probes.add(type + " " + path);
                    return java.nio.file.Files.exists(file().toPath());
                }
            };
            return lastHandle;
        }

        public FileHandle classpath(String path) {
            return getFileHandle(path, FileType.Classpath);
        }

        public FileHandle internal(String path) {
            return getFileHandle(path, FileType.Internal);
        }

        public FileHandle external(String path) {
            return getFileHandle(path, FileType.External);
        }

        public FileHandle absolute(String path) {
            return getFileHandle(path, FileType.Absolute);
        }

        public FileHandle local(String path) {
            return getFileHandle(path, FileType.Local);
        }

        public String getExternalStoragePath() {
            return root.resolve("external").toString();
        }

        public boolean isExternalStorageAvailable() {
            return true;
        }

        public String getLocalStoragePath() {
            return root.resolve("internal").toString();
        }

        public boolean isLocalStorageAvailable() {
            return true;
        }
    }

    static final class Harness {
        private static final String GAME = "age/of/civilizations2/jakowski/lukasz/";
        private static final String MANAGER = GAME + "Files/FileManager";
        private static final String CACHE = "top/tasaed/aohx/monohydrate/load/ProvinceFileCache";
        private final Class<?> manager;
        private final Class<?> cache;
        final Resolver original;

        Harness(int platform) throws Exception {
            Map<String, String> mappings = new HashMap<>();
            mappings.put(MANAGER + "$LoadInterface", Type.getInternalName(Resolver.class));
            mappings.put(GAME + "CFG", Type.getInternalName(Config.class));
            mappings.put(GAME + "Z_Other/ST/sUM", Type.getInternalName(Mods.class));
            mappings.put("com/codedisaster/steamworks/SteamUGC$ItemInstallInfo", Type.getInternalName(Workshop.class));
            mappings.put("com/badlogic/gdx/backends/lwjgl3/Lwjgl3Files", Type.getInternalName(Backend.class));
            String config = "top/tasaed/aohx/monohydrate/core/MonohydrateConfig";
            mappings.put(config, Type.getInternalName(Switch.class));
            mappings.put(config + ".PROVINCE_CANDIDATE_FILTER_ENABLED", "enabled");
            ClassNode source = node(CACHE);
            for (var method : source.methods) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof FieldInsnNode field
                            && field.owner.equals(GAME + "CFG")
                            && field.name.equals("map")) {
                        mappings.put(Type.getType(field.desc).getInternalName(), Type.getInternalName(MapState.class));
                    }
                }
            }
            Map<String, byte[]> definitions = new HashMap<>();
            for (String name : List.of(
                    MANAGER,
                    MANAGER + "$1",
                    MANAGER + "$2",
                    MANAGER + "$3",
                    CACHE,
                    CACHE.replace("ProvinceFileCache", "ProvinceCandidates"),
                    CACHE.replace("ProvinceFileCache", "ProvinceCandidates$Root"),
                    CACHE.replace("ProvinceFileCache", "ProvinceCandidates$Directory"),
                    CACHE.replace("ProvinceFileCache", "ProvinceCandidates$Diagnostics"),
                    CACHE.replace("ProvinceFileCache", "ClasspathDirectories"))) {
                ClassNode target = new ClassNode();
                node(name).accept(new ClassRemapper(target, new SimpleRemapper(mappings)));
                if (name.equals(MANAGER)) {
                    ClassNode mixin = new ClassNode();
                    node(Type.getInternalName(MixinFileManagerCandidates.class))
                            .accept(new ClassRemapper(mixin, new SimpleRemapper(mappings)));
                    new MixinProcessor(mixin, target).process();
                    new NewFieldProcessor(mixin, target).process();
                    new InjectProcessor(target).process();
                    new InjectProcessor(target).process();
                }
                if (name.equals(MANAGER) || name.startsWith(MANAGER + "$")) {
                    target.nestHostClass = null;
                    target.outerClass = null;
                    target.outerMethod = null;
                    target.outerMethodDesc = null;
                    target.innerClasses.clear();
                }
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                target.accept(writer);
                definitions.put(target.name.replace('/', '.'), writer.toByteArray());
            }
            ClassLoader loader = new ClassLoader(ProvinceFileCacheTest.class.getClassLoader()) {
                @Override
                protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> type = findLoadedClass(name);
                        if (type == null) {
                            byte[] bytes = definitions.get(name);
                            type = bytes == null
                                    ? super.loadClass(name, false)
                                    : defineClass(name, bytes, 0, bytes.length);
                        }
                        if (resolve) resolveClass(type);
                        return type;
                    }
                }
            };
            manager = loader.loadClass(MANAGER.replace('/', '.'));
            cache = loader.loadClass(CACHE.replace('/', '.'));
            manager.getField("IS_MAC").setBoolean(null, platform == 1);
            manager.getMethod("initLoadInterface").invoke(null);
            original = cache.isInstance(resolver())
                    ? (Resolver) field(cache, "original").get(resolver())
                    : resolver();
        }

        Resolver resolver() throws Exception {
            return (Resolver) manager.getField("loadInterface").get(null);
        }

        void setResolver(Resolver resolver) throws Exception {
            manager.getField("loadInterface").set(null, resolver);
        }

        FileHandle load(String path) throws Exception {
            return resolver().loadFile(path);
        }

        void begin(Object owner) throws Exception {
            call("beginFrame", owner);
        }

        void call(String method) throws Exception {
            cache.getMethod(method).invoke(null);
        }

        void call(String method, Object owner) throws Exception {
            cache.getMethod(method, Object.class).invoke(null, owner);
        }

        Object state(String name) throws Exception {
            return field(cache, name).get(resolver());
        }

        // Gradle's unknown resource loader intentionally disables local pruning. Supply an exact,
        // empty URL loader's real inventory, not a more permissive production loader whitelist.
        void knownClasspath() throws Exception {
            var context = cache.getDeclaredMethod("context");
            context.setAccessible(true);
            Object candidates = context.invoke(resolver());
            Class<?> inventory = cache.getClassLoader()
                    .loadClass(CACHE.replace('/', '.').replace("ProvinceFileCache", "ClasspathDirectories"));
            var capture = inventory.getDeclaredMethod("capture", ClassLoader.class);
            capture.setAccessible(true);
            try (URLClassLoader loader = new URLClassLoader(new URL[0], ClassLoader.getPlatformClassLoader())) {
                Object snapshot = capture.invoke(null, loader);
                assertNotNull(snapshot);
                field(candidates.getClass(), "classpath").set(candidates, snapshot);
            }
        }

        private static Field field(Class<?> type, String name) throws Exception {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }

        private static ClassNode node(String name) throws IOException {
            try (InputStream input = ProvinceFileCacheTest.class.getResourceAsStream("/" + name + ".class")) {
                if (input == null) throw new IOException("Missing class resource: " + name);
                ClassNode node = new ClassNode();
                new ClassReader(input).accept(node, 0);
                return node;
            }
        }
    }
}
