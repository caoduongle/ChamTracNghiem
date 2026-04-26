package view;

import service.DataManager;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

public class SettingsDialog extends JDialog {
    private JCheckBox chkAutoSave, chkSound;
    private JToggleButton btnToggleTheme;
    private JTextField txtExportPath;

    public SettingsDialog(JFrame parent) {
        super(parent, "Cài đặt hệ thống - Team N7", true);
        setSize(550, 450);
        setLayout(new BorderLayout(10, 10));
        setLocationRelativeTo(parent);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("🖥 Hệ thống", createSystemTab());
        tabs.addTab("📝 Chấm thi & Xuất file", createGradingTab());
        add(tabs, BorderLayout.CENTER);

        JButton btnSave = new JButton("💾 Áp dụng & Lưu");
        btnSave.addActionListener(e -> {
            DataManager.setAutoSavePosition(chkAutoSave.isSelected());
            DataManager.setDarkMode(btnToggleTheme.isSelected());
            DataManager.setSoundEnabled(chkSound.isSelected());
            DataManager.setDefaultExportPath(txtExportPath.getText());
            JOptionPane.showMessageDialog(this, "Đã lưu cài đặt thành công!");
            dispose();
        });

        JPanel pnlBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pnlBottom.add(btnSave);
        add(pnlBottom, BorderLayout.SOUTH);

        service.WindowPersistenceManager.restoreWindow(this, "SettingsDialog", 550, 450);
        service.WindowPersistenceManager.attachSaver(this, "SettingsDialog");
    }

    private JPanel createSystemTab() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        chkAutoSave = new JCheckBox("Ghi nhớ vị trí cửa sổ");
        chkAutoSave.setSelected(DataManager.isAutoSavePosition());
        p.add(chkAutoSave);

        btnToggleTheme = new JToggleButton(DataManager.isDarkMode() ? "🌙 Chế độ Tối" : "☀️ Chế độ Sáng");
        btnToggleTheme.setSelected(DataManager.isDarkMode());
        p.add(new JLabel("Giao diện:")); p.add(btnToggleTheme);

        p.add(Box.createRigidArea(new Dimension(0, 20)));
        p.add(new JLabel("Bảo trì dữ liệu:"));

        JPanel pnlBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));
        JButton btnBackup = new JButton("📦 Sao lưu dữ liệu (Backup)");
        btnBackup.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("Backup_N7_" + System.currentTimeMillis() + ".zip"));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                runTaskWithProgress("Đang sao lưu...", (listener) -> {
                    DataManager.backupData(fc.getSelectedFile(), listener);
                });
            }
        });

        JButton btnRestore = new JButton("📥 Nhập dữ liệu (Restore)");
        btnRestore.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "GHI ĐÈ dữ liệu hiện tại? Hành động này không thể hoàn tác.", "Xác nhận", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                JFileChooser fc = new JFileChooser();
                if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    runTaskWithProgress("Đang phục hồi...", (listener) -> {
                        DataManager.restoreData(fc.getSelectedFile(), listener);
                    });
                }
            }
        });

        pnlBtns.add(btnBackup); pnlBtns.add(Box.createHorizontalStrut(10)); pnlBtns.add(btnRestore);
        p.add(pnlBtns);
        return p;
    }

    private JPanel createGradingTab() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        chkSound = new JCheckBox("Phát âm thanh thông báo khi chấm xong");
        chkSound.setSelected(DataManager.isSoundEnabled());
        p.add(chkSound);

        p.add(Box.createRigidArea(new Dimension(0, 15)));
        p.add(new JLabel("Thư mục xuất file mặc định:"));
        JPanel pnlPath = new JPanel(new BorderLayout(5, 0));
        txtExportPath = new JTextField(DataManager.getDefaultExportPath());
        JButton btnBrowse = new JButton("...");
        btnBrowse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) txtExportPath.setText(fc.getSelectedFile().getAbsolutePath());
        });
        pnlPath.add(txtExportPath, BorderLayout.CENTER); pnlPath.add(btnBrowse, BorderLayout.EAST);
        p.add(pnlPath);
        return p;
    }

    private void runTaskWithProgress(String title, TaskProcessor task) {
        ProgressMonitor pm = new ProgressMonitor(this, title, "Khởi tạo...", 0, 100);
        pm.setMillisToDecideToPopup(0);
        pm.setMillisToPopup(0);

        SwingWorker<Void, Object[]> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                task.process(new DataManager.ProgressListener() {
                    @Override
                    public void onProgress(int current, int total, String fileName) {
                        int progress = (int) ((double) current / total * 100);
                        publish(new Object[]{progress, "Đang xử lý: " + fileName});
                    }
                    @Override
                    public boolean isCanceled() { return pm.isCanceled(); }
                });
                return null;
            }

            @Override
            protected void process(List<Object[]> chunks) {
                if (pm.isCanceled()) return;
                Object[] last = chunks.get(chunks.size() - 1);
                pm.setProgress((int) last[0]);
                pm.setNote((String) last[1]);
            }

            @Override
            protected void done() {
                pm.close();
                try {
                    get();
                    JOptionPane.showMessageDialog(SettingsDialog.this, "Thành công!");
                } catch (Exception ex) {
                    if (!pm.isCanceled()) JOptionPane.showMessageDialog(SettingsDialog.this, "Lỗi: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }
}

interface TaskProcessor {
    void process(DataManager.ProgressListener listener) throws Exception;
}