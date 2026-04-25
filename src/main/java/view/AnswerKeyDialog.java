package view;

import model.ExamConfig;
import org.apache.poi.ss.usermodel.*;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class AnswerKeyDialog extends JDialog {
    private JTextField txtP1, txtP2, txtP3;
    private JTextField txtScoreP1, txtScoreP3, txtScoreP2_1, txtScoreP2_2, txtScoreP2_3, txtScoreP2_4;
    private JLabel lblTotalPossibleScore; // NHÃN HIỂN THỊ TỔNG ĐIỂM
    private JTable tblAnswers;
    private DefaultTableModel tableModel;
    private JButton btnSave, btnImport;

    public AnswerKeyDialog(Frame parent) {
        super(parent, "Thiết lập đáp án chuẩn - Team N7", true);
        setSize(750, 750);
        setLayout(new BorderLayout(10, 10));

        // --- PANEL TRÊN CÙNG ---
        JPanel pnlTopContainer = new JPanel(new GridLayout(2, 1, 5, 5));

        // 1. Nhập số câu
        JPanel pnlInput = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pnlInput.setBorder(BorderFactory.createTitledBorder("Cấu trúc đề thi"));
        pnlInput.add(new JLabel("Câu Phần I:")); txtP1 = new JTextField("40", 3); pnlInput.add(txtP1);
        pnlInput.add(new JLabel(" Câu Phần II:")); txtP2 = new JTextField("4", 3); pnlInput.add(txtP2);
        pnlInput.add(new JLabel(" Câu Phần III:")); txtP3 = new JTextField("6", 3); pnlInput.add(txtP3);

        JButton btnApply = new JButton("Cập nhật bảng");
        btnApply.addActionListener(e -> updateTable());
        pnlInput.add(btnApply);

        // 2. Nhập Barem điểm và hiển thị Tổng điểm
        JPanel pnlScores = new JPanel(new BorderLayout());
        pnlScores.setBorder(BorderFactory.createTitledBorder("Barem điểm & Kiểm tra tổng điểm"));

        JPanel pnlBaremInputs = new JPanel(new GridLayout(2, 6, 5, 2));
        pnlBaremInputs.add(new JLabel(" P.I/Câu:")); txtScoreP1 = new JTextField("0.25"); pnlBaremInputs.add(txtScoreP1);
        pnlBaremInputs.add(new JLabel(" P.II (1 ý):")); txtScoreP2_1 = new JTextField("0.1"); pnlBaremInputs.add(txtScoreP2_1);
        pnlBaremInputs.add(new JLabel(" P.II (2 ý):")); txtScoreP2_2 = new JTextField("0.25"); pnlBaremInputs.add(txtScoreP2_2);
        pnlBaremInputs.add(new JLabel(" P.II (3 ý):")); txtScoreP2_3 = new JTextField("0.5"); pnlBaremInputs.add(txtScoreP2_3);
        pnlBaremInputs.add(new JLabel(" P.II (4 ý):")); txtScoreP2_4 = new JTextField("1.0"); pnlBaremInputs.add(txtScoreP2_4);
        pnlBaremInputs.add(new JLabel(" P.III/Câu:")); txtScoreP3 = new JTextField("0.25"); pnlBaremInputs.add(txtScoreP3);

        // Vùng hiển thị tổng điểm nổi bật
        lblTotalPossibleScore = new JLabel("TỔNG ĐIỂM: 10.0", SwingConstants.CENTER);
        lblTotalPossibleScore.setFont(new Font("Arial", Font.BOLD, 18));
        lblTotalPossibleScore.setForeground(Color.BLUE);
        lblTotalPossibleScore.setPreferredSize(new Dimension(200, 50));

        pnlScores.add(pnlBaremInputs, BorderLayout.CENTER);
        pnlScores.add(lblTotalPossibleScore, BorderLayout.EAST);

        pnlTopContainer.add(pnlInput);
        pnlTopContainer.add(pnlScores);
        add(pnlTopContainer, BorderLayout.NORTH);

        // 3. Bảng nhập đáp án
        tableModel = new DefaultTableModel(new String[]{"STT", "Phần", "Đáp án đúng"}, 0);
        tblAnswers = new JTable(tableModel);
        add(new JScrollPane(tblAnswers), BorderLayout.CENTER);

        // 4. Nút chức năng
        JPanel pnlBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnImport = new JButton("Import Excel");
        btnImport.addActionListener(e -> importFromExcel());
        btnSave = new JButton("Lưu cấu hình & Đóng");
        pnlBottom.add(btnImport);
        pnlBottom.add(btnSave);
        add(pnlBottom, BorderLayout.SOUTH);

        // GẮN SỰ KIỆN TỰ ĐỘNG TÍNH TOÁN
        addAutoCalculateListeners();

        setLocationRelativeTo(parent);
    }

    /**
     * Gắn bộ lắng nghe thay đổi vào tất cả các ô nhập liệu
     */
    private void addAutoCalculateListeners() {
        DocumentListener dl = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { calculateTotalPossibleScore(); }
            public void removeUpdate(DocumentEvent e) { calculateTotalPossibleScore(); }
            public void changedUpdate(DocumentEvent e) { calculateTotalPossibleScore(); }
        };

        // Lắng nghe số lượng câu
        txtP1.getDocument().addDocumentListener(dl);
        txtP2.getDocument().addDocumentListener(dl);
        txtP3.getDocument().addDocumentListener(dl);

        // Lắng nghe barem điểm
        txtScoreP1.getDocument().addDocumentListener(dl);
        txtScoreP3.getDocument().addDocumentListener(dl);
        txtScoreP2_4.getDocument().addDocumentListener(dl); // Chỉ cần lấy điểm tối đa của P2 (đúng 4 ý) để tính tổng
    }

    /**
     * Hàm tính toán tổng điểm tối đa theo thời gian thực
     */
    private void calculateTotalPossibleScore() {
        try {
            int n1 = Integer.parseInt(txtP1.getText().trim());
            int n2 = Integer.parseInt(txtP2.getText().trim());
            int n3 = Integer.parseInt(txtP3.getText().trim());

            double s1 = Double.parseDouble(txtScoreP1.getText().trim());
            double s2Max = Double.parseDouble(txtScoreP2_4.getText().trim());
            double s3 = Double.parseDouble(txtScoreP3.getText().trim());

            double total = (n1 * s1) + (n2 * s2Max) + (n3 * s3);

            // Làm tròn 2 chữ số
            total = Math.round(total * 100.0) / 100.0;

            lblTotalPossibleScore.setText("TỔNG ĐIỂM: " + total);

            // Cảnh báo màu sắc
            if (total > 10.001) {
                lblTotalPossibleScore.setForeground(Color.RED); // Lố điểm
            } else if (Math.abs(total - 10.0) < 0.001) {
                lblTotalPossibleScore.setForeground(new Color(0, 150, 0)); // Vừa khít 10 (Màu xanh lá)
            } else {
                lblTotalPossibleScore.setForeground(Color.BLUE); // Chưa đủ 10
            }

        } catch (Exception e) {
            lblTotalPossibleScore.setText("LỖI NHẬP LIỆU");
            lblTotalPossibleScore.setForeground(Color.GRAY);
        }
    }

    private void updateTable() {
        try {
            List<String> oldP1 = new ArrayList<>();
            List<String> oldP2 = new ArrayList<>();
            List<String> oldP3 = new ArrayList<>();

            for (int i = 0; i < tableModel.getRowCount(); i++) {
                String phan = tableModel.getValueAt(i, 1).toString();
                Object val = tableModel.getValueAt(i, 2);
                String ans = (val == null) ? "" : val.toString();
                if (phan.equals("I")) oldP1.add(ans);
                else if (phan.equals("II")) oldP2.add(ans);
                else if (phan.equals("III")) oldP3.add(ans);
            }

            int p1 = Integer.parseInt(txtP1.getText().trim());
            int p2 = Integer.parseInt(txtP2.getText().trim());
            int p3 = Integer.parseInt(txtP3.getText().trim());

            tableModel.setRowCount(0);
            for (int i = 1; i <= p1; i++) {
                String ans = (i <= oldP1.size()) ? oldP1.get(i - 1) : "";
                tableModel.addRow(new Object[]{i, "I", ans});
            }
            for (int i = 1; i <= p2; i++) {
                String ans = (i <= oldP2.size()) ? oldP2.get(i - 1) : "";
                tableModel.addRow(new Object[]{i + p1, "II", ans});
            }
            for (int i = 1; i <= p3; i++) {
                String ans = (i <= oldP3.size()) ? oldP3.get(i - 1) : "";
                tableModel.addRow(new Object[]{i + p1 + p2, "III", ans});
            }

            calculateTotalPossibleScore(); // Tính lại điểm sau khi update table

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Lỗi: Vui lòng nhập số câu hợp lệ!");
        }
    }

    public ExamConfig getExamConfig() {
        int actualP1 = 0, actualP2 = 0, actualP3 = 0;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String p = tableModel.getValueAt(i, 1).toString();
            if (p.equals("I")) actualP1++;
            else if (p.equals("II")) actualP2++;
            else if (p.equals("III")) actualP3++;
        }

        ExamConfig newConfig = new ExamConfig(actualP1, actualP2, actualP3);

        try {
            newConfig.setScoreP1(Double.parseDouble(txtScoreP1.getText().trim()));
            newConfig.setScoreP3(Double.parseDouble(txtScoreP3.getText().trim()));
            newConfig.setScoreP2_1(Double.parseDouble(txtScoreP2_1.getText().trim()));
            newConfig.setScoreP2_2(Double.parseDouble(txtScoreP2_2.getText().trim()));
            newConfig.setScoreP2_3(Double.parseDouble(txtScoreP2_3.getText().trim()));
            newConfig.setScoreP2_4(Double.parseDouble(txtScoreP2_4.getText().trim()));
        } catch (Exception e) {}

        int countP1 = 1, countP2 = 1, countP3 = 1;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String phan = tableModel.getValueAt(i, 1).toString();
            Object val = tableModel.getValueAt(i, 2);
            String ans = (val == null) ? "" : val.toString().trim().toUpperCase();

            if (phan.equals("I")) {
                newConfig.setAnswer("P1_Câu_" + countP1++, ans);
            } else if (phan.equals("II")) {
                if (ans.length() == 4) {
                    String n = ans.replace("D", "Đ").replace("T", "Đ").replace("F", "S");
                    newConfig.setAnswer("P2_Câu_" + countP2 + "_a", String.valueOf(n.charAt(0)));
                    newConfig.setAnswer("P2_Câu_" + countP2 + "_b", String.valueOf(n.charAt(1)));
                    newConfig.setAnswer("P2_Câu_" + countP2 + "_c", String.valueOf(n.charAt(2)));
                    newConfig.setAnswer("P2_Câu_" + countP2 + "_d", String.valueOf(n.charAt(3)));
                }
                countP2++;
            } else if (phan.equals("III")) {
                newConfig.setAnswer("P3_Câu_" + countP3++, ans);
            }
        }
        return newConfig;
    }

    public void loadConfig(ExamConfig config) {
        if (config == null) return;

        // 1. Cập nhật lại các ô nhập số lượng và điểm
        txtP1.setText(String.valueOf(config.getNumPart1()));
        txtP2.setText(String.valueOf(config.getNumPart2()));
        txtP3.setText(String.valueOf(config.getNumPart3()));

        txtScoreP1.setText(String.valueOf(config.getScoreP1()));
        txtScoreP3.setText(String.valueOf(config.getScoreP3()));
        txtScoreP2_1.setText(String.valueOf(config.getScoreP2_1()));
        txtScoreP2_2.setText(String.valueOf(config.getScoreP2_2()));
        txtScoreP2_3.setText(String.valueOf(config.getScoreP2_3()));
        txtScoreP2_4.setText(String.valueOf(config.getScoreP2_4()));

        // 2. KHÔNG GỌI updateTable() NỮA MÀ ĐỌC THẲNG TỪ CONFIG ĐỂ ĐỔ VÀO BẢNG
        int p1 = config.getNumPart1();
        int p2 = config.getNumPart2();
        int p3 = config.getNumPart3();

        tableModel.setRowCount(0); // Xóa sạch bảng

        // Đổ đáp án Phần 1 từ config
        for (int i = 1; i <= p1; i++) {
            String ans = config.getAnswer("P1_Câu_" + i);
            tableModel.addRow(new Object[]{i, "I", ans != null ? ans : ""});
        }

        // Đổ đáp án Phần 2 (Gộp a, b, c, d) từ config
        for (int i = 1; i <= p2; i++) {
            String a = config.getAnswer("P2_Câu_" + i + "_a");
            String b = config.getAnswer("P2_Câu_" + i + "_b");
            String c = config.getAnswer("P2_Câu_" + i + "_c");
            String d = config.getAnswer("P2_Câu_" + i + "_d");
            String combined = (a != null ? a : "") + (b != null ? b : "") + (c != null ? c : "") + (d != null ? d : "");
            tableModel.addRow(new Object[]{i + p1, "II", combined});
        }

        // Đổ đáp án Phần 3 từ config
        for (int i = 1; i <= p3; i++) {
            String ans = config.getAnswer("P3_Câu_" + i);
            tableModel.addRow(new Object[]{i + p1 + p2, "III", ans != null ? ans : ""});
        }

        // 3. Cập nhật lại con số Tổng điểm màu xanh lá ở góc phải
        calculateTotalPossibleScore();
    }

    private void importFromExcel() {
        DataFormatter formatter = new DataFormatter();
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (FileInputStream fis = new FileInputStream(fileChooser.getSelectedFile());
                 Workbook workbook = WorkbookFactory.create(fis)) {
                Sheet sheet = workbook.getSheetAt(0);
                tableModel.setRowCount(0);
                for (Row row : sheet) {
                    if (row.getRowNum() == 0 || row.getCell(1) == null) continue;
                    String phan = row.getCell(1).getStringCellValue().trim().toUpperCase();
                    String ans = formatter.formatCellValue(row.getCell(2)).trim().toUpperCase();
                    tableModel.addRow(new Object[]{(int)row.getCell(0).getNumericCellValue(), phan, ans});
                }
                calculateTotalPossibleScore();
                JOptionPane.showMessageDialog(this, "Import xong!");
            } catch (Exception e) { JOptionPane.showMessageDialog(this, "Lỗi: " + e.getMessage()); }
        }
    }

    public JButton getBtnSave() { return btnSave; }
}