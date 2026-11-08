package top.tasaed.aohx.monohydrate.load;

import age.of.civilizations2.jakowski.lukasz.Z_Other.ST.sUM;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler.PathSource;

/** Original desktop lookup order; Mac additionally checks external files before internal files. */
public final class DesktopFileResolver {
    private DesktopFileResolver() {}

    public static FileHandle load(String path, boolean mac) {
        for (int i = 0; i < sUM.sUFS; i++) {
            if (mac) {
                FileHandle candidate = Gdx.files.external(sUM.sUF.get(i) + path);
                LoadingProfiler.pathProbe(PathSource.LOCAL);
                if (candidate.exists()) {
                    String folder = sUM.sUF.get(i);
                    FileHandle result = Gdx.files.external(folder + path);
                    LoadingProfiler.pathResolved(PathSource.LOCAL, folder);
                    return result;
                }
            }
            FileHandle candidate = Gdx.files.internal(sUM.sUF.get(i) + path);
            LoadingProfiler.pathProbe(PathSource.LOCAL);
            if (candidate.exists()) {
                String folder = sUM.sUF.get(i);
                FileHandle result = Gdx.files.internal(folder + path);
                LoadingProfiler.pathResolved(PathSource.LOCAL, folder);
                return result;
            }
        }
        for (int i = 0; i < sUM.sUIIS; i++) {
            FileHandle candidate = Gdx.files.absolute(sUM.sUII.get(i).getFolder() + "/" + path);
            LoadingProfiler.pathProbe(PathSource.WORKSHOP);
            if (candidate.exists()) {
                String folder = sUM.sUII.get(i).getFolder();
                FileHandle result = Gdx.files.absolute(folder + "/" + path);
                LoadingProfiler.pathResolved(PathSource.WORKSHOP, folder);
                return result;
            }
        }
        FileHandle result =
                mac && Gdx.files.external(path).exists() ? Gdx.files.external(path) : Gdx.files.internal(path);
        LoadingProfiler.pathResolved(PathSource.VANILLA, null);
        return result;
    }
}
