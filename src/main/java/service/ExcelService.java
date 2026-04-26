package service;

import model.ExamConfig;
import model.ExamSession;
import model.ClassRoom;
import model.OMRModels.ExamReport;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExcelService {

    // 1. XUẤT BẢNG ĐIỂM RIÊNG CỦA 1 ĐỀ (Thêm cột Mã Đề)
    public static void exportExamScoreTable(ClassRoom classRoom, ExamSession session, String filePath) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Điểm " + session.getExamName());

        Row headerRow = sheet.createRow(0);
        String[] columns = {"STT", "Họ Tên", "Mã Đề", "Điểm Đạt Được"};

        CellStyle headerStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);

        for (int i = 0; i < columns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        Map<String, ExamReport> reportMap = new HashMap<>();
        if (session.getReports() != null) {
            for (ExamReport r : session.getReports()) {
                reportMap.put(r.studentId, r);
            }
        }

        int rowIdx = 1;
        for (ClassRoom.Student student : classRoom.students) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(student.stt);
            row.createCell(1).setCellValue(student.name);

            String sttStr = String.valueOf(student.stt);
            if (reportMap.containsKey(sttStr)) {
                ExamReport r = reportMap.get(sttStr);
                row.createCell(2).setCellValue(r.examCode != null ? r.examCode : "Mặc định");
                row.createCell(3).setCellValue(r.totalScore);
            } else {
                row.createCell(2).setCellValue("-");
                row.createCell(3).setCellValue("Chưa chấm");
            }
        }

        for (int i = 0; i < columns.length; i++) sheet.autoSizeColumn(i);

        try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
            workbook.write(fileOut);
        }
        workbook.close();
    }

    // 2. XUẤT BẢNG ĐIỂM TỔNG CỦA TOÀN LỚP (Thêm mã đề vào bên cạnh điểm)
    public static void exportClassScoreTable(ClassRoom classRoom, String filePath) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Tổng kết " + classRoom.className);

        List<String> examNames = DataManager.listSavedExams(classRoom.className);

        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);

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
                            String codeStr = (r.examCode != null && !r.examCode.equals("Mặc định")) ? " [" + r.examCode + "]" : "";
                            row.createCell(scoreColIdx).setCellValue(r.totalScore + codeStr);
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    row.createCell(scoreColIdx).setCellValue("-");
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

    // 3. XUẤT ĐÁP ÁN (Dàn hàng ngang cho TẤT CẢ các mã đề)
    public static void exportAnswerKey(ExamConfig config, String filePath) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Đáp Án Chuẩn");

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Câu hỏi");

        List<String> codes = new ArrayList<>();
        if (config != null && config.getExamCodes() != null) {
            codes.addAll(config.getExamCodes());
        }

        // Tạo tiêu đề các cột mã đề
        for (int i = 0; i < codes.size(); i++) {
            headerRow.createCell(i + 1).setCellValue("Mã: " + codes.get(i));
        }

        int rowIdx = 1;
        List<String> allQuestions = new ArrayList<>();
        for(int i=1; i<=config.getNumPart1(); i++) allQuestions.add("P1_Câu_"+i);
        for(int i=1; i<=config.getNumPart2(); i++) {
            allQuestions.add("P2_Câu_"+i+"_a"); allQuestions.add("P2_Câu_"+i+"_b");
            allQuestions.add("P2_Câu_"+i+"_c"); allQuestions.add("P2_Câu_"+i+"_d");
        }
        for(int i=1; i<=config.getNumPart3(); i++) allQuestions.add("P3_Câu_"+i);

        // Xuất dữ liệu từng câu theo từng mã
        for (String qId : allQuestions) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(qId);
            for (int i = 0; i < codes.size(); i++) {
                config.setActiveCode(codes.get(i));
                String ans = config.getAnswer(qId);
                row.createCell(i + 1).setCellValue(ans != null ? ans : "");
            }
        }
        config.setActiveCode("Mặc định"); // Trả về an toàn

        for (int i = 0; i <= codes.size(); i++) sheet.autoSizeColumn(i);

        try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
            workbook.write(fileOut);
        }
        workbook.close();
    }
}