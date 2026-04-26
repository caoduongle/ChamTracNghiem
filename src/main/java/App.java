
import view.MainView;

import controller.MainController;

import javax.swing.*;
import java.awt.*;

public class App {
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            java.io.StringWriter sw = new java.io.StringWriter();
            throwable.printStackTrace(new java.io.PrintWriter(sw));
            String errorDetails = sw.toString();

            // Đẩy thông báo ra luồng giao diện một cách an toàn
            javax.swing.SwingUtilities.invokeLater(() -> {
                javax.swing.JTextArea textArea = new javax.swing.JTextArea(errorDetails);
                textArea.setEditable(false);
                textArea.setForeground(java.awt.Color.RED);
                javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane(textArea);
                scrollPane.setPreferredSize(new java.awt.Dimension(700, 400));

                javax.swing.JOptionPane.showMessageDialog(null, scrollPane,
                        "Oops! Phần mềm gặp sự cố (Crash Log)",
                        javax.swing.JOptionPane.ERROR_MESSAGE);
            });
        });
        // Đặt dòng này lên đầu tiên trong hàm main, TRƯỚC KHI gọi FlatLaf.setup()
        if (service.DataManager.isDarkMode()) {
            com.formdev.flatlaf.FlatDarkLaf.setup();
        } else {
            com.formdev.flatlaf.FlatLightLaf.setup();
        }

        // invokeLater đảm bảo giao diện được tạo và cập nhật trên Event Dispatch Thread (EDT),
        service.UpdateService.checkForUpdates(null);
        // tránh các lỗi treo UI tiềm ẩn.
        SwingUtilities.invokeLater(() -> {
            MainView view = new MainView();
            // Khởi tạo Controller và truyền View vào để quản lý
            MainController controller = new MainController(view);

            // Hiển thị giao diện lên màn hình
            view.setVisible(true);
        });
    }
}