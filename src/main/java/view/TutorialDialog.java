package view;

import service.DataManager;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

public class TutorialDialog extends JDialog {
    private JLabel lblImage;
    private JButton btnPrev, btnNext;
    private JCheckBox chkDoNotShow;
    private int currentStep = 1;
    private final int MAX_STEPS = 21; // Tổng số ảnh hướng dẫn
    private final String TUTORIAL_PATH = "data/tutorial/";

    public TutorialDialog(JFrame parent) {
        super(parent, "Hướng dẫn chụp và xử lý ảnh (CamScanner)", true);
        setSize(550, 750);
        setLayout(new BorderLayout());
        setLocationRelativeTo(parent);

        // 1. Khu vực hiển thị ảnh
        lblImage = new JLabel("Đang tải ảnh...", SwingConstants.CENTER);
        add(new JScrollPane(lblImage), BorderLayout.CENTER);

        // 2. Khu vực điều khiển (Bottom)
        JPanel pnlBottom = new JPanel(new BorderLayout(10, 10));
        pnlBottom.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        chkDoNotShow = new JCheckBox("Không hiện lại bảng hướng dẫn này vào lần sau");
        chkDoNotShow.setSelected(!DataManager.shouldShowTutorial());

        JPanel pnlButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPrev = new JButton("⬅ Trước");
        btnNext = new JButton("Tiếp ➡");
        JButton btnClose = new JButton("Đóng");

        pnlButtons.add(btnPrev);
        pnlButtons.add(btnNext);
        pnlButtons.add(btnClose);

        pnlBottom.add(chkDoNotShow, BorderLayout.WEST);
        pnlBottom.add(pnlButtons, BorderLayout.EAST);
        add(pnlBottom, BorderLayout.SOUTH);

        // --- SỰ KIỆN ---
        btnPrev.addActionListener(e -> {
            if (currentStep > 1) { currentStep--; loadImage(); }
        });

        btnNext.addActionListener(e -> {
            if (currentStep < MAX_STEPS) { currentStep++; loadImage(); }
        });

        btnClose.addActionListener(e -> savePrefAndClose());

        // Bắt sự kiện khi người dùng ấn dấu X góc trên cùng bên phải
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                savePrefAndClose();
            }
        });

        loadImage(); // Tải ảnh đầu tiên
    }

    private void loadImage() {
        File imgFile = new File(TUTORIAL_PATH + currentStep + ".jpg");
        if (imgFile.exists()) {
            ImageIcon icon = new ImageIcon(imgFile.getAbsolutePath());
            Image img = icon.getImage();

            // Ép kích thước ảnh theo chiều cao cố định để không bị vỡ form
            int targetHeight = 600;
            int targetWidth = (int) (icon.getIconWidth() * ((double) targetHeight / icon.getIconHeight()));

            lblImage.setIcon(new ImageIcon(img.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH)));
            lblImage.setText("");
        } else {
            lblImage.setIcon(null);
            lblImage.setText("Không tìm thấy ảnh: " + imgFile.getName() + " trong thư mục data/tutorial/");
        }

        // Cập nhật trạng thái nút
        btnPrev.setEnabled(currentStep > 1);
        btnNext.setEnabled(currentStep < MAX_STEPS);
        setTitle("Hướng dẫn xử lý ảnh - Bước " + currentStep + " / " + MAX_STEPS);
    }

    private void savePrefAndClose() {
        // Nếu tích vào ô "Không hiện lại" -> lưu false, ngược lại lưu true
        DataManager.setTutorialPreference(!chkDoNotShow.isSelected());
        dispose();
    }
}