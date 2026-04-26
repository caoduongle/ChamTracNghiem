package controller;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import model.ClassRoom;
import model.ExamConfig;
import model.ExamSession;
import service.DataManager;
import view.AnswerKeyDialog;
import view.ClassManagementDialog;
import view.MainView;
import view.StartupDialog;
import view.WrongAnswerDialog;

public class MainController {
    private MainView view;
    private ExamConfig currentConfig;
    private ExamSession currentSession;
    private ClassRoom currentClassRoom;
    private ResultTableManager tableManager;

    private Map<String, model.OMRModels.ExamReport> reportDatabase = new HashMap<>();
    private Map<String, File> assignedFiles = new HashMap<>();
    private List<String> currentRowStts = new ArrayList<>();
    private Map<String, String> studentExamCodes = new HashMap<>();
    private SwingWorker<Void, Object[]> gradingWorker;

    public MainController(MainView view) {
        this.view = view;
        this.tableManager = new ResultTableManager(view);
        initController();
        initApp();

        if (DataManager.getAutoCleanupMode() == 1) {
            DataManager.performSilentDeepCleanup();
        }
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
        view.setVisible(false);
        StartupDialog startup = new StartupDialog(view, currentClassRoom.className);
        startup.setVisible(true);

        if (startup.isGoBackToClass()) {
            showClassMenu(isFirstRun);
            return;
        }

        if (startup.getSelectedExam() != null) {
            if (startup.isNew()) {
                currentSession = new ExamSession(startup.getSelectedExam(), null);
                this.currentConfig = null;
            } else {
                currentSession = DataManager.loadSession(startup.getSelectedExam(), currentClassRoom.className);
                if (currentSession != null) this.currentConfig = currentSession.getConfig();
            }
            view.setTitle("Phần mềm Chấm Thi | Lớp: " + currentClassRoom.className + " | Đề: " + currentSession.getExamName());
            view.setVisible(true);
            loadSessionToUI();
        } else {
            System.exit(0);
        }
    }

    private void loadSessionToUI() {
        if (currentSession != null && currentSession.getReports() != null) {
            for (model.OMRModels.ExamReport report : currentSession.getReports()) {
                reportDatabase.put(report.studentId, report);
            }
        }
        // [FIX] Cập nhật lại Dropdown mã đề vào bảng mỗi khi tải phiên mới
        if (currentConfig != null && currentConfig.getExamCodes() != null) {
            tableManager.updateExamCodeEditor(currentConfig.getExamCodes());
        }
        refreshTable();
    }

    private void refreshTable() {
        tableManager.refresh(currentSession, currentClassRoom, reportDatabase, assignedFiles, studentExamCodes, currentRowStts);
    }

