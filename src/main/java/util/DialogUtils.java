package util;

import javax.swing.*;
import java.awt.*;

public class DialogUtils {
    public static void showConnectionDialog(Component parent) {
        String downloadURL = "https://github.com/caoduongle/ChamTracNghiem/releases/download/v2.0.0/app-release.apk";
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(parent), "Kết nối App", true);
        dialog.setLayout(new BorderLayout());

        // ... (Bê toàn bộ logic tạo UI Connection từ code cũ vào đây) ...

        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    public static void setAppIcon(Window window) {
        try {
            window.setIconImage(new ImageIcon("icon.jpg").getImage());
        } catch (Exception e) {
            System.err.println("Icon not found");
        }
    }
}