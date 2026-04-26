package model;

import java.io.Serializable;
import java.util.*;

public class OMRModels {

    public static class AnswerRecord implements Serializable {
        private static final long serialVersionUID = 1L;

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
        private static final long serialVersionUID = 1L;

        public String studentId;
        public String examCode = "Mặc định"; // FIX: Gán giá trị chống Null
        public double totalScore;
        public String imagePath;
        public List<AnswerRecord> details = new ArrayList<>();
        public String originalImagePath;
        public String studentName = "";
        public String studentSttFile = "";
        public String studentClass = "";
        public String statusMessage = "";


    }
}