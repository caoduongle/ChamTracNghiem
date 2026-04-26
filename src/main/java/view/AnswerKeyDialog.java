package view;

import model.ExamConfig;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

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
import java.util.prefs.Preferences; // THÊM THƯ VIỆN NHỚ VỊ TRÍ

// THÊM THƯ VIỆN KÉO THẢ
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DnDConstants;
import java.awt.datatransfer.DataFlavor;

public class AnswerKeyDialog extends JDialog {
    private JTextField txtP1, txtP2, txtP3;
    private JTextField txtScoreP1, txtScoreP3, txtScoreP2_1, txtScoreP2_2, txtScoreP2_3, txtScoreP2_4;
    private JLabel lblTotalPossibleScore;
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
        btnImport = new JButton("Import Excel (Hoặc Kéo Thả)");
        btnImport.addActionListener(e -> importFromExcel());
        btnSave = new JButton("Lưu cấu hình & Đóng");
        pnlBottom.add(btnImport);
        pnlBottom.add(btnSave);
        add(pnlBottom, BorderLayout.SOUTH);

        addAutoCalculateListeners();
        enableDragAndDrop();

        setLocationRelativeTo(parent);
    }

    private void addAutoCalculateListeners() {
        DocumentListener dl = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { calculateTotalPossibleScore(); }
            public void removeUpdate(DocumentEvent e) { calculateTotalPossibleScore(); }
            public void changedUpdate(DocumentEvent e) { calculateTotalPossibleScore(); }
        };

        txtP1.getDocument().addDocumentListener(dl);
        txtP2.getDocument().addDocumentListener(dl);
        txtP3.getDocument().addDocumentListener(dl);

        txtScoreP1.getDocument().addDocumentListener(dl);
        txtScoreP3.getDocument().addDocumentListener(dl);
        txtScoreP2_4.getDocument().addDocumentListener(dl);
    }

    private void calculateTotalPossibleScore() {
        try {
            int n1 = Integer.parseInt(txtP1.getText().trim());
            int n2 = Integer.parseInt(txtP2.getText().trim());
            int n3 = Integer.parseInt(txtP3.getText().trim());

            double s1 = Double.parseDouble(txtScoreP1.getText().trim());
            double s2Max = Double.parseDouble(txtScoreP2_4.getText().trim());
            double s3 = Double.parseDouble(txtScoreP3.getText().trim());

            double total = (n1 * s1) + (n2 * s2Max) + (n3 * s3);
            total = Math.round(total * 100.0) / 100.0;

            lblTotalPossibleScore.setText("TỔNG ĐIỂM: " + total);

            if (total > 10.001) {
                lblTotalPossibleScore.setForeground(Color.RED);
            } else if (Math.abs(total - 10.0) < 0.001) {
                lblTotalPossibleScore.setForeground(new Color(0, 150, 0));
            } else {
                lblTotalPossibleScore.setForeground(Color.BLUE);
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

            calculateTotalPossibleScore();

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

        txtP1.setText(String.valueOf(config.getNumPart1()));
        txtP2.setText(String.valueOf(config.getNumPart2()));
        txtP3.setText(String.valueOf(config.getNumPart3()));

        txtScoreP1.setText(String.valueOf(config.getScoreP1()));
        txtScoreP3.setText(String.valueOf(config.getScoreP3()));
        txtScoreP2_1.setText(String.valueOf(config.getScoreP2_1()));
        txtScoreP2_2.setText(String.valueOf(config.getScoreP2_2()));
        txtScoreP2_3.setText(String.valueOf(config.getScoreP2_3()));
        txtScoreP2_4.setText(String.valueOf(config.getScoreP2_4()));

        int p1 = config.getNumPart1();
        int p2 = config.getNumPart2();
        int p3 = config.getNumPart3();

        tableModel.setRowCount(0);

        for (int i = 1; i <= p1; i++) {
            String ans = config.getAnswer("P1_Câu_" + i);
            tableModel.addRow(new Object[]{i, "I", ans != null ? ans : ""});
        }

        for (int i = 1; i <= p2; i++) {
            String a = config.getAnswer("P2_Câu_" + i + "_a");
            String b = config.getAnswer("P2_Câu_" + i + "_b");
            String c = config.getAnswer("P2_Câu_" + i + "_c");
            String d = config.getAnswer("P2_Câu_" + i + "_d");
            String combined = (a != null ? a : "") + (b != null ? b : "") + (c != null ? c : "") + (d != null ? d : "");
            tableModel.addRow(new Object[]{i + p1, "II", combined});
        }

        for (int i = 1; i <= p3; i++) {
            String ans = config.getAnswer("P3_Câu_" + i);
            tableModel.addRow(new Object[]{i + p1 + p2, "III", ans != null ? ans : ""});
        }

        calculateTotalPossibleScore();
    }

    private void processExcelFile(File file) {
        try (FileInputStream fis = new FileInputStream(file);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            tableModel.setRowCount(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell qCell = row.getCell(0);
                Cell phanCell = row.getCell(1);
                Cell aCell = row.getCell(2);

                if (qCell != null && phanCell != null && aCell != null) {
                    String qStr = qCell.toString().replace(".0", "").trim();
                    String phan = phanCell.toString().trim().toUpperCase();
                    String ans = aCell.toString().trim().toUpperCase();

                    if (!qStr.isEmpty() && !phan.isEmpty() && !ans.isEmpty()) {
                        try {
                            int stt = (int) Double.parseDouble(qStr);
                            tableModel.addRow(new Object[]{stt, phan, ans});
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            calculateTotalPossibleScore();
            JOptionPane.showMessageDialog(this, "Đã tự động điền đáp án từ file " + file.getName() + "!");
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Lỗi khi đọc file Excel: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ==========================================================
    // ĐÃ FIX: LƯU VÀ GỌI LẠI ĐƯỜNG DẪN THƯ MỤC KHI NHẬP EXCEL ĐÁP ÁN
    // ==========================================================
    private void importFromExcel() {
        JFileChooser fileChooser = new JFileChooser();

        // 1. Khởi tạo bộ nhớ (Preferences)
        Preferences prefs = Preferences.userRoot().node("ChamTracNghiem_N7");

        // 2. Lấy vị trí đã lưu riêng cho mục Đáp Án (Mặc định mở thư mục Home)
        String lastDir = prefs.get("DIR_ANSWER_KEY", System.getProperty("user.home"));
        fileChooser.setCurrentDirectory(new File(lastDir));

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            // 3. Ghi nhớ lại vị trí thư mục vừa chọn vào Registry
            prefs.put("DIR_ANSWER_KEY", fileChooser.getSelectedFile().getParent());

            // Xử lý file
            processExcelFile(fileChooser.getSelectedFile());
        }
    }

    private void enableDragAndDrop() {
        this.setDropTarget(new DropTarget() {
            public synchronized void drop(DropTargetDropEvent evt) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> droppedFiles = (List<File>) evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);

                    if (!droppedFiles.isEmpty()) {
                        File file = droppedFiles.get(0);
                        String low = file.getName().toLowerCase();

                        if (low.endsWith(".xlsx") || low.endsWith(".xls")) {
                            processExcelFile(file);
                        } else {
                            JOptionPane.showMessageDialog(AnswerKeyDialog.this,
                                    "Vui lòng kéo thả đúng file Excel (.xlsx hoặc .xls)!",
                                    "Sai định dạng", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(AnswerKeyDialog.this,
                            "Lỗi khi xử lý file: " + ex.getMessage(),
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    public JButton getBtnSave() { return btnSave; }
}