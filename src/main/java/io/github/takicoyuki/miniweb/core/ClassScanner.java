package io.github.takicoyuki.miniweb.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * 指定パッケージ配下のクラスをクラスパスから探す（Spring のコンポーネントスキャンに相当）。
 * 通常のクラスフォルダ（IDE やテスト実行時）と JAR ファイルの両方に対応する。
 */
public class ClassScanner {

    private final ClassLoader loader;

    public ClassScanner(ClassLoader loader) {
        this.loader = loader;
    }

    public List<Class<?>> scan(String basePackage) {
        String path = basePackage.replace('.', '/');
        List<Class<?>> classes = new ArrayList<>();
        try {
            Enumeration<URL> roots = loader.getResources(path);
            while (roots.hasMoreElements()) {
                URL url = roots.nextElement();
                switch (url.getProtocol()) {
                    case "file" -> scanDirectory(Path.of(url.toURI()), basePackage, classes);
                    case "jar" -> scanJar(url, path, classes);
                    default -> throw new IllegalStateException("対応していないクラスパスです: " + url);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        classes.sort(Comparator.comparing(Class::getName));
        return classes;
    }

    private void scanDirectory(Path dir, String basePackage, List<Class<?>> out) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".class") || name.contains("$")) {
                    continue;
                }
                String relative = dir.relativize(file).toString().replace(file.getFileSystem().getSeparator(), ".");
                load(basePackage + "." + relative.substring(0, relative.length() - ".class".length()), out);
            }
        }
    }

    private void scanJar(URL url, String path, List<Class<?>> out) throws IOException {
        JarURLConnection connection = (JarURLConnection) url.openConnection();
        connection.setUseCaches(false);
        try (JarFile jar = connection.getJarFile()) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith(path + "/") && name.endsWith(".class") && !name.contains("$")) {
                    load(name.substring(0, name.length() - ".class".length()).replace('/', '.'), out);
                }
            }
        }
    }

    private void load(String className, List<Class<?>> out) {
        try {
            out.add(Class.forName(className, false, loader));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("クラスを読み込めません: " + className, e);
        }
    }
}
