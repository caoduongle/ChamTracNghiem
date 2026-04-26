package view;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import service.WindowPersistenceManager;

public class MainView extends JFrame {

    private JButton btnBackToMenu;
    private JButton btnSetAnswerKey;
    private JButton btnStartGrading;
    private JButton btnStopGrading;
    private JTable tblResults;
    private DefaultTableModel tableModel;
    private JLabel lblStatus;
    private JButton btnDeleteResult;
    private JComboBox<String> cbxSortResults;
    private JButton btnExportScores, btnExportConfig;
    private JButton btnDashboard;
    private JButton btnBulkChangeCode;
    private JButton btnChangeSelectedCode;

    private JProgressBar progressBar;

    public MainView() {
        setTitle("Phần mềm Chấm Trắc Nghiệm ");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        initUI();

        // [NEW]: Tự động khôi phục vị trí cũ, nếu chưa có thì để mặc định 1200x700
        WindowPersistenceManager.restoreWindow(this, "MainView", 1200, 700);

        // [NEW]: Tự động lưu khi tắt app
        WindowPersistenceManager.attachSaver(this, "MainView");
    }

    private void initUI() {
        // =================================================================
        // --- CỘT CHỨC NĂNG BÊN TRÁI (Thay thế phần xem trước ảnh cũ) ---
        // =================================================================
        JPanel panelLeft = new JPanel();
        panelLeft.setLayout(new BoxLayout(panelLeft, BoxLayout.Y_AXIS));
        panelLeft.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Khởi tạo các nút
        btnBackToMenu = new JButton("⬅ Trở về Menu");
        btnSetAnswerKey = new JButton("1. Cài đặt đáp án");

        btnStartGrading = new JButton("2. Bắt đầu chấm");
        btnStartGrading.setBackground(new Color(40, 167, 69));
        btnStartGrading.setForeground(Color.WHITE);

        btnStopGrading = new JButton("🛑 Dừng chấm");
        btnStopGrading.setEnabled(false);
        btnStopGrading.setForeground(Color.RED);

        btnChangeSelectedCode = new JButton("🎯 Đổi đề vùng chọn");
        btnBulkChangeCode = new JButton("🔄 Đổi đề hàng loạt");

        btnDeleteResult = new JButton("❌ Xóa bài chọn");
        cbxSortResults = new JComboBox<>(new String[]{
                "Sắp xếp: Theo STT (Mặc định)",
                "Sắp xếp: Điểm (Cao -> Thấp)",
                "Sắp xếp: Trạng thái (Lỗi lên đầu)"
        });

        btnDashboard = new JButton("📈 Thống kê lớp");
        btnExportScores = new JButton("📊 Xuất Bảng Điểm");
        btnExportConfig = new JButton("📝 Xuất Đáp Án");

        // Gom nhóm và gắn vào Sidebar bên trái
        addSectionLabel(panelLeft, "QUẢN LÝ CHUNG");
        addButton(panelLeft, btnBackToMenu);
        addButton(panelLeft, btnSetAnswerKey);

        addSectionLabel(panelLeft, "CHẤM BÀI");
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

        // Bọc cột bên trái bằng một thanh trượt JScrollPane
        JScrollPane scrollLeft = new JScrollPane(panelLeft);
        scrollLeft.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollLeft.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollLeft.setBorder(BorderFactory.createTitledBorder("Thanh Công Cụ"));

        // Cố định độ rộng Sidebar bên trái để không bị co giãn lung tung
        scrollLeft.setMinimumSize(new Dimension(240, 0));
        scrollLeft.setPreferredSize(new Dimension(260, 0));

        // =================================================================
        // --- BẢNG HIỂN THỊ DỮ LIỆU BÊN PHẢI ---
        // =================================================================
        String[] columns = {"STT", "Số Báo Danh", "Mã Đề", "Điểm", "Trạng thái"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 3; // Vẫn chỉ cho phép nháy đúp sửa cột Mã Đề
            }
        };
        tblResults = new JTable(tableModel);
        tblResults.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        tblResults.setRowHeight(25);
        tblResults.setFont(new Font("Arial", Font.PLAIN, 14));

        JScrollPane scrollRight = new JScrollPane(tblResults);
        scrollRight.setBorder(BorderFactory.createTitledBorder("Danh sách kết quả chấm thi"));

        // =================================================================
        // --- CHIA ĐÔI MÀN HÌNH ---
        // =================================================================
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(scrollLeft);
        splitPane.setRightComponent(scrollRight);
        splitPane.setDividerLocation(260); // Vị trí thanh chia
        splitPane.setDividerSize(5);

        add(splitPane, BorderLayout.CENTER);

        // =================================================================
        // --- THANH TRẠNG THÁI DƯỚI CÙNG ---
        // =================================================================
        JPanel panelStatus = new JPanel(new FlowLayout(FlowLayout.LEFT));
        lblStatus = new JLabel("Trạng thái: Sẵn sàng");

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(300, 20));
        progressBar.setVisible(false);

        panelStatus.add(lblStatus);
        panelStatus.add(Box.createHorizontalStrut(20));
        panelStatus.add(progressBar);

        add(panelStatus, BorderLayout.SOUTH);
    }

    // --- Các hàm tiện ích giúp gắn UI đẹp và đều đặn hơn ---
    private void addSectionLabel(JPanel panel, String text) {
        panel.add(Box.createRigidArea(new Dimension(0, 12))); // Khoảng cách trên
        JLabel label = new JLabel(text);
        label.setFont(new Font("Arial", Font.BOLD, 12));
        label.setForeground(new Color(100, 100, 100)); // Màu chữ xám chuyên nghiệp
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createRigidArea(new Dimension(0, 5))); // Khoảng cách dưới
    }

    private void addButton(JPanel panel, JButton btn) {
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setFocusPainted(false);
        panel.add(btn);
        panel.add(Box.createRigidArea(new Dimension(0, 8))); // Khoảng cách giữa các nút
    }

    private void addComboBox(JPanel panel, JComboBox<String> cbx) {
        cbx.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        cbx.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(cbx);
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
    }

    // --- Getters cho Controller ---
    public JButton getBtnBackToMenu() { return btnBackToMenu; }
    public JButton getBtnSetAnswerKey() { return btnSetAnswerKey; }
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

    // (Giữ lại hàm rỗng để các Controller cũ nếu có gọi lệnh setImagePreview sẽ không bị lỗi báo đỏ)
    public void setImagePreview(Icon imageIcon) {
        // Đã xóa UI nên hàm này được để trống
    }

    public void addResultRow(Object[] rowData) {
        tableModel.addRow(rowData);
    }

    public void setStatusMessage(String message) {
        lblStatus.setText("Trạng thái: " + message);
    }

    public void clearView() {
        tableModel.setRowCount(0);
        lblStatus.setText("Trạng thái: Vui lòng chọn đề thi để tiếp tục");
        progressBar.setVisible(false);
        progressBar.setValue(0);
        btnStopGrading.setEnabled(false);
        setTitle("Phần mềm Chấm Trắc Nghiệm - Team N7");
    }
}