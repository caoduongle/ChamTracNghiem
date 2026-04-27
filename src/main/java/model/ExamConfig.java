package model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ExamConfig implements Serializable {
    private static final long serialVersionUID = 2L;

    private int numPart1, numPart2, numPart3;
    private double scoreP1, scoreP2_1, scoreP2_2, scoreP2_3, scoreP2_4, scoreP3;

    private Map<String, Map<String, String>> answersByCode = new HashMap<>();
    private String activeCode = "Mặc định";

    public ExamConfig(int p1, int p2, int p3) {
        this.numPart1 = p1;
        this.numPart2 = p2;
        this.numPart3 = p3;
        addExamCode("Mặc định");
    }

    // [GETTERS/SETTERS]
    public int getNumPart1() { return numPart1; }
    public int getNumPart2() { return numPart2; }
    public int getNumPart3() { return numPart3; }

    public void setScoreP1(double s) { scoreP1 = s; }
    public void setScoreP2_1(double s) { scoreP2_1 = s; }
    public void setScoreP2_2(double s) { scoreP2_2 = s; }
    public void setScoreP2_3(double s) { scoreP2_3 = s; }
    public void setScoreP2_4(double s) { scoreP2_4 = s; }
    public void setScoreP3(double s) { scoreP3 = s; }

    public double getScoreP1() { return scoreP1; }
    public double getScoreP2_1() { return scoreP2_1; }
    public double getScoreP2_2() { return scoreP2_2; }
    public double getScoreP2_3() { return scoreP2_3; }
    public double getScoreP2_4() { return scoreP2_4; }
    public double getScoreP3() { return scoreP3; }

    // [QUẢN LÝ ĐA MÃ ĐỀ]
    public Set<String> getExamCodes() { return answersByCode.keySet(); }

    public void addExamCode(String code) {
        if (code != null && !code.isEmpty()) {
            answersByCode.putIfAbsent(code, new HashMap<>());
        }
    }

    public void removeExamCode(String code) {
        if (!"Mặc định".equals(code)) answersByCode.remove(code);
    }

    public String getActiveCode() { return activeCode; }

    public void setActiveCode(String code) {
        if (code != null && answersByCode.containsKey(code)) {
            this.activeCode = code;
        } else {
            this.activeCode = "Mặc định";
        }
    }

    // [GET/SET ĐÁP ÁN THEO ACTIVE CODE]
    public void setAnswer(String questionId, String answer) {
        Map<String, String> currentAnswers = answersByCode.get(activeCode);
        if (currentAnswers != null) currentAnswers.put(questionId, answer);
    }

    public String getAnswer(String questionId) {
        Map<String, String> currentAnswers = answersByCode.get(activeCode);
        return currentAnswers != null ? currentAnswers.get(questionId) : null;
    }

    public Map<String, String> getAnswers() {
        return answersByCode.getOrDefault(activeCode, new HashMap<>());
    }
}