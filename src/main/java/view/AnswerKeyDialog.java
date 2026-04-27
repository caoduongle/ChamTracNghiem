package view;

import util.TableUtils;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

// THƯ VIỆN KÉO THẢ
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

    // --- CÁC BIẾN QUẢN LÝ ĐA MÃ ĐỀ ---
    private JComboBox<String> cbxExamCodes;
    private DefaultComboBoxModel<String> cbxModel;
    private Map<String, Map<String, String>> uiCache = new HashMap<>();
    private String currentCode = "Mặc định";
    private boolean isUpdatingCombo = false;

    public AnswerKeyDialog(Frame parent) {
        super(parent, "Thiết lập đáp án chuẩn (Đa Mã Đề) - Team N7", true);
        setSize(800, 750);
        setLayout(new BorderLayout(10, 10));

        uiCache.put(currentCode, new HashMap<>());

        // --- PANEL TRÊN CÙNG ---
        JPanel pnlTopContainer = new JPanel(new GridLayout(3, 1, 5, 5));

        // 0. Quản lý Mã Đề
        JPanel pnlCodes = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pnlCodes.setBorder(BorderFactory.createTitledBorder("Quản lý Mã Đề"));
        pnlCodes.add(new JLabel("Đang sửa đáp án cho mã đề: "));

        cbxModel = new DefaultComboBoxModel<>();
        cbxModel.addElement(currentCode);
        cbxExamCodes = new JComboBox<>(cbxModel);
        cbxExamCodes.setPreferredSize(new Dimension(150, 25));

        JButton btnAddCode = new JButton("➕ Thêm Mã");
        JButton btnDelCode = new JButton("❌ Xóa Mã");
        pnlCodes.add(cbxExamCodes);
        pnlCodes.add(btnAddCode);
        pnlCodes.add(btnDelCode);

        // 1. Nhập số câu
        JPanel pnlInput = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pnlInput.setBorder(BorderFactory.createTitledBorder("Cấu trúc đề thi"));
        pnlInput.add(new JLabel("Câu Phần I:")); txtP1 = new JTextField("40", 3); pnlInput.add(txtP1);
        pnlInput.add(new JLabel(" Câu Phần II:")); txtP2 = new JTextField("4", 3); pnlInput.add(txtP2);
        pnlInput.add(new JLabel(" Câu Phần III:")); txtP3 = new JTextField("6", 3); pnlInput.add(txtP3);

        JButton btnApply = new JButton("Cập nhật số câu");
        btnApply.addActionListener(e -> {
            saveUIToCache();
            updateTable();
            loadUIFromCache();
        });
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

        pnlTopContainer.add(pnlCodes);
        pnlTopContainer.add(pnlInput);
        pnlTopContainer.add(pnlScores);
        add(pnlTopContainer, BorderLayout.NORTH);

        // 3. Bảng nhập đáp án
        tableModel = new DefaultTableModel(new String[]{"STT", "Phần", "Đáp án đúng (" + currentCode + ")"}, 0);
        tblAnswers = new JTable(tableModel);
        TableUtils.enableExcelPaste(tblAnswers);
        add(new JScrollPane(tblAnswers), BorderLayout.CENTER);

        // 4. Nút chức năng
        JPanel pnlBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnImport = new JButton("Import Excel Cho Mã Này");
        btnImport.addActionListener(e -> importFromExcel());
        btnSave = new JButton("Lưu tất cả mã đề & Đóng");
        pnlBottom.add(btnImport);
        pnlBottom.add(btnSave);
        add(pnlBottom, BorderLayout.SOUTH);

        // --- SỰ KIỆN QUẢN LÝ MÃ ĐỀ ---
        cbxExamCodes.addActionListener(e -> {
            if (isUpdatingCombo) return;
            if (cbxExamCodes.getSelectedItem() != null) {
                if (tblAnswers.isEditing() && tblAnswers.getCellEditor() != null) {
                    tblAnswers.getCellEditor().stopCellEditing();
                }

                saveUIToCache();
                currentCode = cbxExamCodes.getSelectedItem().toString();
                tblAnswers.getColumnModel().getColumn(2).setHeaderValue("Đáp án đúng (" + currentCode + ")");
                tblAnswers.getTableHeader().repaint();

                updateTable();
                loadUIFromCache();
            }
        });

        btnAddCode.addActionListener(e -> {
            String newCode = JOptionPane.showInputDialog(this, "Nhập tên mã đề mới (VD: Độ Mixi):");
            if (newCode != null && !newCode.trim().isEmpty() && !uiCache.containsKey(newCode.trim())) {
                newCode = newCode.trim();
                uiCache.put(newCode, new HashMap<>());
                isUpdatingCombo = true;
                cbxModel.addElement(newCode);
                isUpdatingCombo = false;
                cbxExamCodes.setSelectedItem(newCode);
            }
        });

        btnDelCode.addActionListener(e -> {
            if (cbxModel.getSize() <= 1) {
                JOptionPane.showMessageDialog(this, "Phải giữ lại ít nhất 1 mã đề!");
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(this, "Xóa mã đề " + currentCode + " và toàn bộ đáp án của nó?", "Xác nhận", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                String codeToDelete = currentCode;

                isUpdatingCombo = true;
                cbxModel.removeElement(codeToDelete);

                currentCode = cbxModel.getElementAt(0);
                cbxExamCodes.setSelectedIndex(0);

                uiCache.remove(codeToDelete);

                tblAnswers.getColumnModel().getColumn(2).setHeaderValue("Đáp án đúng (" + currentCode + ")");
                tblAnswers.getTableHeader().repaint();
                updateTable();
                loadUIFromCache();

                isUpdatingCombo = false;
            }
        });

        addAutoCalculateListeners();
        enableDragAndDrop();
        updateTable();

        setLocationRelativeTo(parent);
        service.WindowPersistenceManager.restoreWindow(this, "AnswerKeyDialog", 800, 750);
        service.WindowPersistenceManager.attachSaver(this, "AnswerKeyDialog");
    }

    private void saveUIToCache() {
        if (tblAnswers.isEditing() && tblAnswers.getCellEditor() != null) {
            tblAnswers.getCellEditor().stopCellEditing();
        }

        Map<String, String> answers = new HashMap<>();
        int countP1 = 1, countP2 = 1, countP3 = 1;

        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String phan = tableModel.getValueAt(i, 1).toString();
            Object val = tableModel.getValueAt(i, 2);
            String ans = (val == null) ? "" : val.toString().trim().toUpperCase();

            if (phan.equals("I")) {
                answers.put("P1_Câu_" + countP1++, ans);
            } else if (phan.equals("II")) {
                if (ans.length() == 4) {
                    String n = ans.replace("D", "Đ").replace("T", "Đ").replace("F", "S");
                    answers.put("P2_Câu_" + countP2 + "_a", String.valueOf(n.charAt(0)));
                    answers.put("P2_Câu_" + countP2 + "_b", String.valueOf(n.charAt(1)));
                    answers.put("P2_Câu_" + countP2 + "_c", String.valueOf(n.charAt(2)));
                    answers.put("P2_Câu_" + countP2 + "_d", String.valueOf(n.charAt(3)));
                }
                countP2++;
            } else if (phan.equals("III")) {
                answers.put("P3_Câu_" + countP3++, ans);
            }
        }
        uiCache.put(currentCode, answers);
    }

    private void loadUIFromCache() {
        Map<String, String> answers = uiCache.getOrDefault(currentCode, new HashMap<>());
        int countP1 = 1, countP2 = 1, countP3 = 1;

        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String phan = tableModel.getValueAt(i, 1).toString();
            if (phan.equals("I")) {
                tableModel.setValueAt(answers.getOrDefault("P1_Câu_" + countP1++, ""), i, 2);
            } else if (phan.equals("II")) {
                String a = answers.getOrDefault("P2_Câu_" + countP2 + "_a", "");
                String b = answers.getOrDefault("P2_Câu_" + countP2 + "_b", "");
                String c = answers.getOrDefault("P2_Câu_" + countP2 + "_c", "");
                String d = answers.getOrDefault("P2_Câu_" + countP2 + "_d", "");
                tableModel.setValueAt(a + b + c + d, i, 2);
                countP2++;
            } else if (phan.equals("III")) {
                tableModel.setValueAt(answers.getOrDefault("P3_Câu_" + countP3++, ""), i, 2);
            }
        }
    }

    private void addAutoCalculateListeners() {
        DocumentListener dl = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { calculateTotalPossibleScore(); }
            public void removeUpdate(DocumentEvent e) { calculateTotalPossibleScore(); }
            public void changedUpdate(DocumentEvent e) { calculateTotalPossibleScore(); }
        };
        txtP1.getDocument().addDocumentListener(dl); txtP2.getDocument().addDocumentListener(dl); txtP3.getDocument().addDocumentListener(dl);
        txtScoreP1.getDocument().addDocumentListener(dl); txtScoreP3.getDocument().addDocumentListener(dl); txtScoreP2_4.getDocument().addDocumentListener(dl);
    }

    private void calculateTotalPossibleScore() {
        try {
            int n1 = Integer.parseInt(txtP1.getText().trim()); int n2 = Integer.parseInt(txtP2.getText().trim()); int n3 = Integer.parseInt(txtP3.getText().trim());
            double s1 = Double.parseDouble(txtScoreP1.getText().trim()); double s2Max = Double.parseDouble(txtScoreP2_4.getText().trim()); double s3 = Double.parseDouble(txtScoreP3.getText().trim());
            double total = Math.round(((n1 * s1) + (n2 * s2Max) + (n3 * s3)) * 100.0) / 100.0;
            lblTotalPossibleScore.setText("TỔNG ĐIỂM: " + total);
            if (total > 10.001) lblTotalPossibleScore.setForeground(Color.RED);
            else if (Math.abs(total - 10.0) < 0.001) lblTotalPossibleScore.setForeground(new Color(0, 150, 0));
            else lblTotalPossibleScore.setForeground(Color.BLUE);
        } catch (Exception e) { lblTotalPossibleScore.setText("LỖI NHẬP LIỆU"); }
    }

    private void updateTable() {
        try {
            int p1 = Integer.parseInt(txtP1.getText().trim());
            int p2 = Integer.parseInt(txtP2.getText().trim());
            int p3 = Integer.parseInt(txtP3.getText().trim());

            tableModel.setRowCount(0);
            for (int i = 1; i <= p1; i++) tableModel.addRow(new Object[]{i, "I", ""});
            for (int i = 1; i <= p2; i++) tableModel.addRow(new Object[]{i + p1, "II", ""});
            for (int i = 1; i <= p3; i++) tableModel.addRow(new Object[]{i + p1 + p2, "III", ""});
            calculateTotalPossibleScore();
        } catch (NumberFormatException e) { JOptionPane.showMessageDialog(this, "Lỗi: Vui lòng nhập số câu hợp lệ!"); }
    }

    public ExamConfig getExamConfig() {
        saveUIToCache();

        int actualP1 = Integer.parseInt(txtP1.getText().trim());
        int actualP2 = Integer.parseInt(txtP2.getText().trim());
        int actualP3 = Integer.parseInt(txtP3.getText().trim());

        ExamConfig newConfig = new ExamConfig(actualP1, actualP2, actualP3);

        try {
            newConfig.setScoreP1(Double.parseDouble(txtScoreP1.getText().trim()));
            newConfig.setScoreP3(Double.parseDouble(txtScoreP3.getText().trim()));
            newConfig.setScoreP2_1(Double.parseDouble(txtScoreP2_1.getText().trim()));
            newConfig.setScoreP2_2(Double.parseDouble(txtScoreP2_2.getText().trim()));
            newConfig.setScoreP2_3(Double.parseDouble(txtScoreP2_3.getText().trim()));
            newConfig.setScoreP2_4(Double.parseDouble(txtScoreP2_4.getText().trim()));
        } catch (Exception e) {}

        for (Map.Entry<String, Map<String, String>> entry : uiCache.entrySet()) {
            String codeName = entry.getKey();
            newConfig.addExamCode(codeName);
            newConfig.setActiveCode(codeName);

            for (Map.Entry<String, String> ansEntry : entry.getValue().entrySet()) {
                newConfig.setAnswer(ansEntry.getKey(), ansEntry.getValue());
            }
        }

        newConfig.setActiveCode("Mặc định");
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

        uiCache.clear();

        isUpdatingCombo = true;
        cbxModel.removeAllElements();

        for (String code : config.getExamCodes()) {
            cbxModel.addElement(code);
            config.setActiveCode(code);

            Map<String, String> answers = new HashMap<>();

            for(int i=1; i<=config.getNumPart1(); i++) {
                String ans = config.getAnswer("P1_Câu_"+i);
                if (ans != null) answers.put("P1_Câu_"+i, ans);
            }
            for(int i=1; i<=config.getNumPart2(); i++) {
                String a = config.getAnswer("P2_Câu_"+i+"_a"); if(a!=null) answers.put("P2_Câu_"+i+"_a", a);
                String b = config.getAnswer("P2_Câu_"+i+"_b"); if(b!=null) answers.put("P2_Câu_"+i+"_b", b);
                String c = config.getAnswer("P2_Câu_"+i+"_c"); if(c!=null) answers.put("P2_Câu_"+i+"_c", c);
                String d = config.getAnswer("P2_Câu_"+i+"_d"); if(d!=null) answers.put("P2_Câu_"+i+"_d", d);
            }
            for(int i=1; i<=config.getNumPart3(); i++) {
                String ans = config.getAnswer("P3_Câu_"+i);
                if (ans != null) answers.put("P3_Câu_"+i, ans);
            }

            uiCache.put(code, answers);
        }

        if (cbxModel.getSize() > 0) {
            currentCode = cbxModel.getElementAt(0);
            cbxExamCodes.setSelectedIndex(0);

            tblAnswers.getColumnModel().getColumn(2).setHeaderValue("Đáp án đúng (" + currentCode + ")");
            tblAnswers.getTableHeader().repaint();

            updateTable();
            loadUIFromCache();
        }

        isUpdatingCombo = false;
    }

    private void processExcelFile(File file) {
        // [FIX]: Dùng WorkbookFactory để đọc được mọi loại file Excel (.xls và .xlsx)
        try (Workbook workbook = WorkbookFactory.create(file)) {

            Sheet sheet = workbook.getSheetAt(0);
            tableModel.setRowCount(0);
            updateTable();

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
                            int rowIndex = -1;
                            if (phan.equals("I")) rowIndex = stt - 1;
                            else if (phan.equals("II")) rowIndex = Integer.parseInt(txtP1.getText().trim()) + stt - 1;
                            else if (phan.equals("III")) rowIndex = Integer.parseInt(txtP1.getText().trim()) + Integer.parseInt(txtP2.getText().trim()) + stt - 1;

                            if (rowIndex >= 0 && rowIndex < tableModel.getRowCount()) {
                                tableModel.setValueAt(ans, rowIndex, 2);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            calculateTotalPossibleScore();
            JOptionPane.showMessageDialog(this, "Đã tự động điền đáp án cho mã đề [" + currentCode + "] từ file Excel!");

        } catch (Exception e) {
            e.printStackTrace();
            // [FIX BẮT LỖI]: Báo rõ cho giáo viên nếu file là CSV bị đổi đuôi
            if (e.getMessage() != null && e.getMessage().contains("neither an OLE2 stream, nor an OOXML stream")) {
                JOptionPane.showMessageDialog(this, "Lỗi: File Excel giả mạo (thực chất là CSV/Văn bản đổi đuôi).\n\nCÁCH KHẮC PHỤC:\n1. Mở file này bằng phần mềm Excel.\n2. Chọn File -> Save As.\n3. Lưu lại với định dạng Excel Workbook (*.xlsx) rồi thử lại.", "Sai định dạng file", JOptionPane.ERROR_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Lỗi khi đọc file Excel: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void importFromExcel() {
        JFileChooser fileChooser = new JFileChooser();
        Preferences prefs = Preferences.userRoot().node("ChamTracNghiem_N7");
        String lastDir = prefs.get("DIR_ANSWER_KEY", System.getProperty("user.home"));
        fileChooser.setCurrentDirectory(new File(lastDir));

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            prefs.put("DIR_ANSWER_KEY", fileChooser.getSelectedFile().getParent());
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
                        if (file.getName().toLowerCase().endsWith(".xlsx") || file.getName().toLowerCase().endsWith(".xls")) {
                            processExcelFile(file);
                        }
                    }
                } catch (Exception ex) {}
            }
        });
    }

    public JButton getBtnSave() { return btnSave; }

}