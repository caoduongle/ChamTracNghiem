package view;

import model.ExamConfig;
import model.OMRModels;
import service.ScoringEngine;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class WrongAnswerDialog extends JDialog {

    // Thêm các biến config và callback để phục vụ việc tính lại điểm
    public WrongAnswerDialog(JFrame parent, OMRModels.ExamReport report, ExamConfig config, Runnable onSavedCallback) {
        super(parent, "Đối chiếu & Sửa điểm thủ công", true);
        setSize(1200, 800);
        setLocationRelativeTo(parent);

        JPanel pnlHeader = new JPanel(new BorderLayout());
        File imgFile = new File(report.originalImagePath != null ? report.originalImagePath : report.imagePath);
        JLabel lblFileName = new JLabel("  File gốc: " + imgFile.getName());
        lblFileName.setFont(new Font("SansSerif", Font.BOLD, 14));
        pnlHeader.add(lblFileName, BorderLayout.WEST);

        // Khu vực nút bấm góc trên bên phải
        JPanel pnlActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnViewProcessed = new JButton("🔍 Xem ảnh đã xử lý");
        JButton btnSaveOverride = new JButton("💾 Cập nhật điểm");
        btnSaveOverride.setBackground(new Color(0, 153, 51));
        btnSaveOverride.setForeground(Color.WHITE);
        pnlActions.add(btnViewProcessed);
        pnlActions.add(btnSaveOverride);
        pnlHeader.add(pnlActions, BorderLayout.EAST);

        String[] cols = {"Câu hỏi", "Đ/A Học sinh (Nháy đúp để sửa)", "Đ/A Chuẩn"};

        // [FIX] Cho phép Edit cột số 1 (Đ/A Học sinh)
        DefaultTableModel detailModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1;
            }
        };

        java.util.List<OMRModels.AnswerRecord> wrongAnswers = new ArrayList<>();
        for (OMRModels.AnswerRecord rec : report.details) {
            if (!rec.isCorrect) wrongAnswers.add(rec);
        }

        // Logic sắp xếp câu hỏi giữ nguyên
        wrongAnswers.sort((r1, r2) -> {
            try {
                String[] p1 = r1.questionId.split("_");
                String[] p2 = r2.questionId.split("_");
                int part1 = Integer.parseInt(p1[0].replace("P", ""));
                int part2 = Integer.parseInt(p2[0].replace("P", ""));
                if (part1 != part2) return Integer.compare(part1, part2);
                int q1 = Integer.parseInt(p1[2]);
                int q2 = Integer.parseInt(p2[2]);
                if (q1 != q2) return Integer.compare(q1, q2);
                if (p1.length > 3 && p2.length > 3) return p1[3].compareTo(p2[3]);
                return 0;
            } catch (Exception e) { return r1.questionId.compareTo(r2.questionId); }
        });

        int wrongCount = wrongAnswers.size();
        for (OMRModels.AnswerRecord rec : wrongAnswers) {
            detailModel.addRow(new Object[]{rec.questionId, rec.studentAnswer, rec.correctAnswer});
        }

        JTable tblDetail = new JTable(detailModel);
        tblDetail.setForeground(Color.RED);
        tblDetail.setFont(new Font("Arial", Font.BOLD, 14));
        tblDetail.setRowHeight(30);

        JPanel pnlTable = new JPanel(new BorderLayout());
        pnlTable.add(new JScrollPane(tblDetail), BorderLayout.CENTER);

        JLabel lblTotalWrong = new JLabel("  Tổng số ý sai: " + wrongCount + " (Sửa trực tiếp vào bảng nếu máy chấm nhầm)", SwingConstants.LEFT);
        lblTotalWrong.setFont(new Font("Arial", Font.ITALIC, 13));
        pnlTable.add(lblTotalWrong, BorderLayout.SOUTH);

        // Logic hiển thị ảnh giữ nguyên
        JLabel lblImage = new JLabel("", SwingConstants.CENTER);
        final String pathOriginal = report.imagePath;
        final String pathProcessed = report.imagePath.replace(".jpg", "_processed.jpg");

        java.util.function.Consumer<String> updateImage = (path) -> {
            File f = new File(path);
            if (f.exists()) {
                ImageIcon icon = new ImageIcon(path);
                Image img = icon.getImage();
                int newWidth = 700;
                int newHeight = (int) (icon.getIconHeight() * ((double) newWidth / icon.getIconWidth()));
                lblImage.setIcon(new ImageIcon(img.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH)));
                lblImage.setText("");
            } else {
                lblImage.setIcon(null);
                lblImage.setText("Không tìm thấy file: " + f.getName());
            }
        };

        updateImage.accept(pathOriginal);
        btnViewProcessed.addActionListener(e -> {
            if (btnViewProcessed.getText().contains("xử lý")) {
                updateImage.accept(pathProcessed);
                btnViewProcessed.setText("🖼 Xem lại ảnh gốc");
            } else {
                updateImage.accept(pathOriginal);
                btnViewProcessed.setText("🔍 Xem ảnh đã xử lý");
            }
        });

        // [NEW] XỬ LÝ SỰ KIỆN LƯU ĐIỂM THỦ CÔNG
        btnSaveOverride.addActionListener(e -> {
            if (tblDetail.isEditing()) tblDetail.getCellEditor().stopCellEditing();

            // 1. Phục hồi lại toàn bộ bài làm gốc
            Map<String, String> updatedAnswers = new HashMap<>();
            for (OMRModels.AnswerRecord rec : report.details) {
                updatedAnswers.put(rec.questionId, rec.studentAnswer);
            }

            // 2. Cập nhật những câu giáo viên vừa sửa trên bảng
            for (int i = 0; i < tblDetail.getRowCount(); i++) {
                String qId = (String) tblDetail.getValueAt(i, 0);
                String newAns = (String) tblDetail.getValueAt(i, 1);
                updatedAnswers.put(qId, newAns.trim().toUpperCase());
            }
            // =========================================================
            // [FIX BẮT BUỘC]: Kích hoạt đúng mã đề của học sinh trước khi chấm
            // =========================================================
            config.setActiveCode(report.examCode);

            // 3. Chấm lại điểm bằng ScoringEngine
            OMRModels.ExamReport newReport = ScoringEngine.gradeExam(report.studentId, report.examCode, updatedAnswers, config);

            // 4. Ghi đè kết quả mới vào object report cũ
            report.totalScore = newReport.totalScore;
            report.details = newReport.details;
            report.statusMessage = "<html><span style=\"font-family: 'Segoe UI Emoji'\">✅</span> Đã sửa tay (" + report.totalScore + "đ)</html>";

            JOptionPane.showMessageDialog(this, "Cập nhật điểm thành công! Điểm mới: " + report.totalScore);

            // 5. Gọi callback để cập nhật bảng MainView & Lưu file .dat
            if (onSavedCallback != null) onSavedCallback.run();
            dispose();
        });

        JScrollPane scrollImage = new JScrollPane(lblImage);
        scrollImage.getVerticalScrollBar().setUnitIncrement(20);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollImage, pnlTable);
        splitPane.setDividerLocation(750);

        setLayout(new BorderLayout());
        add(pnlHeader, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
    }
}