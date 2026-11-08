package top.tasaed.aohx.monohydrate.load;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeBitmapFontData;
import java.lang.reflect.Field;
import team.rainfall.finality.FinalityLogger;

/** Copies a freshly generated, non-incremental font without owning its texture pages. */
public final class SharedBorderFont {
    private static final Field INCREMENTAL_PARAMETER = incrementalParameterField();

    private SharedBorderFont() {}

    public static boolean canShare(BitmapFont source) {
        if (source == null) return false;
        BitmapFontData data = source.getData();
        if (data.getClass() == BitmapFontData.class) return true;
        if (data.getClass() != FreeTypeBitmapFontData.class || INCREMENTAL_PARAMETER == null) return false;
        try {
            // FreeType retains parameters only when it must generate additional glyphs on demand.
            return INCREMENTAL_PARAMETER.get(data) == null;
        } catch (IllegalAccessException exception) {
            FinalityLogger.error("[Monohydrate] Cannot inspect incremental border font", exception);
            return false;
        }
    }

    private static Field incrementalParameterField() {
        try {
            Field field = FreeTypeBitmapFontData.class.getDeclaredField("parameter");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            FinalityLogger.error("[Monohydrate] Cannot inspect FreeType font sharing compatibility", exception);
            return null;
        }
    }

    public static BitmapFont copy(BitmapFont source) {
        BitmapFontData original = source.getData();
        BitmapFontData data = new BitmapFontData();
        data.name = original.name;
        data.imagePaths = original.imagePaths == null ? null : original.imagePaths.clone();
        data.fontFile = original.fontFile;
        data.flipped = original.flipped;
        data.padTop = original.padTop;
        data.padRight = original.padRight;
        data.padBottom = original.padBottom;
        data.padLeft = original.padLeft;
        data.lineHeight = original.lineHeight;
        data.capHeight = original.capHeight;
        data.ascent = original.ascent;
        data.descent = original.descent;
        data.down = original.down;
        data.blankLineScale = original.blankLineScale;
        data.scaleX = original.scaleX;
        data.scaleY = original.scaleY;
        data.markupEnabled = original.markupEnabled;
        data.cursorX = original.cursorX;
        data.spaceXadvance = original.spaceXadvance;
        data.xHeight = original.xHeight;
        data.breakChars = original.breakChars == null ? null : original.breakChars.clone();
        data.xChars = original.xChars == null ? null : original.xChars.clone();
        data.capChars = original.capChars == null ? null : original.capChars.clone();

        // Construct before installing glyphs: BitmapFont.load would remap already mapped UVs
        // and could apply atlas clipping/offset adjustments a second time.
        BitmapFont copy = new BitmapFont(data, source.getRegions(), source.usesIntegerPositions());
        copy.setOwnsTexture(false);
        copy.setColor(source.getColor());
        data.missingGlyph = original.missingGlyph == null ? null : copyGlyph(original.missingGlyph);
        for (int page = 0; page < original.glyphs.length; page++) {
            Glyph[] originalPage = original.glyphs[page];
            if (originalPage == null) continue;
            Glyph[] copiedPage = new Glyph[originalPage.length];
            data.glyphs[page] = copiedPage;
            for (int index = 0; index < originalPage.length; index++) {
                Glyph glyph = originalPage[index];
                if (glyph == null) continue;
                // FreeType may use missingGlyph for several unsupported characters.
                copiedPage[index] = glyph == original.missingGlyph ? data.missingGlyph : copyGlyph(glyph);
            }
        }
        return copy;
    }

    private static Glyph copyGlyph(Glyph original) {
        Glyph glyph = new Glyph();
        glyph.id = original.id;
        glyph.srcX = original.srcX;
        glyph.srcY = original.srcY;
        glyph.width = original.width;
        glyph.height = original.height;
        glyph.u = original.u;
        glyph.v = original.v;
        glyph.u2 = original.u2;
        glyph.v2 = original.v2;
        glyph.xoffset = original.xoffset;
        glyph.yoffset = original.yoffset;
        glyph.xadvance = original.xadvance;
        glyph.fixedWidth = original.fixedWidth;
        glyph.page = original.page;
        if (original.kerning != null) {
            glyph.kerning = new byte[original.kerning.length][];
            for (int page = 0; page < original.kerning.length; page++) {
                byte[] kerning = original.kerning[page];
                if (kerning != null) glyph.kerning[page] = kerning.clone();
            }
        }
        return glyph;
    }
}
