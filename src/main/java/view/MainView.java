package view;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import service.WindowPersistenceManager;

public class MainView extends JFrame {

    private JButton btnConnectPhone, btnBackToMenu, btnSetAnswerKey;
    private JButton btnStartGrading, btnStopGrading, btnDeleteResult;
    private JButton btnExportScores, btnExportConfig, btnDashboard;
    private JButton btnBulkChangeCode, btnChangeSelectedCode;

    private JComboBox<String> cbxTemplate;
    private JComboBox<String> cbxSortResults;
    private JLabel lblTemplatePreview;

    private JTable tblResults;
    private DefaultTableModel tableModel;

    private JLabel lblStatus, lblServerStatus;
    private JProgressBar progressBar;

    public MainView() {
        setTitle("Phần mềm Chấm Trắc Nghiệm");
        try {
            setIconImage(new ImageIcon("icon.jpg").getImage());
        } catch (Exception e) {
            System.err.println("Không tìm thấy icon.jpg");
        }

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        initUI();

        WindowPersistenceManager.restoreWindow(this, "MainView", 1200, 700);
        WindowPersistenceManager.attachSaver(this, "MainView");
    }

    private void initUI() {
        // --- PANEL TRÁI (THANH CÔNG CỤ) ---
        JPanel panelLeft = new JPanel();
        panelLeft.setLayout(new BoxLayout(panelLeft, BoxLayout.Y_AXIS));
        panelLeft.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Khởi tạo các Component
        btnConnectPhone = createStyledButton(
                "<html><span style='font-family: \"Segoe UI Emoji\"'>📱</span> Kết nối Điện thoại</html>",
                new Color(0, 123, 255),
                Color.WHITE
        );
        btnStartGrading = createStyledButton("2. Bắt đầu chấm", new Color(40, 167, 69), Color.WHITE);

        btnBackToMenu = new JButton("⬅ Trở về Menu");
        btnSetAnswerKey = new JButton("1. Cài đặt đáp án");
        btnStopGrading = new JButton("🛑 Dừng chấm");
        btnStopGrading.setEnabled(false);
        btnStopGrading.setForeground(Color.RED);

        // [CẬP NHẬT]: Đã thêm BGD4.1 vào danh sách
        cbxTemplate = new JComboBox<>(new String[]{"BGD4", "BGD4.1", "BGD3", "QM", "TNMAKER"});
        cbxSortResults = new JComboBox<>(new String[]{
                "Sắp xếp: Theo STT (Mặc định)",
                "Sắp xếp: Điểm (Cao -> Thấp)",
                "Sắp xếp: Trạng thái (Lỗi lên đầu)"
        });

        btnChangeSelectedCode = new JButton("🎯 Đổi đề vùng chọn");
        btnBulkChangeCode = new JButton("🔄 Đổi đề hàng loạt");
        btnDeleteResult = new JButton("❌ Xóa bài chọn");
        btnDashboard = new JButton("📈 Thống kê lớp");
        btnExportScores = new JButton("📊 Xuất Bảng Điểm");
        btnExportConfig = new JButton("📝 Xuất Đáp Án");

        // Ráp Component vào Panel Trái
        addSectionLabel(panelLeft, "HỆ THỐNG MẠNG");
        addButton(panelLeft, btnConnectPhone);

        addSectionLabel(panelLeft, "QUẢN LÝ CHUNG");
        addButton(panelLeft, btnBackToMenu);
        addButton(panelLeft, btnSetAnswerKey);

        addSectionLabel(panelLeft, "MẪU PHIẾU CHẤM");
        addComboBox(panelLeft, cbxTemplate);

        lblTemplatePreview = new JLabel("Chưa có ảnh mẫu", SwingConstants.CENTER);
        lblTemplatePreview.setPreferredSize(new Dimension(200, 270)); // Tỷ lệ xấp xỉ giấy A4
        lblTemplatePreview.setMaximumSize(new Dimension(200, 270));
        lblTemplatePreview.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        lblTemplatePreview.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblTemplatePreview.setForeground(Color.GRAY);
        panelLeft.add(lblTemplatePreview);
        panelLeft.add(Box.createRigidArea(new Dimension(0, 8)));

        addSectionLabel(panelLeft, "CHẤM BÀI TRÊN PC");
        addButton(panelLeft, btnStartGrading);
        addButton(panelLeft, btnStopGrading);

        addSectionLabel(panelLeft, "ĐỔI MÃ ĐỀ");
        addButton(panelLeft, btnChangeSelectedCode);
        addButton(panelLeft, btnBulkChangeCode);

        addSectionLabel(panelLeft, "QUẢN LÝ BÀI THI");
        addButton(panelLeft, btnDeleteResult);
        addComboBox(panelLeft, cbxSortResults);

        addSectionLabel(panelLeft, "THỐNG KÊ & XUẤT");
        addButton(panelLeft, btnDashboard);
        addButton(panelLeft, btnExportScores);
        addButton(panelLeft, btnExportConfig);

        JScrollPane scrollLeft = new JScrollPane(panelLeft);
        scrollLeft.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollLeft.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollLeft.setBorder(BorderFactory.createTitledBorder("Thanh Công Cụ"));
        scrollLeft.setMinimumSize(new Dimension(240, 0));
        scrollLeft.setPreferredSize(new Dimension(260, 0));

        // --- PANEL PHẢI (BẢNG KẾT QUẢ) ---
        String[] columns = {"STT", "Số Báo Danh", "Mã Đề", "Điểm", "Trạng thái"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 3; // Chỉ cho phép sửa cột Điểm
            }
        };
        tblResults = new JTable(tableModel);
        tblResults.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        tblResults.setRowHeight(25);
        tblResults.setFont(new Font("Arial", Font.PLAIN, 14));