    private void initController() {
        view.getTblResults().getModel().addTableModelListener(e -> {
            if (e.getColumn() == 3) {
                int row = e.getFirstRow();
                if (row != -1 && row < currentRowStts.size()) {
                    String stt = currentRowStts.get(row);
                    String selectedCode = (String) view.getTblResults().getValueAt(row, 3);

                    studentExamCodes.put(stt, selectedCode);
                    model.OMRModels.ExamReport report = reportDatabase.get(stt);
                    if (report != null && selectedCode != null && !selectedCode.equals(report.examCode)) {
                        report.examCode = selectedCode;
                        report.statusMessage = "<html><span style=\"font-family: 'Segoe UI Emoji'\">⚠️</span> Đã đổi mã, cần chấm lại</html>";

                        if (currentSession != null) {
                            service.DataManager.saveSession(currentSession, currentClassRoom.className);
                        }
                        SwingUtilities.invokeLater(() -> view.getTblResults().setValueAt(report.statusMessage, row, 5));
                    }
                }
            }
        });

        view.getBtnBackToMenu().addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(view, "Bạn muốn lưu và trở về màn hình Chọn Đề?", "Xác nhận", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                view.clearView();
                view.setVisible(false);
                this.currentSession = null;
                this.reportDatabase.clear();
                this.assignedFiles.clear();
                showStartupMenu(false);
            }
        });

        view.getBtnDeleteResult().addActionListener(e -> deleteSelectedReport());
        view.getCbxSortResults().addActionListener(e -> refreshTable());

        view.getBtnDashboard().addActionListener(e -> {
            if (currentSession == null || currentSession.getReports().isEmpty()) {
                JOptionPane.showMessageDialog(view, "Chưa có bài thi nào được chấm để thống kê!");
                return;
            }
            new view.DashboardDialog(view, currentSession).setVisible(true);
        });

        DragDropHandler.applyDropTarget(view.getTblResults(), (validFiles, dropPoint) -> {
            if (validFiles.size() == 1) {
                int row = view.getTblResults().rowAtPoint(dropPoint);
                if (row != -1) {
                    String stt = currentRowStts.get(row);
                    assignedFiles.put(stt, validFiles.get(0));
                    refreshTable();
                    return;
                }
            }

            int autoCount = 0;
            for (File file : validFiles) {
                String rawName = file.getName().substring(0, file.getName().lastIndexOf('.'));
                if (currentRowStts.contains(rawName)) {
                    assignedFiles.put(rawName, file);
                    autoCount++;
                }
            }
            refreshTable();

            if (autoCount > 0) {
                view.setStatusMessage("Đã gán tự động " + autoCount + " bài thi dựa theo STT.");
            } else if (validFiles.size() > 1) {
                JOptionPane.showMessageDialog(view, "Không có file nào khớp với STT của lớp này.");
            }
        });

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
                    DataManager.saveSession(currentSession, currentClassRoom.className);
                }
                // [FIX] Cập nhật lại JComboBox khi giáo viên đổi số lượng mã đề
                tableManager.updateExamCodeEditor(this.currentConfig.getExamCodes());
                refreshTable();
                dialog.dispose();
                view.setStatusMessage("Đã lưu cấu hình đa mã đề: " + currentConfig.getExamCodes().size() + " mã.");
            });
            dialog.setVisible(true);
        });

        view.getBtnStartGrading().addActionListener(e -> startGradingProcess());

        view.getBtnStopGrading().addActionListener(e -> {
            if (gradingWorker != null && !gradingWorker.isDone()) {
                gradingWorker.cancel(true);
                view.setStatusMessage("Đang dừng tiến trình chấm bài...");
            }
        });

        view.getBtnExportScores().addActionListener(e -> saveExcel("BangDiem_" + currentSession.getExamName() + ".xlsx", 1));

        view.getBtnExportConfig().addActionListener(e -> {
            if (currentConfig == null || currentConfig.getExamCodes().isEmpty()) {
                JOptionPane.showMessageDialog(view, "Chưa có cấu hình đáp án để xuất!");
                return;
            }

            Object[] codes = currentConfig.getExamCodes().toArray();
            String selectedCode = (String) JOptionPane.showInputDialog(
                    view, "Chọn mã đề muốn xuất đáp án:", "Xuất đáp án chi tiết",
                    JOptionPane.QUESTION_MESSAGE, null, codes, codes[0]
            );

            if (selectedCode != null) {
                currentConfig.setActiveCode(selectedCode);
                saveExcel(currentSession.getExamName() + "_" + selectedCode + ".xlsx", 2);
            }
        });

        view.getBtnChangeSelectedCode().addActionListener(e -> {
            int[] selectedRows = view.getTblResults().getSelectedRows();
            if (selectedRows.length == 0) {
                JOptionPane.showMessageDialog(view, "Vui lòng chọn các học sinh trên bảng!");
                return;
            }
            if (currentConfig == null || currentConfig.getExamCodes().isEmpty()) {
                JOptionPane.showMessageDialog(view, "Vui lòng cài đặt đáp án trước!");
                return;
            }

            Object[] codes = currentConfig.getExamCodes().toArray();
            String selectedCode = (String) JOptionPane.showInputDialog(
                    view, "Chọn mã đề cho " + selectedRows.length + " học sinh:", "Đổi đề vùng chọn",
                    JOptionPane.QUESTION_MESSAGE, null, codes, codes[0]
            );

            if (selectedCode != null) {
                if (view.getTblResults().isEditing() && view.getTblResults().getCellEditor() != null) {
                    view.getTblResults().getCellEditor().stopCellEditing();
                }

                boolean needsSave = false;
                for (int row : selectedRows) {
                    String stt = currentRowStts.get(row);
                    studentExamCodes.put(stt, selectedCode);
                    model.OMRModels.ExamReport report = reportDatabase.get(stt);
                    if (report != null) {
                        report.examCode = selectedCode;
                        report.statusMessage = "<html><span style=\"font-family: 'Segoe UI Emoji'\">⚠️</span> Đã đổi mã, cần chấm lại</html>";
                        needsSave = true;
                    }
                }
                if (needsSave && currentSession != null) service.DataManager.saveSession(currentSession, currentClassRoom.className);
                refreshTable();
                view.setStatusMessage("Đã cập nhật mã đề " + selectedCode + " cho " + selectedRows.length + " học sinh.");
            }
        });

        view.getBtnBulkChangeCode().addActionListener(e -> {
            if (currentConfig == null || currentConfig.getExamCodes().isEmpty()) {
                JOptionPane.showMessageDialog(view, "Vui lòng cài đặt đáp án trước!");
                return;
            }

            Object[] codes = currentConfig.getExamCodes().toArray();
            String selectedCode = (String) JOptionPane.showInputDialog(
                    view, "Chọn mã đề muốn áp dụng cho TẤT CẢ học sinh:", "Đổi đề hàng loạt",
                    JOptionPane.QUESTION_MESSAGE, null, codes, codes[0]
            );

            if (selectedCode != null) {
                if (view.getTblResults().isEditing() && view.getTblResults().getCellEditor() != null) {
                    view.getTblResults().getCellEditor().stopCellEditing();
                }

                boolean needsSave = false;
                for (int i = 0; i < view.getTblResults().getRowCount(); i++) {
                    String stt = currentRowStts.get(i);
                    studentExamCodes.put(stt, selectedCode);
                    model.OMRModels.ExamReport report = reportDatabase.get(stt);
                    if (report != null) {
                        report.examCode = selectedCode;
                        report.statusMessage = "<html><span style=\"font-family: 'Segoe UI Emoji'\">⚠️</span> Đã đổi mã, cần chấm lại</html>";
                        needsSave = true;
                    }
                }
                if (needsSave && currentSession != null) service.DataManager.saveSession(currentSession, currentClassRoom.className);
                refreshTable();
                view.setStatusMessage("Đã chuyển toàn bộ lớp sang mã đề: " + selectedCode);
            }
        });
    }

    private void handleStudentDoubleClick(String stt, String name) {
        model.OMRModels.ExamReport report = reportDatabase.get(stt);
        boolean hasPendingImage = assignedFiles.containsKey(stt);

        if (report != null || hasPendingImage) {
            String[] options = {
                    "🔍 Xem chi tiết",
                    "<html><span style=\"font-family: 'Segoe UI Emoji'\">📁</span> Gán lại ảnh khác</html>",
                    "<html><span style=\"font-family: 'Segoe UI Emoji'\">❌</span> Xóa bài làm này</html>",
                    "Hủy bỏ"
            };
            int choice = JOptionPane.showOptionDialog(view,
                    "Học sinh " + name + " đang có ảnh bài làm. Bạn muốn làm gì?",
                    "Tùy chọn thao tác", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

            if (choice == 0) {
                if (report != null) {
                    // [REFACTORED] Gọi Dialog Mới từ thư mục view!
                    new WrongAnswerDialog(view, report).setVisible(true);
                } else JOptionPane.showMessageDialog(view, "Bài thi này đang chờ để chấm, chưa có chi tiết để xem!");
            }
            else if (choice == 1) openDragAndDropDialog(stt, name);
            else if (choice == 2) removeStudentExam(stt, name);
        } else {
            openDragAndDropDialog(stt, name);
        }
    }

    private void removeStudentExam(String stt, String name) {
        int confirm = JOptionPane.showConfirmDialog(view, "Bạn có chắc muốn hủy kết quả và xóa ảnh bài làm của học sinh: " + name + "?", "Xác nhận xóa", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            model.OMRModels.ExamReport report = reportDatabase.get(stt);
            if (report != null && report.imagePath != null) {
                try {
                    java.nio.file.Files.deleteIfExists(new File(report.imagePath).toPath());
                    int dotIndex = report.imagePath.lastIndexOf('.');
                    if (dotIndex > 0) {
                        String ext = report.imagePath.substring(dotIndex);
                        java.nio.file.Files.deleteIfExists(new File(report.imagePath.replace(ext, "_processed" + ext)).toPath());
                    }
                } catch (Exception ex) {}
            }
            reportDatabase.remove(stt);
            assignedFiles.remove(stt);
            if (currentSession != null) {
                currentSession.getReports().removeIf(r -> r.studentId.equals(stt));
                service.DataManager.saveSession(currentSession, currentClassRoom.className);
            }
            refreshTable();
            view.setStatusMessage("Đã xóa sạch bài làm của học sinh " + name);
        }
    }

    private void deleteSelectedReport() {
        int[] selectedRows = view.getTblResults().getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(view, "Vui lòng giữ Ctrl hoặc Shift để chọn các học sinh cần xóa!");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(view, "Hủy kết quả và xóa ảnh của " + selectedRows.length + " học sinh đã chọn?", "Xác nhận", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            List<String> sttsToDelete = new ArrayList<>();
            for (int row : selectedRows) sttsToDelete.add(currentRowStts.get(row));

            for (String stt : sttsToDelete) {
                model.OMRModels.ExamReport report = reportDatabase.get(stt);
                if (report != null && report.imagePath != null) {
                    try {
                        java.nio.file.Files.deleteIfExists(new File(report.imagePath).toPath());
                        int dotIndex = report.imagePath.lastIndexOf('.');
                        if (dotIndex > 0) java.nio.file.Files.deleteIfExists(new File(report.imagePath.replace(report.imagePath.substring(dotIndex), "_processed" + report.imagePath.substring(dotIndex))).toPath());
                    } catch (Exception ex) {}
                }
                reportDatabase.remove(stt);
                assignedFiles.remove(stt);
                if (currentSession != null) currentSession.getReports().removeIf(r -> r.studentId.equals(stt));
            }
            if (currentSession != null) service.DataManager.saveSession(currentSession, currentClassRoom.className);
            refreshTable();
            view.setStatusMessage("Đã xóa sạch bài làm của " + selectedRows.length + " học sinh.");
        }
    }

    private void openDragAndDropDialog(String stt, String name) {
        JDialog dropDialog = new JDialog(view, "Gán ảnh bài làm - STT " + stt + ": " + name, true);
        dropDialog.setSize(500, 350);
        dropDialog.setLayout(new BorderLayout(10, 10));

        JPanel pnlDrop = new JPanel(new BorderLayout());
        pnlDrop.setBackground(new Color(240, 248, 255));
        pnlDrop.setBorder(BorderFactory.createDashedBorder(Color.GRAY, 3, 5, 2, true));
        pnlDrop.add(new JLabel("<html><center><font size='5' color='#0066cc'><b>KÉO THẢ ẢNH VÀO ĐÂY</b></font><br><br>hoặc <u>Click</u> để mở thư mục</center></html>", SwingConstants.CENTER), BorderLayout.CENTER);

        pnlDrop.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                JFileChooser chooser = new JFileChooser();
                Preferences prefs = Preferences.userRoot().node("ChamTracNghiem_N7");
                chooser.setCurrentDirectory(new File(prefs.get("DIR_IMAGES", System.getProperty("user.home"))));
                chooser.setDialogTitle("Chọn ảnh bài làm cho học sinh: " + name);
                if (chooser.showOpenDialog(dropDialog) == JFileChooser.APPROVE_OPTION) {
                    prefs.put("DIR_IMAGES", chooser.getSelectedFile().getParent());
                    assignedFiles.put(stt, chooser.getSelectedFile());
                    refreshTable();
                    dropDialog.dispose();
                }
            }
        });

        DragDropHandler.applyDropTarget(pnlDrop, (validFiles, dropPoint) -> {
            assignedFiles.put(stt, validFiles.get(0));
            refreshTable();
            dropDialog.dispose();
        });

        dropDialog.add(pnlDrop, BorderLayout.CENTER);
        JPanel pnlBottom = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton btnClose = new JButton("Hủy bỏ");
        btnClose.addActionListener(e -> dropDialog.dispose());
        pnlBottom.add(btnClose);
        dropDialog.add(pnlBottom, BorderLayout.SOUTH);

        dropDialog.setLocationRelativeTo(view);
        dropDialog.setVisible(true);
    }

    private void saveExcel(String defaultName, int type) {
        JFileChooser fileChooser = new JFileChooser();
        Preferences prefs = Preferences.userRoot().node("ChamTracNghiem_N7");
        fileChooser.setCurrentDirectory(new File(prefs.get("DIR_EXPORT", service.DataManager.getDefaultExportPath())));
        fileChooser.setSelectedFile(new File(defaultName));

        if (fileChooser.showSaveDialog(view) == JFileChooser.APPROVE_OPTION) {
            prefs.put("DIR_EXPORT", fileChooser.getSelectedFile().getParent());
            try {
                String path = fileChooser.getSelectedFile().getAbsolutePath();
                if (!path.endsWith(".xlsx")) path += ".xlsx";
                if (type == 1) service.ExcelService.exportExamScoreTable(currentClassRoom, currentSession, path);
                else service.ExcelService.exportAnswerKey(currentConfig, path);
                JOptionPane.showMessageDialog(view, "Xuất file thành công!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(view, "Lỗi khi xuất file: " + ex.getMessage());
            }
        }
    }

    private void startGradingProcess() {
        if (view.getTblResults().isEditing() && view.getTblResults().getCellEditor() != null) {
            view.getTblResults().getCellEditor().stopCellEditing();
        }
        if (assignedFiles.isEmpty()) {
            JOptionPane.showMessageDialog(view, "Chưa có ảnh nào được gán! Hãy click đúp vào tên học sinh để gán ảnh trước khi chấm.", "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (currentConfig == null) {
            JOptionPane.showMessageDialog(view, "Vui lòng cài đặt đáp án trước khi chấm!", "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
            return;
        }

        gradingWorker = new GradingTask(
                view, currentConfig, currentSession, currentClassRoom,
                reportDatabase, assignedFiles, studentExamCodes,
                () -> refreshTable()
        );
        gradingWorker.execute();
    }
}