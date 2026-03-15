package gold.debug.packager.ui;

import javax.swing.*;
import java.awt.*;

public class CmdFrame extends JPanel {
    private final JTextArea taCmd = new JTextArea(8, 60);
    private final JTextArea taLog = new JTextArea(8, 60);

    public CmdFrame() {
        setLayout(new BorderLayout(10, 10));

        taCmd.setEditable(false);
        taLog.setEditable(false);
        taCmd.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        taLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                wrapTitled(new JScrollPane(taCmd), "命令预览"),
                wrapTitled(new JScrollPane(taLog), "执行日志"));
        split.setResizeWeight(0.5);
        add(split, BorderLayout.CENTER);
    }

    private JComponent wrapTitled(JComponent comp, String title) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    public void appendLog(String log) {
        taLog.append(log + "\n");
        taLog.setCaretPosition(taLog.getDocument().getLength());
    }

    public void setCommandPreview(String command) {
        taCmd.setText(command);
    }
}