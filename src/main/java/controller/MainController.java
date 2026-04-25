package controller;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.table.DefaultTableModel;
import java.util.ArrayList;
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
    private List<File> selectedFiles = new ArrayList<>();

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
            view.setVisible(true);
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
                this.selectedFiles.clear();
                view.setVisible(false);
                showStartupMenu(false);
            }
        });

        view.getBtnSelectFolder().addActionListener(e -> selectFolder());

        view.getBtnSelectAll().addActionListener(e -> view.getTblResults().selectAll());
        // SỰ KIỆN XÓA BÀI (Chỉ xóa file copy trong data)
        view.getBtnDeleteResult().addActionListener(e -> deleteSelectedReport());

        // SỰ KIỆN XÓA VĨNH VIỄN (Xóa cả file gốc A.png)
        view.getBtnDeletePermanent().addActionListener(e -> deletePermanentSelectedReport());

        view.getCbxSortResults().addActionListener(e -> refreshTable());

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

        view.getBtnExportScores().addActionListener(e -> {
            if (currentSession == null || currentSession.getReports().isEmpty()) {
                JOptionPane.showMessageDialog(view, "Chưa có dữ liệu để xuất!");
                return;
            }
            saveExcel("BangDiem_" + currentSession.getExamName() + ".xlsx", 1);
        });

        view.getBtnExportConfig().addActionListener(e -> {
            if (currentConfig == null) {
                JOptionPane.showMessageDialog(view, "Vui lòng lưu cấu hình đáp án trước!");
                return;
            }
            saveExcel("DapAn_" + currentSession.getExamName() + ".xlsx", 2);
        });
    }

    private void saveExcel(String defaultName, int type) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File(defaultName));
        if (fileChooser.showSaveDialog(view) == JFileChooser.APPROVE_OPTION) {
            try {
                String path = fileChooser.getSelectedFile().getAbsolutePath();
                if (!path.endsWith(".xlsx")) path += ".xlsx";

                if (type == 1) {
                    service.ExcelService.exportScoreTable(currentSession.getReports(), path);
                } else {
                    service.ExcelService.exportAnswerKey(currentConfig, path);
                }
                JOptionPane.showMessageDialog(view, "Xuất file thành công!");
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(view, "Lỗi khi xuất file: " + ex.getMessage());
            }
        }
    }

    private void selectFolder() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fileChooser.setMultiSelectionEnabled(true);

        if (fileChooser.showOpenDialog(view) == JFileChooser.APPROVE_OPTION) {
            selectedFiles.clear();
            File[] selected = fileChooser.getSelectedFiles();

            for (File f : selected) {
                if (f.isDirectory()) {
                    File[] imgs = f.listFiles((dir, name) -> {
                        String low = name.toLowerCase();
                        return (low.endsWith(".jpg") || low.endsWith(".png")) && !low.contains("_processed");
                    });
                    if (imgs != null) {
                        for (File img : imgs) selectedFiles.add(img);
                    }
                } else {
                    String low = f.getName().toLowerCase();
                    if (low.endsWith(".jpg") || low.endsWith(".png")) {
                        selectedFiles.add(f);
                    }
                }
            }
            view.setStatusMessage("Đã chọn " + selectedFiles.size() + " file để chấm.");
        }
    }



    // =======================================================
    // CẬP NHẬT: XÓA THƯỜNG NHIỀU BÀI CÙNG LÚC
    // =======================================================
    private void deleteSelectedReport() {
        int[] selectedRows = view.getTblResults().getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(view, "Vui lòng chọn ít nhất một hàng để xóa! (Giữ Ctrl/Shift để chọn nhiều)");
            return;
        }

        if (JOptionPane.showConfirmDialog(view, "Xóa " + selectedRows.length + " bài đã chọn?", "Xác nhận", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {

            // BƯỚC QUAN TRỌNG: Lấy danh sách SBD trước khi xóa
            // (Nếu xóa trực tiếp theo index, bảng sẽ bị co lại làm sai index các hàng tiếp theo)
            List<String> sbdsToDelete = new ArrayList<>();
            for (int row : selectedRows) {
                sbdsToDelete.add(view.getTblResults().getValueAt(row, 1).toString());
            }

            for (String sbd : sbdsToDelete) {
                model.OMRModels.ExamReport report = reportDatabase.get(sbd);
                if (report != null && report.imagePath != null) {
                    try {
                        Files.deleteIfExists(new File(report.imagePath).toPath());
                        Files.deleteIfExists(new File(report.imagePath.replace(".jpg", "_processed.jpg")).toPath());
                    } catch (Exception ex) {
                        System.err.println("Lỗi khi xóa file: " + ex.getMessage());
                    }
                }
                reportDatabase.remove(sbd);
                if (currentSession != null) {
                    currentSession.getReports().removeIf(r -> r.studentId.equals(sbd));
                }
            }

            if (currentSession != null) service.DataManager.saveSession(currentSession);
            refreshTable();
            view.setStatusMessage("Đã xóa " + sbdsToDelete.size() + " bài thi.");
        }
    }

    // =======================================================
    // CẬP NHẬT: XÓA VĨNH VIỄN NHIỀU BÀI CÙNG LÚC
    // =======================================================
    private void deletePermanentSelectedReport() {
        int[] selectedRows = view.getTblResults().getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(view, "Vui lòng chọn ít nhất một hàng để xóa vĩnh viễn!");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(view,
                "⚠️ NGUY HIỂM: Bạn đang chuẩn bị xóa VĨNH VIỄN " + selectedRows.length + " bài làm.\n\n" +
                        "Thao tác này sẽ xóa sạch dữ liệu trên phần mềm VÀ XÓA BỎ LUÔN FILE ẢNH GỐC ở ngoài thư mục.\n" +
                        "Bạn có chắc chắn không?", "Cảnh báo Xóa Hàng Loạt", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            List<String> sbdsToDelete = new ArrayList<>();
            for (int row : selectedRows) {
                sbdsToDelete.add(view.getTblResults().getValueAt(row, 1).toString());
            }

            for (String sbd : sbdsToDelete) {
                model.OMRModels.ExamReport report = reportDatabase.get(sbd);
                if (report != null) {
                    try {
                        if (report.originalImagePath != null) {
                            Files.deleteIfExists(new File(report.originalImagePath).toPath());
                            // Xóa khỏi danh sách file đang duyệt để tránh lỗi
                            selectedFiles.removeIf(f -> f.getAbsolutePath().equals(report.originalImagePath));
                        }
                        if (report.imagePath != null) {
                            Files.deleteIfExists(new File(report.imagePath).toPath());
                            Files.deleteIfExists(new File(report.imagePath.replace(".jpg", "_processed.jpg")).toPath());
                        }
                    } catch (Exception ex) {}
                }
                reportDatabase.remove(sbd);
                if (currentSession != null) {
                    currentSession.getReports().removeIf(r -> r.studentId.equals(sbd));
                }
            }

            if (currentSession != null) service.DataManager.saveSession(currentSession);
            refreshTable();
            view.setStatusMessage("Đã tiêu diệt vĩnh viễn " + sbdsToDelete.size() + " bài thi.");
        }
    }
    private void refreshTable() {
        if (currentSession == null) return;

        List<model.OMRModels.ExamReport> reports = new ArrayList<>(currentSession.getReports());
        int sortType = view.getCbxSortResults().getSelectedIndex();

        if (sortType == 1) {
            reports.sort((a, b) -> a.studentId.compareTo(b.studentId));
        } else if (sortType == 2) {
            reports.sort((a, b) -> Double.compare(b.totalScore, a.totalScore));
        }

        DefaultTableModel model = (DefaultTableModel) view.getTblResults().getModel();
        model.setRowCount(0);
        int count = 1;
        for (model.OMRModels.ExamReport r : reports) {
            Object[] rowData = {count++, r.studentId, r.examCode, r.totalScore, "Đã lưu"};
            view.addResultRow(rowData);
        }
    }

    private void showWrongAnswersDialog(String sbd) {
        model.OMRModels.ExamReport report = reportDatabase.get(sbd);
        if (report == null) return;

        JDialog dialog = new JDialog(view, "Đối chiếu chi tiết - SBD: " + sbd, true);
        dialog.setSize(1200, 800);
        dialog.setLocationRelativeTo(view);

        JPanel pnlHeader = new JPanel(new BorderLayout());

        // ĐÃ CẢI TIẾN: Hiển thị tên file gốc (A.png) thay vì SBD.jpg
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

    private int findRowIndex(String sbd, String maDe) {
        DefaultTableModel model = (DefaultTableModel) view.getTblResults().getModel();
        for (int i = 0; i < model.getRowCount(); i++) {
            String tableSbd = model.getValueAt(i, 1).toString();
            String tableMaDe = model.getValueAt(i, 2).toString();
            if (tableSbd.equals(sbd) && tableMaDe.equals(maDe)) {
                return i;
            }
        }
        return -1;
    }

    private void startGradingProcess() {
        if (selectedFiles.isEmpty() || currentConfig == null) {
            JOptionPane.showMessageDialog(view, "Vui lòng chọn ảnh và cài đặt đáp án trước!", "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
            return;
        }

        SwingWorker<Void, Object[]> worker = new SwingWorker<Void, Object[]>() {
            @Override
            protected Void doInBackground() throws Exception {
                view.getBtnBackToMenu().setEnabled(false);
                view.getBtnStartGrading().setEnabled(false);
                view.getBtnSetAnswerKey().setEnabled(false);

                for (File file : selectedFiles) {
                    try {
                        publish(new Object[]{"STATUS", "Đang xử lý: " + file.getName()});

                        Map<String, String> studentResults = OMRService.processExam(file.getAbsolutePath(), currentConfig);

                        if (studentResults != null) {
                            String sbd = studentResults.getOrDefault("STUDENT_ID", "LỖI_SBD");
                            String maDe = studentResults.getOrDefault("EXAM_CODE", "LỖI_MÃ");

                            model.OMRModels.ExamReport newReport = service.ScoringEngine.gradeExam(
                                    sbd, maDe, studentResults, currentConfig
                            );

                            // LƯU ĐƯỜNG DẪN FILE GỐC VÀO REPORT
                            newReport.originalImagePath = file.getAbsolutePath();

                            double newScore = newReport.totalScore;
                            model.OMRModels.ExamReport oldReport = reportDatabase.get(sbd);

                            boolean isExisting = (oldReport != null && oldReport.examCode.equals(maDe));
                            boolean scoreChanged = isExisting && (oldReport.totalScore != newScore);

                            // Đã fix lỗi Logic: So sánh đường dẫn gốc cũ và mới thay vì so sánh tên SBD
                            boolean fileChanged = isExisting && oldReport.originalImagePath != null && !oldReport.originalImagePath.equals(file.getAbsolutePath());

                            if (isExisting && !scoreChanged && !fileChanged) {
                                continue;
                            }

                            try {
                                File imageDir = new File("data/images/" + currentSession.getExamName());
                                if (!imageDir.exists()) imageDir.mkdirs();

                                File destFile = new File(imageDir, sbd + ".jpg");
                                java.nio.file.Files.copy(file.toPath(), destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                newReport.imagePath = destFile.getAbsolutePath();

                                File originalProcessed = new File(file.getAbsolutePath().replace(".jpg", "_processed.jpg"));
                                if (originalProcessed.exists()) {
                                    File destProcessed = new File(imageDir, sbd + "_processed.jpg");
                                    java.nio.file.Files.copy(originalProcessed.toPath(), destProcessed.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                    originalProcessed.delete();
                                }
                            } catch (Exception ex) {
                                newReport.imagePath = file.getAbsolutePath();
                                System.err.println("Lỗi copy ảnh: " + ex.getMessage());
                            }

                            reportDatabase.put(sbd, newReport);

                            if (currentSession != null) {
                                currentSession.getReports().removeIf(r -> r.studentId.equals(sbd) && r.examCode.equals(maDe));
                                currentSession.addReport(newReport);
                                service.DataManager.saveSession(currentSession);
                            }

                            String previewPath = newReport.imagePath.replace(".jpg", "_processed.jpg");

                            if (isExisting) {
                                publish(new Object[]{"UPDATE", sbd, maDe, newScore, "Đã cập nhật", previewPath});
                            } else {
                                int nextStt = view.getTblResults().getRowCount() + 1;
                                publish(new Object[]{"DATA", new Object[]{nextStt, sbd, maDe, newScore, "Thành công"}, previewPath});
                            }
                        } else {
                            int nextStt = view.getTblResults().getRowCount() + 1;
                            publish(new Object[]{"DATA", new Object[]{nextStt, "ERR", "ERR", 0.0, "Lỗi nhận diện"}, null});
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                        String errorMsg = t.getClass().getSimpleName();
                        int nextStt = view.getTblResults().getRowCount() + 1;
                        publish(new Object[]{"DATA", new Object[]{nextStt, "LỖI", "LỖI", 0.0, "Crashed: " + errorMsg}, null});
                    }
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
                    } else if (type.equals("UPDATE")) {
                        String sbd = (String) chunk[1];
                        String maDe = (String) chunk[2];
                        double score = (double) chunk[3];
                        String status = (String) chunk[4];
                        String imagePath = (String) chunk[5];

                        int rowIndex = findRowIndex(sbd, maDe);
                        if (rowIndex != -1) {
                            DefaultTableModel model = (DefaultTableModel) view.getTblResults().getModel();
                            model.setValueAt(score, rowIndex, 3);
                            model.setValueAt(status, rowIndex, 4);
                        }

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
                try {
                    get();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                view.getBtnBackToMenu().setEnabled(true);
                view.getBtnStartGrading().setEnabled(true);
                view.getBtnSetAnswerKey().setEnabled(true);
                view.setStatusMessage("Hoàn tất quy trình chạy " + selectedFiles.size() + " bài.");

                refreshTable();

                JOptionPane.showMessageDialog(view, "Quy trình xử lý kết thúc! Các thay đổi đã được cập nhật.");
            }
        };

        worker.execute();
    }
}