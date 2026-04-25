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
    private File selectedFolder; // Biến này giữ lại để dự phòng, nhưng ta sẽ dùng selectedFiles
    private ExamConfig currentConfig;
    private ExamSession currentSession;
    private Map<String, model.OMRModels.ExamReport> reportDatabase = new HashMap<>();
    private List<File> selectedFiles = new ArrayList<>(); // Danh sách chứa file ảnh đã chọn

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
                this.selectedFiles.clear();
                view.setVisible(false);
                showStartupMenu(false);
            }
        });

        view.getBtnSelectFolder().addActionListener(e -> selectFolder());

        // 1. SỰ KIỆN XÓA BÀI
        view.getBtnDeleteResult().addActionListener(e -> deleteSelectedReport());

        // 2. SỰ KIỆN THAY ĐỔI KIỂU SẮP XẾP
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
    }

    // 3. THAY THẾ HÀM CHỌN THƯ MỤC THÀNH CHỌN FILE/THƯ MỤC
    private void selectFolder() {
        JFileChooser fileChooser = new JFileChooser();
        // Cho phép chọn cả file và thư mục, và chọn nhiều file cùng lúc
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fileChooser.setMultiSelectionEnabled(true);

        if (fileChooser.showOpenDialog(view) == JFileChooser.APPROVE_OPTION) {
            selectedFiles.clear();
            File[] selected = fileChooser.getSelectedFiles();

            for (File f : selected) {
                if (f.isDirectory()) {
                    // Nếu chọn thư mục, lấy toàn bộ ảnh trong đó
                    File[] imgs = f.listFiles((dir, name) -> {
                        String low = name.toLowerCase();
                        return (low.endsWith(".jpg") || low.endsWith(".png")) && !low.contains("_processed");
                    });
                    if (imgs != null) {
                        for (File img : imgs) selectedFiles.add(img);
                    }
                } else {
                    // Nếu chọn file lẻ
                    String low = f.getName().toLowerCase();
                    if (low.endsWith(".jpg") || low.endsWith(".png")) {
                        selectedFiles.add(f);
                    }
                }
            }
            view.setStatusMessage("Đã chọn " + selectedFiles.size() + " file để chấm.");
        }
    }

    // 4. HÀM XÓA BÀI CHẤM
    private void deleteSelectedReport() {
        int row = view.getTblResults().getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(view, "Vui lòng chọn một hàng để xóa!");
            return;
        }

        String sbd = view.getTblResults().getValueAt(row, 1).toString();
        model.OMRModels.ExamReport report = reportDatabase.get(sbd);

        if (JOptionPane.showConfirmDialog(view, "Xóa kết quả và TẤT CẢ hình ảnh của SBD " + sbd + "?", "Xác nhận xóa sạch", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {

            // --- LOGIC XÓA FILE ẢNH ---
            if (report != null && report.imagePath != null) {
                try {
                    File fOriginal = new File(report.imagePath);
                    // Đường dẫn file processed thường là tên file gốc + _processed.jpg
                    File fProcessed = new File(report.imagePath.replace(".jpg", "_processed.jpg"));

                    java.nio.file.Files.deleteIfExists(fOriginal.toPath());
                    java.nio.file.Files.deleteIfExists(fProcessed.toPath());
                } catch (Exception ex) {
                    System.err.println("Không thể xóa file ảnh: " + ex.getMessage());
                }
            }

            // Xóa khỏi Database và Session
            reportDatabase.remove(sbd);
            if (currentSession != null) {
                currentSession.getReports().removeIf(r -> r.studentId.equals(sbd));
                service.DataManager.saveSession(currentSession);
            }
            refreshTable();
            view.setStatusMessage("Đã xóa sạch dữ liệu của SBD: " + sbd);
        }
    }

    // 5. HÀM LÀM MỚI BẢNG VÀ SẮP XẾP THÔNG MINH
    private void refreshTable() {
        if (currentSession == null) return;

        List<model.OMRModels.ExamReport> reports = new ArrayList<>(currentSession.getReports());
        int sortType = view.getCbxSortResults().getSelectedIndex();

        if (sortType == 1) { // Theo SBD
            reports.sort((a, b) -> a.studentId.compareTo(b.studentId));
        } else if (sortType == 2) { // Theo Điểm (Cao xuống thấp)
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

        // 1. Phần thông tin file và Nút điều khiển
        JPanel pnlHeader = new JPanel(new BorderLayout());
        File imgFile = new File(report.imagePath);
        JLabel lblFileName = new JLabel("  File gốc: " + imgFile.getName());
        lblFileName.setFont(new Font("SansSerif", Font.BOLD, 14));

        JButton btnViewProcessed = new JButton("🔍 Xem ảnh đã xử lý (Debug OMR)");
        pnlHeader.add(lblFileName, BorderLayout.WEST);
        pnlHeader.add(btnViewProcessed, BorderLayout.EAST);

        // 2. Bảng câu sai
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

        // 3. Khu vực hiển thị ảnh (Dùng nhãn để hiển thị)
        JLabel lblImage = new JLabel("", SwingConstants.CENTER);
        final String pathOriginal = report.imagePath;
        final String pathProcessed = report.imagePath.replace(".jpg", "_processed.jpg");

        // Hàm cập nhật ảnh vào Label
        java.util.function.Consumer<String> updateImage = (path) -> {
            File f = new File(path);
            if (f.exists()) {
                ImageIcon icon = new ImageIcon(path);
                Image img = icon.getImage();
                int newWidth = 700; // Tăng kích thước xem cho rõ
                int newHeight = (int) (icon.getIconHeight() * ((double) newWidth / icon.getIconWidth()));
                lblImage.setIcon(new ImageIcon(img.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH)));
                lblImage.setText("");
            } else {
                lblImage.setIcon(null);
                lblImage.setText("Không tìm thấy file: " + f.getName());
            }
        };

        updateImage.accept(pathOriginal); // Mặc định hiện ảnh gốc

        // Sự kiện nút bấm chuyển đổi ảnh
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
    // =========================================================
    // HÀM CHẤM BÀI (ĐÃ CẬP NHẬT ĐỂ CẬP NHẬT ĐIỂM & XÓA BÀI CŨ)
    // =========================================================
    private void startGradingProcess() {
        if (selectedFiles.isEmpty() || currentConfig == null) {
            JOptionPane.showMessageDialog(view, "Vui lòng chọn ảnh và cài đặt đáp án trước!", "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // --- BƯỚC 1: XÓA TRẮNG BẢNG GIAO DIỆN TRƯỚC KHI CHẤM ---
        DefaultTableModel model = (DefaultTableModel) view.getTblResults().getModel();
        model.setRowCount(0);

        SwingWorker<Void, Object[]> worker = new SwingWorker<Void, Object[]>() {
            @Override
            protected Void doInBackground() throws Exception {
                view.getBtnBackToMenu().setEnabled(false);
                view.getBtnStartGrading().setEnabled(false);
                view.getBtnSetAnswerKey().setEnabled(false);

                // Biến đếm STT bắt đầu lại từ 1
                int count = 1;

                for (File file : selectedFiles) {
                    try {
                        publish(new Object[]{"STATUS", "Đang xử lý: " + file.getName()});

                        Map<String, String> studentResults = OMRService.processExam(file.getAbsolutePath(), currentConfig);

                        if (studentResults != null) {
                            String sbd = studentResults.getOrDefault("STUDENT_ID", "LỖI_SBD");
                            String maDe = studentResults.getOrDefault("EXAM_CODE", "LỖI_MÃ");

                            model.OMRModels.ExamReport report = service.ScoringEngine.gradeExam(
                                    sbd, maDe, studentResults, currentConfig
                            );
                            double score = report.totalScore;

                            // Copy ảnh vào data để lưu trữ lâu dài
                            try {
                                File imageDir = new File("data/images/" + currentSession.getExamName());
                                if (!imageDir.exists()) imageDir.mkdirs();

                                File destFile = new File(imageDir, sbd + ".jpg");
                                java.nio.file.Files.copy(file.toPath(), destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                                report.imagePath = destFile.getAbsolutePath();
                            } catch (Exception ex) {
                                report.imagePath = file.getAbsolutePath();
                            }

                            // CẬP NHẬT DỮ LIỆU: Nếu trùng SBD sẽ tự động ghi đè điểm số mới
                            reportDatabase.put(sbd, report);

                            if (currentSession != null) {
                                // Xóa kết quả cũ của SBD này (nếu có) trước khi thêm kết quả mới
                                currentSession.getReports().removeIf(r -> r.studentId.equals(sbd));
                                currentSession.addReport(report);
                                service.DataManager.saveSession(currentSession);
                            }

                            String previewPath = file.getAbsolutePath().replace(".jpg", "_processed.jpg");
                            publish(new Object[]{"DATA", new Object[]{count, sbd, maDe, score, "Thành công"}, previewPath});
                        } else {
                            publish(new Object[]{"DATA", new Object[]{count, "ERR", "ERR", 0.0, "Lỗi nhận diện"}, null});
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                        String errorMsg = t.getClass().getSimpleName();
                        publish(new Object[]{"DATA", new Object[]{count, "LỖI", "LỖI", 0.0, "Crashed: " + errorMsg}, null});
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
                try {
                    get();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // --- BƯỚC 2: TỰ ĐỘNG XÓA BÀI KHÔNG CÒN TRONG DANH SÁCH CHỌN ---
                // Chúng ta sẽ đồng bộ lại session để chỉ giữ lại những bài vừa được chấm
                syncSessionWithSelectedFiles();

                view.getBtnBackToMenu().setEnabled(true);
                view.getBtnStartGrading().setEnabled(true);
                view.getBtnSetAnswerKey().setEnabled(true);
                view.setStatusMessage("Hoàn tất quy trình chạy " + selectedFiles.size() + " bài.");

                // Hiển thị lại bảng để áp dụng sắp xếp và dọn dẹp bài thừa
                refreshTable();

                JOptionPane.showMessageDialog(view, "Quy trình xử lý kết thúc! Kết quả đã được cập nhật.");
            }
        };

        worker.execute();
    }

    /**
     * Hàm hỗ trợ đồng bộ hóa: Loại bỏ những bài thi khỏi danh sách kết quả
     * nếu file ảnh gốc của chúng không còn nằm trong danh sách đang chọn.
     */
    private void syncSessionWithSelectedFiles() {
        if (currentSession == null) return;

        // Tạo danh sách đường dẫn tuyệt đối của các file đang được chọn
        List<String> validPaths = new ArrayList<>();
        for (File f : selectedFiles) {
            validPaths.add(f.getAbsolutePath());
        }

        // Loại bỏ những báo cáo trong Session mà file gốc không còn nằm trong validPaths
        // (Điều này giúp "tự động xóa các file không còn trong thư mục")
        currentSession.getReports().removeIf(report -> {
            File checkFile = new File(report.imagePath);
            // Nếu file ảnh đã bị xóa khỏi ổ cứng hoặc không nằm trong danh sách vừa chọn
            return !checkFile.exists();
        });

        service.DataManager.saveSession(currentSession);
    }
}