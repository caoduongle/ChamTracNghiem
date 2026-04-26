package controller;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.prefs.Preferences;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

// THƯ VIỆN KÉO THẢ
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DnDConstants;
import java.awt.datatransfer.DataFlavor;

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

    private Map<String, String> studentExamCodes = new HashMap<>();
    private SwingWorker<Void, Object[]> gradingWorker;

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

    private void setupTableColumns() {
        DefaultTableModel model = (DefaultTableModel) view.getTblResults().getModel();
        model.setColumnIdentifiers(new Object[]{"STT", "Họ Tên Học Sinh", "File Ảnh", "Mã Đề", "Tổng điểm", "Trạng thái"});

        if (currentConfig != null && currentConfig.getExamCodes() != null) {
            TableColumn codeColumn = view.getTblResults().getColumnModel().getColumn(3);
            JComboBox<String> comboBox = new JComboBox<>(currentConfig.getExamCodes().toArray(new String[0]));
            codeColumn.setCellEditor(new DefaultCellEditor(comboBox));
        }

        TableColumn statusColumn = view.getTblResults().getColumnModel().getColumn(5);
        statusColumn.setCellRenderer(new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value != null) {
                    String cleanText = value.toString().replaceAll("<[^>]*>", "");
                    ((JComponent) c).setToolTipText("<html><p width='300'>" + cleanText + "</p></html>");
                }
                return c;
            }
        });
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

        if (view.getTblResults().isEditing() && view.getTblResults().getCellEditor() != null) {
            view.getTblResults().getCellEditor().stopCellEditing();
        }

        model.setRowCount(0);
        currentRowStts.clear();

        List<ClassRoom.Student> sortedStudents = new ArrayList<>(currentClassRoom.students);
        int sortMode = view.getCbxSortResults().getSelectedIndex();

        sortedStudents.sort((s1, s2) -> {
            String stt1 = String.valueOf(s1.stt);
            String stt2 = String.valueOf(s2.stt);
            model.OMRModels.ExamReport r1 = reportDatabase.get(stt1);
            model.OMRModels.ExamReport r2 = reportDatabase.get(stt2);

            if (sortMode == 1) {
                double score1 = r1 != null ? r1.totalScore : -1.0;
                double score2 = r2 != null ? r2.totalScore : -1.0;
                if (score1 != score2) {
                    return Double.compare(score2, score1);
                }
                return Integer.compare(s1.stt, s2.stt);

            } else if (sortMode == 2) {
                int priority1 = getStatusPriority(stt1, r1);
                int priority2 = getStatusPriority(stt2, r2);
                if (priority1 != priority2) {
                    return Integer.compare(priority1, priority2);
                }
                return Integer.compare(s1.stt, s2.stt);
            }

            return Integer.compare(s1.stt, s2.stt);
        });

        for (ClassRoom.Student student : sortedStudents) {
            String stt = String.valueOf(student.stt);
            currentRowStts.add(stt);

            model.OMRModels.ExamReport report = reportDatabase.get(stt);
            String fileName = "--- Chưa có ảnh ---";

            if (report != null && report.originalImagePath != null) {
                fileName = "<html><span style=\"font-family: 'Segoe UI Emoji'\">✅</span> " + new File(report.originalImagePath).getName() + "</html>";
            }
            else if (assignedFiles.containsKey(stt)) {
                fileName = "<html><span style=\"font-family: 'Segoe UI Emoji'\">⏳</span> " + assignedFiles.get(stt).getName() + " (Chờ)</html>";
            }

            String code = "Mặc định";
            if (report != null && report.examCode != null && !report.examCode.isEmpty()) {
                code = report.examCode;
            } else {
                code = studentExamCodes.getOrDefault(stt, currentConfig != null && currentConfig.getExamCodes() != null && !currentConfig.getExamCodes().isEmpty() ? currentConfig.getExamCodes().iterator().next() : "Mặc định");
            }
            studentExamCodes.put(stt, code);

            String score = report != null ? String.valueOf(report.totalScore) : "";
            String status = report != null && report.statusMessage != null ? report.statusMessage : (assignedFiles.containsKey(stt) ? "Chờ chấm" : "Chưa có bài");

            view.addResultRow(new Object[]{stt, student.name, fileName, code, score, status});
        }
    }

    private int getStatusPriority(String stt, model.OMRModels.ExamReport report) {
        if (report != null && report.statusMessage != null) {
            String status = report.statusMessage;
            if (status.contains("❌")) return 1;
            if (status.contains("⚠️")) return 2;
            if (status.contains("✅")) return 4;
        }
        if (assignedFiles.containsKey(stt)) return 3;
        return 5;
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

                        SwingUtilities.invokeLater(() -> {
                            view.getTblResults().setValueAt(report.statusMessage, row, 5);
                        });
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

        view.getTblResults().setDropTarget(new DropTarget() {
            public synchronized void drop(DropTargetDropEvent evt) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> droppedFiles = (List<File>) evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (droppedFiles.isEmpty()) return;

                    if (droppedFiles.size() == 1) {
                        Point p = evt.getLocation();
                        int row = view.getTblResults().rowAtPoint(p);
                        if (row != -1) {
                            String low = droppedFiles.get(0).getName().toLowerCase();
                            if (low.endsWith(".jpg") || low.endsWith(".png") || low.endsWith(".jpeg")) {
                                String stt = currentRowStts.get(row);
                                assignedFiles.put(stt, droppedFiles.get(0));
                                refreshTable();
                                return;
                            }
                        }
                    }

                    int autoCount = 0;
                    for (File file : droppedFiles) {
                        String low = file.getName().toLowerCase();
                        if (low.endsWith(".jpg") || low.endsWith(".png") || low.endsWith(".jpeg")) {
                            String rawName = file.getName().substring(0, file.getName().lastIndexOf('.'));
                            if (currentRowStts.contains(rawName)) {
                                assignedFiles.put(rawName, file);
                                autoCount++;
                            }
                        }
                    }
                    refreshTable();
                    if (autoCount > 0) {
                        view.setStatusMessage("Đã gán tự động " + autoCount + " bài thi dựa theo STT.");
                    } else if (droppedFiles.size() > 1) {
                        JOptionPane.showMessageDialog(view, "Không có file nào khớp với STT của lớp này.");
                    }
                } catch (Exception ex) { ex.printStackTrace(); }
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
                    view,
                    "Chọn mã đề muốn xuất đáp án:",
                    "Xuất đáp án chi tiết",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    codes,
                    codes[0]
            );

            if (selectedCode != null) {
                currentConfig.setActiveCode(selectedCode);
                saveExcel(currentSession.getExamName() + "_" + selectedCode + ".xlsx", 2);
            }
        });

        view.getBtnChangeSelectedCode().addActionListener(e -> {
            int[] selectedRows = view.getTblResults().getSelectedRows();
            if (selectedRows.length == 0) {
                JOptionPane.showMessageDialog(view, "Vui lòng chọn các học sinh trên bảng (giữ Ctrl/Shift để chọn nhiều)!");
                return;
            }

            if (currentConfig == null || currentConfig.getExamCodes().isEmpty()) {
                JOptionPane.showMessageDialog(view, "Vui lòng cài đặt đáp án và các mã đề trước!");
                return;
            }

            Object[] codes = currentConfig.getExamCodes().toArray();
            String selectedCode = (String) JOptionPane.showInputDialog(
                    view,
                    "Chọn mã đề cho " + selectedRows.length + " học sinh đã chọn:",
                    "Đổi đề vùng chọn",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    codes,
                    codes[0]
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

                if (needsSave && currentSession != null) {
                    service.DataManager.saveSession(currentSession, currentClassRoom.className);
                }

                refreshTable();
                view.setStatusMessage("Đã cập nhật mã đề " + selectedCode + " cho " + selectedRows.length + " học sinh.");
            }
        });

        view.getBtnBulkChangeCode().addActionListener(e -> {
            if (currentConfig == null || currentConfig.getExamCodes().isEmpty()) {
                JOptionPane.showMessageDialog(view, "Vui lòng cài đặt đáp án và các mã đề trước!");
                return;
            }

            Object[] codes = currentConfig.getExamCodes().toArray();
            String selectedCode = (String) JOptionPane.showInputDialog(
                    view,
                    "Chọn mã đề muốn áp dụng cho TẤT CẢ học sinh:",
                    "Đổi đề hàng loạt",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    codes,
                    codes[0]
            );

            if (selectedCode != null) {
                int rowCount = view.getTblResults().getRowCount();
                if (view.getTblResults().isEditing() && view.getTblResults().getCellEditor() != null) {
                    view.getTblResults().getCellEditor().stopCellEditing();
                }

                boolean needsSave = false;

                for (int i = 0; i < rowCount; i++) {
                    String stt = currentRowStts.get(i);
                    studentExamCodes.put(stt, selectedCode);

                    model.OMRModels.ExamReport report = reportDatabase.get(stt);
                    if (report != null) {
                        report.examCode = selectedCode;
                        report.statusMessage = "<html><span style=\"font-family: 'Segoe UI Emoji'\">⚠️</span> Đã đổi mã, cần chấm lại</html>";
                        needsSave = true;
                    }
                }

                if (needsSave && currentSession != null) {
                    service.DataManager.saveSession(currentSession, currentClassRoom.className);
                }

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
                if (report != null) showWrongAnswersDialog(stt);
                else JOptionPane.showMessageDialog(view, "Bài thi này đang chờ để chấm, chưa có chi tiết để xem!");
            }
            else if (choice == 1) openDragAndDropDialog(stt, name);
            else if (choice == 2) removeStudentExam(stt, name);
        } else {
            openDragAndDropDialog(stt, name);
        }
    }

    private void removeStudentExam(String stt, String name) {
        int confirm = JOptionPane.showConfirmDialog(view,
                "Bạn có chắc muốn hủy kết quả và xóa ảnh bài làm của học sinh: " + name + "?",
                "Xác nhận xóa", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

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
            JOptionPane.showMessageDialog(view, "Vui lòng giữ Ctrl hoặc Shift để chọn các học sinh trên bảng cần xóa!");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(view,
                "Bạn có chắc muốn hủy kết quả và xóa ảnh bài làm của " + selectedRows.length + " học sinh đã chọn?",
                "Xác nhận xóa hàng loạt", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            List<String> sttsToDelete = new ArrayList<>();
            for (int row : selectedRows) {
                sttsToDelete.add(currentRowStts.get(row));
            }

            for (String stt : sttsToDelete) {
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
                }
            }

            if (currentSession != null) {
                service.DataManager.saveSession(currentSession, currentClassRoom.className);
            }
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

        JLabel lblGuide = new JLabel("<html><center><font size='5' color='#0066cc'><b>KÉO THẢ ẢNH VÀO ĐÂY</b></font><br><br>hoặc <u>Click</u> để mở thư mục</center></html>", SwingConstants.CENTER);
        pnlDrop.add(lblGuide, BorderLayout.CENTER);

        pnlDrop.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                JFileChooser chooser = new JFileChooser();
                Preferences prefs = Preferences.userRoot().node("ChamTracNghiem_N7");
                String lastDir = prefs.get("DIR_IMAGES", System.getProperty("user.home"));
                chooser.setCurrentDirectory(new File(lastDir));

                chooser.setDialogTitle("Chọn ảnh bài làm cho học sinh: " + name);
                if (chooser.showOpenDialog(dropDialog) == JFileChooser.APPROVE_OPTION) {
                    prefs.put("DIR_IMAGES", chooser.getSelectedFile().getParent());
                    assignedFiles.put(stt, chooser.getSelectedFile());
                    refreshTable();
                    dropDialog.dispose();
                }
            }
        });

        pnlDrop.setDropTarget(new DropTarget() {
            public synchronized void drop(DropTargetDropEvent evt) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> droppedFiles = (List<File>) evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!droppedFiles.isEmpty()) {
                        File file = droppedFiles.get(0);
                        String low = file.getName().toLowerCase();
                        if (low.endsWith(".jpg") || low.endsWith(".png") || low.endsWith(".jpeg")) {
                            assignedFiles.put(stt, file);
                            refreshTable();
                            dropDialog.dispose();
                        } else {
                            JOptionPane.showMessageDialog(dropDialog, "Vui lòng kéo file định dạng ảnh (.jpg, .png, .jpeg)!");
                        }
                    }
                } catch (Exception ex) { ex.printStackTrace(); }
            }
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
        String defaultPath = service.DataManager.getDefaultExportPath();
        fileChooser.setCurrentDirectory(new File(defaultPath));
        fileChooser.setSelectedFile(new File(defaultName));

        Preferences prefs = Preferences.userRoot().node("ChamTracNghiem_N7");
        String lastDir = prefs.get("DIR_EXPORT", System.getProperty("user.home"));
        fileChooser.setCurrentDirectory(new File(lastDir));
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
                ex.printStackTrace();
                JOptionPane.showMessageDialog(view, "Lỗi khi xuất file: " + ex.getMessage());
            }
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

        java.util.List<model.OMRModels.AnswerRecord> wrongAnswers = new java.util.ArrayList<>();
        for (model.OMRModels.AnswerRecord rec : report.details) {
            if (!rec.isCorrect) wrongAnswers.add(rec);
        }

        wrongAnswers.sort((r1, r2) -> {
            try {
                String[] p1 = r1.questionId.split("_");
                String[] p2 = r2.questionId.split("_");

                int part1 = Integer.parseInt(p1[0].replace("P", ""));
                int part2 = Integer.parseInt(p2[0].replace("P", ""));
                if (part1 != part2) return Integer.compare(part1, part2);

                int q1 = Integer.parseInt(p1[2]);
                int q2 = Integer.parseInt(p2[2]);
                if (q1 != q2) return Integer.compare(q1, q2);

                if (p1.length > 3 && p2.length > 3) {
                    return p1[3].compareTo(p2[3]);
                }
                return 0;
            } catch (Exception e) {
                return r1.questionId.compareTo(r2.questionId);
            }
        });

        int wrongCount = wrongAnswers.size();
        for (model.OMRModels.AnswerRecord rec : wrongAnswers) {
            detailModel.addRow(new Object[]{rec.questionId, rec.studentAnswer, rec.correctAnswer});
        }

        JTable tblDetail = new JTable(detailModel);
        tblDetail.setForeground(Color.RED);
        tblDetail.setFont(new Font("Arial", Font.PLAIN, 14));
        tblDetail.setRowHeight(25);

        JPanel pnlTable = new JPanel(new BorderLayout());
        pnlTable.add(new JScrollPane(tblDetail), BorderLayout.CENTER);

        JLabel lblTotalWrong = new JLabel("  Tổng số ý sai: " + wrongCount, SwingConstants.LEFT);
        lblTotalWrong.setFont(new Font("Arial", Font.BOLD, 14));
        pnlTable.add(lblTotalWrong, BorderLayout.SOUTH);

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

    /**
     * HÀM CLONE SÂU: Khắc phục lỗi Race Condition trong cấu hình khi chạy đa luồng
     */
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

    // =====================================================================
    // [NEW] TIẾN TRÌNH CHẤM BÀI SIÊU TỐC ĐA LUỒNG BẰNG EXECUTOR SERVICE
    // =====================================================================
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

        gradingWorker = new SwingWorker<Void, Object[]>() {
            @Override
            protected Void doInBackground() throws Exception {
                view.getBtnBackToMenu().setEnabled(false);
                view.getBtnStartGrading().setEnabled(false);
                view.getBtnSetAnswerKey().setEnabled(false);
                view.getBtnStopGrading().setEnabled(true);

                int totalFiles = assignedFiles.size();
                AtomicInteger currentCount = new AtomicInteger(0); // Bộ đếm an toàn luồng

                boolean useMultiThread = service.DataManager.isMultiThreadEnabled();
                boolean autoClean = service.DataManager.isAutoCleanProcessed();

                publish(new Object[]{"INIT_PROGRESS"});

                // Khởi tạo Thread Pool với số luồng bằng số nhân CPU nếu bật đa luồng
                ExecutorService executor = useMultiThread
                        ? Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
                        : Executors.newSingleThreadExecutor();

                List<Callable<Void>> tasks = new ArrayList<>();

                for (Map.Entry<String, File> entry : assignedFiles.entrySet()) {
                    tasks.add(() -> {
                        if (isCancelled()) return null;

                        String stt = entry.getKey();
                        File file = entry.getValue();

                        try {
                            // Tạo bản sao cấu hình riêng cho từng luồng để chống lỗi đè mã đề (Race Condition)
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

                                try {
                                    File imageDir = new File("data/classes/" + currentClassRoom.className + "/images/" + currentSession.getExamName());
                                    if (!imageDir.exists()) imageDir.mkdirs();

                                    String originalExt = ".jpg";
                                    int extIndex = file.getName().lastIndexOf('.');
                                    if (extIndex > 0) {
                                        originalExt = file.getName().substring(extIndex);
                                    }

                                    File destFile = new File(imageDir, stt + originalExt);
                                    java.nio.file.Files.copy(file.toPath(), destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                    newReport.imagePath = destFile.getAbsolutePath();

                                    // TÍNH NĂNG TỰ ĐỘNG XÓA RÁC (STORAGE OPTIMIZATION)
                                    File originalProcessed = new File(file.getAbsolutePath().replace(originalExt, "_processed" + originalExt));
                                    if (originalProcessed.exists()) {
                                        if (autoClean && !hasError && !hasWarning) {
                                            // Xóa luôn ảnh debug nếu bài 100% hoàn hảo
                                            originalProcessed.delete();
                                        } else {
                                            // Chuyển ảnh debug vào thư mục lưu trữ
                                            File destProcessed = new File(imageDir, stt + "_processed" + originalExt);
                                            java.nio.file.Files.copy(originalProcessed.toPath(), destProcessed.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                            originalProcessed.delete();
                                        }
                                    }
                                } catch (Exception ex) {
                                    newReport.imagePath = file.getAbsolutePath();
                                }

                                // Cập nhật dữ liệu an toàn (Thread-Safe)
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

                        // Tăng bộ đếm an toàn luồng
                        int completed = currentCount.incrementAndGet();
                        int percent = (completed * 100) / totalFiles;
                        String modeText = useMultiThread ? " (Đa luồng CPU)" : "";
                        publish(new Object[]{"STATUS", "Đang chấm" + modeText + ": " + completed + "/" + totalFiles + " bài...", percent});

                        return null;
                    });
                }

                // Kích hoạt toàn bộ Thread chạy đồng thời và đợi đến khi xong hết
                try {
                    executor.invokeAll(tasks);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    executor.shutdown();
                }

                // Chỉ lưu file session đúng 1 lần vào cuối cùng để tránh thắt cổ chai Ổ cứng (I/O Bottleneck)
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
                        refreshTable();
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
                refreshTable();

                if (isCancelled()) {
                    view.setStatusMessage("Đã dừng chấm bài theo yêu cầu.");
                    JOptionPane.showMessageDialog(view, "Tiến trình chấm bài đã bị dừng!");
                } else {
                    view.setStatusMessage("Hoàn tất quy trình chấm bài đa mã đề.");
                    JOptionPane.showMessageDialog(view, "Đã chấm xong! Dữ liệu đã được lưu riêng cho lớp " + currentClassRoom.className);
                }
                if (!isCancelled() && service.DataManager.isSoundEnabled()) {
                    Toolkit.getDefaultToolkit().beep();
                }
            }
        };

        gradingWorker.execute();
    }
}