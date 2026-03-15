package gold.debug.packager.ui;

import gold.debug.packager.core.ManifestReader;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PackagingFrame extends JPanel {

    private final JTextField tfJarPath = new JTextField();
    private final JTextField tfMainClass = new JTextField();

    private final JButton btnJarBrowse = new JButton("选择...");
    private final JButton btnDetectMainClass = new JButton("从 MANIFEST 读取 Main-Class");
    private final JButton btnAutoDeps = new JButton("自动获取依赖");

    private final DefaultListModel<String> extraJarModel = new DefaultListModel<>();
    private final JList<String> listExtraJars = new JList<>(extraJarModel);

    private final JButton btnAddJar = new JButton("添加依赖 JAR");
    private final JButton btnRemoveJar = new JButton("移除选中");
    private final JButton btnClearJar = new JButton("清空");

    public PackagingFrame() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createTitledBorder("打包输入"));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // 主 JAR
        JPanel jarPanel = new JPanel(new BorderLayout(6, 0));
        jarPanel.add(tfJarPath, BorderLayout.CENTER);
        jarPanel.add(btnJarBrowse, BorderLayout.EAST);
        addRow("主 JAR", jarPanel, c, row++);

        // 主类
        JPanel mainClassPanel = new JPanel(new BorderLayout(6, 0));
        mainClassPanel.add(tfMainClass, BorderLayout.CENTER);
        mainClassPanel.add(btnDetectMainClass, BorderLayout.EAST);
        addRow("主类", mainClassPanel, c, row++);

        // 依赖区
        JScrollPane depScrollPane = new JScrollPane(listExtraJars);
        depScrollPane.setPreferredSize(new Dimension(480, 180));

        JPanel depButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        depButtonPanel.add(btnAutoDeps);
        depButtonPanel.add(btnAddJar);
        depButtonPanel.add(btnRemoveJar);
        depButtonPanel.add(btnClearJar);

        JPanel depPanel = new JPanel(new BorderLayout(6, 6));
        depPanel.add(depScrollPane, BorderLayout.CENTER);
        depPanel.add(depButtonPanel, BorderLayout.SOUTH);

        addRow("从包/依赖", depPanel, c, row++);

        wireActions();
    }

    private void wireActions() {
        btnJarBrowse.addActionListener(e -> chooseJarFile(tfJarPath));

        btnDetectMainClass.addActionListener(e -> {
            String jarPath = tfJarPath.getText().trim();
            if (jarPath.isEmpty()) {
                JOptionPane.showMessageDialog(this, "请先选择主 JAR。");
                return;
            }

            try {
                String mainClass = ManifestReader.readMainClass(Path.of(jarPath));
                if (mainClass == null || mainClass.isBlank()) {
                    JOptionPane.showMessageDialog(this, "MANIFEST 中未找到 Main-Class。");
                    return;
                }
                tfMainClass.setText(mainClass.trim());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "读取 Main-Class 失败：\n" + ex.getMessage());
            }
        });

        btnAutoDeps.addActionListener(e -> {
            String jarPath = tfJarPath.getText().trim();
            if (jarPath.isEmpty()) {
                JOptionPane.showMessageDialog(this, "请先选择主 JAR。");
                return;
            }

            try {
                List<Path> deps = ManifestReader.getDependencies(Path.of(jarPath));
                extraJarModel.clear();
                for (Path dep : deps) {
                    if (dep != null) {
                        extraJarModel.addElement(dep.toAbsolutePath().toString());
                    }
                }
                JOptionPane.showMessageDialog(this, "已自动读取依赖，共 " + deps.size() + " 项。");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "自动获取依赖失败：\n" + ex.getMessage());
            }
        });

        btnAddJar.addActionListener(e -> addExtraJars());
        btnRemoveJar.addActionListener(e -> removeSelectedExtraJars());
        btnClearJar.addActionListener(e -> extraJarModel.clear());
    }

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

    private void chooseJarFile(JTextField target) {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int r = fc.showOpenDialog(this);
        if (r == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            if (f == null) return;

            String name = f.getName().toLowerCase();
            if (!name.endsWith(".jar")) {
                JOptionPane.showMessageDialog(this, "请选择 .jar 文件。");
                return;
            }

            target.setText(f.getAbsolutePath());
        }
    }

    private void addExtraJars() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("选择依赖 JAR（可多选）");
        fc.setMultiSelectionEnabled(true);
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);

        int r = fc.showOpenDialog(this);
        if (r == JFileChooser.APPROVE_OPTION) {
            File[] files = fc.getSelectedFiles();
            if (files == null) return;

            for (File f : files) {
                if (f == null) continue;
                if (!f.getName().toLowerCase().endsWith(".jar")) continue;

                String path = f.getAbsolutePath();
                if (!contains(extraJarModel, path)) {
                    extraJarModel.addElement(path);
                }
            }
        }
    }

    private void removeSelectedExtraJars() {
        List<String> selected = listExtraJars.getSelectedValuesList();
        for (String s : selected) {
            extraJarModel.removeElement(s);
        }
    }

    private boolean contains(DefaultListModel<String> model, String value) {
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    // =========================
    // Getter
    // =========================

    public String getJarPath() {
        return tfJarPath.getText().trim();
    }

    public String getMainClass() {
        return tfMainClass.getText().trim();
    }

    public List<String> getExtraJarPaths() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < extraJarModel.size(); i++) {
            result.add(extraJarModel.get(i));
        }
        return result;
    }

    // =========================
    // Setter
    // =========================

    public void setJarPath(String v) {
        tfJarPath.setText(v == null ? "" : v);
    }

    public void setMainClass(String v) {
        tfMainClass.setText(v == null ? "" : v);
    }

    public void setExtraJarPaths(List<String> paths) {
        extraJarModel.clear();
        if (paths == null) return;

        for (String p : paths) {
            if (p != null && !p.trim().isEmpty()) {
                extraJarModel.addElement(p.trim());
            }
        }
    }

    // =========================
    // 控件访问（需要时可直接取）
    // =========================

    public JTextField getTfJarPath() {
        return tfJarPath;
    }

    public JTextField getTfMainClass() {
        return tfMainClass;
    }

    public JList<String> getListExtraJars() {
        return listExtraJars;
    }
}