package top.tasaed.aohx.monohydrate.load;

import com.badlogic.gdx.files.FileHandle;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

/** Negative directory answers require a complete view of FileHandle's resource loader. */
final class ClasspathDirectories {
    /** Failed lazy capture; callers delegate rather than querying this marker. */
    static final ClasspathDirectories UNAVAILABLE = new ClasspathDirectories();

    private final List<Path> directories = new ArrayList<>();
    private final List<Path> missingManifestRoots = new ArrayList<>();
    private final Set<String> archiveDirectories = new HashSet<>();
    private final Set<Path> visited = new HashSet<>();
    private URL inspectedRoot;

    static ClasspathDirectories capture() throws IOException {
        return capture(FileHandle.class.getClassLoader());
    }

    static ClasspathDirectories capture(ClassLoader loader) throws IOException {
        ClasspathDirectories result = new ClasspathDirectories();
        ClassLoader current = loader;
        try {
            for (; current != null; current = current.getParent()) {
                String name = current.getClass().getName();
                result.inspectedRoot = null;
                if (current.getClass() == URLClassLoader.class
                        || name.equals("team.rainfall.finality.loader.util.FinalityClassLoader")) {
                    for (URL url : ((URLClassLoader) current).getURLs()) result.add(url, false);
                } else if (current == ClassLoader.getSystemClassLoader()
                        && name.equals("jdk.internal.loader.ClassLoaders$AppClassLoader")) {
                    for (String entry : System.getProperty("java.class.path").split(File.pathSeparator, -1)) {
                        result.add(
                                Path.of(entry.isEmpty() ? "." : entry).toUri().toURL(), false);
                    }
                } else if (!name.equals("jdk.internal.loader.ClassLoaders$PlatformClassLoader")) {
                    throw new IOException("Unsupported resource loader: " + name);
                }
            }
            return result;
        } catch (Exception ex) {
            // Unknown URL protocols, inaccessible archives and custom resource loaders are not negative evidence.
            throw new IOException(
                    "Classpath capture failed: loader=" + current + ", root=" + result.inspectedRoot + ", reason=" + ex,
                    ex);
        }
    }

    private void add(URL url, boolean manifestDependency) throws Exception {
        inspectedRoot = url;
        if (!url.getProtocol().equals("file")) throw new IOException("Non-file classpath");
        Path path = Path.of(url.toURI()).toAbsolutePath().normalize();
        if (!visited.add(path)) return;
        if (Files.isDirectory(path)) {
            directories.add(path);
            return;
        }
        if (manifestDependency && Files.notExists(path)) {
            // URLClassLoader ignores missing manifest dependencies; other roots still need scanning.
            missingManifestRoots.add(path);
            return;
        }
        if (!Files.exists(path)) throw new IOException("Unavailable classpath root");
        try (JarFile jar = new JarFile(path.toFile(), true, JarFile.OPEN_READ, Runtime.version())) {
            // JAR Index lookup was removed in Java 21; its leftover metadata adds no resource roots.
            if (Runtime.version().feature() < 21 && jar.getJarEntry("META-INF/INDEX.LIST") != null) {
                throw new IOException("Indexed classpath");
            }
            // versionedStream exposes the logical resource names of multi-release jars.
            try (var entries = jar.versionedStream()) {
                entries.forEach(entry -> {
                    String name = entry.getName();
                    int end = name.lastIndexOf('/');
                    if (end >= 0) archiveDirectories.add(name.substring(0, end + 1));
                });
            }
            var manifest = jar.getManifest();
            if (manifest != null) {
                String extra = manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
                if (extra != null) {
                    for (String entry : extra.trim().split("\\s+")) {
                        if (!entry.isEmpty()) add(new URL(url, entry), true);
                    }
                }
            }
        }
    }

    boolean contains(String directory) {
        // A dependency appearing after capture makes this snapshot incomplete. Retain candidates
        // rather than claiming absence; this runs at directory construction, not per province.
        for (Path root : missingManifestRoots) {
            if (!Files.notExists(root)) return true;
        }
        if (archiveDirectories.contains(directory)) return true;
        for (Path root : directories) {
            if (!Files.notExists(root.resolve(directory))) return true;
        }
        return false;
    }
}
