package gold.debug.packager.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// 命令拼接
public class JPackageCommandBuilder {

    private String jdkHome;
    private String type;
    private String name;
    private String inputDir;
    private String mainJarFileName;
    private String mainClass;
    private String iconPath;
    private String dest;
    private String vendor;
    private String appVersion;

    private boolean winMenu;
    private boolean winShortcut;
    private boolean winConsole;
    private boolean winPerUserInstall;
    private boolean winDirChooser;

    public JPackageCommandBuilder setJdkHome(String v) { this.jdkHome = blankToNull(v); return this; }
    public JPackageCommandBuilder setType(String v) { this.type = blankToNull(v); return this; }
    public JPackageCommandBuilder setName(String v) { this.name = blankToNull(v); return this; }

    public JPackageCommandBuilder setInputDir(String v) { this.inputDir = blankToNull(v); return this; }
    public JPackageCommandBuilder setMainJarFileName(String v) { this.mainJarFileName = blankToNull(v); return this; }

    public JPackageCommandBuilder setMainClass(String v) { this.mainClass = blankToNull(v); return this; }
    public JPackageCommandBuilder setIconPath(String v) { this.iconPath = blankToNull(v); return this; }
    public JPackageCommandBuilder setDest(String v) { this.dest = blankToNull(v); return this; }
    public JPackageCommandBuilder setVendor(String v) { this.vendor = blankToNull(v); return this; }
    public JPackageCommandBuilder setAppVersion(String v) { this.appVersion = blankToNull(v); return this; }

    public JPackageCommandBuilder setWinMenu(boolean v) { this.winMenu = v; return this; }
    public JPackageCommandBuilder setWinShortcut(boolean v) { this.winShortcut = v; return this; }
    public JPackageCommandBuilder setWinConsole(boolean v) { this.winConsole = v; return this; }
    public JPackageCommandBuilder setWinPerUserInstall(boolean v) { this.winPerUserInstall = v; return this; }
    public JPackageCommandBuilder setWinDirChooser(boolean v) { this.winDirChooser = v; return this; }

    public List<String> build() {
        require(type, "打包类型");
        require(name, "应用名称");
        require(inputDir, "--input 目录");
        require(mainJarFileName, "--main-jar 文件名");
        require(mainClass, "主类 Main-Class");
        require(dest, "输出目录");

        List<String> cmd = new ArrayList<>();
        cmd.add(resolveJPackageExe());

        cmd.add("--type"); cmd.add(type);
        cmd.add("--name"); cmd.add(name);
        cmd.add("--input"); cmd.add(inputDir);
        cmd.add("--main-jar"); cmd.add(mainJarFileName);
        cmd.add("--main-class"); cmd.add(mainClass);
        cmd.add("--dest"); cmd.add(dest);

        if (vendor != null) { cmd.add("--vendor"); cmd.add(vendor); }
        if (appVersion != null) { cmd.add("--app-version"); cmd.add(appVersion); }
        if (iconPath != null) { cmd.add("--icon"); cmd.add(iconPath); }

        // windows options（注意：app-image 不应出现安装器选项）
        if (!"app-image".equals(type)) {
            if (winMenu) cmd.add("--win-menu");
            if (winShortcut) cmd.add("--win-shortcut");
            if (winConsole) cmd.add("--win-console");
            if (winPerUserInstall) cmd.add("--win-per-user-install");
            if (winDirChooser) cmd.add("--win-dir-chooser");
        } else {
            // app-image 阶段，最多只允许 win-console（你也可以选择完全禁用）
            if (winConsole) cmd.add("--win-console");
        }

        return cmd;
    }

    public static String pretty(List<String> cmd) {
        StringBuilder sb = new StringBuilder();
        for (String s : cmd) {
            if (s.contains(" ") || s.contains("\t")) sb.append("\"").append(s).append("\"");
            else sb.append(s);
            sb.append(" ");
        }
        return sb.toString().trim();
    }

    private String resolveJPackageExe() {
        // 1) UI 填写优先
        if (jdkHome != null) return Path.of(jdkHome, "bin", "jpackage.exe").toString();

        // 2) 读取环境变量 JAVA_HOME
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            return Path.of(javaHome.trim(), "bin", "jpackage.exe").toString();
        }

        // 3) 走 PATH
        return "jpackage";
    }

    private static void require(String v, String name) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException(name + " 未填写");
    }
    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
