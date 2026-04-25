package service;

import model.ExamConfig;
import model.OMRModels.ExamReport;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ExcelService {

    // 1. XUẤT BẢNG ĐIỂM
    public static void exportScoreTable(List<ExamReport> reports, String filePath) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Bảng Điểm");

        // Tạo Header
        Row headerRow = sheet.createRow(0);
        String[] columns = {"STT", "Số báo danh", "Mã đề", "Tổng điểm", "Trạng thái"};

        CellStyle headerStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);

        for (int i = 0; i < columns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        // Đổ dữ liệu
        int rowIdx = 1;
        for (ExamReport report : reports) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(rowIdx - 1);
            row.createCell(1).setCellValue(report.studentId);
            row.createCell(2).setCellValue(report.examCode);
            row.createCell(3).setCellValue(report.totalScore);
            row.createCell(4).setCellValue("Thành công");
        }

        for (int i = 0; i < columns.length; i++) sheet.autoSizeColumn(i);

        try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
            workbook.write(fileOut);
        }
        workbook.close();
    }

    // 2. XUẤT BẢNG ĐÁP ÁN
    public static void exportAnswerKey(ExamConfig config, String filePath) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Đáp Án Chuẩn");

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Câu hỏi");
        headerRow.createCell(1).setCellValue("Đáp án");

        int rowIdx = 1;
        // Giả sử config.getAnswers() trả về Map<String, String> hoặc List đáp án
        Map<String, String> answers = config.getAnswers();
        for (Map.Entry<String, String> entry : answers.entrySet()) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue());
        }

        try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
            workbook.write(fileOut);
        }
        workbook.close();
    }
}