package controller;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.table.DefaultTableModel;
import java.util.ArrayList;
import java.util.HashMap;

import model.ExamSession;
import model.ClassRoom;
import view.MainView;
import view.AnswerKeyDialog;
import view.StartupDialog;
import view.ClassManagementDialog;
import model.ExamConfig;
import service.OMRService;
import service.DataManager;

import javax.swing.*;
import java.io.File;
import java.util.List;
import java.util.Map;

public class MainController {
    private MainView view;
    private ExamConfig currentConfig;
    private ExamSession currentSession;
    private ClassRoom currentClassRoom;

    private Map<String, model.OMRModels.ExamReport> reportDatabase = new HashMap<>();
    private Map<String, File> assignedFiles = new HashMap<>();
    private List<String> currentRowStts = new ArrayList<>();

    public MainController(MainView view) {
        this.view = view;
        initController();
        initApp();
    }

    public void initApp() {
        showClassMenu(true);
    }

    private void showClassMenu(boolean isFirstRun) {
        view.setVisible(false);
        ClassManagementDialog classDialog = new ClassManagementDialog(view);
        classDialog.setVisible(true);

        if (classDialog.getSelectedClass() != null) {
            this.currentClassRoom = classDialog.getSelectedClass();
            showStartupMenu(isFirstRun);
        } else {
            if (isFirstRun) System.exit(0);
        }
    }

    private void showStartupMenu(boolean isFirstRun) {
        // TRUYỀN TÊN LỚP VÀO ĐÂY
        StartupDialog startup = new StartupDialog(view, currentClassRoom.className);
        startup.setVisible(true);

        if (startup.getSelectedExam() != null) {
            if (startup.isNew()) {
                currentSession = new ExamSession(startup.getSelectedExam(), null);
                this.currentConfig = null;
            } else {
                // LOAD CŨNG TRUYỀN TÊN LỚP VÀO
                currentSession = DataManager.loadSession(startup.getSelectedExam(), currentClassRoom.className);
                if (currentSession != null) this.currentConfig = currentSession.getConfig();
            }
            view.setTitle("Phần mềm Chấm Thi | Lớp: " + currentClassRoom.className + " | Đề: " + currentSession.getExamName());
            view.setVisible(true);
            loadSessionToUI();
        } else {
            showClassMenu(isFirstRun);
        }
    }

    private void setupTableColumns() {
        DefaultTableModel model = (DefaultTableModel) view.getTblResults().getModel();
        model.setColumnIdentifiers(new Object[]{"STT", "Họ Tên Học Sinh", "File Ảnh Bài Làm", "Tổng điểm", "Trạng thái"});
    }

    private void loadSessionToUI() {
        if (currentSession != null && currentSession.getReports() != null) {
            for (model.OMRModels.ExamReport report : currentSession.getReports()) {
                reportDatabase.put(report.studentId, report);
            }
        }
        refreshTable();
    }

    private void refreshTable() {
        if (currentSession == null || currentClassRoom == null) return;
        setupTableColumns();
        DefaultTableModel model = (DefaultTableModel) view.getTblResults().getModel();
        model.setRowCount(0);
        currentRowStts.clear();

        for (ClassRoom.Student student : currentClassRoom.students) {
            String stt = String.valueOf(student.stt);
            currentRowStts.add(stt);

            model.OMRModels.ExamReport report = reportDatabase.get(stt);

            String fileName = "--- Chưa có ảnh ---";
            if (assignedFiles.containsKey(stt)) {
                fileName = "⏳ " + assignedFiles.get(stt).getName() + " (Chờ chấm)";
            } else if (report != null && report.originalImagePath != null) {
                fileName = "✅ " + new File(report.originalImagePath).getName();
            }

            String score = report != null ? String.valueOf(report.totalScore) : "";
            String status = report != null && report.statusMessage != null ? report.statusMessage : "Chưa chấm";

            view.addResultRow(new Object[]{stt, student.name, fileName, score, status});
        }
    }

