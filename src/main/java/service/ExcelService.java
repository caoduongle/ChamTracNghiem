package service;

import model.*;
import model.OMRModels.ExamReport;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.util.*;

public class ExcelService {

    // [CLEAN CODE]: Hàm tạo Style dùng chung để tiết kiệm RAM, tránh lỗi "Too many styles"
    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    // 1. XUẤT BẢNG ĐIỂM RIÊNG CỦA 1 ĐỀ
    public static void exportExamScoreTable(ClassRoom classRoom, ExamSession session, String filePath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Điểm " + session.getExamName());
            CellStyle headerStyle = createHeaderStyle(workbook);

            Row headerRow = sheet.createRow(0);
            String[] cols = {"STT", "Họ Tên", "Mã Đề", "Điểm"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
            }

            Map<String, ExamReport> reportMap = new HashMap<>();
            for (ExamReport r : session.getReports()) reportMap.put(r.studentId, r);

            int rowIdx = 1;
            for (ClassRoom.Student student : classRoom.students) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(student.stt);
                row.createCell(1).setCellValue(student.name);

                ExamReport r = reportMap.get(String.valueOf(student.stt));
                if (r != null) {
                    row.createCell(2).setCellValue(r.examCode);
                    row.createCell(3).setCellValue(r.totalScore);
                } else {
                    row.createCell(3).setCellValue("Vắng/Chưa chấm");
                }
            }

            for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);
            try (FileOutputStream out = new FileOutputStream(filePath)) { workbook.write(out); }
        }
    }

    // 2. [FIX]: KHÔI PHỤC HÀM XUẤT BẢNG ĐIỂM TỔNG CỦA TOÀN LỚP (Dùng trong Quản lý lớp)
    public static void exportClassScoreTable(ClassRoom classRoom, String filePath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Tổng kết " + classRoom.className);
            CellStyle headerStyle = createHeaderStyle(workbook);
            List<String> examNames = DataManager.listSavedExams(classRoom.className);

            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("STT");
            headerRow.getCell(0).setCellStyle(headerStyle);
            headerRow.createCell(1).setCellValue("Họ Tên");
            headerRow.getCell(1).setCellStyle(headerStyle);

            int colIdx = 2;
            for (String examName : examNames) {
                Cell cell = headerRow.createCell(colIdx++);
                cell.setCellValue(examName);
                cell.setCellStyle(headerStyle);
            }

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
                    if (!found) row.createCell(scoreColIdx).setCellValue("-");
                    scoreColIdx++;
                }
            }

            for (int i = 0; i < colIdx; i++) sheet.autoSizeColumn(i);
            try (FileOutputStream fileOut = new FileOutputStream(filePath)) { workbook.write(fileOut); }
        }
    }

    // 3. XUẤT ĐÁP ÁN (CẤU TRÚC 3 CỘT)
    public static void exportAnswerKey(ExamConfig config, String filePath) throws IOException {
        if (config == null) return;
        try (Workbook workbook = new XSSFWorkbook()) {
            String activeCode = config.getActiveCode();
            Sheet sheet = workbook.createSheet("Đáp án mã " + activeCode);
            CellStyle headerStyle = createHeaderStyle(workbook);

            Row headerRow = sheet.createRow(0);
            String[] headers = {"STT", "Phần", "Đáp án đúng (" + activeCode + ")"};
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (int i = 1; i <= config.getNumPart1(); i++) {
                Row r = sheet.createRow(rowIdx++);
                r.createCell(0).setCellValue(i);
                r.createCell(1).setCellValue("I");
                r.createCell(2).setCellValue(val(config.getAnswer("P1_Câu_" + i)));
            }
            for (int i = 1; i <= config.getNumPart2(); i++) {
                Row r = sheet.createRow(rowIdx++);
                r.createCell(0).setCellValue(i);
                r.createCell(1).setCellValue("II");
                String fullAns = String.format("%s%s%s%s",
                        val(config.getAnswer("P2_Câu_"+i+"_a")), val(config.getAnswer("P2_Câu_"+i+"_b")),
                        val(config.getAnswer("P2_Câu_"+i+"_c")), val(config.getAnswer("P2_Câu_"+i+"_d")));
                r.createCell(2).setCellValue(fullAns);
            }
            for (int i = 1; i <= config.getNumPart3(); i++) {
                Row r = sheet.createRow(rowIdx++);
                r.createCell(0).setCellValue(i);
                r.createCell(1).setCellValue("III");
                r.createCell(2).setCellValue(val(config.getAnswer("P3_Câu_" + i)));
            }

            for (int i = 0; i < 3; i++) sheet.autoSizeColumn(i);
            try (FileOutputStream out = new FileOutputStream(filePath)) { workbook.write(out); }
        }
    }

    private static String val(String s) { return s == null ? "" : s; }
}