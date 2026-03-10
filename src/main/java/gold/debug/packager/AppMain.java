package gold.debug.packager;

// import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import gold.debug.packager.ui.MainFrame;

import javax.swing.*;

public class AppMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // 选一种即可：亮色 / 暗色
                // FlatLaf.setup(new FlatLightLaf());
                FlatLaf.setup(new FlatDarkLaf());

                // 可选：让原生菜单栏更像系统（对 Mac 更明显，Windows 也无害）
                System.setProperty("apple.laf.useScreenMenuBar", "true");
            } catch (Exception ex) {
                // 兜底：FlatLaf 失败时用系统 LAF
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {}
            }

            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}

