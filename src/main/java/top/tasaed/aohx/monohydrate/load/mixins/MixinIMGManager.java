package top.tasaed.aohx.monohydrate.load.mixins;

import age.of.civilizations2.jakowski.lukasz.IMGManager;
import age.of.civilizations2.jakowski.lukasz.Image;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import team.rainfall.finality.luminosity2.annotations.Mixin;
import team.rainfall.finality.luminosity2.annotations.Overwrite;
import top.tasaed.aohx.monohydrate.core.MonohydrateConfig;

@Mixin(mixinClass = "age.of.civilizations2.jakowski.lukasz.IMGManager")
public class MixinIMGManager {
    @Overwrite
    public static final int addIMG(String imageName, Texture.TextureFilter filter) {
        return IMGManager.addIMG(
                imageName, MonohydrateConfig.NATIVE_TEXTURE_FORMAT_ENABLED ? null : Pixmap.Format.RGBA8888, filter);
    }

    @Overwrite
    public static final int addIMG2(String imageName, Texture.TextureFilter filter) {
        return IMGManager.addIMG2(
                imageName, MonohydrateConfig.NATIVE_TEXTURE_FORMAT_ENABLED ? null : Pixmap.Format.RGBA8888, filter);
    }

    @Overwrite
    public static final Image loadImage(String imageName) {
        return IMGManager.loadImage(
                imageName,
                MonohydrateConfig.NATIVE_TEXTURE_FORMAT_ENABLED ? null : Pixmap.Format.RGBA8888,
                Texture.TextureFilter.Linear);
    }

    @Overwrite
    public static final Image loadImage(String imageName, Texture.TextureFilter filter) {
        return IMGManager.loadImage(
                imageName, MonohydrateConfig.NATIVE_TEXTURE_FORMAT_ENABLED ? null : Pixmap.Format.RGBA8888, filter);
    }

    @Overwrite
    public static final Texture loadTexture(String file) {
        return IMGManager.loadTexture(
                file, MonohydrateConfig.NATIVE_TEXTURE_FORMAT_ENABLED ? null : Pixmap.Format.RGBA8888);
    }
}
