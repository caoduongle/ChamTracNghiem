package controller;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.table.DefaultTableModel;
import java.util.HashMap;

import model.ExamSession;
import view.MainView;
import view.AnswerKeyDialog;
import view.StartupDialog;
import model.ExamConfig;
import service.OMRService;
import service.DataManager;

import javax.swing.*;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class MainController {
    private MainView view;
    private File selectedFolder;
    private ExamConfig currentConfig;
    private ExamSession currentSession;
    private Map<String, model.OMRModels.ExamReport> reportDatabase = new HashMap<>();

    public MainController(MainView view) {
        this.view = view;
        initController();
        initApp();
    }

    public void initApp() {
        showStartupMenu(true);
    }

    private void showStartupMenu(boolean isFirstRun) {
        StartupDialog startup = new StartupDialog(view);
        startup.setVisible(true);

        if (startup.getSelectedExam() != null) {
            if (startup.isNew()) {
                currentSession = new ExamSession(startup.getSelectedExam(), null);
                this.currentConfig = null;
            } else {
                currentSession = DataManager.loadSession(startup.getSelectedExam());
                if (currentSession == null) {
                    JOptionPane.showMessageDialog(null, "Lỗi: Không thể đọc file dữ liệu. File có thể đã bị hỏng!", "Lỗi Dữ Liệu", JOptionPane.ERROR_MESSAGE);
                    if (isFirstRun) System.exit(0);
                    else { showStartupMenu(false); return; }
                }
                this.currentConfig = currentSession.getConfig();
                loadSessionToUI();
            }
            view.setTitle("Phần mềm Chấm Trắc Nghiệm - Team N7 | Đề: " + currentSession.getExamName());
            view.setVisible(true); // Mở giao diện chính
        } else {
            if (isFirstRun) {
                System.exit(0);
            }
        }
    }

    private void loadSessionToUI() {
        DefaultTableModel model = (DefaultTableModel) view.getTblResults().getModel();
        model.setRowCount(0);
        reportDatabase.clear();

        if (currentSession.getReports() == null || currentSession.getReports().isEmpty()) return;

        int count = 1;
        for (model.OMRModels.ExamReport report : currentSession.getReports()) {
            reportDatabase.put(report.studentId, report);
            Object[] rowData = {count++, report.studentId, report.examCode, report.totalScore, "Đã chấm (Lịch sử)"};
            view.addResultRow(rowData);
        }
        view.setStatusMessage("Đã tải lịch sử: " + currentSession.getReports().size() + " bài thi.");
    }

    private void initController() {
        view.getBtnBackToMenu().addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(view,
                    "Bạn có chắc chắn muốn trở về Menu chính không?",
                    "Xác nhận", JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                view.clearView();
                this.currentSession = null;
                this.currentConfig = null;
                this.selectedFolder = null;
                this.reportDatabase.clear();
                view.setVisible(false);
                showStartupMenu(false);
            }
        });

        view.getBtnSelectFolder().addActionListener(e -> selectFolder());

        view.getTblResults().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    JTable target = (JTable) e.getSource();
                    int row = target.getSelectedRow();
                    if (row != -1) {
                        String sbd = target.getValueAt(row, 1).toString();
                        showWrongAnswersDialog(sbd);
                    }
                }
            }
        });

        view.getBtnSetAnswerKey().addActionListener(e -> {
            AnswerKeyDialog dialog = new AnswerKeyDialog(view);
            if (this.currentConfig != null) {
                dialog.loadConfig(this.currentConfig);
            }
            dialog.getBtnSave().addActionListener(event -> {
                this.currentConfig = dialog.getExamConfig();
                if (currentSession != null) {
                    currentSession.setConfig(this.currentConfig);
                    DataManager.saveSession(currentSession);
                }
                dialog.dispose();
                view.setStatusMessage("Đã lưu cấu hình: " + currentConfig.getTotalQuestions() + " câu.");
            });
            dialog.setVisible(true);
        });

        view.getBtnStartGrading().addActionListener(e -> startGradingProcess());
    }

    private void selectFolder() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fileChooser.showOpenDialog(view) == JFileChooser.APPROVE_OPTION) {
            selectedFolder = fileChooser.getSelectedFile();
            view.setStatusMessage("Thư mục: " + selectedFolder.getName());
        }
    }

    private void showWrongAnswersDialog(String sbd) {
        model.OMRModels.ExamReport report = reportDatabase.get(sbd);
        if (report == null) return;

        JDialog dialog = new JDialog(view, "Đối chiếu bài làm - SBD: " + sbd, true);
        dialog.setSize(1100, 750);
        dialog.setLocationRelativeTo(view);

        String[] cols = {"Câu hỏi", "Đ/A Học sinh", "Đ/A Chuẩn"};
        DefaultTableModel detailModel = new DefaultTableModel(cols, 0);
        int wrongCount = 0;
        for (model.OMRModels.AnswerRecord rec : report.details) {
            if (!rec.isCorrect) {
                detailModel.addRow(new Object[]{rec.questionId, rec.studentAnswer, rec.correctAnswer});
                wrongCount++;
            }
        }
        JTable tblDetail = new JTable(detailModel);
        tblDetail.setForeground(java.awt.Color.RED);

        JPanel pnlTable = new JPanel(new BorderLayout());
        pnlTable.add(new JScrollPane(tblDetail), BorderLayout.CENTER);
        pnlTable.add(new JLabel("  Tổng số ý sai: " + wrongCount, SwingConstants.LEFT), BorderLayout.SOUTH);

        JLabel lblImage = new JLabel("Không tìm thấy ảnh gốc", SwingConstants.CENTER);
        if (report.imagePath != null && new File(report.imagePath).exists()) {
            ImageIcon icon = new ImageIcon(report.imagePath);
            Image img = icon.getImage();
            int newWidth = 650;
            int newHeight = (int) (icon.getIconHeight() * ((double) newWidth / icon.getIconWidth()));
            lblImage.setIcon(new ImageIcon(img.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH)));
            lblImage.setText("");
        }

        JScrollPane scrollImage = new JScrollPane(lblImage);
        scrollImage.getVerticalScrollBar().setUnitIncrement(16);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollImage, pnlTable);
        splitPane.setDividerLocation(700);

        dialog.add(splitPane);
        dialog.setVisible(true);
    }

    private void startGradingProcess() {
        if (selectedFolder == null || currentConfig == null) {
            JOptionPane.showMessageDialog(view, "Vui lòng chọn thư mục và cài đặt đáp án trước!", "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File[] imageFiles = selectedFolder.listFiles((dir, name) -> {
            String lowerName = name.toLowerCase();
            return (lowerName.endsWith(".jpg") || lowerName.endsWith(".png"))
                    && !lowerName.contains("_nan_phang")
                    && !lowerName.contains("_processed");
        });

        if (imageFiles == null || imageFiles.length == 0) {
            view.setStatusMessage("Không tìm thấy ảnh bài thi hợp lệ!");
            return;
        }

        SwingWorker<Void, Object[]> worker = new SwingWorker<Void, Object[]>() {
            @Override
            protected Void doInBackground() throws Exception {
                view.getBtnBackToMenu().setEnabled(false);
                view.getBtnStartGrading().setEnabled(false);
                view.getBtnSetAnswerKey().setEnabled(false);

                int count = view.getTblResults().getRowCount() + 1;

                for (File file : imageFiles) {
                    publish(new Object[]{"STATUS", "Đang xử lý: " + file.getName()});

                    Map<String, String> studentResults = OMRService.processExam(file.getAbsolutePath(), currentConfig);

                    if (studentResults != null) {
                        String sbd = studentResults.get("STUDENT_ID");
                        String maDe = studentResults.get("EXAM_CODE");

                        model.OMRModels.ExamReport report = service.ScoringEngine.gradeExam(
                                sbd, maDe, studentResults, currentConfig
                        );
                        double score = report.totalScore;

                        try {
                            File imageDir = new File("data/images/" + currentSession.getExamName());
                            if (!imageDir.exists()) imageDir.mkdirs();

                            File destFile = new File(imageDir, sbd + ".jpg");
                            Files.copy(file.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                            report.imagePath = destFile.getAbsolutePath();
                        } catch (Exception ex) {
                            report.imagePath = file.getAbsolutePath();
                        }

                        reportDatabase.put(sbd, report);

                        if (currentSession != null) {
                            currentSession.getReports().removeIf(r -> r.studentId.equals(sbd));
                            currentSession.addReport(report);
                            DataManager.saveSession(currentSession);
                        }

                        String previewPath = file.getAbsolutePath().replace(".jpg", "_processed.jpg");
                        publish(new Object[]{"DATA", new Object[]{count, sbd, maDe, score, "Thành công"}, previewPath});
                    } else {
                        publish(new Object[]{"DATA", new Object[]{count, "ERR", "ERR", 0.0, "Lỗi nhận diện"}, null});
                    }
                    count++;
                }
                return null;
            }

            @Override
            protected void process(List<Object[]> chunks) {
                for (Object[] chunk : chunks) {
                    String type = (String) chunk[0];
                    if (type.equals("STATUS")) {
                        view.setStatusMessage((String) chunk[1]);
                    } else if (type.equals("DATA")) {
                        view.addResultRow((Object[]) chunk[1]);
                        String imagePath = (String) chunk[2];
                        if (imagePath != null && new File(imagePath).exists()) {
                            ImageIcon icon = new ImageIcon(new ImageIcon(imagePath).getImage()
                                    .getScaledInstance(400, 500, Image.SCALE_SMOOTH));
                            view.setImagePreview(icon);
                        }
                    }
                }
            }

            @Override
            protected void done() {
                view.getBtnBackToMenu().setEnabled(true);
                view.getBtnStartGrading().setEnabled(true);
                view.getBtnSetAnswerKey().setEnabled(true);
                view.setStatusMessage("Hoàn thành chấm " + imageFiles.length + " bài.");
                JOptionPane.showMessageDialog(view, "Đã chấm xong và lưu dữ liệu thành công!");
            }
        };

        worker.execute();
    }
}