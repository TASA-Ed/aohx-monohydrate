package top.tasaed.aohx.monohydrate.profiler.mixins;

import com.badlogic.gdx.files.FileHandle;
import team.rainfall.finality.luminosity2.annotations.Mixin;
import team.rainfall.finality.luminosity2.annotations.Overwrite;
import top.tasaed.aohx.monohydrate.load.DesktopFileResolver;

@Mixin(mixinClass = "age.of.civilizations2.jakowski.lukasz.Files.FileManager$2")
public class MixinFileManagerDesktop {
    @Overwrite
    public FileHandle loadFile(String sFile) {
        return DesktopFileResolver.load(sFile, false);
    }
}
