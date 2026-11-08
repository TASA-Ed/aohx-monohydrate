package top.tasaed.aohx.monohydrate.profiler;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import team.rainfall.finality.luminosity2.processor.InjectProcessor;
import team.rainfall.finality.luminosity2.processor.MixinProcessor;
import team.rainfall.finality.luminosity2.processor.NewFieldProcessor;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler.Point;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler.Report;
import top.tasaed.aohx.monohydrate.profiler.mixins.MixinCoreProvinceProfile;

public class MixinCoreProvinceProfileTest {
    private final List<Report> reports = new ArrayList<>();
    private final LoadingProfiler profiler = new LoadingProfiler(() -> Globals.now, reports::add, () -> null);

    @BeforeEach
    void reset() {
        Globals.now = 0;
        Globals.failure = "";
        Globals.path = null;
        Globals.map = new MapFixture();
    }

    @Test
    void laterCoreMixinDoesNotDuplicateStages() throws Exception {
        Target target = transform();
        profiler.beginFrame();
        for (int id = 0; id < 3; id++) {
            profiler.begin(Point.LOAD_PROVINCE);
            target.loadProvince(id);
            profiler.end(Point.LOAD_PROVINCE);
        }
        Report report = finish();
        assertTrue(report.accounting().timingComplete());
        assertEquals(0, report.accounting().unreturnedStages());
        assertEquals(0, report.accounting().unexpectedStageEndCalls());
        long[] durations = {10, 20, 30, 40};
        for (int i = 0; i < durations.length; i++) {
            var stage = report.stages().get(i);
            assertEquals(3, stage.calls(), stage.method());
            assertEquals(3, stage.returnedCalls(), stage.method());
            assertEquals(0, stage.unreturnedCalls(), stage.method());
            assertEquals(3 * durations[i], stage.totalNanos(), stage.method());
        }
        assertEquals(
                List.of(0, 1, 2), target.provinces().stream().map(p -> p.id).toList());
        assertSame(Globals.DATA, target.provinces().get(0).data);
        assertEquals("map/test/data/provinces/2", Globals.path);
        assertEquals(0, target.rebuilds);
        assertEquals(17, target.otherPlugin());
    }

    @Test
    void readAndDeserializeFailuresRebuildWithoutCompletingFailedStage() throws Exception {
        for (String failure : List.of("read", "deserialize")) {
            reset();
            reports.clear();
            Target target = transform();
            List<Report> results = new ArrayList<>();
            LoadingProfiler p = new LoadingProfiler(() -> Globals.now, results::add, () -> null);
            Globals.failure = failure;
            p.beginFrame();
            p.begin(Point.LOAD_PROVINCE);
            target.loadProvince(7);
            p.end(Point.LOAD_PROVINCE);
            p.markComplete();
            p.endFrame();
            Report report = results.get(0);
            var failed = report.stages().get(failure.equals("read") ? 1 : 2);
            assertEquals(1, failed.calls());
            assertEquals(0, failed.returnedCalls());
            assertEquals(0, failed.totalNanos());
            assertEquals(1, report.stages().get(4).returnedCalls());
            assertEquals(50, report.stages().get(4).totalNanos());
            assertEquals(1, report.accounting().unreturnedStages());
            assertEquals(0, report.accounting().unexpectedStageEndCalls());
            assertFalse(report.accounting().timingComplete());
            assertEquals(1, target.rebuilds);
            assertEquals(7, target.rebuiltId);
            assertTrue(target.provinces().isEmpty());
        }
    }

    @Test
    void missingClassDoesNotRebuild() throws Exception {
        Target target = transform();
        Globals.failure = "class";
        profiler.beginFrame();
        profiler.begin(Point.LOAD_PROVINCE);
        target.loadProvince(2);
        profiler.end(Point.LOAD_PROVINCE);
        Report report = finish();
        assertEquals(1, report.stages().get(2).unreturnedCalls());
        assertEquals(0, report.stages().get(4).calls());
        assertEquals(0, target.rebuilds);
        assertTrue(target.provinces().isEmpty());
    }

    @Test
    void pathFailurePropagatesWithoutRebuilding() throws Exception {
        Target target = transform();
        Globals.failure = "path";
        profiler.beginFrame();
        profiler.begin(Point.LOAD_PROVINCE);
        assertThrows(GdxRuntimeException.class, () -> target.loadProvince(3));
        Report report = finish();
        assertEquals(1, report.stages().get(0).unreturnedCalls());
        assertEquals(0, report.stages().get(1).calls());
        assertEquals(0, target.rebuilds);
    }

    @Test
    void rebuildFailurePropagatesAndRemainsUnreturned() throws Exception {
        Target target = transform();
        Globals.failure = "rebuild";
        profiler.beginFrame();
        profiler.begin(Point.LOAD_PROVINCE);
        assertThrows(IllegalStateException.class, () -> target.loadProvince(4));
        Report report = finish();
        assertEquals(2, report.accounting().unreturnedStages());
        assertEquals(1, report.stages().get(4).calls());
        assertEquals(0, report.stages().get(4).returnedCalls());
        assertEquals(0, report.accounting().unexpectedStageEndCalls());
    }

