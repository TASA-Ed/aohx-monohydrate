package top.tasaed.aohx.monohydrate.load;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.zip.DeflaterOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import team.rainfall.finality.luminosity2.processor.MixinProcessor;
import team.rainfall.finality.luminosity2.processor.NewFieldProcessor;
import top.tasaed.aohx.monohydrate.load.mixins.MixinPixmapIOCIM;

class MixinPixmapIOCIMTest {
    private static Method reader;

    @BeforeAll
    static void prepare() throws Exception {
        GdxNativesLoader.load();
        ClassNode source = readClass(MixinPixmapIOCIM.class.getName());
        ClassNode original = readClass("com.badlogic.gdx.graphics.PixmapIO$CIM");
        String name = "top/tasaed/aohx/monohydrate/load/TransformedCIM";
        ClassNode target = new ClassNode();
        original.accept(new ClassRemapper(target, new SimpleRemapper(Map.of(original.name, name))));
        target.nestHostClass = null;
        target.innerClasses.clear();
        new MixinProcessor(source, target).process();
        new NewFieldProcessor(source, target).process();
        ClassWriter writer = new ClassWriter(0);
        target.accept(writer);
        byte[] bytecode = writer.toByteArray();
        Class<?> transformed = new ClassLoader(MixinPixmapIOCIMTest.class.getClassLoader()) {
            Class<?> define() {
                return defineClass(name.replace('/', '.'), bytecode, 0, bytecode.length);
            }
        }.define();
        reader = transformed.getDeclaredMethod("read", FileHandle.class);
        reader.setAccessible(true);
    }

    private static ClassNode readClass(String name) throws IOException {
        try (InputStream stream =
                MixinPixmapIOCIMTest.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    static Pixmap read(FileHandle file) throws Exception {
        try {
            return (Pixmap) reader.invoke(null, file);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    @Test
    void decodesBigEndianDimensionsAllFormatsAndMultiplePixelBatches() throws Exception {
        for (Pixmap.Format format : Pixmap.Format.values()) {
            Pixmap expected = new Pixmap(257, 129, format);
            try {
                byte[] pixels = new byte[expected.getPixels().capacity()];
                new Random(71).nextBytes(pixels);
                MemoryFile file = new MemoryFile(compress(headerAndPixels(257, 129, format, pixels)));
                Pixmap actual = read(file);
                try {
                    assertEquals(257, actual.getWidth());
                    assertEquals(129, actual.getHeight());
                    assertEquals(expected.getFormat(), actual.getFormat());
                    ByteBuffer buffer = actual.getPixels();
                    assertEquals(0, buffer.position());
                    assertEquals(buffer.capacity(), buffer.limit());
                    byte[] decoded = new byte[buffer.remaining()];
                    buffer.get(decoded);
                    assertArrayEquals(pixels, decoded);
                } finally {
                    actual.dispose();
                }
                assertTrue(file.closed);
            } finally {
                expected.dispose();
            }
        }
    }

    @Test
    void rejectsEveryTruncatedHeaderWithoutReusingPreviousHeaderBytes() throws Exception {
        byte[] full = headerAndPixels(1, 1, Pixmap.Format.RGBA8888, new byte[] {1, 2, 3, 4});
        for (int length = 0; length < 12; length++) {
            read(new MemoryFile(compress(full))).dispose();
            MemoryFile file = new MemoryFile(compress(Arrays.copyOf(full, length)));
            GdxRuntimeException error = assertThrows(GdxRuntimeException.class, () -> read(file));
            assertEquals("Couldn't read Pixmap from file 'mods/chosen/image.cim'", error.getMessage());
            assertInstanceOf(EOFException.class, error.getCause());
            assertTrue(file.closed);
        }
    }

    @Test
    void preservesFileHandleOpenFailureAndCause() {
        GdxRuntimeException cause = new GdxRuntimeException("original resolver failure");
        FileHandle file = new FileHandle("mods/chosen/image.cim") {
            @Override
            public InputStream read() {
                throw cause;
            }
        };
        GdxRuntimeException error = assertThrows(GdxRuntimeException.class, () -> read(file));
        assertSame(cause, error.getCause());
        assertEquals("Couldn't read Pixmap from file 'mods/chosen/image.cim'", error.getMessage());
    }

    @Test
    void preservesReadFailureAndClosesStream() {
        IOException cause = new IOException("broken input");
        boolean[] closed = {false};
        FileHandle file = new FileHandle("mods/chosen/image.cim") {
            @Override
            public InputStream read() {
                return new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw cause;
                    }

                    @Override
                    public void close() {
                        closed[0] = true;
                    }
                };
            }
        };
        GdxRuntimeException error = assertThrows(GdxRuntimeException.class, () -> read(file));
        assertSame(cause, error.getCause());
        assertTrue(closed[0]);
    }

    private static byte[] headerAndPixels(int width, int height, Pixmap.Format format, byte[] pixels)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(width);
            out.writeInt(height);
            out.writeInt(Pixmap.Format.toGdx2DPixmapFormat(format));
            out.write(pixels);
        }
        return bytes.toByteArray();
    }

    private static byte[] compress(byte[] bytes) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream out = new DeflaterOutputStream(compressed)) {
            out.write(bytes);
        }
        return compressed.toByteArray();
    }

    private static final class MemoryFile extends FileHandle {
        private final byte[] compressed;
        private boolean closed;

        MemoryFile(byte[] compressed) {
            super("mods/chosen/image.cim");
            this.compressed = compressed;
        }

        @Override
        public InputStream read() {
            return new ByteArrayInputStream(compressed) {
                @Override
                public synchronized int read(byte[] target, int offset, int length) {
                    // Force fragmented compressed input, including during readFully(header).
                    return super.read(target, offset, Math.min(length, 3));
                }

                @Override
                public void close() {
                    closed = true;
                }
            };
        }
    }
}
