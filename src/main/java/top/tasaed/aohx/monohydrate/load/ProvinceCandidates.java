package top.tasaed.aohx.monohydrate.load;

import age.of.civilizations2.jakowski.lukasz.Files.FileManager;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Files.FileType;
import com.badlogic.gdx.files.FileHandle;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import team.rainfall.finality.FinalityLogger;
import top.tasaed.aohx.monohydrate.core.MonohydrateConfig;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler.PathSource;

/** One map/mod snapshot in one loading session; never caches individual file hits or misses. */
final class ProvinceCandidates {
    private static final String[] MAP_DIRECTORIES = {
        "/provinces/", "/army_boxes/", "/civs_template/", "/suggested_owners/", "/formable_civs/"
    };
    private static final Root[] UNCACHEABLE = new Root[0];
    final String map;
    final String[] local;
    final String[] workshop;
    private final Files files;
    private final boolean mac;
    private final Directory data;
    private final String texturePrefix;
    private final Root[] roots;
    private final List<Directory> directories = new ArrayList<>();
    private ClasspathDirectories classpath;
    private final Diagnostics diagnostics;

    /** Shared by all map/mod snapshots within one loading session. */
    static final class Diagnostics {
        private boolean failureReported;

        void failed(String directory, String root, Exception cause) {
            if (failureReported) return;
            failureReported = true;
            FinalityLogger.error(
                    "[Monohydrate] Province candidate cache build failed; using original resolver: directory="
                            + directory + ", root=" + root + ", reason=" + cause,
                    cause);
        }
    }

    private record Root(FileType type, String prefix, PathSource source, String folder, int ordinal) {}

    private static final class Directory {
        final String path;
        final boolean numeric;
        // null: not built; UNCACHEABLE: permanently delegated for this snapshot.
        Root[] candidates;

        Directory(String path, boolean numeric) {
            this.path = path;
            this.numeric = numeric;
        }

        void reset() {
            if (candidates != UNCACHEABLE) candidates = null;
        }
    }

    ProvinceCandidates(
            Files files, boolean mac, String map, String[] local, String[] workshop, Diagnostics diagnostics) {
        this.files = files;
        this.mac = mac;
        this.map = map;
        this.local = local;
        this.workshop = workshop;
        this.diagnostics = diagnostics;
        data = new Directory("map/" + map + "/data/provinces/", true);
        directories.add(data);
        texturePrefix = "map/" + map + "/data/scales/provinces/";
        for (String directory : MAP_DIRECTORIES) {
            directories.add(new Directory("map/" + map + directory, false));
        }
        List<Root> ordered = new ArrayList<>();
        for (String folder : local) {
            if (mac) ordered.add(new Root(FileType.External, folder, PathSource.LOCAL, folder, ordered.size()));
            ordered.add(new Root(FileType.Internal, folder, PathSource.LOCAL, folder, ordered.size()));
        }
        for (String folder : workshop) {
            ordered.add(new Root(FileType.Absolute, folder + "/", PathSource.WORKSHOP, folder, ordered.size()));
        }
        roots = ordered.toArray(Root[]::new);
    }

    static boolean targetDirectory(String path) {
        for (String directory : MAP_DIRECTORIES) {
            if (path.contains(directory)) return true;
        }
        return false;
    }

