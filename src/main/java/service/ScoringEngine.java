package service;

import model.OMRModels;
import model.ExamConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScoringEngine {

    public static OMRModels.ExamReport gradeExam(
            String sbd, String maDe,
            Map<String, String> studentAnswers,
            ExamConfig config) { // --- SỬA THAM SỐ NHẬN VÀO LÀ CẢ CẤU HÌNH ---

        Map<String, String> answerKey = config.getAnswers();
        double totalScore = 0.0;
        int p1Correct = 0;
        int p3Correct = 0;

        Map<Integer, Integer> p2CorrectCounts = new HashMap<>();
        List<OMRModels.AnswerRecord> detailList = new ArrayList<>();

        for (Map.Entry<String, String> entry : answerKey.entrySet()) {
            String qName = entry.getKey();
            String correctAns = entry.getValue();
            String studentAns = studentAnswers.getOrDefault(qName, "?");

            if (qName.startsWith("P1_")) {
                boolean isCorrect = correctAns.equals(studentAns);
                if (isCorrect) {
                    p1Correct++;
                    totalScore += config.getScoreP1(); // DÙNG ĐIỂM TỪ CẤU HÌNH
                }
                detailList.add(new OMRModels.AnswerRecord(qName, studentAns, correctAns, isCorrect ? config.getScoreP1() : 0));
            }
            else if (qName.startsWith("P2_")) {
                String[] parts = qName.split("_");
                if (parts.length >= 4) {
                    int qNum = Integer.parseInt(parts[2]);
                    p2CorrectCounts.putIfAbsent(qNum, 0);

                    boolean isCorrect = correctAns.equals(studentAns);
                    if (isCorrect) p2CorrectCounts.put(qNum, p2CorrectCounts.get(qNum) + 1);

                    detailList.add(new OMRModels.AnswerRecord(qName, studentAns, correctAns, 0));
                }
            }
            else if (qName.startsWith("P3_")) {
                String cleanStudent = studentAns.replace("?", "").replace(",", ".").trim();
                String cleanCorrect = correctAns.replace(",", ".").trim();

                boolean isCorrect = cleanCorrect.equals(cleanStudent) && !cleanStudent.isEmpty();
                if (isCorrect) {
                    p3Correct++;
                    totalScore += config.getScoreP3(); // DÙNG ĐIỂM TỪ CẤU HÌNH
                }
                detailList.add(new OMRModels.AnswerRecord(qName, cleanStudent, cleanCorrect, isCorrect ? config.getScoreP3() : 0));
            }
        }

        // --- TÍNH ĐIỂM PHẦN II TỪ CẤU HÌNH ---
        double p2TotalScore = 0.0;
        for (int correctCount : p2CorrectCounts.values()) {
            if (correctCount == 1) p2TotalScore += config.getScoreP2_1();
            else if (correctCount == 2) p2TotalScore += config.getScoreP2_2();
            else if (correctCount == 3) p2TotalScore += config.getScoreP2_3();
            else if (correctCount == 4) p2TotalScore += config.getScoreP2_4();
        }
        totalScore += p2TotalScore;

        totalScore = Math.round(totalScore * 100.0) / 100.0;

        OMRModels.ExamReport report = new OMRModels.ExamReport();
        report.studentId = sbd;
        report.examCode = maDe;
        report.totalScore = totalScore;
        report.details = detailList;

        return report;
    }
}