package gold.debug.packager.config;

import java.util.ArrayList;
import java.util.List;

// 保存/加载配置的模型
public class PackagerConfig {
    public String jdkHome;
    public String mainJarPath;
    public List<String> extraJarPaths = new ArrayList<>();

    public String mainClass;
    public String appName;
    public String vendor;
    public String version;

    public String iconPath;     // 允许 png/jpg/ico
    public String outputDir;
    public String type;         // app-image / msi / exe

    public boolean winMenu;
    public boolean winShortcut;
    public boolean winConsole;
    public boolean winPerUserInstall;
    public boolean winDirChooser;
}
