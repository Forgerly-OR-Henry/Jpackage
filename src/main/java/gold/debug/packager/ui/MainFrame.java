package gold.debug.packager.ui;

import gold.debug.packager.config.ConfigPath;
import gold.debug.packager.config.PackagerConfig;
import gold.debug.packager.core.MainWork;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

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
        setTitle("JPackage 1.0.0");
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

        // 实时预览
        DocumentListener listener = new SimpleDocListener(this::refreshCommandPreview);

        basicInfoFrame.getTfJdkHome().getDocument().addDocumentListener(listener);
        basicInfoFrame.getTfAppName().getDocument().addDocumentListener(listener);
        basicInfoFrame.getTfVersion().getDocument().addDocumentListener(listener);
        basicInfoFrame.getTfVendor().getDocument().addDocumentListener(listener);
        basicInfoFrame.getTfIconPath().getDocument().addDocumentListener(listener);
        basicInfoFrame.getTfOutputDir().getDocument().addDocumentListener(listener);

        packagingFrame.getTfJarPath().getDocument().addDocumentListener(listener);
        packagingFrame.getTfMainClass().getDocument().addDocumentListener(listener);

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

        cmdFrame.appendLog("");

        MainWork.runPackagingAsync(
                current,
                msg -> SwingUtilities.invokeLater(() -> cmdFrame.appendLog(msg)),
                () -> SwingUtilities.invokeLater(() -> {
                    btnRun.setEnabled(true);
                    btnBuildCmd.setEnabled(true);
                })
        );
    }

    private void refreshCommandPreview() {
        try {
            PackagerConfig config = readUIToConfig();
            lastCommandPreview = MainWork.buildCommandPreview(config);
            cmdFrame.setCommandPreview(lastCommandPreview);
        } catch (Exception ex) {
            lastCommandPreview = "命令生成失败：\n" + ex.getMessage();
            cmdFrame.setCommandPreview(lastCommandPreview);
        }
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

    private static class SimpleDocListener implements DocumentListener {
        private final Runnable action;

        private SimpleDocListener(Runnable action) {
            this.action = action;
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            action.run();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            action.run();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            action.run();
        }
    }
}