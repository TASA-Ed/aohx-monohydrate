package top.tasaed.aohx.monohydrate.load;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeBitmapFontData;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.utils.Array;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class SharedBorderFontTest {
    @Test
    void incrementalFreeTypeFontsKeepTheirOriginalLoadingPath() throws Exception {
        FreeTypeBitmapFontData data = new FreeTypeBitmapFontData();
        BitmapFont font = new BitmapFont(data, Array.with(new TextureRegion()), false);
        assertTrue(SharedBorderFont.canShare(font));

        FreeTypeFontParameter parameter = new FreeTypeFontParameter();
        parameter.incremental = true;
        Field field = FreeTypeBitmapFontData.class.getDeclaredField("parameter");
        field.setAccessible(true);
        field.set(data, parameter);
        assertFalse(SharedBorderFont.canShare(font));
        assertFalse(font.ownsTexture());
    }

    @Test
    void customGlyphProvidersAreNotFlattenedIntoStaticData() {
        BitmapFontData data = new BitmapFontData() {
            @Override
            public Glyph getGlyph(char ch) {
                return glyph(ch, 0);
            }
        };
        BitmapFont font = new BitmapFont(data, Array.with(new TextureRegion()), false);
        assertEquals(18, new GlyphLayout(font, "AV").width);
        assertFalse(SharedBorderFont.canShare(font));
        assertTrue(SharedBorderFont.canShare(font()));
        assertFalse(SharedBorderFont.canShare(null));
    }

    @Test
    void scaleAndKerningChangesDoNotAffectTheOtherFont() {
        BitmapFont owner = font();
        BitmapFont copy = SharedBorderFont.copy(owner);
        float originalWidth = new GlyphLayout(owner, "AV").width;
        assertEquals(originalWidth, new GlyphLayout(copy, "AV").width);

        owner.getData().setScale(2);
        assertEquals(originalWidth, new GlyphLayout(copy, "AV").width);
        assertEquals(originalWidth * 2, new GlyphLayout(owner, "AV").width);
        copy.getData().getGlyph('A').setKerning('V', -4);
        assertEquals(-2, owner.getData().getGlyph('A').getKerning('V'));
        assertEquals(originalWidth - 2, new GlyphLayout(copy, "AV").width);
        copy.setFixedWidthGlyphs("AV");
        assertFalse(owner.getData().getGlyph('A').fixedWidth);
        assertEquals(-2, owner.getData().getGlyph('A').getKerning('V'));
    }

    @Test
    void missingGlyphKeepsFallbackAndInternalAliasesWithoutSharingMutations() {
        BitmapFont owner = font();
        Glyph missing = glyph(0, 1);
        owner.getData().missingGlyph = missing;
        owner.getData().setGlyph(0, missing);
        owner.getData().setGlyph('?', missing);
        BitmapFont copy = SharedBorderFont.copy(owner);
        assertEquals(new GlyphLayout(owner, "A\u2603").width, new GlyphLayout(copy, "A\u2603").width);
        assertSame(copy.getData().missingGlyph, copy.getData().getGlyph('?'));
        copy.getData().missingGlyph.xadvance = 99;
        assertEquals(10, owner.getData().missingGlyph.xadvance);
        assertEquals(99, copy.getData().getGlyph((char) 0).xadvance);

        // A missing glyph need not be present in the glyph table.
        owner.getData().missingGlyph = glyph(7, 0);
        BitmapFont detached = SharedBorderFont.copy(owner);
        assertEquals(new GlyphLayout(owner, "\u2603").width, new GlyphLayout(detached, "\u2603").width);
        detached.getData().missingGlyph.width = 3;
        assertEquals(8, owner.getData().missingGlyph.width);
    }

    @Test
    void colorsAndMultiPageCachedVerticesRemainIndependent() {
        BitmapFont owner = font();
        float originalWidth = new GlyphLayout(owner, "AV").width;
        BitmapFont copy = SharedBorderFont.copy(owner);
        owner.setColor(Color.RED);
        copy.setColor(Color.BLUE);
        owner.getCache().setText("AV", 3, 7);
        copy.getCache().setText("VA", 20, 30);
        float[] ownerPage0 = owner.getCache().getVertices(0).clone();
        float[] ownerPage1 = owner.getCache().getVertices(1).clone();
        copy.getCache().translate(10, 20);
        copy.getCache().setColors(Color.GREEN);
        copy.getCache().clear();
        assertEquals(Color.RED, owner.getColor());
        assertArrayEquals(ownerPage0, owner.getCache().getVertices(0));
        assertArrayEquals(ownerPage1, owner.getCache().getVertices(1));
        assertEquals(20, owner.getCache().getVertexCount(0));
        assertEquals(20, owner.getCache().getVertexCount(1));
        assertFalse(copy.ownsTexture());
        // Borrower disposal cannot touch the texture pages (these CPU-only regions have no texture).
        copy.dispose();
        assertEquals(originalWidth, new GlyphLayout(owner, "AV").width);
    }

    private static BitmapFont font() {
        BitmapFontData data = new BitmapFontData();
        data.lineHeight = 14;
        data.capHeight = 10;
        data.xHeight = 8;
        data.ascent = 2;
        data.descent = -2;
        data.down = -14;
        data.spaceXadvance = 5;
        BitmapFont font = new BitmapFont(data, Array.with(new TextureRegion(), new TextureRegion()), false);
        Glyph a = glyph('A', 0);
        a.setKerning('V', -2);
        data.setGlyph('A', a);
        data.setGlyph('V', glyph('V', 1));
        return font;
    }

    private static Glyph glyph(int id, int page) {
        Glyph glyph = new Glyph();
        glyph.id = id;
        glyph.page = page;
        glyph.width = 8;
        glyph.height = 10;
        glyph.xadvance = 10;
        glyph.yoffset = -10;
        glyph.u = 0.1f;
        glyph.v = 0.2f;
        glyph.u2 = 0.3f;
        glyph.v2 = 0.4f;
        return glyph;
    }
}
