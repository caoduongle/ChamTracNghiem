
import view.MainView;

import controller.MainController;

import javax.swing.*;
import java.awt.*;

public class App {
    public static void main(String[] args) {
        // Đặt dòng này lên đầu tiên trong hàm main, TRƯỚC KHI gọi FlatLaf.setup()
        if (service.DataManager.isDarkMode()) {
            com.formdev.flatlaf.FlatDarkLaf.setup();
        } else {
            com.formdev.flatlaf.FlatLightLaf.setup();
        }

        // invokeLater đảm bảo giao diện được tạo và cập nhật trên Event Dispatch Thread (EDT),
        service.UpdateService.checkForUpdates();
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