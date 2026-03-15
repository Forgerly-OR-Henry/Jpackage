package gold.debug.packager.ui;

import gold.debug.packager.config.ConfigPath;
import gold.debug.packager.config.PackagerConfig;
import gold.debug.packager.core.JPackageCommandBuilder;
import gold.debug.packager.core.ProcessRunner;
import gold.debug.packager.core.StagingManager;
import gold.debug.packager.icon.IconConverter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MainFrame extends JFrame {

    private final BasicInformationFrame basicInfoFrame;
    private final PackagingFrame packagingFrame;
    private final CmdFrame cmdFrame;

    private final JComboBox<String> cbType = new JComboBox<>(new String[]{"app-image", "msi", "exe"});
    private final JCheckBox ckWinMenu = new JCheckBox("开始菜单");
    private final JCheckBox ckWinShortcut = new JCheckBox("桌面快捷方式");
    private final JCheckBox ckConsole = new JCheckBox("显示控制台");
    private final JCheckBox ckPerUser = new JCheckBox("仅当前用户");
    private final JCheckBox ckDirChooser = new JCheckBox("可选安装目录");

    private final JButton btnSaveCfg = new JButton("保存配置");
    private final JButton btnBuildCmd = new JButton("生成命令");
    private final JButton btnCopyCmd = new JButton("复制命令");
    private final JButton btnRun = new JButton("开始打包");

    private String lastCommandPreview = "";

    public MainFrame() {
        setTitle("JPackage GUI (Swing) - 稳定版");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1180, 820);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(root);

        basicInfoFrame = new BasicInformationFrame();
        packagingFrame = new PackagingFrame();
        cmdFrame = new CmdFrame();

        root.add(buildTopButtonBar(), BorderLayout.NORTH);
        root.add(buildCenterArea(), BorderLayout.CENTER);
        root.add(buildBottomPanel(), BorderLayout.SOUTH);

        wireActions();

        loadConfigIntoUI(ConfigPath.loadOrDefault(ConfigPath.defaultConfigPath()));

        refreshTypeEnableState();
        refreshCommandPreview();
    }

    private JPanel buildTopButtonBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panel.add(btnSaveCfg);
        return panel;
    }

    private JComponent buildCenterArea() {
        JPanel formPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        formPanel.add(basicInfoFrame);
        formPanel.add(packagingFrame);

        // 上：基础信息 + 打包输入
        // 下：命令预览 + 执行日志
        // 用纵向分割，避免顶部被压扁，保证 PackagingFrame 的依赖列表能显示足够高度
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                formPanel,
                cmdFrame
        );
        splitPane.setResizeWeight(0.46);
        splitPane.setOneTouchExpandable(true);
        splitPane.setContinuousLayout(true);

        return splitPane;
    }

    private JPanel buildBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftPanel.add(new JLabel("打包类型："));
        leftPanel.add(cbType);

        leftPanel.add(Box.createHorizontalStrut(10));
        leftPanel.add(new JLabel("Windows 选项："));
        leftPanel.add(ckWinMenu);
        leftPanel.add(ckWinShortcut);
        leftPanel.add(ckConsole);
        leftPanel.add(ckPerUser);
        leftPanel.add(ckDirChooser);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightPanel.add(btnBuildCmd);
        rightPanel.add(btnCopyCmd);
        rightPanel.add(btnRun);

        bottomPanel.add(leftPanel, BorderLayout.CENTER);
        bottomPanel.add(rightPanel, BorderLayout.EAST);

        return bottomPanel;
    }

    private void wireActions() {
        btnSaveCfg.addActionListener(e -> {
            try {
                ConfigPath.save(ConfigPath.defaultConfigPath(), readUIToConfig());
                cmdFrame.appendLog("配置已保存：" + ConfigPath.defaultConfigPath().toAbsolutePath());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "保存失败: " + ex.getMessage());
            }
        });

        btnBuildCmd.addActionListener(e -> refreshCommandPreview());

        btnCopyCmd.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(lastCommandPreview), null);
            cmdFrame.appendLog("命令已复制到剪贴板。");
        });

        btnRun.addActionListener(e -> runPackaging());

        cbType.addActionListener(e -> {
            refreshTypeEnableState();
            refreshCommandPreview();
        });

        ckWinMenu.addActionListener(e -> refreshCommandPreview());
        ckWinShortcut.addActionListener(e -> refreshCommandPreview());
        ckConsole.addActionListener(e -> refreshCommandPreview());
        ckPerUser.addActionListener(e -> refreshCommandPreview());
        ckDirChooser.addActionListener(e -> refreshCommandPreview());

        // 当窗口首次显示后再设一次 divider，避免某些 LaF 下初始比例不准
        SwingUtilities.invokeLater(this::refreshCommandPreview);
    }

    private void refreshTypeEnableState() {
        String type = (String) cbType.getSelectedItem();
        boolean isAppImage = "app-image".equals(type);

        ckWinMenu.setEnabled(!isAppImage);
        ckWinShortcut.setEnabled(!isAppImage);
        ckPerUser.setEnabled(!isAppImage);
        ckDirChooser.setEnabled(!isAppImage);

        if (isAppImage) {
            ckWinMenu.setSelected(false);
            ckWinShortcut.setSelected(false);
            ckPerUser.setSelected(false);
            ckDirChooser.setSelected(false);
        }
    }

    private void runPackaging() {
        cmdFrame.appendLog("");

        PackagerConfig current = readUIToConfig();

        if (isBlank(current.mainJarPath)) {
            JOptionPane.showMessageDialog(this, "请选择主 JAR。");
            return;
        }
        if (isBlank(current.mainClass)) {
            JOptionPane.showMessageDialog(this, "请填写主类 Main-Class。");
            return;
        }
        if (isBlank(current.outputDir)) {
            JOptionPane.showMessageDialog(this, "请选择输出目录。");
            return;
        }
        if (isBlank(current.appName)) {
            JOptionPane.showMessageDialog(this, "请填写应用名称。");
            return;
        }
        if (isBlank(current.type)) {
            JOptionPane.showMessageDialog(this, "请选择打包类型。");
            return;
        }

        btnRun.setEnabled(false);
        btnBuildCmd.setEnabled(false);

        new Thread(() -> {
            Path stagingDir = null;
            try {
                Path outDir = Path.of(current.outputDir);
                Path workDir = outDir.resolve(".packager_work");

                Path iconIco = null;
                if (!isBlank(current.iconPath)) {
                    iconIco = IconConverter.ensureIco(Path.of(current.iconPath), workDir.resolve("icons"));
                    cmdFrame.appendLog("图标准备完成: " + iconIco);
                }

                Path mainJar = Path.of(current.mainJarPath);
                List<Path> extra = new ArrayList<>();
                for (String p : current.extraJarPaths) {
                    if (!isBlank(p)) {
                        extra.add(Path.of(p));
                    }
                }

                StagingManager.StagingResult staged = StagingManager.stageJars(workDir, mainJar, extra);
                stagingDir = staged.stagingDir;

                List<String> cmd = new JPackageCommandBuilder()
                        .setJdkHome(current.jdkHome)
                        .setType(current.type)
                        .setName(current.appName)
                        .setInputDir(stagingDir.toString())
                        .setMainJarFileName(staged.mainJarFileName)
                        .setMainClass(current.mainClass)
                        .setIconPath(iconIco == null ? null : iconIco.toString())
                        .setDest(current.outputDir)
                        .setVendor(current.vendor)
                        .setAppVersion(current.version)
                        .setWinMenu(current.winMenu)
                        .setWinShortcut(current.winShortcut)
                        .setWinConsole(current.winConsole)
                        .setWinPerUserInstall(current.winPerUserInstall)
                        .setWinDirChooser(current.winDirChooser)
                        .build();

                cmdFrame.appendLog("开始执行 jpackage ...");
                cmdFrame.appendLog(JPackageCommandBuilder.pretty(cmd));
                cmdFrame.appendLog("------------------------------------------------------------");

                int code = ProcessRunner.run(cmd, cmdFrame::appendLog);

                cmdFrame.appendLog("------------------------------------------------------------");
                cmdFrame.appendLog("完成，退出码: " + code);

                try {
                    ConfigPath.save(ConfigPath.defaultConfigPath(), current);
                } catch (Exception ignored) {
                }

            } catch (Exception ex) {
                cmdFrame.appendLog("执行失败: " + ex.getMessage());
            } finally {
                StagingManager.tryDeleteDirectory(stagingDir);
                SwingUtilities.invokeLater(() -> {
                    btnRun.setEnabled(true);
                    btnBuildCmd.setEnabled(true);
                });
            }
        }, "jpackage-runner").start();
    }

    private void refreshCommandPreview() {
        try {
            List<String> cmd = buildCommandForPreview();
            lastCommandPreview = JPackageCommandBuilder.pretty(cmd);
            cmdFrame.setCommandPreview(lastCommandPreview);
        } catch (Exception ex) {
            lastCommandPreview = "命令生成失败：\n" + ex.getMessage();
            cmdFrame.setCommandPreview(lastCommandPreview);
        }
    }

    private List<String> buildCommandForPreview() {
        String jarPath = packagingFrame.getJarPath().trim();
        if (jarPath.isEmpty()) {
            throw new IllegalArgumentException("主 JAR 未选择");
        }

        Path jar = Path.of(jarPath);
        String inputDir = jar.getParent() == null ? "." : jar.getParent().toString();

        return new JPackageCommandBuilder()
                .setJdkHome(basicInfoFrame.getJdkHome())
                .setType((String) cbType.getSelectedItem())
                .setName(basicInfoFrame.getAppName())
                .setInputDir(inputDir)
                .setMainJarFileName(jar.getFileName().toString())
                .setMainClass(packagingFrame.getMainClass())
                .setIconPath(basicInfoFrame.getIconPath())
                .setDest(basicInfoFrame.getOutputDir())
                .setVendor(basicInfoFrame.getVendor())
                .setAppVersion(basicInfoFrame.getVersion())
                .setWinMenu(ckWinMenu.isSelected())
                .setWinShortcut(ckWinShortcut.isSelected())
                .setWinConsole(ckConsole.isSelected())
                .setWinPerUserInstall(ckPerUser.isSelected())
                .setWinDirChooser(ckDirChooser.isSelected())
                .build();
    }

    private PackagerConfig readUIToConfig() {
        PackagerConfig c = new PackagerConfig();
        c.jdkHome = basicInfoFrame.getJdkHome();
        c.mainJarPath = packagingFrame.getJarPath();
        c.mainClass = packagingFrame.getMainClass();
        c.appName = basicInfoFrame.getAppName();
        c.vendor = basicInfoFrame.getVendor();
        c.version = basicInfoFrame.getVersion();
        c.iconPath = basicInfoFrame.getIconPath();
        c.outputDir = basicInfoFrame.getOutputDir();
        c.type = (String) cbType.getSelectedItem();

        c.winMenu = ckWinMenu.isSelected();
        c.winShortcut = ckWinShortcut.isSelected();
        c.winConsole = ckConsole.isSelected();
        c.winPerUserInstall = ckPerUser.isSelected();
        c.winDirChooser = ckDirChooser.isSelected();

        c.extraJarPaths = packagingFrame.getExtraJarPaths();
        return c;
    }

    private void loadConfigIntoUI(PackagerConfig loaded) {
        if (loaded == null) {
            loaded = new PackagerConfig();
        }

        basicInfoFrame.setJdkHome(nullToEmpty(loaded.jdkHome));
        basicInfoFrame.setAppName(nullToEmpty(loaded.appName));
        basicInfoFrame.setVersion(nullToEmpty(loaded.version));
        basicInfoFrame.setVendor(nullToEmpty(loaded.vendor));
        basicInfoFrame.setIconPath(nullToEmpty(loaded.iconPath));
        basicInfoFrame.setOutputDir(nullToEmpty(loaded.outputDir));

        packagingFrame.setJarPath(nullToEmpty(loaded.mainJarPath));
        packagingFrame.setMainClass(nullToEmpty(loaded.mainClass));
        packagingFrame.setExtraJarPaths(loaded.extraJarPaths);

        if (!isBlank(loaded.type)) {
            cbType.setSelectedItem(loaded.type);
        } else {
            cbType.setSelectedItem("app-image");
        }

        ckWinMenu.setSelected(loaded.winMenu);
        ckWinShortcut.setSelected(loaded.winShortcut);
        ckConsole.setSelected(loaded.winConsole);
        ckPerUser.setSelected(loaded.winPerUserInstall);
        ckDirChooser.setSelected(loaded.winDirChooser);

        refreshTypeEnableState();

        cmdFrame.appendLog("已加载配置：" + ConfigPath.defaultConfigPath().toAbsolutePath());
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}