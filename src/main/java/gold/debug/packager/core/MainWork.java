package gold.debug.packager.core;

import gold.debug.packager.config.ConfigPath;
import gold.debug.packager.config.PackagerConfig;
import gold.debug.packager.icon.IconConverter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class MainWork {

    private MainWork() {
    }

    public static String buildCommandPreview(PackagerConfig config) throws Exception {
        validatePreviewConfig(config);

        Path jar = Path.of(config.mainJarPath).toAbsolutePath().normalize();
        String inputDir = jar.getParent() == null ? "." : jar.getParent().toString();

        List<String> cmd = new JPackageCommandBuilder()
                .setJdkHome(config.jdkHome)
                .setType(config.type)
                .setName(config.appName)
                .setInputDir(inputDir)
                .setMainJarFileName(jar.getFileName().toString())
                .setMainClass(config.mainClass)
                .setIconPath(config.iconPath)
                .setDest(config.outputDir)
                .setVendor(config.vendor)
                .setAppVersion(config.version)
                .setWinMenu(config.winMenu)
                .setWinShortcut(config.winShortcut)
                .setWinConsole(config.winConsole)
                .setWinPerUserInstall(config.winPerUserInstall)
                .setWinDirChooser(config.winDirChooser)
                .build();

        return JPackageCommandBuilder.pretty(cmd);
    }

    public static void runPackagingAsync(PackagerConfig config,
                                         Consumer<String> log,
                                         Runnable onFinally) {
        new Thread(() -> {
            Path stagingDir = null;
            try {
                validateRunConfig(config);

                Path outDir = Path.of(config.outputDir).toAbsolutePath().normalize();
                Path workDir = outDir.resolve(".packager_work");

                Path iconIco = null;
                if (!isBlank(config.iconPath)) {
                    iconIco = IconConverter.ensureIco(
                            Path.of(config.iconPath).toAbsolutePath().normalize(),
                            workDir.resolve("icons")
                    );
                    log.accept("图标准备完成: " + iconIco);
                }

                Path mainJar = Path.of(config.mainJarPath).toAbsolutePath().normalize();

                List<ManifestReader.DependencyItem> dependencyItems = resolveDependencyItems(config, mainJar);

                StagingManager.StagingResult staged =
                        StagingManager.stageJars(workDir, mainJar, dependencyItems);
                stagingDir = staged.stagingDir;

                List<String> cmd = new JPackageCommandBuilder()
                        .setJdkHome(config.jdkHome)
                        .setType(config.type)
                        .setName(config.appName)
                        .setInputDir(stagingDir.toString())
                        .setMainJarFileName(staged.mainJarFileName)
                        .setMainClass(config.mainClass)
                        .setIconPath(iconIco == null ? null : iconIco.toString())
                        .setDest(config.outputDir)
                        .setVendor(config.vendor)
                        .setAppVersion(config.version)
                        .setWinMenu(config.winMenu)
                        .setWinShortcut(config.winShortcut)
                        .setWinConsole(config.winConsole)
                        .setWinPerUserInstall(config.winPerUserInstall)
                        .setWinDirChooser(config.winDirChooser)
                        .build();

                log.accept("开始执行 jpackage ...");
                log.accept(JPackageCommandBuilder.pretty(cmd));
                log.accept("------------------------------------------------------------");

                int code = ProcessRunner.run(cmd, log);

                log.accept("------------------------------------------------------------");
                log.accept("完成，退出码: " + code);

                try {
                    ConfigPath.save(ConfigPath.defaultConfigPath(), config);
                } catch (Exception ignored) {
                }

            } catch (Exception ex) {
                log.accept("执行失败: " + ex.getMessage());
            } finally {
                StagingManager.tryDeleteDirectory(stagingDir);
                if (onFinally != null) {
                    onFinally.run();
                }
            }
        }, "jpackage-runner").start();
    }

    private static List<ManifestReader.DependencyItem> resolveDependencyItems(PackagerConfig config,
                                                                              Path mainJar) throws Exception {
        Path mainJarDir = mainJar.getParent();
        if (mainJarDir == null) {
            throw new Exception("无法确定主 JAR 所在目录：" + mainJar);
        }

        List<ManifestReader.DependencyItem> result = new ArrayList<>();

        // 优先使用界面当前列表中的依赖
        if (config.extraJarPaths != null && !config.extraJarPaths.isEmpty()) {
            for (String p : config.extraJarPaths) {
                if (isBlank(p)) {
                    continue;
                }

                Path abs = Path.of(p).toAbsolutePath().normalize();
                if (!Files.exists(abs) || !Files.isRegularFile(abs)) {
                    throw new Exception("从包/依赖不存在：" + abs);
                }

                String relativePath;
                if (abs.startsWith(mainJarDir)) {
                    relativePath = normalizeRelative(mainJarDir.relativize(abs).toString());
                } else {
                    // 如果手动添加的依赖不在主 jar 目录树内，就退化成文件名平铺
                    relativePath = abs.getFileName().toString();
                }

                result.add(new ManifestReader.DependencyItem(abs, relativePath));
            }
            return result;
        }

        // 如果界面没填依赖，则尝试从 MANIFEST 读取
        return ManifestReader.readClassesPaths(mainJar);
    }

    private static void validatePreviewConfig(PackagerConfig config) throws Exception {
        if (config == null) {
            throw new Exception("配置为空。");
        }
        if (isBlank(config.mainJarPath)) {
            throw new Exception("主 JAR 未选择");
        }
        if (isBlank(config.mainClass)) {
            throw new Exception("主类 Main-Class 未填写");
        }
        if (isBlank(config.outputDir)) {
            throw new Exception("输出目录未填写");
        }
        if (isBlank(config.appName)) {
            throw new Exception("应用名称未填写");
        }
        if (isBlank(config.type)) {
            throw new Exception("打包类型未选择");
        }
    }

    private static void validateRunConfig(PackagerConfig config) throws Exception {
        validatePreviewConfig(config);

        Path jar = Path.of(config.mainJarPath).toAbsolutePath().normalize();
        if (!Files.exists(jar) || !Files.isRegularFile(jar)) {
            throw new Exception("主 JAR 不存在：" + jar);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String normalizeRelative(String s) {
        return s.replace("\\", "/");
    }
}
