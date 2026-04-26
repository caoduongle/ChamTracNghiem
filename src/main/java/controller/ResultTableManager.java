package controller;

import model.ClassRoom;
import model.ExamSession;
import model.OMRModels;
import view.MainView;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ResultTableManager {
    private final MainView view;
    private final JTable table;
    private final DefaultTableModel tableModel;

    public ResultTableManager(MainView view) {
        this.view = view;
        this.table = view.getTblResults();
        this.tableModel = (DefaultTableModel) table.getModel();
        setupColumns();
    }

    private void setupColumns() {
        tableModel.setColumnIdentifiers(new Object[]{"STT", "Họ Tên Học Sinh", "File Ảnh", "Mã Đề", "Tổng điểm", "Trạng thái"});

        TableColumn statusColumn = table.getColumnModel().getColumn(5);
        statusColumn.setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value != null) {
                    String cleanText = value.toString().replaceAll("<[^>]*>", "");
                    ((JComponent) c).setToolTipText("<html><p width='300'>" + cleanText + "</p></html>");
                }
                return c;
            }
        });
    }

    public void updateExamCodeEditor(java.util.Set<String> examCodes) {
        TableColumn codeColumn = table.getColumnModel().getColumn(3);
        if (examCodes != null && !examCodes.isEmpty()) {
            JComboBox<String> comboBox = new JComboBox<>(examCodes.toArray(new String[0]));
            codeColumn.setCellEditor(new DefaultCellEditor(comboBox));
        } else {
            codeColumn.setCellEditor(null);
        }
    }

    public void refresh(ExamSession session, ClassRoom classRoom,
                        Map<String, OMRModels.ExamReport> reportDatabase,
                        Map<String, File> assignedFiles,
                        Map<String, String> studentExamCodes,
                        List<String> currentRowStts) {

        if (session == null || classRoom == null) return;

        if (table.isEditing() && table.getCellEditor() != null) {
            table.getCellEditor().stopCellEditing();
        }

        tableModel.setRowCount(0);
        currentRowStts.clear();

        List<ClassRoom.Student> sortedStudents = new ArrayList<>(classRoom.students);
        int sortMode = view.getCbxSortResults().getSelectedIndex();

        sortedStudents.sort((s1, s2) -> {
            String stt1 = String.valueOf(s1.stt);
            String stt2 = String.valueOf(s2.stt);
            OMRModels.ExamReport r1 = reportDatabase.get(stt1);
            OMRModels.ExamReport r2 = reportDatabase.get(stt2);

            if (sortMode == 1) {
                double score1 = r1 != null ? r1.totalScore : -1.0;
                double score2 = r2 != null ? r2.totalScore : -1.0;
                if (score1 != score2) return Double.compare(score2, score1);
            } else if (sortMode == 2) {
                int p1 = getStatusPriority(r1, assignedFiles.containsKey(stt1));
                int p2 = getStatusPriority(r2, assignedFiles.containsKey(stt2));
                if (p1 != p2) return Integer.compare(p1, p2);
            }
            return Integer.compare(s1.stt, s2.stt);
        });

        for (ClassRoom.Student student : sortedStudents) {
            String stt = String.valueOf(student.stt);
            currentRowStts.add(stt);

            OMRModels.ExamReport report = reportDatabase.get(stt);
            boolean isPending = assignedFiles.containsKey(stt);

            String fileNameDisplay = "--- Chưa có ảnh ---";
            String code = report != null && report.examCode != null ? report.examCode : studentExamCodes.getOrDefault(stt, "---");
            String score = report != null ? String.valueOf(report.totalScore) : "";
            String status = report != null ? report.statusMessage : "Chưa có bài";

            // [FIX] LOGIC MỚI: Luôn ưu tiên hiển thị hàng chờ ⏳ nếu vừa gán ảnh mới
            if (isPending) {
                fileNameDisplay = "<html><span style=\"font-family: 'Segoe UI Emoji'\">⏳</span> " + assignedFiles.get(stt).getName() + " (Chờ)</html>";
                status = "Đang chờ chấm...";
                score = "-"; // Ẩn điểm cũ đi để tránh nhầm lẫn khi đang đợi chấm lại
            }
            else if (report != null && report.originalImagePath != null) {
                fileNameDisplay = "<html><span style=\"font-family: 'Segoe UI Emoji'\">✅</span> " + new File(report.originalImagePath).getName() + "</html>";
            }

            tableModel.addRow(new Object[]{stt, student.name, fileNameDisplay, code, score, status});
        }
    }

    private int getStatusPriority(OMRModels.ExamReport report, boolean isPending) {
        if (isPending) return 3; // Ưu tiên gom các bài đang chờ chấm lại gần nhau
        if (report != null && report.statusMessage != null) {
            if (report.statusMessage.contains("❌")) return 1;
            if (report.statusMessage.contains("⚠️")) return 2;
            if (report.statusMessage.contains("✅")) return 4;
        }
        return 5;
    }
}