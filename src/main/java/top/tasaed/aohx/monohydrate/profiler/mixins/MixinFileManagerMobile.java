package top.tasaed.aohx.monohydrate.profiler.mixins;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import team.rainfall.finality.luminosity2.annotations.Mixin;
import team.rainfall.finality.luminosity2.annotations.Overwrite;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler.PathSource;

@Mixin(mixinClass = "age.of.civilizations2.jakowski.lukasz.Files.FileManager$3")
public class MixinFileManagerMobile {
    @Overwrite
    public FileHandle loadFile(String sFile) {
        FileHandle result = Gdx.files.local(sFile).exists() ? Gdx.files.local(sFile) : Gdx.files.internal(sFile);
        LoadingProfiler.pathResolved(PathSource.VANILLA, null);
        return result;
    }
}
