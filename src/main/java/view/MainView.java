package view;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class MainView extends JFrame {

    private JButton btnBackToMenu;
    private JButton btnSelectFolder;
    private JButton btnSetAnswerKey;
    private JButton btnStartGrading;
    private JLabel lblImagePreview;
    private JTable tblResults;
    private DefaultTableModel tableModel;
    private JLabel lblStatus;
    private JButton btnDeleteResult;
    private JComboBox<String> cbxSortResults;

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
        btnSelectFolder = new JButton("1. Chọn thư mục bài thi");
        btnSetAnswerKey = new JButton("2. Cài đặt đáp án");
        btnStartGrading = new JButton("3. Bắt đầu chấm");

        btnDeleteResult = new JButton("❌ Xóa bài chọn");
        cbxSortResults = new JComboBox<>(new String[]{"Sắp xếp: Mặc định", "Sắp xếp: SBD", "Sắp xếp: Điểm (Cao-Thấp)"});

        panelControl.add(btnBackToMenu);
        panelControl.add(btnSelectFolder);
        panelControl.add(btnSetAnswerKey);
        panelControl.add(btnStartGrading);
        panelControl.add(new JLabel(" | ")); // Vạch ngăn cách
        panelControl.add(btnDeleteResult);
        panelControl.add(cbxSortResults);

        add(panelControl, BorderLayout.NORTH);
        panelControl.add(btnBackToMenu);
        panelControl.add(btnSelectFolder);
        panelControl.add(btnSetAnswerKey);
        panelControl.add(btnStartGrading);
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

        JPanel panelStatus = new JPanel(new FlowLayout(FlowLayout.LEFT));
        lblStatus = new JLabel("Trạng thái: Sẵn sàng");
        panelStatus.add(lblStatus);
        add(panelStatus, BorderLayout.SOUTH);
    }

    public JButton getBtnBackToMenu() { return btnBackToMenu; }
    public JButton getBtnSelectFolder() { return btnSelectFolder; }
    public JButton getBtnSetAnswerKey() { return btnSetAnswerKey; }
    public JButton getBtnStartGrading() { return btnStartGrading; }
    public JTable getTblResults() { return tblResults; }

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
        setTitle("Phần mềm Chấm Trắc Nghiệm - Team N7");
    }
    public JButton getBtnDeleteResult() { return btnDeleteResult; }
    public JComboBox<String> getCbxSortResults() { return cbxSortResults; }
}