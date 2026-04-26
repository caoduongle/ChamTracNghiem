package view;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class MainView extends JFrame {

    private JButton btnBackToMenu;
    private JButton btnSetAnswerKey;
    private JButton btnStartGrading;
    private JLabel lblImagePreview;
    private JTable tblResults;
    private DefaultTableModel tableModel;
    private JLabel lblStatus;
    private JButton btnDeleteResult;
    private JComboBox<String> cbxSortResults;
    private JButton btnExportScores, btnExportConfig;
    private JButton btnDashboard;

    // --- TÍNH NĂNG MỚI: THANH TIẾN ĐỘ ---
    private JProgressBar progressBar;

    public MainView() {
        setTitle("Phần mềm Chấm Trắc Nghiệm - Team N7");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        initUI();
    }

    private void initUI() {
        JPanel panelControl = new JPanel(new FlowLayout(FlowLayout.LEFT));

        btnBackToMenu = new JButton("⬅ Trở về Menu");
        btnSetAnswerKey = new JButton("1. Cài đặt đáp án");
        btnStartGrading = new JButton("2. Bắt đầu chấm");

        btnDeleteResult = new JButton("❌ Xóa bài chọn");
        cbxSortResults = new JComboBox<>(new String[]{"Sắp xếp: Mặc định", "Sắp xếp: SBD", "Sắp xếp: Điểm (Cao-Thấp)"});

        btnDashboard = new JButton("📈 Thống kê lớp");
        btnExportScores = new JButton("📊 Xuất Bảng Điểm");
        btnExportConfig = new JButton("📝 Xuất Đáp Án");

        panelControl.add(btnBackToMenu);
        panelControl.add(btnSetAnswerKey);
        panelControl.add(btnStartGrading);
        panelControl.add(new JLabel("  |  "));
        panelControl.add(btnDeleteResult);
        panelControl.add(cbxSortResults);
        panelControl.add(btnDashboard);
        panelControl.add(btnExportScores);
        panelControl.add(btnExportConfig);

        add(panelControl, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(400);

        lblImagePreview = new JLabel("Hình ảnh bài thi sẽ hiển thị ở đây", SwingConstants.CENTER);
        lblImagePreview.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        splitPane.setLeftComponent(new JScrollPane(lblImagePreview));

        String[] columns = {"STT", "Số Báo Danh", "Mã Đề", "Điểm", "Trạng thái"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblResults = new JTable(tableModel);
        splitPane.setRightComponent(new JScrollPane(tblResults));

        add(splitPane, BorderLayout.CENTER);

        // --- KHU VỰC CHỨA TRẠNG THÁI VÀ THANH TIẾN ĐỘ ---
        JPanel panelStatus = new JPanel(new FlowLayout(FlowLayout.LEFT));
        lblStatus = new JLabel("Trạng thái: Sẵn sàng");

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true); // Hiện chữ % bên trong thanh
        progressBar.setPreferredSize(new Dimension(300, 20));
        progressBar.setVisible(false); // Ẩn đi khi chưa bắt đầu chấm

        panelStatus.add(lblStatus);
        panelStatus.add(Box.createHorizontalStrut(20)); // Tạo khoảng cách
        panelStatus.add(progressBar); // Gắn vào giao diện

        add(panelStatus, BorderLayout.SOUTH);
    }

    public JButton getBtnBackToMenu() { return btnBackToMenu; }
    public JButton getBtnSetAnswerKey() { return btnSetAnswerKey; }
    public JButton getBtnStartGrading() { return btnStartGrading; }
    public JTable getTblResults() { return tblResults; }
    public JButton getBtnDeleteResult() { return btnDeleteResult; }
    public JComboBox<String> getCbxSortResults() { return cbxSortResults; }
    public JButton getBtnExportScores() { return btnExportScores; }
    public JButton getBtnExportConfig() { return btnExportConfig; }
    public JButton getBtnDashboard() { return btnDashboard; }
    public JProgressBar getProgressBar() { return progressBar; } // Getter cho thanh tiến độ

    public void setImagePreview(Icon imageIcon) {
        lblImagePreview.setText("");
        lblImagePreview.setIcon(imageIcon);
    }

    public void addResultRow(Object[] rowData) {
        tableModel.addRow(rowData);
    }

    public void setStatusMessage(String message) {
        lblStatus.setText("Trạng thái: " + message);
    }

    public void clearView() {
        tableModel.setRowCount(0);
        lblImagePreview.setIcon(null);
        lblImagePreview.setText("Hình ảnh bài thi sẽ hiển thị ở đây");
        lblStatus.setText("Trạng thái: Vui lòng chọn đề thi để tiếp tục");
        progressBar.setVisible(false); // Ẩn đi khi reset
        progressBar.setValue(0);
        setTitle("Phần mềm Chấm Trắc Nghiệm - Team N7");
    }
}