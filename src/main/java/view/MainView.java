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
                "<html><span style='font-family: \"Segoe UI Emoji\"'>📱</span> Hướng dẫn kết nối App</html>",
                new Color(240, 240, 240), // Nền xám nhạt
                Color.BLACK               // Chữ đen
        );
        btnStartGrading = createStyledButton("2. Bắt đầu chấm", new Color(40, 167, 69), Color.WHITE);

        btnBackToMenu = new JButton("⬅ Trở về Menu");
        btnSetAnswerKey = new JButton("1. Cài đặt đáp án");
        btnStopGrading = new JButton("🛑 Dừng chấm");
        btnStopGrading.setEnabled(false);
        btnStopGrading.setForeground(Color.RED);

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
        addButtonWithHelp(panelLeft, btnConnectPhone, "Hướng dẫn kết nối App", "<b>Chức năng:</b> Hướng dẫn kết nối điện thoại làm máy quét.<br><br><b>Chi tiết:</b> Bảng hiển thị mã QR và liên kết tải file APK để cài app lên Android. Đảm bảo 2 máy dùng chung 1 mạng Wi-Fi LAN để việc nhận ảnh tự động diễn ra mượt mà nhất.");

        addSectionLabel(panelLeft, "QUẢN LÝ CHUNG");
        addButtonWithHelp(panelLeft, btnBackToMenu, "Trở về Menu", "<b>Chức năng:</b> Đóng đề thi hiện tại để quay về giao diện Quản lý Đề thi.<br><br><b>Cách dùng:</b> Click vào đây khi muốn chuyển sang ca chấm đề thi khác hoặc chọn lớp khác.");
        addButtonWithHelp(panelLeft, btnSetAnswerKey, "Cài đặt đáp án", "<b>Chức năng:</b> Cài đặt đáp án đúng cho từng câu hỏi của các mã đề thi.<br><br><b>Tiện ích:</b> Bạn có thể chọn nhập trực tiếp đáp án trên bảng, copy và paste đáp án từ file Excel, hoặc đặc biệt là dùng app điện thoại quét trực tiếp phiếu đáp án mẫu của giáo viên để tự động điền nhanh.");

        addSectionLabel(panelLeft, "MẪU PHIẾU CHẤM");
        addComboBoxWithHelp(panelLeft, cbxTemplate, "Chọn mẫu phiếu chấm", "<b>Chức năng:</b> Chọn mẫu phiếu thi tương ứng với phiếu học sinh làm bài.<br><br><b>Lưu ý:</b> Hệ thống hỗ trợ nhiều mẫu phiếu thông dụng như BGD4 (mẫu Bộ GD mới), BGD4.1, BGD3, QM, TNMAKER. Chọn đúng mẫu phiếu để thuật toán AI nhận diện tọa độ chấm chính xác 100%.");

        lblTemplatePreview = new JLabel("Chưa có ảnh mẫu", SwingConstants.CENTER);
        lblTemplatePreview.setPreferredSize(new Dimension(200, 270)); // Tỷ lệ xấp xỉ giấy A4
        lblTemplatePreview.setMaximumSize(new Dimension(200, 270));
        lblTemplatePreview.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        lblTemplatePreview.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblTemplatePreview.setForeground(Color.GRAY);
        panelLeft.add(lblTemplatePreview);
        panelLeft.add(Box.createRigidArea(new Dimension(0, 8)));

        addSectionLabel(panelLeft, "CHẤM BÀI TRÊN PC");
        addButtonWithHelp(panelLeft, btnStartGrading, "Bắt đầu chấm bài", "<b>Chức năng:</b> Kích hoạt chế độ lắng nghe và nhận ảnh bài làm tự động từ điện thoại.<br><br><b>Cách dùng:</b> Sau khi kết nối App và bấm nút này, bạn chỉ cần dùng điện thoại quét bài thi, kết quả chấm (điểm số, trạng thái bài lỗi) sẽ lập tức truyền tải và hiển thị trên bảng kết quả máy tính theo thời gian thực.");
        addButtonWithHelp(panelLeft, btnStopGrading, "Dừng chấm bài", "<b>Chức năng:</b> Tạm dừng quá trình nhận bài thi tự động từ điện thoại.<br><br><b>Cách dùng:</b> Bấm khi hoàn thành xong ca chấm thi hoặc muốn cấu hình lại hệ thống mà không muốn bị gián đoạn.");

        addSectionLabel(panelLeft, "ĐỔI MÃ ĐỀ");
        addButtonWithHelp(panelLeft, btnChangeSelectedCode, "Đổi đề vùng chọn", "<b>Chức năng:</b> Đổi mã đề thi cho học sinh đang chọn trong bảng.<br><br><b>Cách dùng:</b> Trường hợp học sinh tô sai hoặc thiếu mã đề, bạn chọn dòng bài thi trong bảng kết quả, nhấp nút này và nhập mã đề đúng để hệ thống tự động tính toán lại điểm số ngay lập tức.");
        addButtonWithHelp(panelLeft, btnBulkChangeCode, "Đổi đề hàng loạt", "<b>Chức năng:</b> Thay đổi mã đề đồng loạt cho danh sách kết quả chấm.<br><br><b>Ứng dụng:</b> Dành cho việc cấu hình lại hoặc sửa lỗi mã đề đồng loạt theo số thứ tự hoặc số báo danh.");

        addSectionLabel(panelLeft, "QUẢN LÝ BÀI THI");
        addButtonWithHelp(panelLeft, btnDeleteResult, "Xóa kết quả bài chọn", "<b>Chức năng:</b> Xóa bài thi đã chấm khỏi danh sách đề thi hiện tại.<br><br><b>Ứng dụng:</b> Dùng khi muốn chấm lại bài thi của học sinh bị chụp mờ, chụp sai hoặc muốn loại bỏ bài thi lỗi khỏi danh sách tính điểm.");
        addComboBoxWithHelp(panelLeft, cbxSortResults, "Sắp xếp kết quả", "<b>Chức năng:</b> Sắp xếp danh sách học sinh theo các bộ lọc khác nhau.<br><br><b>Các tùy chọn:</b> Sắp xếp theo STT danh sách lớp, sắp xếp theo Điểm từ cao đến thấp để xếp loại học lực, hoặc đưa các bài bị lỗi (mờ ảnh, tô đúp...) lên đầu để dễ kiểm tra lại.");

        addSectionLabel(panelLeft, "THỐNG KÊ & XUẤT");
        addButtonWithHelp(panelLeft, btnDashboard, "Thống kê lớp", "<b>Chức năng:</b> Biểu đồ thống kê chi tiết kết quả đề thi.<br><br><b>Báo cáo:</b> Cung cấp biểu đồ phổ điểm trực quan, thống kê số lượng bài đạt Giỏi, Khá, Trung bình, Yếu, và phân tích chi tiết mức độ câu hỏi (câu nào nhiều học sinh làm đúng/sai nhất).");
        addButtonWithHelp(panelLeft, btnExportScores, "Xuất Bảng Điểm Excel", "<b>Chức năng:</b> Xuất toàn bộ kết quả thi của lớp ra file Excel chi tiết.<br><br><b>Nội dung:</b> File Excel xuất ra chứa đầy đủ thông tin: STT, Số báo danh, Họ tên, Mã đề, Điểm số cụ thể và chi tiết đáp án từng câu của từng học sinh để nhập vào hệ thống quản lý trường học.");
        addButtonWithHelp(panelLeft, btnExportConfig, "Xuất Đáp Án", "<b>Chức năng:</b> Xuất file cấu hình đáp án của đề thi hiện tại.<br><br><b>Cách dùng:</b> Xuất ra file để lưu trữ backup hoặc chia sẻ sang máy tính khác của các giáo viên chấm cùng khối.");

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

    private void addButtonWithHelp(JPanel panel, JButton btn, String helpTitle, String helpHtml) {
        JPanel wrapper = new JPanel(new BorderLayout(5, 0));
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        wrapper.setAlignmentX(Component.CENTER_ALIGNMENT);
        wrapper.setOpaque(false);

        btn.setFocusPainted(false);
        wrapper.add(btn, BorderLayout.CENTER);

        JButton btnHelp = new JButton("?");
        btnHelp.setPreferredSize(new Dimension(28, 35));
        btnHelp.setMargin(new java.awt.Insets(0, 0, 0, 0));
        btnHelp.setFont(new Font("Arial", Font.BOLD, 12));
        btnHelp.setBackground(new Color(225, 225, 225));
        btnHelp.setFocusPainted(false);
        btnHelp.setToolTipText("Nhấp để xem hướng dẫn");
        btnHelp.addActionListener(e -> {
            HelpDialog.showHelp(btnHelp, helpTitle, helpHtml);
        });

        wrapper.add(btnHelp, BorderLayout.EAST);
        panel.add(wrapper);
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
    }

    private void addComboBox(JPanel panel, JComboBox<String> cbx) {
        cbx.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        cbx.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(cbx);
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
    }

    private void addComboBoxWithHelp(JPanel panel, JComboBox<String> cbx, String helpTitle, String helpHtml) {
        JPanel wrapper = new JPanel(new BorderLayout(5, 0));
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        wrapper.setAlignmentX(Component.CENTER_ALIGNMENT);
        wrapper.setOpaque(false);

        wrapper.add(cbx, BorderLayout.CENTER);

        JButton btnHelp = new JButton("?");
        btnHelp.setPreferredSize(new Dimension(28, 35));
        btnHelp.setMargin(new java.awt.Insets(0, 0, 0, 0));
        btnHelp.setFont(new Font("Arial", Font.BOLD, 12));
        btnHelp.setBackground(new Color(225, 225, 225));
        btnHelp.setFocusPainted(false);
        btnHelp.setToolTipText("Nhấp để xem hướng dẫn");
        btnHelp.addActionListener(e -> {
            HelpDialog.showHelp(btnHelp, helpTitle, helpHtml);
        });

        wrapper.add(btnHelp, BorderLayout.EAST);
        panel.add(wrapper);
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