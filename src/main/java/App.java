import view.MainView;
import controller.MainController;

import javax.swing.SwingUtilities;

public class App {
    public static void main(String[] args) {
        // invokeLater đảm bảo giao diện được tạo và cập nhật trên Event Dispatch Thread (EDT),
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