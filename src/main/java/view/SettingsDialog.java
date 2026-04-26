package view;

import service.DataManager;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

public class SettingsDialog extends JDialog {
    private JCheckBox chkAutoSave, chkSound, chkMultiThread, chkAutoClean;
    private JToggleButton btnToggleTheme;
    private JTextField txtExportPath;
    private JSlider sldOmrThreshold;

    public SettingsDialog(JFrame parent) {
        super(parent, "Cài đặt hệ thống - Team N7", true);
        setSize(550, 600);
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
            DataManager.setMultiThreadEnabled(chkMultiThread.isSelected());

            DataManager.setSoundEnabled(chkSound.isSelected());
            DataManager.setAutoCleanProcessed(chkAutoClean.isSelected());
            DataManager.setDefaultExportPath(txtExportPath.getText());

            if (sldOmrThreshold != null) {
                DataManager.setOmrThreshold(sldOmrThreshold.getValue());
            }

            JOptionPane.showMessageDialog(this, "Đã lưu cài đặt thành công!");
            dispose();
        });

        JPanel pnlBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pnlBottom.add(btnSave);
        add(pnlBottom, BorderLayout.SOUTH);

        service.WindowPersistenceManager.restoreWindow(this, "SettingsDialog", 550, 600);
        service.WindowPersistenceManager.attachSaver(this, "SettingsDialog");
    }

    private JPanel createSystemTab() {
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        chkAutoSave = new JCheckBox("Ghi nhớ vị trí cửa sổ");
        chkAutoSave.setSelected(DataManager.isAutoSavePosition());
        contentPanel.add(chkAutoSave);

        chkMultiThread = new JCheckBox("⚡ Bật chế độ Chấm Siêu tốc (Đa luồng CPU)");
        chkMultiThread.setSelected(DataManager.isMultiThreadEnabled());
        chkMultiThread.setToolTipText("Sử dụng 100% sức mạnh CPU để chấm song song nhiều bài cùng lúc.");
        contentPanel.add(chkMultiThread);

        btnToggleTheme = new JToggleButton(DataManager.isDarkMode() ? "🌙 Chế độ Tối" : "☀️ Chế độ Sáng");
        btnToggleTheme.setSelected(DataManager.isDarkMode());
        contentPanel.add(new JLabel("Giao diện:"));
        contentPanel.add(btnToggleTheme);

        contentPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        contentPanel.add(new JLabel("Bảo trì dữ liệu:"));

        JPanel pnlBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        JButton btnBackup = new JButton("📦 Backup");
        btnBackup.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("Backup_N7_" + System.currentTimeMillis() + ".zip"));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                runTaskWithProgress("Đang sao lưu...", (listener) -> {
                    DataManager.backupData(fc.getSelectedFile(), listener);
                });
            }
        });

        JButton btnRestore = new JButton("📥 Restore");
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

        JButton btnClearCache = new JButton("<html><span style=\"font-family: 'Segoe UI Emoji'\">🧹</span> Dọn dẹp rác</html>");
        btnClearCache.setToolTipText("Xóa toàn bộ các ảnh xử lý thừa và dọn dẹp các thư mục rác của Lớp/Đề thi đã bị xóa.");
        btnClearCache.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Bạn có muốn quét sâu để dọn dẹp ảnh rác và dữ liệu mồ côi (từ các lớp/đề đã xóa) không?", "Xác nhận dọn dẹp", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                runTaskWithProgress("Đang dọn dẹp bộ nhớ...", (listener) -> {
                    DataManager.performDeepCleanup(listener);
                });
            }
        });

        pnlBtns.add(btnBackup); pnlBtns.add(btnRestore); pnlBtns.add(btnClearCache);
        contentPanel.add(pnlBtns);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(contentPanel, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(wrapper);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBorder(null);

        JPanel p = new JPanel(new BorderLayout());
        p.add(scrollPane, BorderLayout.CENTER);
        return p;
    }

    private JPanel createGradingTab() {
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        chkSound = new JCheckBox("Phát âm thanh thông báo khi chấm xong");
        chkSound.setSelected(DataManager.isSoundEnabled());
        contentPanel.add(chkSound);

        chkAutoClean = new JCheckBox("Tự động xóa ảnh xử lý nếu bài đúng 100% (Tiết kiệm dung lượng)");
        chkAutoClean.setSelected(DataManager.isAutoCleanProcessed());
        contentPanel.add(chkAutoClean);

        contentPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        contentPanel.add(new JLabel("Độ nhạy quét nhận diện vết tô OMR (0 - 255):"));

        JPanel pnlInput = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));
        pnlInput.add(new JLabel("Mức độ hiện tại: "));

        JSpinner spnOmrValue = new JSpinner(new SpinnerNumberModel(DataManager.getOmrThreshold(), 0, 255, 1));
        pnlInput.add(spnOmrValue);

        JLabel lblWarning = new JLabel("");
        lblWarning.setFont(new Font("Arial", Font.ITALIC, 12));
        lblWarning.setForeground(Color.GRAY);
        pnlInput.add(Box.createHorizontalStrut(10));
        pnlInput.add(lblWarning);

        contentPanel.add(pnlInput);

        sldOmrThreshold = new JSlider(JSlider.HORIZONTAL, 0, 255, DataManager.getOmrThreshold());
        sldOmrThreshold.setMajorTickSpacing(50);
        sldOmrThreshold.setMinorTickSpacing(10);
        sldOmrThreshold.setPaintTicks(true);
        sldOmrThreshold.setPaintLabels(true);

        JLabel lblPreview = new JLabel();
        lblPreview.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblPreview.setIcon(generateOmrPreview(sldOmrThreshold.getValue()));

        Runnable updateUI = () -> {
            int val = sldOmrThreshold.getValue();
            String warningText = "";
            if (val <= 100) {
                warningText = "(Khắt khe - Dễ bỏ qua nét tô nhạt!)";
            } else if (val >= 180) {
                warningText = "(Rất nhạy - Dễ nhận nhầm vết tẩy!)";
            }
            lblWarning.setText(warningText);
            lblPreview.setIcon(generateOmrPreview(val));
        };

        sldOmrThreshold.addChangeListener(e -> {
            spnOmrValue.setValue(sldOmrThreshold.getValue());
            updateUI.run();
        });

        spnOmrValue.addChangeListener(e -> {
            int val = (int) spnOmrValue.getValue();
            sldOmrThreshold.setValue(val);
            updateUI.run();
        });

        updateUI.run();

        contentPanel.add(sldOmrThreshold);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 15)));
        contentPanel.add(lblPreview);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 20)));

        contentPanel.add(new JLabel("Thư mục xuất file mặc định:"));
        JPanel pnlPath = new JPanel(new BorderLayout(5, 0));
        txtExportPath = new JTextField(DataManager.getDefaultExportPath());
        JButton btnBrowse = new JButton("...");
        btnBrowse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) txtExportPath.setText(fc.getSelectedFile().getAbsolutePath());
        });
        pnlPath.add(txtExportPath, BorderLayout.CENTER); pnlPath.add(btnBrowse, BorderLayout.EAST);
        contentPanel.add(pnlPath);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(contentPanel, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(wrapper);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBorder(null);

        JPanel p = new JPanel(new BorderLayout());
        p.add(scrollPane, BorderLayout.CENTER);
        return p;
    }

    private ImageIcon generateOmrPreview(int threshold) {
        int width = 500;
        int height = 150;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.LIGHT_GRAY);
        g.drawRect(0, 0, width - 1, height - 1);

        int[] grays = {50, 130, 190};
        String[] labels = {"Tô đậm (Tốt)", "Tô vừa phải", "Tô quá mờ/Nhạt"};

        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 12));
        g.drawString("Mô phỏng bài làm:", 15, 40);
        g.drawString("Phần mềm nhận diện:", 15, 110);

        for (int i = 0; i < 3; i++) {
            int cx = 200 + i * 120;

            g.setColor(new Color(grays[i], grays[i], grays[i]));
            g.fillOval(cx - 15, 20, 30, 30);
            g.setColor(Color.GRAY);
            g.drawOval(cx - 15, 20, 30, 30);

            g.setColor(Color.BLACK);
            g.setFont(new Font("Arial", Font.PLAIN, 12));
            g.drawString(labels[i], cx - 25, 70);

            boolean isDetected = grays[i] <= threshold;

            if (isDetected) {
                g.setColor(Color.BLACK);
                g.fillOval(cx - 15, 90, 30, 30);
                g.setColor(new Color(0, 150, 0));
                g.setFont(new Font("Arial", Font.BOLD, 12));
                g.drawString("✔ Được tính", cx - 20, 138);
            } else {
                g.setColor(Color.WHITE);
                g.fillOval(cx - 15, 90, 30, 30);
                g.setColor(Color.LIGHT_GRAY);
                g.drawOval(cx - 15, 90, 30, 30);
                g.setColor(Color.RED);
                g.setFont(new Font("Arial", Font.BOLD, 12));
                g.drawString("❌ Bỏ qua", cx - 20, 138);
            }
        }
        g.dispose();
        return new ImageIcon(img);
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
                        // Tính toán an toàn không bao giờ bị / zero
                        int progress = (total > 0) ? (int) ((double) current / total * 100) : 100;
                        // Ép giới hạn 0-100 để không crash ProgressMonitor
                        progress = Math.max(0, Math.min(100, progress));
                        publish(new Object[]{progress, fileName}); // Đã fix text hiển thị gọn gàng
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