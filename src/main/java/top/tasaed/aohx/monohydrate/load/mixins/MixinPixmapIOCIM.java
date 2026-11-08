package top.tasaed.aohx.monohydrate.load.mixins;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.StreamUtils;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.zip.InflaterInputStream;
import team.rainfall.finality.luminosity2.annotations.Mixin;
import team.rainfall.finality.luminosity2.annotations.Overwrite;
import team.rainfall.finality.luminosity2.annotations.Shadow;
import top.tasaed.aohx.monohydrate.core.MonohydrateConfig;

@Mixin(mixinClass = "com.badlogic.gdx.graphics.PixmapIO$CIM")
public class MixinPixmapIOCIM {
    // Luminosity keeps the target's field and initializer; no new buffer is allocated.
    @Shadow
    private static final byte[] readBuffer = null;

    @Overwrite
    public static Pixmap read(FileHandle file) {
        DataInputStream in = null;
        try {
            in = new DataInputStream(new InflaterInputStream(new BufferedInputStream(file.read())));
            synchronized (readBuffer) {
                int width;
                int height;
                Pixmap.Format format;
                if (MonohydrateConfig.CIM_HEADER_READ_ENABLED) {
                    in.readFully(readBuffer, 0, 12);
                    width = monohydrate$readInt(0);
                    height = monohydrate$readInt(4);
                    format = Pixmap.Format.fromGdx2DPixmapFormat(monohydrate$readInt(8));
                } else {
                    width = in.readInt();
                    height = in.readInt();
                    format = Pixmap.Format.fromGdx2DPixmapFormat(in.readInt());
                }
                Pixmap pixmap = new Pixmap(width, height, format);
                ByteBuffer pixelBuf = pixmap.getPixels();
                ((Buffer) pixelBuf).position(0);
                ((Buffer) pixelBuf).limit(pixelBuf.capacity());
                int readBytes;
                while ((readBytes = in.read(readBuffer)) > 0) {
                    pixelBuf.put(readBuffer, 0, readBytes);
                }
                ((Buffer) pixelBuf).position(0);
                ((Buffer) pixelBuf).limit(pixelBuf.capacity());
                return pixmap;
            }
        } catch (Exception e) {
            throw new GdxRuntimeException("Couldn't read Pixmap from file '" + file + "'", e);
        } finally {
            StreamUtils.closeQuietly(in);
        }
    }

    private static int monohydrate$readInt(int offset) {
        return (readBuffer[offset] & 0xff) << 24
                | (readBuffer[offset + 1] & 0xff) << 16
                | (readBuffer[offset + 2] & 0xff) << 8
                | (readBuffer[offset + 3] & 0xff);
    }
}
