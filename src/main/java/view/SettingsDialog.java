package view;

import service.DataManager;
import javax.swing.*;
import java.awt.*;

public class SettingsDialog extends JDialog {
    private JCheckBox chkAutoSave;
    private JToggleButton btnToggleTheme;

    public SettingsDialog(JFrame parent) {
        super(parent, "Cài đặt hệ thống", true);
        setSize(400, 250);
        setLayout(new BorderLayout(10, 10));
        setLocationRelativeTo(parent);

        JPanel pnlContent = new JPanel();
        pnlContent.setLayout(new BoxLayout(pnlContent, BoxLayout.Y_AXIS));
        pnlContent.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // 1. Chức năng tự động lưu vị trí
        chkAutoSave = new JCheckBox("Tự động ghi nhớ vị trí cửa sổ lần cuối");
        chkAutoSave.setSelected(DataManager.isAutoSavePosition());
        chkAutoSave.setFont(new Font("Arial", Font.PLAIN, 14));
        pnlContent.add(chkAutoSave);
        pnlContent.add(Box.createRigidArea(new Dimension(0, 20)));

        // 2. Chế độ Sáng/Tối
        JLabel lblTheme = new JLabel("Chế độ hiển thị:");
        lblTheme.setFont(new Font("Arial", Font.BOLD, 14));
        pnlContent.add(lblTheme);
        pnlContent.add(Box.createRigidArea(new Dimension(0, 10)));

        btnToggleTheme = new JToggleButton(DataManager.isDarkMode() ? "🌙 Chế độ Tối (Dark)" : "☀️ Chế độ Sáng (Light)");
        btnToggleTheme.setSelected(DataManager.isDarkMode());
        btnToggleTheme.addActionListener(e -> {
            if (btnToggleTheme.isSelected()) {
                btnToggleTheme.setText("🌙 Chế độ Tối (Dark)");
            } else {
                btnToggleTheme.setText("☀️ Chế độ Sáng (Light)");
            }
        });
        pnlContent.add(btnToggleTheme);

        add(pnlContent, BorderLayout.CENTER);

        // Nút Lưu
        JButton btnSave = new JButton("Áp dụng & Lưu");
        btnSave.addActionListener(e -> {
            DataManager.setAutoSavePosition(chkAutoSave.isSelected());
            DataManager.setDarkMode(btnToggleTheme.isSelected());
            JOptionPane.showMessageDialog(this, "Đã lưu cài đặt! Một số thay đổi sẽ có hiệu lực sau khi khởi động lại.");
            dispose();
        });

        JPanel pnlBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pnlBottom.add(btnSave);
        add(pnlBottom, BorderLayout.SOUTH);

        // Lưu vị trí cho chính cửa sổ cài đặt
        service.WindowPersistenceManager.restoreWindow(this, "SettingsDialog", 400, 250);
        service.WindowPersistenceManager.attachSaver(this, "SettingsDialog");
    }
}