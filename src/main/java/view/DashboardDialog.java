package view;

import model.ExamSession;
import model.OMRModels.AnswerRecord;
import model.OMRModels.ExamReport;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class DashboardDialog extends JDialog {

    public DashboardDialog(JFrame parent, ExamSession session) {
        super(parent, "Dashboard Thống kê - Đề: " + session.getExamName(), true);
        setSize(1000, 800); // Tăng kích thước để chứa thêm biểu đồ
        setLocationRelativeTo(parent);
        // Chia làm lưới 2x2 để chứa tối đa 4 biểu đồ
        setLayout(new GridLayout(2, 2, 10, 10));

        List<ExamReport> allReports = session.getReports();

        // 1. Lọc bỏ các bài có đánh dấu lỗi (❌ Lỗi)
        List<ExamReport> validReports = allReports.stream()
                .filter(r -> r.statusMessage == null || !r.statusMessage.contains("❌ Lỗi"))
                .collect(Collectors.toList());

        if (validReports.isEmpty()) {
            setLayout(new BorderLayout());
            add(new JLabel("<html><center><h2>Chưa có dữ liệu hợp lệ!</h2>Các bài bị lỗi sẽ không được đưa vào thống kê.</center></html>", SwingConstants.CENTER));
            return;
        }

        // --- KHỞI TẠO DỮ LIỆU THỐNG KÊ ---
        int[] scoreDistribution = new int[11];
        int gioi = 0, kha = 0, trungBinh = 0, yeu = 0;
        Map<String, Integer> questionMissCount = new HashMap<>();

        for (ExamReport report : validReports) {
            // Thống kê phổ điểm
            int roundedScore = (int) Math.round(report.totalScore);
            if (roundedScore >= 0 && roundedScore <= 10) scoreDistribution[roundedScore]++;

            // Thống kê học lực
            if (report.totalScore >= 8.0) gioi++;
            else if (report.totalScore >= 6.5) kha++;
            else if (report.totalScore >= 5.0) trungBinh++;
            else yeu++;

            // Thống kê câu sai (Item Analysis)
            for (AnswerRecord detail : report.details) {
                if (!detail.isCorrect) {
                    questionMissCount.put(detail.questionId, questionMissCount.getOrDefault(detail.questionId, 0) + 1);
                }
            }
        }

        // --- BIỂU ĐỒ 1: PHỔ ĐIỂM (BAR CHART) ---
        DefaultCategoryDataset barDataset = new DefaultCategoryDataset();
        for (int i = 0; i <= 10; i++) {
            barDataset.addValue(scoreDistribution[i], "Số lượng", String.valueOf(i));
        }
        JFreeChart barChart = ChartFactory.createBarChart("Phổ Điểm Toàn Lớp", "Mức Điểm", "Số Học Sinh", barDataset, PlotOrientation.VERTICAL, false, true, false);
        add(new ChartPanel(barChart));

        // --- BIỂU ĐỒ 2: TỶ LỆ HỌC LỰC (PIE CHART) ---
        DefaultPieDataset pieDataset = new DefaultPieDataset();
        pieDataset.setValue("Giỏi (>= 8.0)", gioi);
        pieDataset.setValue("Khá (6.5 - 7.9)", kha);
        pieDataset.setValue("Trung Bình (5.0 - 6.4)", trungBinh);
        pieDataset.setValue("Yếu (< 5.0)", yeu);
        JFreeChart pieChart = ChartFactory.createPieChart("Tỷ lệ Học lực", pieDataset, true, true, false);
        add(new ChartPanel(pieChart));

        // --- BIỂU ĐỒ 3: TOP 10 CÂU SAI NHIỀU NHẤT (HORIZONTAL BAR CHART) ---
        DefaultCategoryDataset missDataset = new DefaultCategoryDataset();

        // Sắp xếp Map theo số lượng sai giảm dần và lấy top 10
        questionMissCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(entry -> {
                    // Cắt ngắn tên câu hỏi cho đẹp (VD: P1_Câu_12 -> Câu 12)
                    String shortName = entry.getKey().replace("P1_", "").replace("P2_", "").replace("P3_", "");
                    missDataset.addValue(entry.getValue(), "Số học sinh sai", shortName);
                });

        JFreeChart missChart = ChartFactory.createBarChart(
                "Top 10 câu sai nhiều nhất",
                "Câu hỏi", "Số học sinh làm sai",
                missDataset, PlotOrientation.HORIZONTAL, // Để nằm ngang cho dễ đọc tên câu
                false, true, false
        );

        // Tùy chỉnh trục số để chỉ hiện số nguyên
        CategoryPlot plot = (CategoryPlot) missChart.getPlot();
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        add(new ChartPanel(missChart));

        // --- Ô THỨ 4: THÔNG TIN TỔNG QUAN (LABEL TRỰC QUAN) ---
        JPanel pnlInfo = new JPanel(new GridLayout(4, 1));
        pnlInfo.setBorder(BorderFactory.createTitledBorder("Thông tin chung"));
        pnlInfo.setBackground(Color.WHITE);

        JLabel lblTotal = new JLabel("  Số bài làm hợp lệ: " + validReports.size());
        JLabel lblError = new JLabel("  Số bài bị loại bỏ (Lỗi): " + (allReports.size() - validReports.size()));

        double avg = validReports.stream().mapToDouble(r -> r.totalScore).average().orElse(0.0);
        JLabel lblAvg = new JLabel("  Điểm trung bình lớp: " + String.format("%.2f", avg));

        lblTotal.setFont(new Font("Arial", Font.BOLD, 16));
        lblAvg.setFont(new Font("Arial", Font.BOLD, 16));
        lblAvg.setForeground(new Color(0, 102, 204));

        pnlInfo.add(lblTotal);
        pnlInfo.add(lblError);
        pnlInfo.add(lblAvg);
        add(pnlInfo);
    }
}