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

    // [ĐÃ THÊM]: Biến lưu tên mẫu phiếu được chọn từ giao diện
    private String templateId;

    private Runnable onUpdateTableCallback;

    // [ĐÃ SỬA]: Thêm tham số String templateId vào Constructor
    public GradingTask(MainView view, ExamConfig currentConfig, ExamSession currentSession,
                       ClassRoom currentClassRoom, Map<String, model.OMRModels.ExamReport> reportDatabase,
                       Map<String, File> assignedFiles, Map<String, String> studentExamCodes,
                       String templateId,
                       Runnable onUpdateTableCallback) {
        this.view = view;
        this.currentConfig = currentConfig;
        this.currentSession = currentSession;
        this.currentClassRoom = currentClassRoom;
        this.reportDatabase = reportDatabase;
        this.assignedFiles = assignedFiles;
        this.studentExamCodes = studentExamCodes;
        this.templateId = templateId; // Lưu lại mẫu phiếu
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

            Map<String, String> studentResults = OMRService.processExam(file.getAbsolutePath(), threadConfig, this.templateId);

            if (studentResults != null) {
                ValidationResult validation = validateStudentResults(studentResults);
                model.OMRModels.ExamReport newReport = service.ScoringEngine.gradeExam(stt, "AUTO", studentResults, threadConfig);
                boolean isPerfect = !validation.hasError && !validation.hasWarning && checkPerfectScore(newReport);

                String savedImagePath = FileUtil.handleGradedExamFiles(
                        file, stt, currentClassRoom.className, currentSession.getExamName(), autoClean, isPerfect
                );

                newReport.originalImagePath = file.getAbsolutePath();
                newReport.imagePath = savedImagePath;
                newReport.studentName = getStudentName(stt);
                newReport.studentSttFile = stt;
                newReport.studentClass = currentClassRoom.className;
                newReport.examCode = selectedCode;
                newReport.statusMessage = generateStatusMessage(validation);

                synchronized(reportDatabase) { reportDatabase.put(stt, newReport); }
                synchronized(currentSession) {
                    currentSession.getReports().removeIf(r -> r.studentId.equals(stt));
                    currentSession.addReport(newReport);
                }
                publish(new Object[]{"UPDATE", stt});

            } else {
                // =====================================================================
                // SỬA LỖI ĐIỂM MA: Tạo đối tượng Report MỚI TINH để reset điểm về 0
                // =====================================================================
                model.OMRModels.ExamReport failedReport = new model.OMRModels.ExamReport();
                failedReport.studentId = stt;
                failedReport.originalImagePath = file.getAbsolutePath();
                failedReport.imagePath = file.getAbsolutePath();
                failedReport.studentName = getStudentName(stt);
                failedReport.studentSttFile = stt;
                failedReport.studentClass = currentClassRoom.className;
                failedReport.examCode = selectedCode;

                failedReport.statusMessage = "<html><span style=\"font-family: 'Segoe UI Emoji'; color: #D32F2F; font-weight: bold;\">🚨 LỖI ẢNH: Không tìm thấy 4 góc</span></html>";

                synchronized(reportDatabase) { reportDatabase.put(stt, failedReport); }
                synchronized(currentSession) {
                    currentSession.getReports().removeIf(r -> r.studentId.equals(stt));
                    currentSession.addReport(failedReport);
                }
                publish(new Object[]{"UPDATE", stt});
            }
        } catch (Throwable t) {
            t.printStackTrace();
            // =====================================================================
            // SỬA LỖI KẸT "CHỜ CHẤM": Bắt quả tang Crash ngầm và báo lên UI
            // =====================================================================
            model.OMRModels.ExamReport crashReport = new model.OMRModels.ExamReport();
            crashReport.studentId = stt;
            crashReport.originalImagePath = file.getAbsolutePath();
            crashReport.imagePath = file.getAbsolutePath();
            crashReport.studentName = getStudentName(stt);
            crashReport.studentSttFile = stt;
            crashReport.studentClass = currentClassRoom.className;

            crashReport.statusMessage = "<html><span style=\"font-family: 'Segoe UI Emoji'; color: #FF8C00; font-weight: bold;\">⚠️ LỖI ĐỌC ẢNH (" + t.getClass().getSimpleName() + ")</span></html>";

            synchronized(reportDatabase) { reportDatabase.put(stt, crashReport); }
            synchronized(currentSession) {
                currentSession.getReports().removeIf(r -> r.studentId.equals(stt));
                currentSession.addReport(crashReport);
            }
            publish(new Object[]{"UPDATE", stt});
        }

        int completed = currentCount.incrementAndGet();
        publish(new Object[]{"STATUS", "Đang chấm" + (useMultiThread ? " (Đa luồng)" : "") + ": " + completed + "/" + totalFiles + " bài...", (completed * 100) / totalFiles});
    }

    private ValidationResult validateStudentResults(Map<String, String> studentResults) {
        ValidationResult res = new ValidationResult();
        int totalQuestions = studentResults.size();
        int garbageCount = 0; // Bộ đếm số lượng câu không đọc được

        for (Map.Entry<String, String> entryResult : studentResults.entrySet()) {
            String val = entryResult.getValue();
            String qName = entryResult.getKey()
                    .replace("P1_", "Phần 1 - ").replace("P2_", "Phần 2 - ").replace("P3_", "Phần 3 - ").replace("_", " ");

            // Nếu câu bị trống (do khung quét rơi vào vùng giấy trắng)
            if (val.equals("?")) {
                garbageCount++;
            }
            else if (val.startsWith("ERR_")) {
                res.hasError = true;
                res.errorList.add(qName + " (Tô đúp)");
                studentResults.put(entryResult.getKey(), "?");
                garbageCount++; // Lỗi đúp (thường do khung đè lên vạch kẻ đen)
            }
            else if (val.startsWith("WARN_FMT_")) {
                res.hasWarning = true;
                res.errorList.add(qName + " (Lỗi Format)");
                studentResults.put(entryResult.getKey(), val.substring(9));
            }
            else if (val.startsWith("WARN_")) {
                res.hasWarning = true;
                res.errorList.add(qName + " (Tô mờ)");
                studentResults.put(entryResult.getKey(), val.substring(5));
            }
        }

        // [LOGIC BẮT BỆNH SAI MẪU PHIẾU]:
        // Nếu số lượng câu hỏi rác (trống/lỗi) chiếm hơn 50% tổng số câu -> Báo động đỏ!
        if (totalQuestions > 0 && ((double) garbageCount / totalQuestions) >= 0.5) {
            res.isWrongTemplate = true;
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
        if (validation.isWrongTemplate) {
            return "<html><span style=\"font-family: 'Segoe UI Emoji'; color: #D32F2F; font-weight: bold;\">🚨 SAI MẪU PHIẾU (Hoặc giấy trắng)</span></html>";
        }
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
        boolean isWrongTemplate = false; // <--- [MỚI]: Cờ báo hiệu sai mẫu phiếu
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
        try { get(); } catch (Exception e) {} // Nuốt ngoại lệ để không bung hộp thoại xấu xí ra màn hình

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
}