    private void initController() {
        view.getBtnBackToMenu().addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(view, "Bạn muốn trở về màn hình Chọn Đề/Lớp?", "Xác nhận", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                view.clearView();
                this.currentSession = null;
                this.reportDatabase.clear();
                this.assignedFiles.clear();
                showStartupMenu(false);
            }
        });

        view.getBtnSelectFolder().addActionListener(e -> {
            JOptionPane.showMessageDialog(view, "Hãy CLICK ĐÚP CHUỘT vào tên học sinh trong bảng để gán ảnh bài làm!", "Hướng dẫn", JOptionPane.INFORMATION_MESSAGE);
        });

        view.getBtnDeleteResult().addActionListener(e -> deleteSelectedReport());
        view.getCbxSortResults().addActionListener(e -> refreshTable());

        view.getTblResults().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = ((JTable) e.getSource()).getSelectedRow();
                    if (row != -1) {
                        String stt = currentRowStts.get(row);
                        String name = (String) view.getTblResults().getValueAt(row, 1);
                        handleStudentDoubleClick(stt, name);
                    }
                }
            }
        });

        view.getBtnSetAnswerKey().addActionListener(e -> {
            AnswerKeyDialog dialog = new AnswerKeyDialog(view);
            if (this.currentConfig != null) dialog.loadConfig(this.currentConfig);
            dialog.getBtnSave().addActionListener(event -> {
                this.currentConfig = dialog.getExamConfig();
                if (currentSession != null) {
                    currentSession.setConfig(this.currentConfig);
                    // SAVE CÓ TRUYỀN TÊN LỚP
                    DataManager.saveSession(currentSession, currentClassRoom.className);
                }
                dialog.dispose();
                view.setStatusMessage("Đã lưu cấu hình: " + currentConfig.getTotalQuestions() + " câu.");
            });
            dialog.setVisible(true);
        });

        view.getBtnStartGrading().addActionListener(e -> startGradingProcess());
        view.getBtnExportScores().addActionListener(e -> saveExcel("BangDiem_" + currentSession.getExamName() + ".xlsx", 1));
        view.getBtnExportConfig().addActionListener(e -> saveExcel("DapAn_" + currentSession.getExamName() + ".xlsx", 2));
    }

    private void handleStudentDoubleClick(String stt, String name) {
        model.OMRModels.ExamReport report = reportDatabase.get(stt);
        if (report != null) {
            String[] options = {"🔍 Xem chi tiết", "📁 Gán lại ảnh khác", "Hủy"};
            int choice = JOptionPane.showOptionDialog(view,
                    "Học sinh " + name + " đã có điểm. Chọn thao tác:",
                    "Tùy chọn", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

            if (choice == 0) showWrongAnswersDialog(stt);
            else if (choice == 1) selectImageForStudent(stt, name);
        } else {
            selectImageForStudent(stt, name);
        }
    }

    private void selectImageForStudent(String stt, String name) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn ảnh bài làm cho học sinh: " + name);
        if (chooser.showOpenDialog(view) == JFileChooser.APPROVE_OPTION) {
            assignedFiles.put(stt, chooser.getSelectedFile());
            refreshTable();
        }
    }

    private void saveExcel(String defaultName, int type) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File(defaultName));
        if (fileChooser.showSaveDialog(view) == JFileChooser.APPROVE_OPTION) {
            try {
                String path = fileChooser.getSelectedFile().getAbsolutePath();
                if (!path.endsWith(".xlsx")) path += ".xlsx";
                if (type == 1) service.ExcelService.exportScoreTable(currentSession.getReports(), path);
                else service.ExcelService.exportAnswerKey(currentConfig, path);
                JOptionPane.showMessageDialog(view, "Xuất file thành công!");
            } catch (Exception ex) { ex.printStackTrace(); }
        }
    }

    private void deleteSelectedReport() {
        int row = view.getTblResults().getSelectedRow();
        if (row == -1) return;

        String stt = currentRowStts.get(row);
        model.OMRModels.ExamReport report = reportDatabase.get(stt);

        if (JOptionPane.showConfirmDialog(view, "Xóa kết quả chấm của học sinh này?", "Xác nhận", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            if (report != null && report.imagePath != null) {
                try {
                    java.nio.file.Files.deleteIfExists(new File(report.imagePath).toPath());
                    java.nio.file.Files.deleteIfExists(new File(report.imagePath.replace(".jpg", "_processed.jpg")).toPath());
                } catch (Exception ex) {}
            }

            reportDatabase.remove(stt);
            assignedFiles.remove(stt);

            if (currentSession != null) {
                currentSession.getReports().removeIf(r -> r.studentId.equals(stt));
                // XÓA THÌ PHẢI LƯU LẠI
                service.DataManager.saveSession(currentSession, currentClassRoom.className);
            }
            refreshTable();
        }
    }

    private void showWrongAnswersDialog(String stt) {
        model.OMRModels.ExamReport report = reportDatabase.get(stt);
        if (report == null) return;

        JDialog dialog = new JDialog(view, "Đối chiếu chi tiết", true);
        dialog.setSize(1200, 800);
        dialog.setLocationRelativeTo(view);

        JPanel pnlHeader = new JPanel(new BorderLayout());
        File imgFile = new File(report.originalImagePath != null ? report.originalImagePath : report.imagePath);
        JLabel lblFileName = new JLabel("  File gốc: " + imgFile.getName());
        lblFileName.setFont(new Font("SansSerif", Font.BOLD, 14));

        JButton btnViewProcessed = new JButton("🔍 Xem ảnh đã xử lý (Debug OMR)");
        pnlHeader.add(lblFileName, BorderLayout.WEST);
        pnlHeader.add(btnViewProcessed, BorderLayout.EAST);

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
        tblDetail.setForeground(Color.RED);

        JPanel pnlTable = new JPanel(new BorderLayout());
        pnlTable.add(new JScrollPane(tblDetail), BorderLayout.CENTER);
        pnlTable.add(new JLabel("  Tổng số ý sai: " + wrongCount, SwingConstants.LEFT), BorderLayout.SOUTH);

        JLabel lblImage = new JLabel("", SwingConstants.CENTER);
        final String pathOriginal = report.imagePath;
        final String pathProcessed = report.imagePath.replace(".jpg", "_processed.jpg");

        java.util.function.Consumer<String> updateImage = (path) -> {
            File f = new File(path);
            if (f.exists()) {
                ImageIcon icon = new ImageIcon(path);
                Image img = icon.getImage();
                int newWidth = 700;
                int newHeight = (int) (icon.getIconHeight() * ((double) newWidth / icon.getIconWidth()));
                lblImage.setIcon(new ImageIcon(img.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH)));
                lblImage.setText("");
            } else {
                lblImage.setIcon(null);
                lblImage.setText("Không tìm thấy file: " + f.getName());
            }
        };

        updateImage.accept(pathOriginal);

        btnViewProcessed.addActionListener(e -> {
            if (btnViewProcessed.getText().contains("Debug")) {
                updateImage.accept(pathProcessed);
                btnViewProcessed.setText("🖼 Xem lại ảnh gốc");
            } else {
                updateImage.accept(pathOriginal);
                btnViewProcessed.setText("🔍 Xem ảnh đã xử lý (Debug OMR)");
            }
        });

        JScrollPane scrollImage = new JScrollPane(lblImage);
        scrollImage.getVerticalScrollBar().setUnitIncrement(20);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollImage, pnlTable);
        splitPane.setDividerLocation(750);

        dialog.setLayout(new BorderLayout());
        dialog.add(pnlHeader, BorderLayout.NORTH);
        dialog.add(splitPane, BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    private void startGradingProcess() {
        if (assignedFiles.isEmpty()) {
            JOptionPane.showMessageDialog(view, "Chưa có ảnh nào được gán! Hãy click đúp vào tên học sinh để gán ảnh trước khi chấm.", "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (currentConfig == null) {
            JOptionPane.showMessageDialog(view, "Vui lòng cài đặt đáp án trước khi chấm!", "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
            return;
        }

        SwingWorker<Void, Object[]> worker = new SwingWorker<Void, Object[]>() {
            @Override
            protected Void doInBackground() throws Exception {
                view.getBtnBackToMenu().setEnabled(false);
                view.getBtnStartGrading().setEnabled(false);
                view.getBtnSetAnswerKey().setEnabled(false);

                for (Map.Entry<String, File> entry : assignedFiles.entrySet()) {
                    String stt = entry.getKey();
                    File file = entry.getValue();

                    try {
                        publish(new Object[]{"STATUS", "Đang chấm bài cho STT " + stt + "..."});

                        Map<String, String> studentResults = OMRService.processExam(file.getAbsolutePath(), currentConfig);

                        if (studentResults != null) {
                            String fName = "Chưa có tên";
                            for (model.ClassRoom.Student st : currentClassRoom.students) {
                                if (String.valueOf(st.stt).equals(stt)) { fName = st.name; break; }
                            }

                            model.OMRModels.ExamReport newReport = service.ScoringEngine.gradeExam(
                                    stt, "AUTO", studentResults, currentConfig
                            );

                            newReport.originalImagePath = file.getAbsolutePath();
                            newReport.studentName = fName;
                            newReport.studentSttFile = stt;
                            newReport.studentClass = currentClassRoom.className;
                            newReport.statusMessage = "Thành công";

                            try {
                                // TẠO THƯ MỤC ẢNH RIÊNG CHO LỚP
                                File imageDir = new File("data/classes/" + currentClassRoom.className + "/images/" + currentSession.getExamName());
                                if (!imageDir.exists()) imageDir.mkdirs();

                                File destFile = new File(imageDir, stt + ".jpg");
                                java.nio.file.Files.copy(file.toPath(), destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                newReport.imagePath = destFile.getAbsolutePath();

                                File originalProcessed = new File(file.getAbsolutePath().replace(".jpg", "_processed.jpg"));
                                if (originalProcessed.exists()) {
                                    File destProcessed = new File(imageDir, stt + "_processed.jpg");
                                    java.nio.file.Files.copy(originalProcessed.toPath(), destProcessed.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                    originalProcessed.delete();
                                }
                            } catch (Exception ex) {
                                newReport.imagePath = file.getAbsolutePath();
                            }

                            reportDatabase.put(stt, newReport);
                            if (currentSession != null) {
                                currentSession.getReports().removeIf(r -> r.studentId.equals(stt));
                                currentSession.addReport(newReport);
                                // CHẤM XONG LƯU VÀO FOLDER CỦA LỚP
                                service.DataManager.saveSession(currentSession, currentClassRoom.className);
                            }
                            publish(new Object[]{"UPDATE", stt});
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
                return null;
            }

            @Override
            protected void process(List<Object[]> chunks) {
                for (Object[] chunk : chunks) {
                    if (chunk[0].equals("STATUS")) view.setStatusMessage((String) chunk[1]);
                    else if (chunk[0].equals("UPDATE")) refreshTable();
                }
            }

            @Override
            protected void done() {
                try { get(); } catch (Exception e) {}
                view.getBtnBackToMenu().setEnabled(true);
                view.getBtnStartGrading().setEnabled(true);
                view.getBtnSetAnswerKey().setEnabled(true);

                assignedFiles.clear();
                refreshTable();

                view.setStatusMessage("Hoàn tất quy trình chấm bài.");
                JOptionPane.showMessageDialog(view, "Đã chấm xong! Dữ liệu đã được lưu riêng cho lớp " + currentClassRoom.className);
            }
        };

        worker.execute();
    }
}