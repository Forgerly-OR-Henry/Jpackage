package gold.debug.packager.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

public final class ManifestReader {

    private ManifestReader() {
    }

    /**
     * 读取 jar 中的 MANIFEST.MF
     */
    public static Manifest readManifest(Path jarPath) throws Exception {
        checkJarPath(jarPath);

        try (InputStream in = Files.newInputStream(jarPath);
             JarInputStream jis = new JarInputStream(in)) {

            Manifest manifest = jis.getManifest();
            if (manifest == null) {
                throw new Exception("未在 JAR 中找到 META-INF/MANIFEST.MF：" + jarPath.toAbsolutePath());
            }
            return manifest;
        } catch (IOException e) {
            throw new Exception("读取 MANIFEST 失败：" + jarPath.toAbsolutePath(), e);
        }
    }

    /**
     * 读取 Main-Class
     */
    public static String readMainClass(Path jarPath) throws Exception {
        Manifest manifest = readManifest(jarPath);
        String mainClass = manifest.getMainAttributes().getValue("Main-Class");

        if (mainClass == null || mainClass.trim().isEmpty()) {
            throw new Exception("MANIFEST 中未找到 Main-Class：" + jarPath.toAbsolutePath());
        }

        return mainClass.trim();
    }

    /**
     * 读取 Class-Path 中声明的依赖。
     *
     * 规则：
     * 1. Class-Path 中每一项都按“相对于主 jar 所在目录”解析
     * 2. 例如：
     *    a.jar      -> <主jar目录>/a.jar
     *    lib/b.jar  -> <主jar目录>/lib/b.jar
     * 3. 若任一依赖不存在，则抛异常
     */
    public static List<Path> getDependencies(Path mainJarPath) throws Exception {
        Manifest manifest = readManifest(mainJarPath);
        String classPath = manifest.getMainAttributes().getValue("Class-Path");

        List<Path> result = new ArrayList<>();
        if (classPath == null || classPath.trim().isEmpty()) {
            return result;
        }

        Path mainJarDir = mainJarPath.toAbsolutePath().normalize().getParent();
        if (mainJarDir == null) {
            throw new Exception("无法确定主 JAR 所在目录：" + mainJarPath.toAbsolutePath());
        }

        // 用 Set 去重，同时保持原顺序
        Set<Path> uniquePaths = new LinkedHashSet<>();

        // MANIFEST 的 Class-Path 以空格分隔
        String[] entries = classPath.trim().split("\\s+");
        for (String entry : entries) {
            if (entry == null || entry.trim().isEmpty()) {
                continue;
            }

            String raw = entry.trim();

            // 按你的需求：直接按相对主 jar 目录处理
            Path resolved = mainJarDir.resolve(raw).normalize();

            if (!Files.exists(resolved) || !Files.isRegularFile(resolved)) {
                throw new Exception(
                        "主包下从包路径不正确：\n" +
                                "主包：" + mainJarPath.toAbsolutePath() + "\n" +
                                "MANIFEST Class-Path 项：" + raw + "\n" +
                                "解析后路径：" + resolved.toAbsolutePath()
                );
            }

            uniquePaths.add(resolved.toAbsolutePath());
        }

        result.addAll(uniquePaths);
        return result;
    }

    private static void checkJarPath(Path jarPath) throws Exception {
        if (jarPath == null) {
            throw new Exception("JAR 路径不能为空。");
        }
        if (!Files.exists(jarPath)) {
            throw new Exception("JAR 文件不存在：" + jarPath.toAbsolutePath());
        }
        if (!Files.isRegularFile(jarPath)) {
            throw new Exception("路径不是文件：" + jarPath.toAbsolutePath());
        }
        String fileName = jarPath.getFileName() == null ? "" : jarPath.getFileName().toString().toLowerCase();
        if (!fileName.endsWith(".jar")) {
            throw new Exception("不是 JAR 文件：" + jarPath.toAbsolutePath());
        }
    }
}