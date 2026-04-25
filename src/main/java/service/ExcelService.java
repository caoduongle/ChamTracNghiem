package service;

import model.ExamConfig;
import model.ExamSession;
import model.ClassRoom;
import model.OMRModels.ExamReport;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExcelService {

    // 1. XUẤT BẢNG ĐIỂM RIÊNG CỦA 1 ĐỀ (Dùng trong lúc chấm bài)
    public static void exportExamScoreTable(ClassRoom classRoom, ExamSession session, String filePath) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Điểm " + session.getExamName());

        Row headerRow = sheet.createRow(0);
        String[] columns = {"STT", "Họ Tên", "Điểm Đạt Được"};

        CellStyle headerStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);

        for (int i = 0; i < columns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        // Tạo map report để tra cứu nhanh điểm theo STT
        Map<String, Double> scoreMap = new HashMap<>();
        if (session.getReports() != null) {
            for (ExamReport r : session.getReports()) {
                scoreMap.put(r.studentId, r.totalScore); // studentId lúc này là STT
            }
        }

        int rowIdx = 1;
        for (ClassRoom.Student student : classRoom.students) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(student.stt);
            row.createCell(1).setCellValue(student.name);

            String sttStr = String.valueOf(student.stt);
            if (scoreMap.containsKey(sttStr)) {
                row.createCell(2).setCellValue(scoreMap.get(sttStr));
            } else {
                row.createCell(2).setCellValue("Chưa chấm");
            }
        }

        for (int i = 0; i < columns.length; i++) sheet.autoSizeColumn(i);

        try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
            workbook.write(fileOut);
        }
        workbook.close();
    }

    // 2. XUẤT BẢNG ĐIỂM TỔNG CỦA TOÀN LỚP (Dùng trong Quản lý lớp)
    public static void exportClassScoreTable(ClassRoom classRoom, String filePath) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Tổng kết " + classRoom.className);

        // Lấy danh sách tất cả các đề của lớp này
        List<String> examNames = DataManager.listSavedExams(classRoom.className);

        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);

        // Tạo Header cơ bản
        headerRow.createCell(0).setCellValue("STT");
        headerRow.getCell(0).setCellStyle(headerStyle);
        headerRow.createCell(1).setCellValue("Họ Tên");
        headerRow.getCell(1).setCellStyle(headerStyle);

        // Thêm tên các đề thi làm Header các cột tiếp theo
        int colIdx = 2;
        for (String examName : examNames) {
            Cell cell = headerRow.createCell(colIdx++);
            cell.setCellValue(examName);
            cell.setCellStyle(headerStyle);
        }

        // Load tất cả các session (đề thi) vào bộ nhớ
        Map<String, ExamSession> sessions = new HashMap<>();
        for (String examName : examNames) {
            sessions.put(examName, DataManager.loadSession(examName, classRoom.className));
        }

        int rowIdx = 1;
        for (ClassRoom.Student student : classRoom.students) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(student.stt);
            row.createCell(1).setCellValue(student.name);

            String sttStr = String.valueOf(student.stt);
            int scoreColIdx = 2;

            // Duyệt qua từng đề, tra cứu xem học sinh này được mấy điểm
            for (String examName : examNames) {
                ExamSession session = sessions.get(examName);
                boolean found = false;
                if (session != null && session.getReports() != null) {
                    for (ExamReport r : session.getReports()) {
                        if (r.studentId.equals(sttStr)) {
                            row.createCell(scoreColIdx).setCellValue(r.totalScore);
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    row.createCell(scoreColIdx).setCellValue("-"); // Nếu chưa làm bài
                }
                scoreColIdx++;
            }
        }

        for (int i = 0; i < colIdx; i++) sheet.autoSizeColumn(i);

        try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
            workbook.write(fileOut);
        }
        workbook.close();
    }

    // 3. XUẤT ĐÁP ÁN
    public static void exportAnswerKey(ExamConfig config, String filePath) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Đáp Án Chuẩn");

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Câu hỏi");
        headerRow.createCell(1).setCellValue("Đáp án");

        int rowIdx = 1;
        Map<String, String> answers = config.getAnswers();
        if (answers != null) {
            for (Map.Entry<String, String> entry : answers.entrySet()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(entry.getKey());
                row.createCell(1).setCellValue(entry.getValue());
            }
        }

        try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
            workbook.write(fileOut);
        }
        workbook.close();
    }
}