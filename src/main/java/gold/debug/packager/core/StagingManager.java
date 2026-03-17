package gold.debug.packager.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class StagingManager {

    private StagingManager() {
    }

    public static class StagingResult {
        public final Path stagingDir;
        public final String mainJarFileName;
        public final List<String> extraJarFileNames;

        public StagingResult(Path stagingDir, String mainJarFileName, List<String> extraJarFileNames) {
            this.stagingDir = stagingDir;
            this.mainJarFileName = mainJarFileName;
            this.extraJarFileNames = extraJarFileNames;
        }
    }

    /**
     * 保留 MANIFEST Class-Path 的相对目录结构进行复制
     *
     * 例如：
     * a.jar      -> staging/a.jar
     * lib/b.jar  -> staging/lib/b.jar
     */
    public static StagingResult stageJars(Path baseWorkDir,
                                          Path mainJar,
                                          List<ManifestReader.DependencyItem> dependencyItems) throws IOException {
        Files.createDirectories(baseWorkDir);

        Path staging = baseWorkDir.resolve("staging_" + System.currentTimeMillis());
        Files.createDirectories(staging);

        Path mainDst = staging.resolve(mainJar.getFileName().toString());
        Files.copy(mainJar, mainDst, StandardCopyOption.REPLACE_EXISTING);

        List<String> copied = new ArrayList<>();

        if (dependencyItems != null) {
            for (ManifestReader.DependencyItem item : dependencyItems) {
                if (item == null) continue;

                Path src = item.sourcePath();
                if (src == null) continue;

                String relative = normalizeRelativePath(item.relativePath());
                if (relative.isBlank()) continue;

                Path dst = staging.resolve(relative).normalize();
                Path parent = dst.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }

                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                copied.add(staging.relativize(dst).toString().replace("\\", "/"));
            }
        }

        return new StagingResult(staging, mainDst.getFileName().toString(), copied);
    }

    public static void tryDeleteDirectory(Path dir) {
        if (dir == null) return;
        try {
            if (Files.notExists(dir)) return;
            Files.walk(dir)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
    }

    private static String normalizeRelativePath(String path) {
        return path == null ? "" : path.replace("\\", "/");
    }
}