package top.tasaed.aohx.monohydrate.load;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Files.FileManager;
import age.of.civilizations2.jakowski.lukasz.Z_Other.ST.sUM;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files;
import com.badlogic.gdx.files.FileHandle;
import top.tasaed.aohx.monohydrate.core.MonohydrateConfig;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler;

/** Installed only after the game's default desktop resolver has initialized. */
public final class ProvinceFileCache implements FileManager.LoadInterface {
    private static ProvinceFileCache installed;
    private final FileManager.LoadInterface original;
    private final boolean mac;
    private Object session;
    private Thread thread;
    private boolean inFrame;
    private Files files;
    private Object mapIdentity;
    private ProvinceCandidates candidates;
    private ProvinceCandidates.Diagnostics diagnostics;

    private ProvinceFileCache(FileManager.LoadInterface original, boolean mac) {
        this.original = original;
        this.mac = mac;
    }

    public static void install() {
        if (installed != null) installed.clear();
        installed = null;
        if (!MonohydrateConfig.PROVINCE_CANDIDATE_FILTER_ENABLED) return;
        FileManager.LoadInterface resolver = FileManager.loadInterface;
        if (resolver instanceof ProvinceFileCache cache) {
            installed = cache;
            return;
        }
        if (resolver == null) return;
        Class<?> type = resolver.getClass();
        if (type.getClassLoader() != FileManager.class.getClassLoader()) return;
        String name = type.getName();
        boolean mac = name.equals("age.of.civilizations2.jakowski.lukasz.Files.FileManager$1");
        if (!mac && !name.equals("age.of.civilizations2.jakowski.lukasz.Files.FileManager$2")) return;
        installed = new ProvinceFileCache(resolver, mac);
        FileManager.loadInterface = installed;
    }

    public static void beginFrame(Object owner) {
        ProvinceFileCache cache = current();
        if (cache == null) return;
        if (cache.session != owner || cache.thread != Thread.currentThread()) {
            cache.clear();
            cache.session = owner;
            cache.thread = Thread.currentThread();
        }
        cache.inFrame = true;
    }

    public static void endFrame(Object owner) {
        ProvinceFileCache cache = current();
        if (cache != null && cache.session == owner) cache.inFrame = false;
    }

    public static void endSession(Object owner) {
        ProvinceFileCache cache = current();
        if (cache != null && cache.session == owner) cache.clear();
    }

    private static ProvinceFileCache current() {
        ProvinceFileCache cache = installed;
        if (cache != null && FileManager.loadInterface != cache) {
            cache.clear();
            return null;
        }
        return cache;
    }

    private void clear() {
        session = null;
        thread = null;
        inFrame = false;
        candidates = null;
        files = null;
        mapIdentity = null;
        diagnostics = null;
    }

    @Override
    public FileHandle loadFile(String path) {
        if (!active() || path == null || !path.startsWith("map/") || !ProvinceCandidates.targetDirectory(path)) {
            return delegate(path);
        }
        ProvinceCandidates context = context();
        return context == null ? delegate(path) : context.load(path, original);
    }

    private FileHandle delegate(String path) {
        LoadingProfiler.pathOriginalResolverCall();
        return original.loadFile(path);
    }

    @Override
    public FileHandle getSaveType(String path) {
        FileHandle result = original.getSaveType(path);
        invalidate(path);
        return result;
    }

    /** Call before handing out a writable resource or mutating it outside the game's save API. */
    public static void invalidate(String path) {
        ProvinceFileCache cache = current();
        if (cache == null || cache.session == null || path == null || !ProvinceCandidates.targetDirectory(path)) return;
        ProvinceCandidates context = cache.context();
        if (context != null) context.invalidate(path);
    }

    public static void invalidateData() {
        ProvinceFileCache cache = current();
        if (cache != null && cache.candidates != null) cache.candidates.invalidateData();
    }

    public static void invalidateTextures() {
        ProvinceFileCache cache = current();
        if (cache != null && cache.candidates != null) cache.candidates.invalidateTextures();
    }

    private boolean active() {
        if (FileManager.loadInterface != this) {
            clear();
            return false;
        }
        return inFrame && thread == Thread.currentThread();
    }

    private ProvinceCandidates context() {
        if (Gdx.files == null
                || Gdx.files.getClass() != Lwjgl3Files.class
                || CFG.map == null
                || sUM.sUF == null
                || sUM.sUII == null
                || sUM.sUFS < 0
                || sUM.sUIIS < 0
                || sUM.sUFS > sUM.sUF.size()
                || sUM.sUIIS > sUM.sUII.size()) {
            candidates = null;
            return null;
        }
        String map = CFG.map.getFile_ActiveMap_Path2();
        if (files == Gdx.files && mapIdentity == CFG.map && matches(map)) return candidates;
        if (!relative(map)) {
            candidates = null;
            return null;
        }
        candidates = null;
        files = Gdx.files;
        mapIdentity = CFG.map;
        String[] local = new String[sUM.sUFS];
        String[] workshop = new String[sUM.sUIIS];
        for (int i = 0; i < local.length; i++) {
            local[i] = sUM.sUF.get(i);
            if (!relative(local[i])) return null;
        }
        for (int i = 0; i < workshop.length; i++) {
            if (sUM.sUII.get(i) == null) return null;
            workshop[i] = sUM.sUII.get(i).getFolder();
            if (workshop[i] == null) return null;
        }
        if (diagnostics == null) diagnostics = new ProvinceCandidates.Diagnostics();
        candidates = new ProvinceCandidates(files, mac, map, local, workshop, diagnostics);
        return candidates;
    }

    private boolean matches(String map) {
        if (candidates == null
                || !candidates.map.equals(map)
                || candidates.local.length != sUM.sUFS
                || candidates.workshop.length != sUM.sUIIS) return false;
        for (int i = 0; i < candidates.local.length; i++) {
            if (!candidates.local[i].equals(sUM.sUF.get(i))) return false;
        }
        for (int i = 0; i < candidates.workshop.length; i++) {
            if (sUM.sUII.get(i) == null
                    || !candidates.workshop[i].equals(sUM.sUII.get(i).getFolder())) return false;
        }
        return true;
    }

    private static boolean relative(String path) {
        if (path == null
                || path.isEmpty()
                || path.startsWith("/")
                || path.indexOf('\\') >= 0
                || path.indexOf(':') >= 0) {
            return false;
        }
        int start = 0;
        while (start < path.length()) {
            int end = path.indexOf('/', start);
            if (end < 0) end = path.length();
            int length = end - start;
            if (length == 0
                    || (length == 1 && path.charAt(start) == '.')
                    || (length == 2 && path.charAt(start) == '.' && path.charAt(start + 1) == '.')) return false;
            start = end + 1;
        }
        return true;
    }
}
