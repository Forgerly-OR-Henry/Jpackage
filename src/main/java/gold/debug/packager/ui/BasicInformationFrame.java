package gold.debug.packager.ui;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class BasicInformationFrame extends JPanel {

    private final JTextField tfJdkHome = new JTextField();
    private final JTextField tfAppName = new JTextField();
    private final JTextField tfVersion = new JTextField();
    private final JTextField tfVendor = new JTextField();
    private final JTextField tfIconPath = new JTextField();
    private final JTextField tfOutputDir = new JTextField();

    private final JButton btnJdkBrowse = new JButton("选择...");
    private final JButton btnJdkFromEnv = new JButton("从环境变量读取");
    private final JButton btnIconBrowse = new JButton("选择...");
    private final JButton btnOutputBrowse = new JButton("选择...");

    public BasicInformationFrame() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createTitledBorder("基础信息"));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // JDK 路径：输入框 + 选择按钮 + 环境变量按钮
        JPanel jdkPanel = new JPanel(new BorderLayout(6, 0));
        JPanel jdkBtnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        jdkBtnPanel.add(btnJdkBrowse);
        jdkBtnPanel.add(btnJdkFromEnv);
        jdkPanel.add(tfJdkHome, BorderLayout.CENTER);
        jdkPanel.add(jdkBtnPanel, BorderLayout.EAST);
        addRow("JDK 路径", jdkPanel, c, row++);

        addRow("应用名称", tfAppName, c, row++);
        addRow("版本号", tfVersion, c, row++);
        addRow("厂商", tfVendor, c, row++);

        // 图标：输入框 + 选择按钮
        JPanel iconPanel = new JPanel(new BorderLayout(6, 0));
        iconPanel.add(tfIconPath, BorderLayout.CENTER);
        iconPanel.add(btnIconBrowse, BorderLayout.EAST);
        addRow("图标", iconPanel, c, row++);

        // 输出目录：输入框 + 选择按钮
        JPanel outputPanel = new JPanel(new BorderLayout(6, 0));
        outputPanel.add(tfOutputDir, BorderLayout.CENTER);
        outputPanel.add(btnOutputBrowse, BorderLayout.EAST);
        addRow("输出目录", outputPanel, c, row++);

        wireActions();
    }

    private void wireActions() {
        btnJdkFromEnv.addActionListener(e -> {
            String javaHome = System.getenv("JAVA_HOME");
            if (javaHome == null || javaHome.isBlank()) {
                JOptionPane.showMessageDialog(this, "未检测到环境变量 JAVA_HOME。");
                return;
            }
            tfJdkHome.setText(javaHome.trim());
        });

        btnJdkBrowse.addActionListener(e -> chooseDirectory(tfJdkHome));
        btnOutputBrowse.addActionListener(e -> chooseDirectory(tfOutputDir));
        btnIconBrowse.addActionListener(e -> chooseImageFile(tfIconPath));
    }

    // 通用行添加函数：支持 JTextField / JPanel / JScrollPane 等任意 JComponent

    private void addRow(String label, JComponent component, GridBagConstraints c, int row) {
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        c.gridwidth = 1;
        add(new JLabel(label), c);

        c.gridx = 1;
        c.gridy = row;
        c.weightx = 1;
        c.gridwidth = 2;
        add(component, c);
    }

    private void chooseDirectory(JTextField target) {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int r = fc.showOpenDialog(this);
        if (r == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            if (f != null) {
                target.setText(f.getAbsolutePath());
            }
        }
    }

    private void chooseImageFile(JTextField target) {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int r = fc.showOpenDialog(this);
        if (r == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            if (f != null) {
                String name = f.getName().toLowerCase();
                if (!(name.endsWith(".ico") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"))) {
                    JOptionPane.showMessageDialog(this, "请选择 ico/png/jpg/jpeg 文件。");
                    return;
                }
                target.setText(f.getAbsolutePath());
            }
        }
    }

    // =========================
    // Getter
    // =========================

    public String getJdkHome() {
        return tfJdkHome.getText().trim();
    }

    public String getAppName() {
        return tfAppName.getText().trim();
    }

    public String getVersion() {
        return tfVersion.getText().trim();
    }

    public String getVendor() {
        return tfVendor.getText().trim();
    }

    public String getIconPath() {
        return tfIconPath.getText().trim();
    }

    public String getOutputDir() {
        return tfOutputDir.getText().trim();
    }

    // =========================
    // Setter
    // =========================

    public void setJdkHome(String v) {
        tfJdkHome.setText(v == null ? "" : v);
    }

    public void setAppName(String v) {
        tfAppName.setText(v == null ? "" : v);
    }

    public void setVersion(String v) {
        tfVersion.setText(v == null ? "" : v);
    }

    public void setVendor(String v) {
        tfVendor.setText(v == null ? "" : v);
    }

    public void setIconPath(String v) {
        tfIconPath.setText(v == null ? "" : v);
    }

    public void setOutputDir(String v) {
        tfOutputDir.setText(v == null ? "" : v);
    }

    // =========================
    // 需要时可直接取控件本身
    // =========================

    public JTextField getTfJdkHome() {
        return tfJdkHome;
    }

    public JTextField getTfAppName() {
        return tfAppName;
    }

    public JTextField getTfVersion() {
        return tfVersion;
    }

    public JTextField getTfVendor() {
        return tfVendor;
    }

    public JTextField getTfIconPath() {
        return tfIconPath;
    }

    public JTextField getTfOutputDir() {
        return tfOutputDir;
    }
}