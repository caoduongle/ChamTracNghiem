package model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ExamSession implements Serializable {
    private static final long serialVersionUID = 1L;

    private String examName;
    private ExamConfig config;
    private List<model.OMRModels.ExamReport> reports;

    public ExamSession(String examName, ExamConfig config) {
        this.examName = examName;
        this.config = config;
        this.reports = new ArrayList<>();
    }

    public String getExamName() { return examName; }
    public void setExamName(String examName) { this.examName = examName; }

    public ExamConfig getConfig() { return config; }
    public void setConfig(ExamConfig config) { this.config = config; }

    public List<model.OMRModels.ExamReport> getReports() { return reports; }
    public void addReport(model.OMRModels.ExamReport report) { this.reports.add(report); }
}