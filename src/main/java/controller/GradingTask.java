package controller;

import model.ClassRoom;
import model.ExamConfig;
import model.ExamSession;
import view.MainView;
import service.OMRService;

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
        view.getBtnBackToMenu().setEnabled(false);
        view.getBtnStartGrading().setEnabled(false);
        view.getBtnSetAnswerKey().setEnabled(false);
        view.getBtnStopGrading().setEnabled(true);

        int totalFiles = assignedFiles.size();
        AtomicInteger currentCount = new AtomicInteger(0);

        boolean useMultiThread = service.DataManager.isMultiThreadEnabled();
        boolean autoClean = service.DataManager.isAutoCleanProcessed();

        publish(new Object[]{"INIT_PROGRESS"});

        int coreCount = Runtime.getRuntime().availableProcessors();
        long maxMemoryMB = Runtime.getRuntime().maxMemory() / (1024 * 1024);

        int safeThreads = 1;
        if (useMultiThread) {
            if (maxMemoryMB < 1500) {
                safeThreads = Math.min(2, coreCount);
            } else if (maxMemoryMB < 3000) {
                safeThreads = Math.min(4, coreCount);
            } else {
                safeThreads = coreCount;
            }
        }
        safeThreads = Math.max(1, safeThreads);
        ExecutorService executor = Executors.newFixedThreadPool(safeThreads);

        List<Callable<Void>> tasks = new ArrayList<>();

        for (Map.Entry<String, File> entry : assignedFiles.entrySet()) {
            tasks.add(() -> {
                if (isCancelled()) return null;

                String stt = entry.getKey();
                File file = entry.getValue();

                try {
                    ExamConfig threadConfig = useMultiThread ? deepCloneConfig(currentConfig) : currentConfig;

                    String selectedCode = studentExamCodes.getOrDefault(stt, "Mặc định");
                    threadConfig.setActiveCode(selectedCode);

                    Map<String, String> studentResults = OMRService.processExam(file.getAbsolutePath(), threadConfig);

                    if (studentResults != null) {
                        String fName = "Chưa có tên";
                        for (model.ClassRoom.Student st : currentClassRoom.students) {
                            if (String.valueOf(st.stt).equals(stt)) { fName = st.name; break; }
                        }

                        boolean hasError = false;
                        boolean hasWarning = false;
                        List<String> errorList = new ArrayList<>();

                        for (Map.Entry<String, String> entryResult : studentResults.entrySet()) {
                            String val = entryResult.getValue();
                            String qName = entryResult.getKey()
                                    .replace("P1_", "Phần 1 - ")
                                    .replace("P2_", "Phần 2 - ")
                                    .replace("P3_", "Phần 3 - ")
                                    .replace("_", " ");

                            if (val.startsWith("ERR_")) {
                                hasError = true;
                                errorList.add(qName + " (Tô đúp)");
                                studentResults.put(entryResult.getKey(), "?");
                            } else if (val.startsWith("WARN_FMT_")) {
                                hasWarning = true;
                                errorList.add(qName + " (Lỗi Format)");
                                studentResults.put(entryResult.getKey(), val.substring(9));
                            } else if (val.startsWith("WARN_")) {
                                hasWarning = true;
                                errorList.add(qName + " (Tô mờ)");
                                studentResults.put(entryResult.getKey(), val.substring(5));
                            }
                        }

                        model.OMRModels.ExamReport newReport = service.ScoringEngine.gradeExam(
                                stt, "AUTO", studentResults, threadConfig
                        );

                        boolean hasWrongAnswers = false;
                        for (model.OMRModels.AnswerRecord detail : newReport.details) {
                            if (!detail.isCorrect) {
                                hasWrongAnswers = true;
                                break;
                            }
                        }

                        newReport.originalImagePath = file.getAbsolutePath();
                        newReport.studentName = fName;
                        newReport.studentSttFile = stt;
                        newReport.studentClass = currentClassRoom.className;
                        newReport.examCode = selectedCode;

                        String baseStatus = "";
                        if (hasError) {
                            baseStatus = "<html><span style=\"font-family: 'Segoe UI Emoji'\">❌</span> Lỗi: " + String.join(", ", errorList) + "</html>";
                        }
                        else if (hasWarning) {
                            baseStatus = "<html><span style=\"font-family: 'Segoe UI Emoji'\">⚠️</span> Nhắc: " + String.join(", ", errorList) + "</html>";
                        }
                        else {
                            baseStatus = "<html><span style=\"font-family: 'Segoe UI Emoji'\">✅</span> Thành công</html>";
                        }

                        newReport.statusMessage = baseStatus;

                        // =========================================================
                        // [BẢN VÁ AN TOÀN]: LOGIC COPY & XÓA FILE CHỐNG MẤT DỮ LIỆU
                        // =========================================================
                        try {
                            File imageDir = new File("data/classes/" + currentClassRoom.className + "/images/" + currentSession.getExamName());
                            if (!imageDir.exists()) imageDir.mkdirs();

                            String originalExt = ".jpg";
                            int extIndex = file.getName().lastIndexOf('.');
                            if (extIndex > 0) {
                                originalExt = file.getName().substring(extIndex);
                            }

                            File destFile = new File(imageDir, stt + originalExt);
                            File originalProcessed = new File(file.getAbsolutePath().replace(originalExt, "_processed" + originalExt));
                            File destProcessed = new File(imageDir, stt + "_processed" + originalExt);

                            // Kiểm tra xem file nguồn và file đích có bị trùng nhau không (Chấm lại bài cũ)
                            boolean isSameSourceAndDest = file.getAbsolutePath().equals(destFile.getAbsolutePath());
                            boolean isSameProcessed = originalProcessed.getAbsolutePath().equals(destProcessed.getAbsolutePath());

                            if (!isSameSourceAndDest) {
                                java.nio.file.Files.copy(file.toPath(), destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            }
                            newReport.imagePath = destFile.getAbsolutePath();

                            if (originalProcessed.exists()) {
                                // Nếu bài làm đúng 100% không tì vết -> Xóa rác
                                if (autoClean && !hasError && !hasWarning && !hasWrongAnswers) {
                                    originalProcessed.delete(); // Xóa temp ở Desktop
                                    if (!isSameProcessed && destProcessed.exists()) {
                                        destProcessed.delete(); // Xóa luôn file trong data nếu có
                                    }
                                } else {
                                    // Bắt buộc giữ lại file đối chiếu
                                    if (!isSameProcessed) {
                                        // Chỉ copy và xóa temp nếu kéo từ Desktop vào
                                        java.nio.file.Files.copy(originalProcessed.toPath(), destProcessed.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                        originalProcessed.delete();
                                    }
                                    // NẾU LÀ CÙNG 1 FILE (isSameProcessed = true): KHÔNG LÀM GÌ CẢ ĐỂ GIỮ NGUYÊN ẢNH!
                                }
                            }
                        } catch (Exception ex) {
                            newReport.imagePath = file.getAbsolutePath();
                        }

                        synchronized(reportDatabase) {
                            reportDatabase.put(stt, newReport);
                        }
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
                int percent = (completed * 100) / totalFiles;
                String modeText = useMultiThread ? " (Đa luồng CPU)" : "";
                publish(new Object[]{"STATUS", "Đang chấm" + modeText + ": " + completed + "/" + totalFiles + " bài...", percent});

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

    @Override
    protected void process(List<Object[]> chunks) {
        for (Object[] chunk : chunks) {
            if (chunk[0].equals("STATUS")) {
                view.setStatusMessage((String) chunk[1]);
                if (chunk.length > 2) {
                    view.getProgressBar().setValue((int) chunk[2]);
                }
            }
            else if (chunk[0].equals("INIT_PROGRESS")) {
                view.getProgressBar().setVisible(true);
                view.getProgressBar().setValue(0);
            }
            else if (chunk[0].equals("UPDATE")) {
                String stt = (String) chunk[1];
                assignedFiles.remove(stt);

                if (onUpdateTableCallback != null) {
                    onUpdateTableCallback.run();
                }
            }
        }
    }

    @Override
    protected void done() {
        try { get(); } catch (Exception e) {}
        view.getBtnBackToMenu().setEnabled(true);
        view.getBtnStartGrading().setEnabled(true);
        view.getBtnSetAnswerKey().setEnabled(true);
        view.getBtnStopGrading().setEnabled(false);

        view.getProgressBar().setVisible(false);

        assignedFiles.clear();

        if (onUpdateTableCallback != null) {
            onUpdateTableCallback.run();
        }

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