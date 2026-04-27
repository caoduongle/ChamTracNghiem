package view;

import model.ExamSession;
import model.OMRModels.ExamReport;
import model.OMRModels.AnswerRecord;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class ItemAnalysisDialog extends JDialog {

    private Map<String, String> studentNameMap = new HashMap<>();

    private class QuestionStats {
        String questionId;
        String correctAnswer;
        int totalAnswers = 0;
        int correctTotal = 0;
        int correctHighGroup = 0;
        int correctLowGroup = 0;

        Map<String, List<String>> choiceStudents = new HashMap<>();
        Map<String, List<String>> wrongAnswersStudents = new HashMap<>();

        public QuestionStats(String id, String correct) {
            this.questionId = id;
            this.correctAnswer = correct;
        }
    }

    public ItemAnalysisDialog(JDialog parent, ExamSession session, String targetExamCode) {
        super(parent, "Phân tích chuyên sâu & Tra cứu lỗi - Mã đề: " + targetExamCode, true);
        setSize(1250, 800);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        // [FIX]: Chuyển GridLayout thành (3, 1) để thêm dòng thứ 3
        JPanel pnlLegend = new JPanel(new GridLayout(3, 1, 5, 5));
        pnlLegend.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(0, 102, 204), 2),
                "<html><span style=\"font-family: 'Segoe UI Emoji'\">📖</span> Hướng dẫn đọc chỉ số học thuật</html>",
                0, 0, new Font("Arial", Font.BOLD, 14), new Color(0, 102, 204)));
        pnlLegend.setBackground(new Color(245, 250, 255));

        JLabel lblP = new JLabel("<html><body style='margin-left:10px;'><b>• Độ Khó (P):</b> Tỷ lệ làm ĐÚNG. P gần 1.0 là <b>DỄ</b>, P gần 0.0 là <b>KHÓ</b>.</body></html>");
        JLabel lblD = new JLabel("<html><body style='margin-left:10px;'><b>• Độ Phân Hóa (D):</b> Khả năng lọc HS giỏi/kém. <b>D ≥ 0.3</b> là đạt chuẩn. <b>D < 0</b> là câu hỏi bị lỗi (HS kém làm đúng nhiều hơn HS giỏi).</body></html>");

