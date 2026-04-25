package model;

import java.io.Serializable;
import java.util.*;

public class OMRModels {

    public static class AnswerRecord implements Serializable {
        private static final long serialVersionUID = 1L; // Cố định phiên bản

        public String questionId;
        public String studentAnswer;
        public String correctAnswer;
        public boolean isCorrect;
        public double pointsAwarded;

        public AnswerRecord(String qId, String sAns, String cAns, double points) {
            this.questionId = qId;
            this.studentAnswer = sAns;
            this.correctAnswer = cAns;
            this.isCorrect = sAns.equals(cAns);
            this.pointsAwarded = this.isCorrect ? points : 0.0;
        }

        @Override
        public String toString() {
            return String.format("%-10s | %-10s | %-10s | %-6s | +%.2f",
                    questionId, studentAnswer, correctAnswer, (isCorrect ? "ĐÚNG" : "SAI"), pointsAwarded);
        }
    }

    public static class ExamReport implements Serializable {
        private static final long serialVersionUID = 1L; // Cố định phiên bản

        public String studentId;
        public String examCode;
        public double totalScore;
        public String imagePath;
        public List<AnswerRecord> details = new ArrayList<>();
        public String originalImagePath;
        public String studentName = "";
        public String studentSttFile = "";
        public String studentClass = "";
        public String statusMessage = "";

        public void printReport() {
            System.out.println("\n================= PHIẾU ĐIỂM ==================");
            System.out.println("SBD: " + studentId + " | Mã đề: " + examCode);
            System.out.println("-----------------------------------------------");
            System.out.println("Câu hỏi    | Trả lời    | Đáp án     | Kết quả| Điểm");
            System.out.println("-----------------------------------------------");
            for (AnswerRecord record : details) {
                System.out.println(record.toString());
            }
            System.out.println("-----------------------------------------------");
            System.out.printf("TỔNG ĐIỂM BÀI THI: %.2f\n", totalScore);
            System.out.println("===============================================\n");
        }
    }
}