    FileHandle load(String path, FileManager.LoadInterface original) {
        Directory directory = directory(path);
        if (directory == null || directory.candidates == UNCACHEABLE) return delegate(path, original);
        if (directory.candidates == null) {
            long start = MonohydrateConfig.PROFILER_ENABLED ? System.nanoTime() : 0;
            directory.candidates = build(directory);
            boolean built = directory.candidates != UNCACHEABLE;
            if (MonohydrateConfig.PROFILER_ENABLED) {
                int retained = built ? directory.candidates.length : 0;
                LoadingProfiler.pathCacheBuilt(
                        System.nanoTime() - start, built, retained, built ? roots.length - retained : 0);
            }
            if (!built) return delegate(path, original);
        }
        int next = 0;
        for (Root root : directory.candidates) {
            LoadingProfiler.pathFilterSkips(root.ordinal - next);
            next = root.ordinal + 1;
            FileHandle candidate = files.getFileHandle(root.prefix + path, root.type);
            LoadingProfiler.pathProbe(root.source);
            if (candidate.exists()) {
                FileHandle result = files.getFileHandle(root.prefix + path, root.type);
                LoadingProfiler.pathResolved(root.source, root.folder);
                return result;
            }
        }
        LoadingProfiler.pathFilterSkips(roots.length - next);
        FileHandle result;
        if (mac && files.external(path).exists()) {
            result = files.external(path);
        } else {
            result = files.internal(path);
        }
        LoadingProfiler.pathResolved(PathSource.VANILLA, null);
        return result;
    }

    private FileHandle delegate(String path, FileManager.LoadInterface original) {
        LoadingProfiler.pathOriginalResolverCall();
        return original.loadFile(path);
    }

    private Root[] build(Directory directory) {
        List<Root> candidates = new ArrayList<>();
        Root inspected = null;
        try {
            for (Root root : roots) {
                inspected = root;
                FileHandle handle = files.getFileHandle(root.prefix + directory.path, root.type);
                Path physical = handle.file().toPath();
                // notExists distinguishes a proven miss from permissions/I/O uncertainty.
                boolean present = !java.nio.file.Files.notExists(physical);
                if (!present && root.type == FileType.Internal) {
                    if (classpath == null) {
                        // A failed capture must not be retried for every directory in this snapshot.
                        classpath = ClasspathDirectories.UNAVAILABLE;
                        classpath = ClasspathDirectories.capture();
                    }
                    if (classpath == ClasspathDirectories.UNAVAILABLE) return UNCACHEABLE;
                    present = classpath.contains(handle.path() + "/");
                }
                if (present) candidates.add(root);
            }
        } catch (IOException | RuntimeException ex) {
            diagnostics.failed(directory.path, inspected == null ? null : inspected.type + ":" + inspected.prefix, ex);
            return UNCACHEABLE;
        }
        return candidates.toArray(Root[]::new);
    }

    private Directory directory(String path) {
        if (path == null) return null;
        for (int i = 0; i < directories.size(); i++) {
            Directory directory = directories.get(i);
            if (path.startsWith(directory.path)) {
                return (directory.numeric
                                ? digits(path, directory.path.length(), path.length())
                                : filename(path, directory.path.length()))
                        ? directory
                        : null;
            }
        }
        if (!path.startsWith(texturePrefix)) return null;
        int slash = path.indexOf('/', texturePrefix.length());
        if (slash < 0 || !digits(path, texturePrefix.length(), slash) || !digits(path, slash + 1, path.length())) {
            return null;
        }
        Directory directory = new Directory(path.substring(0, slash + 1), true);
        directories.add(directory);
        return directory;
    }

    private static boolean filename(String path, int start) {
        int length = path.length() - start;
        if (length == 0
                || (length == 1 && path.charAt(start) == '.')
                || (length == 2 && path.charAt(start) == '.' && path.charAt(start + 1) == '.')) return false;
        for (int i = start; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '/' || c == '\\' || c == ':') return false;
        }
        return true;
    }

    private static boolean digits(String value, int start, int end) {
        if (start == end) return false;
        for (int i = start; i < end; i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    void invalidate(String path) {
        // A returned save handle may be written later: do not rebuild its negative record this session.
        int start = path == null ? -1 : path.indexOf("map/" + map + "/");
        if (start < 0) return;
        Directory directory = directory(path.substring(start));
        if (directory != null) directory.candidates = UNCACHEABLE;
    }

    void invalidateData() {
        data.reset();
    }

    void invalidateTextures() {
        for (Directory directory : directories) {
            if (directory.path.startsWith(texturePrefix)) directory.reset();
        }
    }
}
