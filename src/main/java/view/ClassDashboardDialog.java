package view;

import model.ClassRoom;
import model.ExamSession;
import model.OMRModels.ExamReport;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassDashboardDialog extends JDialog {

    private ClassRoom classRoom;
    private List<ExamSession> allSessions;

    public ClassDashboardDialog(JFrame parent, ClassRoom classRoom) {
        super(parent, "📊 Thống kê Tổng quan Lớp: " + classRoom.className, true);
        this.classRoom = classRoom;
        this.allSessions = loadAllSessionsForClass(classRoom.className);

        setSize(1000, 700);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        if (allSessions.isEmpty()) {
            add(new JLabel("<html><center><h2>Lớp này chưa có bài thi nào được lưu!</h2>Vui lòng chấm và lưu ít nhất 1 bài thi để xem thống kê.</center></html>", SwingConstants.CENTER), BorderLayout.CENTER);
            return;
        }

        initUI();
    }

    private void initUI() {
        // --- 1. VẼ BIỂU ĐỒ ĐƯỜNG (TIẾN ĐỘ LỚP) ---
        DefaultCategoryDataset lineDataset = new DefaultCategoryDataset();
        for (ExamSession session : allSessions) {
            double sum = 0;
            int count = 0;
            for (ExamReport report : session.getReports()) {
                sum += report.totalScore;
                count++;
            }
            double avg = count > 0 ? (sum / count) : 0;
            // Làm tròn 2 chữ số thập phân
            avg = Math.round(avg * 100.0) / 100.0;
            lineDataset.addValue(avg, "Điểm Trung Bình Lớp", session.getExamName());
        }

        JFreeChart lineChart = ChartFactory.createLineChart(
                "Tiến độ Điểm Trung Bình Lớp",
                "Tên Đề Thi", "Điểm Trung Bình",
                lineDataset, PlotOrientation.VERTICAL,
                true, true, false
        );

        org.jfree.chart.plot.CategoryPlot plot = lineChart.getCategoryPlot();
        org.jfree.chart.renderer.category.LineAndShapeRenderer renderer =
                (org.jfree.chart.renderer.category.LineAndShapeRenderer) plot.getRenderer();
        renderer.setDefaultShapesVisible(true); // Bật dấu chấm tròn

        ChartPanel chartPanel = new ChartPanel(lineChart);
        chartPanel.setPreferredSize(new Dimension(1000, 300));
        chartPanel.setBorder(BorderFactory.createTitledBorder("📉 Biểu đồ tiến độ"));
        add(chartPanel, BorderLayout.NORTH);

        // --- 2. VẼ BẢNG MA TRẬN ĐIỂM TẤT CẢ CÁC ĐỀ ---
        // Cột cơ bản
        List<String> columns = new ArrayList<>();
        columns.add("STT");
        columns.add("Họ Tên Học Sinh");

        // Thêm các cột tương ứng với các đề thi
        for (ExamSession session : allSessions) {
            columns.add(session.getExamName());
        }
        columns.add("Điểm TB Hệ Tích Lũy"); // Cột cuối cùng

        DefaultTableModel tableModel = new DefaultTableModel(columns.toArray(), 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        // Đổ dữ liệu học sinh
        for (ClassRoom.Student student : classRoom.students) {
            List<Object> rowData = new ArrayList<>();
            rowData.add(student.stt);
            rowData.add(student.name);

            double totalStudentScore = 0;
            int examAttended = 0;

            for (ExamSession session : allSessions) {
                // Tìm xem học sinh này có điểm trong đề này không
                boolean found = false;
                for (ExamReport report : session.getReports()) {
                    if (report.studentId.equals(String.valueOf(student.stt))) {
                        rowData.add(report.totalScore);
                        totalStudentScore += report.totalScore;
                        examAttended++;
                        found = true;
                        break;
                    }
                }
                if (!found) rowData.add("-"); // Vắng thi hoặc chưa chấm
            }

            // Tính điểm TB tích lũy của học sinh đó
            if (examAttended > 0) {
                double avgScore = Math.round((totalStudentScore / examAttended) * 100.0) / 100.0;
                rowData.add(avgScore);
            } else {
                rowData.add("-");
            }

            tableModel.addRow(rowData.toArray());
        }

        JTable tblMatrix = new JTable(tableModel);
        tblMatrix.setRowHeight(25);
        JScrollPane scrollPane = new JScrollPane(tblMatrix);
        scrollPane.setBorder(BorderFactory.createTitledBorder("📋 Bảng điểm chi tiết qua các kỳ thi"));

        add(scrollPane, BorderLayout.CENTER);

        // Nút đóng
        JPanel pnlBottom = new JPanel();
        JButton btnClose = new JButton("Đóng");
        btnClose.addActionListener(e -> dispose());
        pnlBottom.add(btnClose);
        add(pnlBottom, BorderLayout.SOUTH);
    }

    // Hàm tự động lùng sục đọc tất cả file .dat của lớp hiện tại
    // Hàm tự động lùng sục đọc tất cả file .dat của lớp hiện tại
    private List<ExamSession> loadAllSessionsForClass(String className) {
        List<ExamSession> sessions = new ArrayList<>();

        // 1. Quét thư mục gốc của lớp
        File baseDir = new File("data/classes/" + className);
        if (baseDir.exists()) {
            File[] files = baseDir.listFiles((d, name) -> name.endsWith(".dat") && !name.equals("class_info.dat"));
            if (files != null) {
                for (File file : files) {
                    try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                        sessions.add((ExamSession) ois.readObject());
                    } catch (Exception e) {}
                }
            }
        }

        // 2. Quét thư mục con "exams" (Nơi hệ thống thực sự lưu đề thi)
        File examDir = new File("data/classes/" + className + "/exams");
        if (examDir.exists()) {
            File[] files = examDir.listFiles((d, name) -> name.endsWith(".dat"));
            if (files != null) {
                for (File file : files) {
                    try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                        sessions.add((ExamSession) ois.readObject());
                    } catch (Exception e) {}
                }
            }
        }

        return sessions;
    }
}