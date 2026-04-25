package model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class ExamConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private int numPart1, numPart2, numPart3;
    private Map<String, String> answers = new HashMap<>();

    // --- CÁC BIẾN LƯU BAREM ĐIỂM (Mặc định chuẩn 2025) ---
    private double scoreP1 = 0.25;
    private double scoreP3 = 0.25;
    private double scoreP2_1 = 0.1;
    private double scoreP2_2 = 0.25;
    private double scoreP2_3 = 0.5;
    private double scoreP2_4 = 1.0;

    public ExamConfig(int p1, int p2, int p3) {
        this.numPart1 = p1;
        this.numPart2 = p2;
        this.numPart3 = p3;
    }

    public int getTotalQuestions() { return numPart1 + numPart2 + numPart3; }

    public void setAnswer(String questionId, String ans) { answers.put(questionId, ans); }
    public String getAnswer(String questionId) { return answers.get(questionId); }
    public Map<String, String> getAnswers() { return answers; }

    public int getNumPart1() { return numPart1; }
    public int getNumPart2() { return numPart2; }
    public int getNumPart3() { return numPart3; }

    // --- GETTER & SETTER CHO BAREM ĐIỂM ---
    public double getScoreP1() { return scoreP1; }
    public void setScoreP1(double scoreP1) { this.scoreP1 = scoreP1; }

    public double getScoreP3() { return scoreP3; }
    public void setScoreP3(double scoreP3) { this.scoreP3 = scoreP3; }

    public double getScoreP2_1() { return scoreP2_1; }
    public void setScoreP2_1(double scoreP2_1) { this.scoreP2_1 = scoreP2_1; }

    public double getScoreP2_2() { return scoreP2_2; }
    public void setScoreP2_2(double scoreP2_2) { this.scoreP2_2 = scoreP2_2; }

    public double getScoreP2_3() { return scoreP2_3; }
    public void setScoreP2_3(double scoreP2_3) { this.scoreP2_3 = scoreP2_3; }

    public double getScoreP2_4() { return scoreP2_4; }
    public void setScoreP2_4(double scoreP2_4) { this.scoreP2_4 = scoreP2_4; }
}