// [FIX]: Sử dụng font Segoe UI Emoji cho các icon để tránh lỗi ô vuông
        JLabel lblEval = new JLabel("<html><body style='margin-left:10px;'><b>• Đánh giá chất lượng:</b> " +
                "<span style=\"font-family: 'Segoe UI Emoji'; color:#009900;\">⭐⭐⭐</span>: Rất tốt (Phân hóa cực mạnh); " +
                "<span style=\"font-family: 'Segoe UI Emoji';\">⭐⭐</span>: Tốt (Đạt chuẩn); " +
                "<span style=\"font-family: 'Segoe UI Emoji'; color:#E67E22;\">⭐</span>: Tạm ổn (Cần biên tập lại); " +
                "<span style=\"font-family: 'Segoe UI Emoji'; color:#CC0000;\">❌</span>: Kém (Nên loại bỏ khỏi ngân hàng đề).</body></html>");

        lblEval.setFont(new Font("Arial", Font.PLAIN, 13));
        lblP.setFont(new Font("Arial", Font.PLAIN, 13));
        lblD.setFont(new Font("Arial", Font.PLAIN, 13));


        pnlLegend.add(lblP);
        pnlLegend.add(lblD);
        pnlLegend.add(lblEval); // Thêm vào bảng chú thích
        add(pnlLegend, BorderLayout.NORTH);

        List<ExamReport> codeReports = session.getReports().stream()
                .filter(r -> targetExamCode.equals(r.examCode))
                .sorted((r1, r2) -> Double.compare(r2.totalScore, r1.totalScore))
                .collect(Collectors.toList());

        if (codeReports.isEmpty()) {
            add(new JLabel("Chưa có dữ liệu chấm cho mã đề này!", SwingConstants.CENTER));
            return;
        }

        int groupSize = (int) Math.max(1, Math.round(codeReports.size() * 0.27));
        if (groupSize * 2 > codeReports.size()) groupSize = codeReports.size() / 2;

        Map<String, QuestionStats> statsMap = processData(codeReports, groupSize);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Arial", Font.BOLD, 13));

        tabbedPane.addTab("Phần I: Trắc nghiệm (A,B,C,D)", createPart1Panel(statsMap, groupSize));
        tabbedPane.addTab("Phần II: Đúng / Sai", createPart2Panel(statsMap, groupSize));
        tabbedPane.addTab("Phần III: Trả lời ngắn (Tổng hợp lỗi)", createPart3Panel(statsMap, groupSize));

        add(tabbedPane, BorderLayout.CENTER);

        JPanel pnlBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnClose = new JButton("Đóng");
        btnClose.addActionListener(e -> dispose());
        pnlBottom.add(btnClose);
        add(pnlBottom, BorderLayout.SOUTH);

        // [CLEAN CODE]: Bổ sung tự động nhớ vị trí và kích thước cửa sổ
        service.WindowPersistenceManager.restoreWindow(this, "ItemAnalysisDialog", 1250, 800);
        service.WindowPersistenceManager.attachSaver(this, "ItemAnalysisDialog");
    }

    private Map<String, QuestionStats> processData(List<ExamReport> reports, int groupSize) {
        Map<String, QuestionStats> statsMap = new TreeMap<>();
        int total = reports.size();
        studentNameMap.clear();

        for (int i = 0; i < total; i++) {
            ExamReport r = reports.get(i);
            String stt = r.studentId;

            try {
                java.lang.reflect.Field nameField = r.getClass().getField("studentName");
                Object nameVal = nameField.get(r);
                if (nameVal != null && !nameVal.toString().trim().isEmpty()) {
                    studentNameMap.put(stt, nameVal.toString());
                }
            } catch (Exception ignore) {}

            boolean isHigh = (i < groupSize);
            boolean isLow = (i >= total - groupSize);

            for (AnswerRecord rec : r.details) {
                statsMap.putIfAbsent(rec.questionId, new QuestionStats(rec.questionId, rec.correctAnswer));
                QuestionStats qs = statsMap.get(rec.questionId);
                qs.totalAnswers++;

                String ans = rec.studentAnswer.trim().toUpperCase();

                if (rec.questionId.startsWith("P1_") || rec.questionId.startsWith("P2_")) {
                    qs.choiceStudents.putIfAbsent(ans, new ArrayList<>());
                    qs.choiceStudents.get(ans).add(stt);
                } else if (rec.questionId.startsWith("P3_")) {
                    if (!rec.isCorrect && !ans.isEmpty() && !ans.equals("?")) {
                        qs.wrongAnswersStudents.putIfAbsent(ans, new ArrayList<>());
                        qs.wrongAnswersStudents.get(ans).add(stt);
                    }
                }

                if (rec.isCorrect) {
                    qs.correctTotal++;
                    if (isHigh) qs.correctHighGroup++;
                    if (isLow) qs.correctLowGroup++;
                }
            }
        }
        return statsMap;
    }

    private JPanel createPart1Panel(Map<String, QuestionStats> stats, int gSize) {
        String[] cols = {"Câu hỏi", "Đ/A Chuẩn", "Tỷ lệ Sai (%)", "A (%)", "B (%)", "C (%)", "D (%)", "Độ Khó (P)", "Độ Phân Hóa (D)", "Đánh giá"};
        DefaultTableModel model = createDynamicModel(cols);

        for (QuestionStats qs : stats.values()) {
            if (!qs.questionId.startsWith("P1_")) continue;

            double errRate = calculateRate(qs.totalAnswers - qs.correctTotal, qs.totalAnswers);
            double p = calculateIndex(qs.correctTotal, qs.totalAnswers);
            double d = calculateIndex(qs.correctHighGroup - qs.correctLowGroup, gSize);

            int countA = qs.choiceStudents.containsKey("A") ? qs.choiceStudents.get("A").size() : 0;
            int countB = qs.choiceStudents.containsKey("B") ? qs.choiceStudents.get("B").size() : 0;
            int countC = qs.choiceStudents.containsKey("C") ? qs.choiceStudents.get("C").size() : 0;
            int countD = qs.choiceStudents.containsKey("D") ? qs.choiceStudents.get("D").size() : 0;

            model.addRow(new Object[]{
                    formatQId(qs.questionId), qs.correctAnswer, errRate,
                    calculateRate(countA, qs.totalAnswers), calculateRate(countB, qs.totalAnswers),
                    calculateRate(countC, qs.totalAnswers), calculateRate(countD, qs.totalAnswers),
                    p, d, evaluate(d)
            });
        }
        return createInteractiveTablePanel(model, 1, stats);
    }

    private JPanel createPart2Panel(Map<String, QuestionStats> stats, int gSize) {
        String[] cols = {"Câu hỏi", "Đ/A Chuẩn", "Tỷ lệ Sai (%)", "Chọn Đ (%)", "Chọn S (%)", "Độ Khó (P)", "Độ Phân Hóa (D)", "Đánh giá"};
        DefaultTableModel model = createDynamicModel(cols);

        for (QuestionStats qs : stats.values()) {
            if (!qs.questionId.startsWith("P2_")) continue;

            double errRate = calculateRate(qs.totalAnswers - qs.correctTotal, qs.totalAnswers);
            double p = calculateIndex(qs.correctTotal, qs.totalAnswers);
            double d = calculateIndex(qs.correctHighGroup - qs.correctLowGroup, gSize);

            int countTrue = (qs.choiceStudents.containsKey("Đ") ? qs.choiceStudents.get("Đ").size() : 0) +
                    (qs.choiceStudents.containsKey("T") ? qs.choiceStudents.get("T").size() : 0);
            int countFalse = (qs.choiceStudents.containsKey("S") ? qs.choiceStudents.get("S").size() : 0) +
                    (qs.choiceStudents.containsKey("F") ? qs.choiceStudents.get("F").size() : 0);

            model.addRow(new Object[]{
                    formatQId(qs.questionId), qs.correctAnswer, errRate,
                    calculateRate(countTrue, qs.totalAnswers), calculateRate(countFalse, qs.totalAnswers),
                    p, d, evaluate(d)
            });
        }
        return createInteractiveTablePanel(model, 2, stats);
    }

    private JPanel createPart3Panel(Map<String, QuestionStats> stats, int gSize) {
        int maxWrongs = 0;
        for (QuestionStats qs : stats.values()) {
            if (qs.questionId.startsWith("P3_")) {
                if (qs.wrongAnswersStudents.size() > maxWrongs) maxWrongs = qs.wrongAnswersStudents.size();
            }
        }

        int topN = Math.max(1, Math.min(maxWrongs, 8));

        List<String> colNames = new ArrayList<>(Arrays.asList("Câu hỏi", "Đ/A Chuẩn", "Tỷ lệ Sai (%)"));
        for (int i = 1; i <= topN; i++) colNames.add("Lỗi #" + i);
        colNames.add("Độ Khó (P)"); colNames.add("Độ Phân Hóa (D)");

        DefaultTableModel model = createDynamicModel(colNames.toArray(new String[0]));

        for (QuestionStats qs : stats.values()) {
            if (!qs.questionId.startsWith("P3_")) continue;

            double errRate = calculateRate(qs.totalAnswers - qs.correctTotal, qs.totalAnswers);
            double p = calculateIndex(qs.correctTotal, qs.totalAnswers);
            double d = calculateIndex(qs.correctHighGroup - qs.correctLowGroup, gSize);

            List<Map.Entry<String, List<String>>> sortedWrongs = qs.wrongAnswersStudents.entrySet().stream()
                    .sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size()))
                    .collect(Collectors.toList());

            List<Object> rowData = new ArrayList<>();
            rowData.add(formatQId(qs.questionId)); rowData.add(qs.correctAnswer); rowData.add(errRate);

            for (int i = 0; i < topN; i++) {
                if (i < sortedWrongs.size()) {
                    Map.Entry<String, List<String>> entry = sortedWrongs.get(i);
                    rowData.add(entry.getKey() + " (" + entry.getValue().size() + " HS)");
                } else rowData.add("-");
            }

            rowData.add(p); rowData.add(d);
            model.addRow(rowData.toArray());
        }
        return createInteractiveTablePanel(model, 3, stats);
    }

    private DefaultTableModel createDynamicModel(String[] cols) {
        return new DefaultTableModel(cols, 0) {
            @Override
            public Class<?> getColumnClass(int col) {
                if (getRowCount() > 0) {
                    Object val = getValueAt(0, col);
                    if (val != null) return val.getClass();
                }
                return Object.class;
            }
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
    }

    // [CLEAN CODE] Gộp logic kiểm tra cột có được click hay không vào 1 hàm duy nhất
    private boolean isClickableColumn(int partType, int col, int totalCols) {
        if (partType == 1 && col >= 3 && col <= 6) return true;
        if (partType == 2 && col >= 3 && col <= 4) return true;
        if (partType == 3 && col >= 3 && col < totalCols - 2) return true;
        return false;
    }

    private JPanel createInteractiveTablePanel(DefaultTableModel model, int partType, Map<String, QuestionStats> statsMap) {
        JTable tbl = new JTable(model);
        tbl.setRowHeight(30);
        tbl.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
        tbl.getTableHeader().setBackground(new Color(230, 230, 250));
        tbl.setRowSorter(new TableRowSorter<>(model));

        tbl.setRowSelectionAllowed(false);
        tbl.setCellSelectionEnabled(false);
        tbl.setFocusable(false);

        DefaultTableCellRenderer interactiveRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, false, false, row, column);
                setHorizontalAlignment(JLabel.CENTER);
                String valStr = (value != null) ? value.toString() : "";

                String correctAns = table.getValueAt(row, 1).toString().toUpperCase();
                boolean isClickableCol = isClickableColumn(partType, column, table.getColumnCount());
                boolean isCorrectCol = false;

                if (isClickableCol) {
                    if (partType == 1) {
                        String[] opts = {"A", "B", "C", "D"};
                        if (opts[column - 3].equals(correctAns)) { isCorrectCol = true; isClickableCol = false; }
                    } else if (partType == 2) {
                        String[] opts = {"Đ", "S"};
                        String cAns = correctAns.equals("T") ? "Đ" : (correctAns.equals("F") ? "S" : correctAns);
                        if (opts[column - 3].equals(cAns)) { isCorrectCol = true; isClickableCol = false; }
                    }
                }

                if (isClickableCol && !valStr.equals("0.0") && !valStr.equals("-")) {
                    setForeground(new Color(0, 102, 204));
                    setText("<html><u><b>" + valStr + "</b></u></html>");
                    setCursor(new Cursor(Cursor.HAND_CURSOR));
                } else if (isCorrectCol) {
                    setForeground(new Color(0, 153, 51));
                    setText("<html><b>" + valStr + "</b></html>");
                    setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                } else {
                    setForeground(Color.BLACK);
                    setText(valStr);
                    setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }
                return c;
            }
        };

        DefaultTableCellRenderer normalCenter = new DefaultTableCellRenderer();
        normalCenter.setHorizontalAlignment(JLabel.CENTER);

        for (int i = 1; i < tbl.getColumnCount(); i++) {
            tbl.getColumnModel().getColumn(i).setCellRenderer(
                    isClickableColumn(partType, i, tbl.getColumnCount()) ? interactiveRenderer : normalCenter
            );
        }

        tbl.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int i = 0; i < tbl.getColumnCount(); i++) {
            String colName = tbl.getColumnName(i);
            int width = 100;
            if (colName.equals("Câu hỏi") || colName.equals("Đ/A Chuẩn")) width = 90;
            else if (colName.contains("(%)")) width = 100;
            else if (colName.contains("Độ Khó") || colName.contains("Độ Phân Hóa")) width = 120;
            else if (colName.equals("Đánh giá")) width = 150;
            else if (colName.startsWith("Lỗi #")) width = 180;
            tbl.getColumnModel().getColumn(i).setPreferredWidth(width);
        }

        tbl.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = tbl.rowAtPoint(e.getPoint());
                int col = tbl.columnAtPoint(e.getPoint());

                if (row < 0 || !isClickableColumn(partType, col, tbl.getColumnCount())) return;

                String correctAns = tbl.getValueAt(row, 1).toString().toUpperCase();
                boolean allowClick = true;

                if (partType == 1) {
                    String[] opts = {"A", "B", "C", "D"};
                    if (opts[col - 3].equals(correctAns)) allowClick = false;
                } else if (partType == 2) {
                    String[] opts = {"Đ", "S"};
                    String cAns = correctAns.equals("T") ? "Đ" : (correctAns.equals("F") ? "S" : correctAns);
                    if (opts[col - 3].equals(cAns)) allowClick = false;
                }

                if (allowClick) {
                    Object cellValue = tbl.getValueAt(row, col);
                    if (cellValue == null || cellValue.toString().equals("0.0") || cellValue.toString().equals("-")) return;

                    String displayId = tbl.getValueAt(row, 0).toString();
                    String originalQId = displayId.replace("P.I ", "P1_").replace("P.II ", "P2_").replace("P.III ", "P3_").replace(" ", "_");
                    QuestionStats qs = statsMap.get(originalQId);
                    if (qs == null) return;

                    List<String> foundStudents = new ArrayList<>();
                    String title = "";

                    if (partType == 1) {
                        String clickedOpt = new String[]{"A", "B", "C", "D"}[col - 3];
                        foundStudents = qs.choiceStudents.getOrDefault(clickedOpt, new ArrayList<>());
                        title = "Chọn sai phương án: " + clickedOpt;
                    } else if (partType == 2) {
                        if (col == 3) {
                            foundStudents.addAll(qs.choiceStudents.getOrDefault("Đ", new ArrayList<>()));
                            foundStudents.addAll(qs.choiceStudents.getOrDefault("T", new ArrayList<>()));
                            title = "Chọn sai: Đ (Đúng)";
                        } else {
                            foundStudents.addAll(qs.choiceStudents.getOrDefault("S", new ArrayList<>()));
                            foundStudents.addAll(qs.choiceStudents.getOrDefault("F", new ArrayList<>()));
                            title = "Chọn sai: S (Sai)";
                        }
                    } else if (partType == 3) {
                        String valStr = cellValue.toString();
                        int lastParen = valStr.lastIndexOf(" (");
                        if (lastParen > 0) {
                            String wrongAns = valStr.substring(0, lastParen);
                            foundStudents = qs.wrongAnswersStudents.getOrDefault(wrongAns, new ArrayList<>());
                            title = "Viết sai thành: [" + wrongAns + "]";
                        }
                    }

                    if (!foundStudents.isEmpty()) {
                        showStudentListDialog(tbl, title, displayId, foundStudents);
                    }
                }
            }
        });

        JPanel p = new JPanel(new BorderLayout());
        JScrollPane scroll = new JScrollPane(tbl);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    private void showStudentListDialog(Component parent, String title, String qId, List<String> sttList) {
        sttList.sort((s1, s2) -> {
            try { return Integer.compare(Integer.parseInt(s1), Integer.parseInt(s2)); }
            catch (Exception ex) { return s1.compareTo(s2); }
        });

        String[] cols = {"STT", "Họ Tên Học Sinh"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        for (String stt : sttList) {
            String studentName = studentNameMap.getOrDefault(stt, "Học sinh số " + stt);
            model.addRow(new Object[]{stt, studentName});
        }

        JTable tblList = new JTable(model);
        tblList.setRowHeight(28);
        tblList.getTableHeader().setFont(new Font("Arial", Font.BOLD, 13));
        tblList.getTableHeader().setBackground(new Color(240, 248, 255));

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        tblList.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);

        tblList.getColumnModel().getColumn(0).setPreferredWidth(60);
        tblList.getColumnModel().getColumn(0).setMaxWidth(80);
        tblList.getColumnModel().getColumn(1).setPreferredWidth(280);

        JScrollPane scroll = new JScrollPane(tblList);
        scroll.setPreferredSize(new Dimension(380, 280));

        JOptionPane.showMessageDialog(parent, scroll, qId + " - " + title, JOptionPane.INFORMATION_MESSAGE);
    }

    private double calculateRate(int part, int total) {
        if (total == 0) return 0;
        return Math.round(((double) part / total) * 1000.0) / 10.0;
    }

    private double calculateIndex(int diff, int base) {
        if (base == 0) return 0;
        return Math.round(((double) diff / base) * 100.0) / 100.0;
    }

    private String formatQId(String id) {
        return id.replace("P1_", "P.I ").replace("P2_", "P.II ").replace("P3_", "P.III ").replace("_", " ");
    }

    private String evaluate(double d) {
        if (d >= 0.4) return "⭐⭐⭐ Rất Tốt";
        if (d >= 0.3) return "⭐⭐ Khá Tốt";
        if (d >= 0.2) return "⭐ Tạm Ổn";
        return "❌ Kém";
    }
}