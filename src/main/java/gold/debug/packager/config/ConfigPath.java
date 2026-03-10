package gold.debug.packager.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class ConfigPath {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 对外：默认配置文件路径（自动按运行模式选择） */
    public static Path defaultConfigPath() {
        RunModeDetector.RunMode mode = RunModeDetector.detect(ConfigPath.class);
        Path baseDir = resolveAppBaseDir(mode);
        Path cfgDir = resolveConfigDir(baseDir);
        return cfgDir.resolve("packager.json");
    }

    /** 根据运行模式确定 baseDir（已处理所有异常，永不抛出） */
    private static Path resolveAppBaseDir(RunModeDetector.RunMode mode) {
        Path loc = getCodeSourcePathSafe(ConfigPath.class);

        switch (mode) {
            case RUN_JAR -> {
                // 直接 java -jar xxx.jar：loc 通常是 jar 文件路径
                // 防御：如果拿到的不是文件，就退回到其 parent 或 user.dir
                Path jarDir = (loc != null) ? loc.getParent() : null;
                return (jarDir != null) ? jarDir : fallbackDir();
            }
            case RUN_APP -> {
                // jpackage app-image：jar 通常在 <root>/app/xxx.jar
                // baseDir 应该是 <root>（即 app/runtime/config 同级）
                if (loc != null) {
                    Path appDir = loc.getParent();        // .../app
                    Path root = (appDir != null) ? appDir.getParent() : null; // .../<root>
                    if (root != null) return root;
                }
                // 兜底：用工作目录或上级目录探测（你的 RunModeDetector 也有类似兜底逻辑）
                return fallbackDir();
            }
            case RUN_CLASS -> {
                return (loc != null) ? loc : fallbackDir();
            }
            default -> {
                return fallbackDir();
            }
        }
    }

    /** 配置目录选择：统一放在 baseDir/config（不污染用户目录） */
    private static Path resolveConfigDir(Path baseDir) {
        // 配置在 app/runtime 同级的 config 或 jar 同级 config。
        // 直接 baseDir/config 即可。
        return ensureDir(baseDir.resolve("config"));
    }

    private static Path ensureDir(Path p) {
        try {
            Files.createDirectories(p);
        } catch (Exception ignored) {}
        return p;
    }

    /** 安全获取 CodeSource 路径（不会抛 URISyntaxException） */
    private static Path getCodeSourcePathSafe(Class<?> anchorClass) {
        try {
            var cs = anchorClass.getProtectionDomain().getCodeSource();
            if (cs == null || cs.getLocation() == null) return null;
            return Paths.get(cs.getLocation().toURI()).toAbsolutePath().normalize();
        } catch (Exception e) {
            return null;
        }
    }

    private static Path fallbackDir() {
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    public static PackagerConfig loadOrDefault(Path path) {
        try {
            if (Files.notExists(path)) return new PackagerConfig();
            String json = Files.readString(path, StandardCharsets.UTF_8);
            PackagerConfig cfg = GSON.fromJson(json, PackagerConfig.class);
            return cfg == null ? new PackagerConfig() : cfg;
        } catch (Exception e) {
            return new PackagerConfig();
        }
    }

    public static void save(Path path, PackagerConfig cfg) throws IOException {
        Files.createDirectories(path.getParent());
        String json = GSON.toJson(cfg);
        Files.writeString(path, json, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}