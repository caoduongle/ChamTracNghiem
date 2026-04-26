package view;

import model.ExamSession;
import model.OMRModels.AnswerRecord;
import model.OMRModels.ExamReport;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class DashboardDialog extends JDialog {

    public DashboardDialog(JFrame parent, ExamSession session) {
        super(parent, "Dashboard Thống kê - Đề: " + session.getExamName(), true);
        setSize(1200, 800); // Mở rộng form để hiển thị các tab và cột thoải mái hơn
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        List<ExamReport> allReports = session.getReports();

        // 1. Lọc bỏ các bài lỗi
        List<ExamReport> validReports = allReports.stream()
                .filter(r -> r.statusMessage == null || !r.statusMessage.contains("❌ Lỗi"))
                .collect(Collectors.toList());

        if (validReports.isEmpty()) {
            add(new JLabel("<html><center><h2>Chưa có dữ liệu hợp lệ!</h2>Các bài bị lỗi sẽ không được đưa vào thống kê.</center></html>", SwingConstants.CENTER));
            return;
        }

        // --- KHU VỰC THÔNG TIN CHUNG TOÀN LỚP (BÊN TRÊN) ---
        JPanel pnlGeneralInfo = new JPanel(new FlowLayout(FlowLayout.CENTER, 40, 10));
        pnlGeneralInfo.setBorder(BorderFactory.createTitledBorder("Thông tin tổng quan Toàn Lớp"));
        pnlGeneralInfo.setBackground(Color.WHITE);

        double avg = validReports.stream().mapToDouble(r -> r.totalScore).average().orElse(0.0);
        pnlGeneralInfo.add(new JLabel("<html><font size='4'>Số bài hợp lệ: <b>" + validReports.size() + "</b></font></html>"));
        pnlGeneralInfo.add(new JLabel("<html><font size='4'>Số bài lỗi: <b>" + (allReports.size() - validReports.size()) + "</b></font></html>"));
        pnlGeneralInfo.add(new JLabel("<html><font color='#0066cc' size='5'>Điểm TB toàn lớp: <b>" + String.format("%.2f", avg) + "</b></font></html>"));

        add(pnlGeneralInfo, BorderLayout.NORTH);

        // --- KHU VỰC CHIA TAB THEO TỪNG MÃ ĐỀ (BÊN DƯỚI) ---
        // Gom nhóm các bài thi theo Mã Đề
        Map<String, List<ExamReport>> reportsByCode = validReports.stream()
                .collect(Collectors.groupingBy(r -> r.examCode != null ? r.examCode : "Mặc định"));

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Arial", Font.BOLD, 14));

        // Tạo 1 Tab riêng cho mỗi Mã Đề
        for (Map.Entry<String, List<ExamReport>> entry : reportsByCode.entrySet()) {
            String code = entry.getKey();
            List<ExamReport> reports = entry.getValue();
            tabbedPane.addTab("Mã Đề: " + code + " (Sĩ số: " + reports.size() + ")", createTabForCode(code, reports));
        }

        add(tabbedPane, BorderLayout.CENTER);
        // Thêm vào cuối hàm public DashboardDialog(...) { ... }
        service.WindowPersistenceManager.restoreWindow(this, "DashboardDialog", 1200, 800);
        service.WindowPersistenceManager.attachSaver(this, "DashboardDialog");
    }

    // Hàm tạo nội dung cho từng Tab Mã Đề
    private JPanel createTabForCode(String code, List<ExamReport> reports) {
        JPanel pnlTab = new JPanel(new BorderLayout(10, 10));
        pnlTab.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 1. Dữ liệu phổ điểm và học lực của mã đề này
        int[] scoreDistribution = new int[11];
        int gioi = 0, kha = 0, trungBinh = 0, yeu = 0;

        // Bộ đếm câu sai riêng biệt cho 3 Phần
        Map<String, Integer> p1Misses = new HashMap<>();
        Map<String, Integer> p2Misses = new HashMap<>();
        Map<String, Integer> p3Misses = new HashMap<>();

        for (ExamReport report : reports) {
            int roundedScore = (int) Math.round(report.totalScore);
            if (roundedScore > 10) roundedScore = 10;
            if (roundedScore < 0) roundedScore = 0;

            scoreDistribution[roundedScore]++;

            if (report.totalScore >= 8.0) gioi++;
            else if (report.totalScore >= 6.5) kha++;
            else if (report.totalScore >= 5.0) trungBinh++;
            else yeu++;

            // Phân loại câu sai vào 3 phần
            for (AnswerRecord detail : report.details) {
                if (!detail.isCorrect) {
                    String qId = detail.questionId;
                    if (qId.startsWith("P1_")) {
                        p1Misses.put(qId, p1Misses.getOrDefault(qId, 0) + 1);
                    } else if (qId.startsWith("P2_")) {
                        p2Misses.put(qId, p2Misses.getOrDefault(qId, 0) + 1);
                    } else if (qId.startsWith("P3_")) {
                        p3Misses.put(qId, p3Misses.getOrDefault(qId, 0) + 1);
                    }
                }
            }
        }

        // 2. Khu vực Biểu đồ trên cùng (Phổ điểm + Pie Chart)
        JPanel pnlCharts = new JPanel(new GridLayout(1, 2, 10, 10));

        DefaultCategoryDataset barDataset = new DefaultCategoryDataset();
        for (int i = 0; i <= 10; i++) {
            barDataset.addValue(scoreDistribution[i], "Số lượng", String.valueOf(i));
        }
        JFreeChart barChart = ChartFactory.createBarChart("Phổ Điểm (Mã " + code + ")", "Mức Điểm", "Số Học Sinh", barDataset, PlotOrientation.VERTICAL, false, true, false);
        pnlCharts.add(new ChartPanel(barChart));

        DefaultPieDataset pieDataset = new DefaultPieDataset();
        pieDataset.setValue("Giỏi (>= 8.0)", gioi);
        pieDataset.setValue("Khá (6.5 - 7.9)", kha);
        pieDataset.setValue("Trung Bình (5.0 - 6.4)", trungBinh);
        pieDataset.setValue("Yếu (< 5.0)", yeu);
        JFreeChart pieChart = ChartFactory.createPieChart("Tỷ lệ Học lực (Mã " + code + ")", pieDataset, true, true, false);
        pnlCharts.add(new ChartPanel(pieChart));

        pnlTab.add(pnlCharts, BorderLayout.CENTER);

        // 3. Khu vực Top 3 Câu Sai Từng Phần (Bên dưới)
        JPanel pnlTopMisses = new JPanel(new GridLayout(1, 3, 10, 0));
        pnlTopMisses.setBorder(BorderFactory.createTitledBorder(null, "Top 3 sai nhiều nhất theo cấu trúc đề", TitledBorder.LEFT, TitledBorder.TOP, new Font("Arial", Font.BOLD, 15)));
        pnlTopMisses.setPreferredSize(new Dimension(1000, 250));

        // Nạp dữ liệu vào 3 ô vuông
        pnlTopMisses.add(createTopMissPanel("Phần I (Nhiều lựa chọn)", p1Misses, reports.size()));
        pnlTopMisses.add(createTopMissPanel("Phần II (Đúng/Sai từng ý)", p2Misses, reports.size()));
        pnlTopMisses.add(createTopMissPanel("Phần III (Trả lời ngắn)", p3Misses, reports.size()));

        pnlTab.add(pnlTopMisses, BorderLayout.SOUTH);

        return pnlTab;
    }

    // Hàm tiện ích tạo ô chứa danh sách Top 3 câu sai dạng HTML cho đẹp
    private JPanel createTopMissPanel(String title, Map<String, Integer> misses, int totalStudents) {
        JPanel pnl = new JPanel(new BorderLayout());
        pnl.setBorder(BorderFactory.createTitledBorder(title));
        pnl.setBackground(new Color(250, 250, 255));

        // Lọc top 3 có giá trị sai cao nhất
        List<Map.Entry<String, Integer>> topMisses = misses.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .collect(Collectors.toList());

        if (topMisses.isEmpty()) {
            pnl.add(new JLabel("Hoàn hảo! Không có ai sai.", SwingConstants.CENTER), BorderLayout.CENTER);
            return pnl;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family: Arial; font-size: 14px; margin: 10px;'>");
        sb.append("<ol style='padding-left: 20px;'>");

        for (Map.Entry<String, Integer> entry : topMisses) {
            String rawName = entry.getKey();

            // Format tự động cho đẹp: P2_Câu_1_a -> Câu 1 ý a
            String displayName = rawName
                    .replace("P1_Câu_", "Câu ")
                    .replace("P2_Câu_", "Câu ")
                    .replace("P3_Câu_", "Câu ")
                    .replace("_", " ý ");

            int missCount = entry.getValue();
            double percent = (double) missCount / totalStudents * 100;

            sb.append("<li style='margin-bottom: 12px;'>");
            sb.append("<b>").append(displayName).append("</b><br>");
            sb.append("<font color='#cc0000'>Sai: <b>").append(missCount).append(" HS</b></font> ");
            sb.append("<i>(Chiếm ").append(String.format("%.1f", percent)).append("%)</i>");
            sb.append("</li>");
        }
        sb.append("</ol></body></html>");

        JLabel lblInfo = new JLabel(sb.toString());
        lblInfo.setVerticalAlignment(SwingConstants.TOP);
        pnl.add(lblInfo, BorderLayout.CENTER);

        return pnl;
    }
}