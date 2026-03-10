package gold.debug.packager.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

// 支持非胖包：主 jar + 从 jar
public class StagingManager {

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

    public static StagingResult stageJars(Path baseWorkDir, Path mainJar, List<Path> extraJars) throws IOException {
        Files.createDirectories(baseWorkDir);
        Path staging = baseWorkDir.resolve("staging_" + System.currentTimeMillis());
        Files.createDirectories(staging);

        // copy main jar
        Path mainDst = staging.resolve(mainJar.getFileName().toString());
        Files.copy(mainJar, mainDst, StandardCopyOption.REPLACE_EXISTING);

        List<String> extraNames = new ArrayList<>();
        for (Path p : extraJars) {
            if (p == null) continue;
            Path dst = staging.resolve(p.getFileName().toString());
            Files.copy(p, dst, StandardCopyOption.REPLACE_EXISTING);
            extraNames.add(dst.getFileName().toString());
        }

        return new StagingResult(staging, mainDst.getFileName().toString(), extraNames);
    }

    public static void tryDeleteDirectory(Path dir) {
        if (dir == null) return;
        try {
            if (Files.notExists(dir)) return;
            Files.walk(dir)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                    });
        } catch (Exception ignored) {}
    }
}

