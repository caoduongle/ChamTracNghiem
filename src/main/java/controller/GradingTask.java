package controller;

import model.ClassRoom;
import model.ExamConfig;
import model.ExamSession;
import view.MainView;
import service.OMRService;
import util.FileUtil;

import javax.swing.*;
import java.awt.Toolkit;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class GradingTask extends SwingWorker<Void, Object[]> {
    private MainView view;
    private ExamConfig currentConfig;
    private ExamSession currentSession;
    private ClassRoom currentClassRoom;
    private Map<String, model.OMRModels.ExamReport> reportDatabase;
    private Map<String, File> assignedFiles;
    private Map<String, String> studentExamCodes;
    private Runnable onUpdateTableCallback;

    public GradingTask(MainView view, ExamConfig currentConfig, ExamSession currentSession,
                       ClassRoom currentClassRoom, Map<String, model.OMRModels.ExamReport> reportDatabase,
                       Map<String, File> assignedFiles, Map<String, String> studentExamCodes,
                       Runnable onUpdateTableCallback) {
        this.view = view;
        this.currentConfig = currentConfig;
        this.currentSession = currentSession;
        this.currentClassRoom = currentClassRoom;
        this.reportDatabase = reportDatabase;
        this.assignedFiles = assignedFiles;
        this.studentExamCodes = studentExamCodes;
        this.onUpdateTableCallback = onUpdateTableCallback;
    }

    private ExamConfig deepCloneConfig(ExamConfig original) {
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(bos);
            oos.writeObject(original);
            oos.flush();
            java.io.ObjectInputStream ois = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bos.toByteArray()));
            return (ExamConfig) ois.readObject();
        } catch (Exception e) {
            return original;
        }
    }

    @Override
    protected Void doInBackground() throws Exception {
        toggleUIComponents(false);
        publish(new Object[]{"INIT_PROGRESS"});

        int totalFiles = assignedFiles.size();
        AtomicInteger currentCount = new AtomicInteger(0);
        boolean useMultiThread = service.DataManager.isMultiThreadEnabled();
        boolean autoClean = service.DataManager.isAutoCleanProcessed();

        ExecutorService executor = Executors.newFixedThreadPool(calculateSafeThreadCount(useMultiThread));
        List<Callable<Void>> tasks = new ArrayList<>();

        for (Map.Entry<String, File> entry : assignedFiles.entrySet()) {
            tasks.add(() -> {
                if (isCancelled()) return null;
                processSingleExam(entry.getKey(), entry.getValue(), useMultiThread, autoClean, currentCount, totalFiles);
                return null;
            });
        }

        try {
            executor.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
        }

        if (currentSession != null) {
            service.DataManager.saveSession(currentSession, currentClassRoom.className);
        }

        return null;
    }

    // ===================================================================================
    // CÁC HÀM HELPER ĐƯỢC TÁCH RA ĐỂ TUÂN THỦ CLEAN CODE (SRP & DRY)
    // ===================================================================================

    private void processSingleExam(String stt, File file, boolean useMultiThread, boolean autoClean, AtomicInteger currentCount, int totalFiles) {
        try {
            ExamConfig threadConfig = useMultiThread ? deepCloneConfig(currentConfig) : currentConfig;
            String selectedCode = studentExamCodes.getOrDefault(stt, "Mặc định");
            threadConfig.setActiveCode(selectedCode);

            Map<String, String> studentResults = OMRService.processExam(file.getAbsolutePath(), threadConfig);

            if (studentResults != null) {
                // 1. Phân tích kết quả, lọc lỗi và cảnh báo
                ValidationResult validation = validateStudentResults(studentResults);

                // 2. Chấm điểm
                model.OMRModels.ExamReport newReport = service.ScoringEngine.gradeExam(stt, "AUTO", studentResults, threadConfig);

                // 3. Kiểm tra xem bài làm có bị sai câu nào không
                boolean isPerfect = !validation.hasError && !validation.hasWarning && checkPerfectScore(newReport);

                // 4. Gọi FileUtil để xử lý copy/xóa ảnh (Thay thế cho đoạn code dài ngoằng cũ)
                String savedImagePath = FileUtil.handleGradedExamFiles(
                        file, stt, currentClassRoom.className, currentSession.getExamName(), autoClean, isPerfect
                );

                // 5. Cập nhật thông tin báo cáo
                newReport.originalImagePath = file.getAbsolutePath();
                newReport.imagePath = savedImagePath;
                newReport.studentName = getStudentName(stt);
                newReport.studentSttFile = stt;
                newReport.studentClass = currentClassRoom.className;
                newReport.examCode = selectedCode;
                newReport.statusMessage = generateStatusMessage(validation);

                // 6. Lưu vào Database
                synchronized(reportDatabase) { reportDatabase.put(stt, newReport); }
                synchronized(currentSession) {
                    currentSession.getReports().removeIf(r -> r.studentId.equals(stt));
                    currentSession.addReport(newReport);
                }

                publish(new Object[]{"UPDATE", stt});
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

        int completed = currentCount.incrementAndGet();
        publish(new Object[]{"STATUS", "Đang chấm" + (useMultiThread ? " (Đa luồng)" : "") + ": " + completed + "/" + totalFiles + " bài...", (completed * 100) / totalFiles});
    }

    private ValidationResult validateStudentResults(Map<String, String> studentResults) {
        ValidationResult res = new ValidationResult();
        for (Map.Entry<String, String> entryResult : studentResults.entrySet()) {
            String val = entryResult.getValue();
            String qName = entryResult.getKey()
                    .replace("P1_", "Phần 1 - ").replace("P2_", "Phần 2 - ").replace("P3_", "Phần 3 - ").replace("_", " ");

            if (val.startsWith("ERR_")) {
                res.hasError = true;
                res.errorList.add(qName + " (Tô đúp)");
                studentResults.put(entryResult.getKey(), "?");
            } else if (val.startsWith("WARN_FMT_")) {
                res.hasWarning = true;
                res.errorList.add(qName + " (Lỗi Format)");
                studentResults.put(entryResult.getKey(), val.substring(9));
            } else if (val.startsWith("WARN_")) {
                res.hasWarning = true;
                res.errorList.add(qName + " (Tô mờ)");
                studentResults.put(entryResult.getKey(), val.substring(5));
            }
        }
        return res;
    }

    private boolean checkPerfectScore(model.OMRModels.ExamReport report) {
        for (model.OMRModels.AnswerRecord detail : report.details) {
            if (!detail.isCorrect) return false;
        }
        return true;
    }

    private String getStudentName(String stt) {
        for (model.ClassRoom.Student st : currentClassRoom.students) {
            if (String.valueOf(st.stt).equals(stt)) return st.name;
        }
        return "Chưa có tên";
    }

    private String generateStatusMessage(ValidationResult validation) {
        if (validation.hasError) return "<html><span style=\"font-family: 'Segoe UI Emoji'\">❌</span> Lỗi: " + String.join(", ", validation.errorList) + "</html>";
        if (validation.hasWarning) return "<html><span style=\"font-family: 'Segoe UI Emoji'\">⚠️</span> Nhắc: " + String.join(", ", validation.errorList) + "</html>";
        return "<html><span style=\"font-family: 'Segoe UI Emoji'\">✅</span> Thành công</html>";
    }

    private int calculateSafeThreadCount(boolean useMultiThread) {
        int coreCount = Runtime.getRuntime().availableProcessors();
        if (!useMultiThread) return 1;
        long maxMemoryMB = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        if (maxMemoryMB < 1500) return Math.min(2, coreCount);
        if (maxMemoryMB < 3000) return Math.min(4, coreCount);
        return coreCount;
    }

    private void toggleUIComponents(boolean isGradingFinished) {
        view.getBtnBackToMenu().setEnabled(isGradingFinished);
        view.getBtnStartGrading().setEnabled(isGradingFinished);
        view.getBtnSetAnswerKey().setEnabled(isGradingFinished);
        view.getBtnStopGrading().setEnabled(!isGradingFinished);
        if (isGradingFinished) view.getProgressBar().setVisible(false);
    }

    // Class nội bộ lưu trữ kết quả phân tích lỗi
    private static class ValidationResult {
        boolean hasError = false;
        boolean hasWarning = false;
        List<String> errorList = new ArrayList<>();
    }

    // ===================================================================================
    // UI UPDATES (SwingWorker Methods)
    // ===================================================================================

    @Override
    protected void process(List<Object[]> chunks) {
        for (Object[] chunk : chunks) {
            if (chunk[0].equals("STATUS")) {
                view.setStatusMessage((String) chunk[1]);
                if (chunk.length > 2) view.getProgressBar().setValue((int) chunk[2]);
            }
            else if (chunk[0].equals("INIT_PROGRESS")) {
                view.getProgressBar().setVisible(true);
                view.getProgressBar().setValue(0);
            }
            else if (chunk[0].equals("UPDATE")) {
                String stt = (String) chunk[1];
                assignedFiles.remove(stt);
                if (onUpdateTableCallback != null) onUpdateTableCallback.run();
            }
        }
    }

    @Override
    protected void done() {
        try { get(); } catch (Exception e) {}

        toggleUIComponents(true);
        assignedFiles.clear();
        if (onUpdateTableCallback != null) onUpdateTableCallback.run();

        if (isCancelled()) {
            view.setStatusMessage("Đã dừng chấm bài theo yêu cầu.");
            JOptionPane.showMessageDialog(view, "Tiến trình chấm bài đã bị dừng!");
        } else {
            view.setStatusMessage("Hoàn tất quy trình chấm bài đa mã đề.");
            JOptionPane.showMessageDialog(view, "Đã chấm xong! Dữ liệu đã được lưu riêng cho lớp " + currentClassRoom.className);
        }

        if (!isCancelled() && service.DataManager.getAutoCleanupMode() == 2) {
            service.DataManager.performSilentDeepCleanup();
        }
        if (!isCancelled() && service.DataManager.isSoundEnabled()) {
            Toolkit.getDefaultToolkit().beep();
        }
    }
} // KẾT THÚC CLASS TẠI ĐÂY, KHÔNG ĐỂ MÃ NÀO LỌT RA NGOÀI DẤU NGOẶC NÀY NỮA

