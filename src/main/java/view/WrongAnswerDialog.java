package view;

import model.OMRModels;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;

public class WrongAnswerDialog extends JDialog {

    public WrongAnswerDialog(JFrame parent, OMRModels.ExamReport report) {
        super(parent, "Đối chiếu chi tiết", true);
        setSize(1200, 800);
        setLocationRelativeTo(parent);

        JPanel pnlHeader = new JPanel(new BorderLayout());
        File imgFile = new File(report.originalImagePath != null ? report.originalImagePath : report.imagePath);
        JLabel lblFileName = new JLabel("  File gốc: " + imgFile.getName());
        lblFileName.setFont(new Font("SansSerif", Font.BOLD, 14));

        JButton btnViewProcessed = new JButton("🔍 Xem ảnh đã xử lý (Debug OMR)");
        pnlHeader.add(lblFileName, BorderLayout.WEST);
        pnlHeader.add(btnViewProcessed, BorderLayout.EAST);

        String[] cols = {"Câu hỏi", "Đ/A Học sinh", "Đ/A Chuẩn"};
        DefaultTableModel detailModel = new DefaultTableModel(cols, 0);

        java.util.List<OMRModels.AnswerRecord> wrongAnswers = new ArrayList<>();
        for (OMRModels.AnswerRecord rec : report.details) {
            if (!rec.isCorrect) wrongAnswers.add(rec);
        }

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
            } catch (Exception e) {
                return r1.questionId.compareTo(r2.questionId);
            }
        });

        int wrongCount = wrongAnswers.size();
        for (OMRModels.AnswerRecord rec : wrongAnswers) {
            detailModel.addRow(new Object[]{rec.questionId, rec.studentAnswer, rec.correctAnswer});
        }

        JTable tblDetail = new JTable(detailModel);
        tblDetail.setForeground(Color.RED);
        tblDetail.setFont(new Font("Arial", Font.PLAIN, 14));
        tblDetail.setRowHeight(25);

        JPanel pnlTable = new JPanel(new BorderLayout());
        pnlTable.add(new JScrollPane(tblDetail), BorderLayout.CENTER);

        JLabel lblTotalWrong = new JLabel("  Tổng số ý sai: " + wrongCount, SwingConstants.LEFT);
        lblTotalWrong.setFont(new Font("Arial", Font.BOLD, 14));
        pnlTable.add(lblTotalWrong, BorderLayout.SOUTH);

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
            if (btnViewProcessed.getText().contains("Debug")) {
                updateImage.accept(pathProcessed);
                btnViewProcessed.setText("🖼 Xem lại ảnh gốc");
            } else {
                updateImage.accept(pathOriginal);
                btnViewProcessed.setText("🔍 Xem ảnh đã xử lý (Debug OMR)");
            }
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