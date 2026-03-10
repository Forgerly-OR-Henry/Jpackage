package gold.debug.packager.ui;

import gold.debug.packager.core.JPackageCommandBuilder;
import gold.debug.packager.core.JarManifestUtils;
import gold.debug.packager.core.StagingManager;
import gold.debug.packager.core.ProcessRunner;
import gold.debug.packager.config.ConfigPath;
import gold.debug.packager.config.PackagerConfig;
import gold.debug.packager.icon.IconConverter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MainFrame extends JFrame {

    // config
    private final Path cfgPath = ConfigPath.defaultConfigPath();
    private PackagerConfig cfg = new PackagerConfig();
    private boolean appNameTouchedByUser = false;

    // left fields
    private final JTextField tfJdkHome = new JTextField();
    private final JButton btnJdkFromEnv = new JButton("从环境变量读取");

    private final JTextField tfAppName = new JTextField();
    private final JTextField tfVersion = new JTextField();
    private final JTextField tfVendor = new JTextField();
    private final JTextField tfIconPath = new JTextField();
    private final JTextField tfOutputDir = new JTextField();

    // right fields
    private final JTextField tfJarPath = new JTextField();
    private final JTextField tfMainClass = new JTextField();

    // deps list
    private final DefaultListModel<String> extraJarModel = new DefaultListModel<>();
    private final JList<String> listExtraJars = new JList<>(extraJarModel);
    private final JButton btnAddJar = new JButton("添加依赖 JAR");
    private final JButton btnRemoveJar = new JButton("移除选中");
    private final JButton btnClearJar = new JButton("清空");

    // bottom
    private final JTextArea taCmd = new JTextArea(8, 60);
    private final JTextArea taLog = new JTextArea(8, 60);

    private final JComboBox<String> cbType = new JComboBox<>(new String[]{"app-image", "msi", "exe"});
    private final JCheckBox ckWinMenu = new JCheckBox("", false);
    private final JCheckBox ckWinShortcut = new JCheckBox("", false);
    private final JCheckBox ckConsole = new JCheckBox("", false);
    private final JCheckBox ckPerUser = new JCheckBox("", false);
    private final JCheckBox ckDirChooser = new JCheckBox("", false);

    private final JButton btnSaveCfg = new JButton("保存配置");
    private final JButton btnBuildCmd = new JButton("生成命令");
    private final JButton btnCopyCmd = new JButton("复制命令");
    private final JButton btnRun = new JButton("开始打包");

    public MainFrame() {
        setTitle("JPackage GUI (Swing) - 稳定版");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1120, 780);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(root);

        root.add(buildTopBar(), BorderLayout.NORTH);
        root.add(buildMainArea(), BorderLayout.CENTER);
        root.add(buildBottomArea(), BorderLayout.SOUTH);

        taCmd.setEditable(false);
        taCmd.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        taLog.setEditable(false);
        taLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        listExtraJars.setVisibleRowCount(6);

        wireActions();

        // 启动自动加载配置（无需手动导入）
        loadConfigIntoUI(ConfigPath.loadOrDefault(cfgPath));

        // 初次启动若未填 JDK，则可以让用户点按钮或自动填一次（你截图里按钮更符合“稳定可控”）
        refreshTypeEnableState();
        refreshCommandPreview();
    }

    private JPanel buildTopBar() {
        JPanel p = new JPanel(new BorderLayout());
        p.add(btnSaveCfg, BorderLayout.WEST);
        return p;
    }

    private JPanel buildMainArea() {
        JPanel p = new JPanel(new BorderLayout(10, 10));

        JPanel top = new JPanel(new GridLayout(1, 2, 10, 10));
        top.add(buildLeftForm());
        top.add(buildRightForm());

        p.add(top, BorderLayout.NORTH);

        // 命令预览 + 执行日志 横向
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrapTitled(new JScrollPane(taCmd), "命令预览"),
                wrapTitled(new JScrollPane(taLog), "执行日志"));
        split.setResizeWeight(0.55);
        p.add(split, BorderLayout.CENTER);

        return p;
    }

    private JPanel buildLeftForm() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("基础信息"));
        GridBagConstraints c = baseGbc();

        int r = 0;

        // JDK row: text + browse + from env
        JTextField tf = tfJdkHome;
        JButton btnBrowse = mkBrowseBtn(() -> chooseDir(tfJdkHome));
        JPanel jdkPanel = new JPanel(new BorderLayout(6, 0));
        jdkPanel.add(tf, BorderLayout.CENTER);

        JPanel jdkBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        jdkBtns.add(btnBrowse);
        jdkBtns.add(btnJdkFromEnv);
        jdkPanel.add(jdkBtns, BorderLayout.EAST);

        addRow(p, c, labelWithHelp("JDK 路径", "可为空：将尝试使用 JAVA_HOME 或 PATH 中的 jpackage。按钮可一键从环境变量读取 JAVA_HOME。"), jdkPanel);

        addRow(p, c, labelWithHelp("应用名称", "传给 --name。若未手动修改，将从主 JAR 文件名自动推断。"), tfAppName);
        addRow(p, c, labelWithHelp("版本号", "传给 --app-version（建议用 x.y.z）。"), tfVersion);
        addRow(p, c, labelWithHelp("厂商", "传给 --vendor（可用于安装器显示信息）。"), tfVendor);

        addRow(p, c, labelWithHelp("图标", "支持 ico/png/jpg/jpeg。png/jpg 会在打包前自动转换为 ico。"), panelWithBrowse(tfIconPath, () -> chooseImage(tfIconPath)));
        addRow(p, c, labelWithHelp("输出目录", "传给 --dest，建议选一个空目录或专用 dist 目录。"), panelWithBrowse(tfOutputDir, () -> chooseDir(tfOutputDir)));

        return p;
    }

    private JPanel buildRightForm() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("打包输入"));
        GridBagConstraints c = baseGbc();

        int r = 0;

        addRow(p, c, labelWithHelp("主 JAR", "必选。用于 --main-jar。若不是胖包，可在下面添加依赖 JAR（会被复制到 --input 目录）。"),
                panelWithBrowse(tfJarPath, () -> chooseFile(tfJarPath, "jar")));

        // 主类 + 读取 manifest
        JButton btnDetect = new JButton("从 MANIFEST 读取 Main-Class");
        JPanel mainClassPanel = new JPanel(new BorderLayout(6, 0));
        mainClassPanel.add(tfMainClass, BorderLayout.CENTER);
        mainClassPanel.add(btnDetect, BorderLayout.EAST);

        addRow(p, c, labelWithHelp("主类", "用于 --main-class。若 jar 的 MANIFEST 含 Main-Class 可自动读取。"), mainClassPanel);
        btnDetect.addActionListener(e -> detectMainClass());

        // 依赖 JAR（带滚动）
        JScrollPane sp = new JScrollPane(listExtraJars);
        sp.setPreferredSize(new Dimension(480, 160));
        JPanel depBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        depBtns.add(btnAddJar);
        depBtns.add(btnRemoveJar);
        depBtns.add(btnClearJar);

        JPanel depPanel = new JPanel(new BorderLayout(6, 6));
        depPanel.add(sp, BorderLayout.CENTER);
        depPanel.add(depBtns, BorderLayout.SOUTH);

        addRow(p, c, labelWithHelp("从包/依赖", "非胖包时使用：这些 jar 会在打包前复制到 staging（--input）目录，避免缺依赖运行失败。"), depPanel);

        return p;
    }

    private JPanel buildBottomArea() {
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBorder(new EmptyBorder(8, 0, 0, 0));

        // 左：打包类型 + Windows 选项
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.add(new JLabel("打包类型："));
        left.add(cbType);

        left.add(new JLabel("Windows 选项："));

        left.add(checkWithHelp(ckWinMenu, "开始菜单", "仅 msi/exe 生效：--win-menu"));
        left.add(checkWithHelp(ckWinShortcut, "桌面快捷方式", "仅 msi/exe 生效：--win-shortcut"));
        left.add(checkWithHelp(ckConsole, "显示控制台", "是否添加 --win-console（勾选才加）"));
        left.add(checkWithHelp(ckPerUser, "仅当前用户", "仅 msi/exe 生效：--win-per-user-install"));
        left.add(checkWithHelp(ckDirChooser, "可选安装目录", "仅 msi/exe 生效：--win-dir-chooser"));

        // 右：按钮靠右下
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.add(btnBuildCmd);
        right.add(btnCopyCmd);
        right.add(btnRun);

        p.add(left, BorderLayout.CENTER);
        p.add(right, BorderLayout.EAST);

        return p;
    }

    // ====================== actions ======================

    private void wireActions() {
        btnSaveCfg.addActionListener(e -> {
            try {
                ConfigPath.save(cfgPath, readUIToConfig());
                logLine("配置已保存：" + cfgPath.toAbsolutePath());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "保存失败: " + ex.getMessage());
            }
        });

        btnJdkFromEnv.addActionListener(e -> {
            String javaHome = System.getenv("JAVA_HOME");
            if (javaHome == null || javaHome.isBlank()) {
                JOptionPane.showMessageDialog(this, "未检测到环境变量 JAVA_HOME。");
                return;
            }
            tfJdkHome.setText(javaHome.trim());
            refreshCommandPreview();
        });

        btnAddJar.addActionListener(e -> addExtraJars());
        btnRemoveJar.addActionListener(e -> removeSelectedExtraJars());
        btnClearJar.addActionListener(e -> extraJarModel.clear());

        cbType.addActionListener(e -> {
            refreshTypeEnableState();
            refreshCommandPreview();
        });

        btnBuildCmd.addActionListener(e -> refreshCommandPreview());
        btnCopyCmd.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(taCmd.getText()), null);
            logLine("命令已复制到剪贴板。");
        });
        btnRun.addActionListener(e -> runPackaging());

        // 自动建议应用名：仅当用户没手动改过
        tfJarPath.getDocument().addDocumentListener(new SimpleDocListener(() -> {
            String jar = tfJarPath.getText().trim();
            if (!jar.isEmpty() && !appNameTouchedByUser) {
                tfAppName.setText(suggestAppNameFromJar(jar));
            }
            refreshCommandPreview();
        }));
        tfAppName.getDocument().addDocumentListener(new SimpleDocListener(() -> appNameTouchedByUser = true));

        // 刷新命令
        var listener = new SimpleDocListener(this::refreshCommandPreview);
        tfJdkHome.getDocument().addDocumentListener(listener);
        tfMainClass.getDocument().addDocumentListener(listener);
        tfVersion.getDocument().addDocumentListener(listener);
        tfVendor.getDocument().addDocumentListener(listener);
        tfIconPath.getDocument().addDocumentListener(listener);
        tfOutputDir.getDocument().addDocumentListener(listener);

        ckWinMenu.addActionListener(e -> refreshCommandPreview());
        ckWinShortcut.addActionListener(e -> refreshCommandPreview());
        ckConsole.addActionListener(e -> refreshCommandPreview());
        ckPerUser.addActionListener(e -> refreshCommandPreview());
        ckDirChooser.addActionListener(e -> refreshCommandPreview());
    }

    private void refreshTypeEnableState() {
        String type = (String) cbType.getSelectedItem();
        boolean isAppImage = "app-image".equals(type);

        // app-image：禁用开始菜单/快捷方式/安装器选项
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

    private void detectMainClass() {
        String jar = tfJarPath.getText().trim();
        if (jar.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先选择主 JAR。");
            return;
        }
        try {
            String main = JarManifestUtils.readMainClass(Path.of(jar));
            if (main == null || main.isBlank()) {
                JOptionPane.showMessageDialog(this, "未在 MANIFEST.MF 找到 Main-Class，请手动填写。");
                return;
            }
            tfMainClass.setText(main.trim());
            logLine("已读取 Main-Class: " + main.trim());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "读取失败: " + ex.getMessage());
        }
    }

    private void refreshCommandPreview() {
        try {
            List<String> cmd = buildCommandForPreview();
            taCmd.setText(JPackageCommandBuilder.pretty(cmd));
        } catch (Exception ex) {
            taCmd.setText("命令生成失败：\n" + ex.getMessage());
        }
    }

    private List<String> buildCommandForPreview() {
        String jarPath = tfJarPath.getText().trim();
        if (jarPath.isEmpty()) throw new IllegalArgumentException("主 JAR 未选择");

        Path jar = Path.of(jarPath);
        String inputDir = jar.getParent() == null ? "." : jar.getParent().toString();

        return new JPackageCommandBuilder()
                .setJdkHome(tfJdkHome.getText().trim())
                .setType((String) cbType.getSelectedItem())
                .setName(tfAppName.getText().trim())
                .setInputDir(inputDir)
                .setMainJarFileName(jar.getFileName().toString())
                .setMainClass(tfMainClass.getText().trim())
                .setIconPath(tfIconPath.getText().trim())
                .setDest(tfOutputDir.getText().trim())
                .setVendor(tfVendor.getText().trim())
                .setAppVersion(tfVersion.getText().trim())
                .setWinMenu(ckWinMenu.isSelected())
                .setWinShortcut(ckWinShortcut.isSelected())
                .setWinConsole(ckConsole.isSelected())
                .setWinPerUserInstall(ckPerUser.isSelected())
                .setWinDirChooser(ckDirChooser.isSelected())
                .build();
    }

    private void runPackaging() {
        taLog.setText("");

        PackagerConfig current = readUIToConfig();

        if (isBlank(current.mainJarPath)) { JOptionPane.showMessageDialog(this, "请选择主 JAR。"); return; }
        if (isBlank(current.mainClass)) { JOptionPane.showMessageDialog(this, "请填写主类 Main-Class。"); return; }
        if (isBlank(current.outputDir)) { JOptionPane.showMessageDialog(this, "请选择输出目录。"); return; }
        if (isBlank(current.appName)) { JOptionPane.showMessageDialog(this, "请填写应用名称。"); return; }

        btnRun.setEnabled(false);
        btnBuildCmd.setEnabled(false);

        new Thread(() -> {
            Path stagingDir = null;
            try {
                Path outDir = Path.of(current.outputDir);
                Path workDir = outDir.resolve(".packager_work");

                // icon convert
                Path iconIco = null;
                if (!isBlank(current.iconPath)) {
                    iconIco = IconConverter.ensureIco(Path.of(current.iconPath), workDir.resolve("icons"));
                    logLine("图标准备完成: " + iconIco);
                }

                // staging jars
                Path mainJar = Path.of(current.mainJarPath);
                List<Path> extra = new ArrayList<>();
                for (String p : current.extraJarPaths) if (!isBlank(p)) extra.add(Path.of(p));

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

                logLine("开始执行 jpackage ...");
                logLine(JPackageCommandBuilder.pretty(cmd));
                logLine("------------------------------------------------------------");

                int code = ProcessRunner.run(cmd, this::logLine);

                logLine("------------------------------------------------------------");
                logLine("完成，退出码: " + code);

                // 成功/失败都保存一次（符合“稳定可复现”）
                try { ConfigPath.save(cfgPath, current); } catch (Exception ignored) {}

            } catch (Exception ex) {
                logLine("执行失败: " + ex.getMessage());
            } finally {
                StagingManager.tryDeleteDirectory(stagingDir);
                SwingUtilities.invokeLater(() -> {
                    btnRun.setEnabled(true);
                    btnBuildCmd.setEnabled(true);
                });
            }
        }, "jpackage-runner").start();
    }

    // ====================== config mapping ======================

    private PackagerConfig readUIToConfig() {
        PackagerConfig c = new PackagerConfig();
        c.jdkHome = emptyToNull(tfJdkHome.getText());
        c.mainJarPath = emptyToNull(tfJarPath.getText());
        c.mainClass = emptyToNull(tfMainClass.getText());
        c.appName = emptyToNull(tfAppName.getText());
        c.vendor = emptyToNull(tfVendor.getText());
        c.version = emptyToNull(tfVersion.getText());
        c.iconPath = emptyToNull(tfIconPath.getText());
        c.outputDir = emptyToNull(tfOutputDir.getText());
        c.type = (String) cbType.getSelectedItem();

        c.winMenu = ckWinMenu.isSelected();
        c.winShortcut = ckWinShortcut.isSelected();
        c.winConsole = ckConsole.isSelected();
        c.winPerUserInstall = ckPerUser.isSelected();
        c.winDirChooser = ckDirChooser.isSelected();

        c.extraJarPaths = new ArrayList<>();
        for (int i = 0; i < extraJarModel.size(); i++) c.extraJarPaths.add(extraJarModel.get(i));
        return c;
    }

    private void loadConfigIntoUI(PackagerConfig loaded) {
        this.cfg = loaded == null ? new PackagerConfig() : loaded;

        tfJdkHome.setText(nullToEmpty(cfg.jdkHome));
        tfJarPath.setText(nullToEmpty(cfg.mainJarPath));
        tfMainClass.setText(nullToEmpty(cfg.mainClass));
        tfAppName.setText(nullToEmpty(cfg.appName));
        tfVendor.setText(nullToEmpty(cfg.vendor));
        tfVersion.setText(nullToEmpty(cfg.version));
        tfIconPath.setText(nullToEmpty(cfg.iconPath));
        tfOutputDir.setText(nullToEmpty(cfg.outputDir));

        if (cfg.type != null && (cfg.type.equals("app-image") || cfg.type.equals("msi") || cfg.type.equals("exe"))) {
            cbType.setSelectedItem(cfg.type);
        } else {
            cbType.setSelectedItem("app-image");
        }

        ckWinMenu.setSelected(cfg.winMenu);
        ckWinShortcut.setSelected(cfg.winShortcut);
        ckConsole.setSelected(cfg.winConsole);
        ckPerUser.setSelected(cfg.winPerUserInstall);
        ckDirChooser.setSelected(cfg.winDirChooser);

        extraJarModel.clear();
        if (cfg.extraJarPaths != null) {
            for (String s : cfg.extraJarPaths) if (!isBlank(s)) extraJarModel.addElement(s);
        }

        appNameTouchedByUser = false;
        refreshTypeEnableState();
        refreshCommandPreview();

        logLine("已加载配置：" + cfgPath.toAbsolutePath());
    }

    // ====================== helpers ======================

    private String suggestAppNameFromJar(String jarPath) {
        String name = new File(jarPath).getName();
        int i = name.lastIndexOf('.');
        if (i > 0) name = name.substring(0, i);
        return name.trim().isEmpty() ? "MyApp" : name.trim();
    }

    private void addExtraJars() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("选择依赖 JAR（可多选）");
        fc.setMultiSelectionEnabled(true);
        int r = fc.showOpenDialog(this);
        if (r == JFileChooser.APPROVE_OPTION) {
            File[] files = fc.getSelectedFiles();
            if (files != null) {
                for (File f : files) {
                    if (!f.getName().toLowerCase().endsWith(".jar")) continue;
                    String path = f.getAbsolutePath();
                    if (!contains(extraJarModel, path)) extraJarModel.addElement(path);
                }
            }
        }
        refreshCommandPreview();
    }

    private void removeSelectedExtraJars() {
        List<String> selected = listExtraJars.getSelectedValuesList();
        for (String s : selected) extraJarModel.removeElement(s);
        refreshCommandPreview();
    }

    private boolean contains(DefaultListModel<String> m, String v) {
        for (int i = 0; i < m.size(); i++) if (m.get(i).equalsIgnoreCase(v)) return true;
        return false;
    }

    private void logLine(String s) {
        SwingUtilities.invokeLater(() -> {
            taLog.append(s + "\n");
            taLog.setCaretPosition(taLog.getDocument().getLength());
        });
    }

    // UI building blocks
    private GridBagConstraints baseGbc() {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1.0;
        return c;
    }

    private void addRow(JPanel p, GridBagConstraints c, JComponent label, JComponent field) {
        int row = c.gridy;
        c.gridx = 0; c.gridy = row; c.weightx = 0; c.gridwidth = 1;
        p.add(label, c);
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 1;
        p.add(field, c);
        c.gridy = row + 1;
    }

    private JPanel panelWithBrowse(JTextField tf, Runnable browse) {
        JButton b = mkBrowseBtn(browse);
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.add(tf, BorderLayout.CENTER);
        panel.add(b, BorderLayout.EAST);
        return panel;
    }

    private JButton mkBrowseBtn(Runnable r) {
        JButton b = new JButton("选择...");
        b.addActionListener(e -> r.run());
        return b;
    }

    private JComponent wrapTitled(JComponent comp, String title) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    private JComponent labelWithHelp(String text, String tip) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        panel.add(new JLabel(text));
        JLabel q = new JLabel("?");
        q.setForeground(new Color(80, 80, 80));
        q.setToolTipText(tip);
        q.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        panel.add(q);
        return panel;
    }

    private JComponent checkWithHelp(JCheckBox box, String text, String tip) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.add(box);
        panel.add(new JLabel(text));
        JLabel q = new JLabel("?");
        q.setToolTipText(tip);
        q.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        panel.add(q);
        return panel;
    }

    private void chooseFile(JTextField target, String ext) {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int r = fc.showOpenDialog(this);
        if (r == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            if (ext != null && !f.getName().toLowerCase().endsWith("." + ext)) {
                JOptionPane.showMessageDialog(this, "请选择 ." + ext + " 文件");
                return;
            }
            target.setText(f.getAbsolutePath());
        }
    }

    private void chooseImage(JTextField target) {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int r = fc.showOpenDialog(this);
        if (r == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            String n = f.getName().toLowerCase();
            if (!(n.endsWith(".ico") || n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg"))) {
                JOptionPane.showMessageDialog(this, "请选择 ico/png/jpg/jpeg 文件");
                return;
            }
            target.setText(f.getAbsolutePath());
        }
    }

    private void chooseDir(JTextField target) {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int r = fc.showOpenDialog(this);
        if (r == JFileChooser.APPROVE_OPTION) {
            target.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
    private static String nullToEmpty(String s) { return s == null ? "" : s; }
    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private interface Runnable0 { void run(); }
    private static class SimpleDocListener implements javax.swing.event.DocumentListener {
        private final Runnable0 r;
        SimpleDocListener(Runnable0 r) { this.r = r; }
        public void insertUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
    }
}