    private Report finish() {
        profiler.markComplete();
        profiler.endFrame();
        return reports.get(0);
    }

    static Target transform() throws Exception {
        String game = "age/of/civilizations2/jakowski/lukasz/";
        ClassNode source = remap(
                node(MixinCoreProvinceProfile.class),
                Map.of(
                        game + "CFG", Type.getInternalName(Globals.class),
                        game + "Map", Type.getInternalName(MapFixture.class),
                        game + "Files/FileManager", Type.getInternalName(FilesFixture.class),
                        game + "Province", Type.getInternalName(ProvinceFixture.class),
                        game + "Province_GameData2", Type.getInternalName(Data.class)));
        // City behavior has its own integration test; exercise the complete province implementation here.
        source.methods.removeIf(m -> m.name.equals("buildCitiesProvinceID"));
        ClassNode target = node(Receiver.class);
        target.nestHostClass = null;
        target.innerClasses.clear();
        new MixinProcessor(source, target).process();
        new NewFieldProcessor(source, target).process();
        new InjectProcessor(target).process();
        // The real loader repeats the processors for another plugin targeting the same Core class.
        ClassNode other = node(OtherMixin.class);
        new MixinProcessor(other, target).process();
        new NewFieldProcessor(other, target).process();
        new InjectProcessor(target).process();
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        target.accept(writer);
        byte[] bytes = writer.toByteArray();
        Class<?> type = new ClassLoader(MixinCoreProvinceProfileTest.class.getClassLoader()) {
            Class<?> define() {
                return defineClass(target.name.replace('/', '.'), bytes, 0, bytes.length);
            }
        }.define();
        return (Target) type.getConstructor().newInstance();
    }

    private static ClassNode node(Class<?> type) throws IOException {
        try (InputStream in = type.getResourceAsStream("/" + Type.getInternalName(type) + ".class")) {
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, 0);
            return node;
        }
    }

    private static ClassNode remap(ClassNode node, Map<String, String> types) {
        ClassNode result = new ClassNode();
        node.accept(new ClassRemapper(result, new SimpleRemapper(types)));
        return result;
    }

    public static class Data {}

    public static class MapFixture {
        public String getFileActiveMapPath() {
            return "test/";
        }
    }

    public static class Globals {
        public static long now;
        public static String failure;
        public static String path;
        public static boolean LOGs = false;
        public static MapFixture map;
        public static final Data DATA = new Data();

        public static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
            now += 30;
            if (failure.equals("deserialize")) throw new IOException("corrupt province");
            if (failure.equals("class")) throw new ClassNotFoundException("unknown province class");
            return DATA;
        }
    }

    public static class FilesFixture {
        public static FileHandle loadFile(String path) {
            Globals.path = path;
            Globals.now += 10;
            if (Globals.failure.equals("path")) throw new GdxRuntimeException("path failure");
            return new FileHandle() {
                @Override
                public byte[] readBytes() {
                    Globals.now += 20;
                    if (Globals.failure.equals("read") || Globals.failure.equals("rebuild"))
                        throw new GdxRuntimeException("missing province");
                    return new byte[] {1};
                }
            };
        }
    }

    public static class ProvinceFixture {
        final int id;
        final Data data;

        public ProvinceFixture(int id, Data data) {
            this.id = id;
            this.data = data;
            Globals.now += 40;
        }
    }

    public abstract static class Target {
        public abstract List<ProvinceFixture> provinces();

        public int rebuilds;
        public int rebuiltId;

        public void loadProvince(int id) {
            throw new AssertionError("untransformed");
        }

        public int otherPlugin() {
            return 0;
        }
    }

    public static class Receiver extends Target {
        private final List<ProvinceFixture> lProvs = new ArrayList<>();

        @Override
        public List<ProvinceFixture> provinces() {
            return lProvs;
        }

        @Override
        public void loadProvince(int id) {
            FileHandle file =
                    FilesFixture.loadFile("map/" + Globals.map.getFileActiveMapPath() + "data/provinces/" + id);
            try {
                lProvs.add(new ProvinceFixture(id, (Data) Globals.deserialize(file.readBytes())));
            } catch (ClassNotFoundException e) {
                // Original game does not rebuild this case.
            } catch (IOException | GdxRuntimeException e) {
                build_LoadProvince(id);
            }
        }

        private void build_LoadProvince(int id) {
            rebuilds++;
            rebuiltId = id;
            Globals.now += 50;
            if (Globals.failure.equals("rebuild")) throw new IllegalStateException("rebuild failed");
        }
    }

    public static class OtherMixin {
        public int otherPlugin() {
            return 17;
        }
    }
}