        JScrollPane scrollRight = new JScrollPane(tblResults);
        scrollRight.setBorder(BorderFactory.createTitledBorder("Danh sách kết quả chấm thi"));

        // Phân chia layout
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollLeft, scrollRight);
        splitPane.setDividerLocation(260);
        splitPane.setDividerSize(5);
        add(splitPane, BorderLayout.CENTER);

        // --- PANEL DƯỚI (TRẠNG THÁI) ---
        JPanel panelStatus = new JPanel(new FlowLayout(FlowLayout.LEFT));
        lblServerStatus = new JLabel("📡 Server: Offline");
        lblServerStatus.setForeground(Color.RED);
        lblStatus = new JLabel("Trạng thái: Sẵn sàng");

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(300, 20));
        progressBar.setVisible(false);

        panelStatus.add(lblServerStatus);
        panelStatus.add(Box.createHorizontalStrut(15));
        panelStatus.add(lblStatus);
        panelStatus.add(Box.createHorizontalStrut(20));
        panelStatus.add(progressBar);

        add(panelStatus, BorderLayout.SOUTH);
    }

    // --- CÁC HÀM TIỆN ÍCH (HELPER METHODS) ---
    private JButton createStyledButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFont(new Font("Arial", Font.BOLD, 13));
        return btn;
    }

    private void addSectionLabel(JPanel panel, String text) {
        panel.add(Box.createRigidArea(new Dimension(0, 12)));
        JLabel label = new JLabel(text);
        label.setFont(new Font("Arial", Font.BOLD, 12));
        label.setForeground(new Color(100, 100, 100));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createRigidArea(new Dimension(0, 5)));
    }

    private void addButton(JPanel panel, JButton btn) {
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setFocusPainted(false);
        panel.add(btn);
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
    }

    private void addComboBox(JPanel panel, JComboBox<String> cbx) {
        cbx.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        cbx.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(cbx);
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
    }

    // --- GETTERS & SETTERS ---
    public JButton getBtnConnectPhone() { return btnConnectPhone; }
    public JButton getBtnBackToMenu() { return btnBackToMenu; }
    public JButton getBtnSetAnswerKey() { return btnSetAnswerKey; }
    public JComboBox<String> getCbxTemplate() { return cbxTemplate; }
    public JButton getBtnStartGrading() { return btnStartGrading; }
    public JButton getBtnStopGrading() { return btnStopGrading; }
    public JTable getTblResults() { return tblResults; }
    public JButton getBtnDeleteResult() { return btnDeleteResult; }
    public JComboBox<String> getCbxSortResults() { return cbxSortResults; }
    public JButton getBtnExportScores() { return btnExportScores; }
    public JButton getBtnExportConfig() { return btnExportConfig; }
    public JButton getBtnDashboard() { return btnDashboard; }
    public JProgressBar getProgressBar() { return progressBar; }
    public JButton getBtnBulkChangeCode() { return btnBulkChangeCode; }
    public JButton getBtnChangeSelectedCode() { return btnChangeSelectedCode; }
    public JLabel getLblServerStatus() { return lblServerStatus; }
    public JLabel getLblTemplatePreview() { return lblTemplatePreview; }

    public void addResultRow(Object[] rowData) { tableModel.addRow(rowData); }
    public void setStatusMessage(String message) { lblStatus.setText("Trạng thái: " + message); }

    public void clearView() {
        tableModel.setRowCount(0);
        lblStatus.setText("Trạng thái: Vui lòng chọn đề thi để tiếp tục");
        progressBar.setVisible(false);
        progressBar.setValue(0);
        btnStopGrading.setEnabled(false);
        setTitle("Phần mềm Chấm Trắc Nghiệm");
    }
}