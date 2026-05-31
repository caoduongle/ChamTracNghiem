package controller;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import javax.swing.*;

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
        startGlobalServer();
        initController();
        initApp();
        service.TemplateSyncService.syncTemplates();
        if (DataManager.getAutoCleanupMode() == 1) DataManager.performSilentDeepCleanup();
    }

    private void registerPendingSubmission(String stt, String examCode, File imageFile) {
        assignedFiles.put(stt, imageFile);
        boolean isDefaultCode = examCode == null || examCode.trim().isEmpty() || examCode.trim().equalsIgnoreCase("Mặc định") || examCode.trim().equalsIgnoreCase("Mặc định (Không mã)");

        if (!isDefaultCode) {
            studentExamCodes.put(stt, examCode.trim());
            if (currentConfig != null && !currentConfig.getExamCodes().contains(examCode.trim())) {
                currentConfig.getExamCodes().add(examCode.trim());
                tableManager.updateExamCodeEditor(currentConfig.getExamCodes());
            }
        } else {
            studentExamCodes.remove(stt);
        }

        model.OMRModels.ExamReport report = reportDatabase.computeIfAbsent(stt, k -> {
            model.OMRModels.ExamReport newReport = new model.OMRModels.ExamReport();
            newReport.studentId = stt;
            if (currentSession != null && currentSession.getReports() != null) {
                currentSession.getReports().add(newReport);
            }
            return newReport;
        });

        report.imagePath = imageFile.getAbsolutePath();
        report.originalImagePath = imageFile.getAbsolutePath();
        report.examCode = studentExamCodes.get(stt);
        report.statusMessage = "Chờ chấm...";

        if (currentSession != null) service.DataManager.saveSession(currentSession, currentClassRoom.className);
    }

    private void startGlobalServer() {
        boolean success = service.LocalServer.startServer(8080, new service.LocalServer.ServerSyncListener() {
            @Override
            public void onImageReceived(String className, String examName, String stt, String templateId, String examCode, String imagePath) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        if (currentClassRoom != null && currentClassRoom.className.trim().equalsIgnoreCase(className.trim()) &&
                                currentSession != null && currentSession.getExamName().trim().equalsIgnoreCase(examName.trim())) {

                            int incomingStt = -1;
                            try { incomingStt = Integer.parseInt(stt.trim()); } catch(Exception ignored) {}

                            String exactSttKey = stt.trim();
                            for (String rowStt : currentRowStts) {
                                try {
                                    if (incomingStt != -1 && Integer.parseInt(rowStt.trim()) == incomingStt) { exactSttKey = rowStt; break; }
                                } catch(Exception ignored) {}
                            }

                            registerPendingSubmission(exactSttKey, examCode, new java.io.File(imagePath));
                            refreshTable();
                            view.setStatusMessage("📱 Đã nhận bài STT: " + exactSttKey + " - Chờ chấm...");
                        }
                    } catch (Exception ex) { ex.printStackTrace(); }
                });
            }

            @Override
            public void onTemplateChanged(String className, String examName, String newTemplateId) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        if (currentClassRoom != null && currentClassRoom.className.trim().equalsIgnoreCase(className.trim()) &&
                                currentSession != null && currentSession.getExamName().trim().equalsIgnoreCase(examName.trim())) {
                            view.getCbxTemplate().setSelectedItem(newTemplateId);
                            view.setStatusMessage("🔄 Điện thoại vừa chọn mẫu phiếu: " + newTemplateId);
                        }
                    } catch (Exception ex) { ex.printStackTrace(); }
                });
            }
        });

        if (success) {
            view.setTitle("Phần mềm Chấm Thi | 📡 IP: " + service.LocalServer.getLocalIP() + ":8080");
            view.getLblServerStatus().setText("📡 Server: Online (" + service.LocalServer.getLocalIP() + ":8080)");
            view.getLblServerStatus().setForeground(new Color(0, 150, 0));
        } else {
            view.setTitle("Phần mềm Chấm Thi | ⚠️ CỔNG 8080 BỊ CHIẾM");
            view.getLblServerStatus().setText("⚠️ Lỗi: Cổng 8080 đã bị ứng dụng khác chiếm dụng!");
            view.getLblServerStatus().setForeground(new Color(211, 47, 47));
        }
    }

    public void initApp() { showClassMenu(true); }

    private void showClassMenu(boolean isFirstRun) {
        view.setVisible(false);
        ClassManagementDialog classDialog = new ClassManagementDialog(view);
        classDialog.setVisible(true);

        if (classDialog.getSelectedClass() != null) {
            this.currentClassRoom = classDialog.getSelectedClass();
            showStartupMenu(isFirstRun);
        } else {
            System.exit(0);
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

            view.setTitle("Chấm Thi | Lớp: " + currentClassRoom.className + " | Đề: " + currentSession.getExamName() + " | 📡 IP: " + service.LocalServer.getLocalIP() + ":8080");
            view.setVisible(true);
            loadSessionToUI();
        } else {
            System.exit(0);
        }
    }

    private void loadSessionToUI() {
        if (currentSession != null && currentSession.getReports() != null) {
            studentExamCodes.clear();
            assignedFiles.clear();

            for (model.OMRModels.ExamReport report : currentSession.getReports()) {
                reportDatabase.put(report.studentId, report);
                if (report.examCode != null) studentExamCodes.put(report.studentId, report.examCode);

                String path = (report.imagePath != null) ? report.imagePath : report.originalImagePath;
                if (path != null) {
                    File imgFile = new File(path);
                    if (imgFile.exists()) {
                        boolean isWaiting = report.statusMessage == null ||
                                report.statusMessage.contains("Chờ chấm") ||
                                report.statusMessage.contains("Đã đổi mã");
                        if (isWaiting) {
                            assignedFiles.put(report.studentId, imgFile);
                        }
                    }
                }
            }
        }

        if (currentConfig != null && currentConfig.getExamCodes() != null) {
            tableManager.updateExamCodeEditor(currentConfig.getExamCodes());
        }

        if (currentClassRoom != null && currentSession != null) {
            String savedTemp = Preferences.userRoot().node("ChamTracNghiem_N7")
                    .get("TEMPLATE_" + currentClassRoom.className + "_" + currentSession.getExamName(), "BGD4");
            view.getCbxTemplate().setSelectedItem(savedTemp);
            updateTemplatePreview(savedTemp);
        }

        refreshTable();
    }

    private void refreshTable() {
        tableManager.refresh(currentSession, currentClassRoom, reportDatabase, assignedFiles, studentExamCodes, currentRowStts);
    }

    private void initController() {
        view.setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        view.getBtnConnectPhone().addActionListener(e -> showConnectionDialog());

        view.getCbxTemplate().addActionListener(e -> {
            String selected = (String) view.getCbxTemplate().getSelectedItem();
            updateTemplatePreview(selected);

            if (currentClassRoom != null && currentSession != null) {
                Preferences.userRoot().node("ChamTracNghiem_N7")
                        .put("TEMPLATE_" + currentClassRoom.className + "_" + currentSession.getExamName(), selected);
            }
        });
        // Thêm vào trong hàm initController()
        view.getLblTemplatePreview().setCursor(new Cursor(Cursor.HAND_CURSOR)); // Hiển thị hình bàn tay khi di chuột vào
        view.getLblTemplatePreview().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                String selected = (String) view.getCbxTemplate().getSelectedItem();
                showFullScreenTemplate(selected);
            }
        });

        view.getTblResults().getModel().addTableModelListener(e -> {
            if (e.getType() == javax.swing.event.TableModelEvent.UPDATE && e.getColumn() == 3) {
                int row = e.getFirstRow();
                if (row != -1 && row < currentRowStts.size()) {
                    String stt = currentRowStts.get(row);
                    String selectedCode = (String) view.getTblResults().getValueAt(row, 3);
                    model.OMRModels.ExamReport report = reportDatabase.get(stt);

                    if (report != null) {
                        String oldCode = (report.examCode == null || report.examCode.trim().isEmpty()) ? "Mặc định" : report.examCode;
                        String newCode = (selectedCode == null || selectedCode.trim().isEmpty()) ? "Mặc định" : selectedCode;

                        if (!oldCode.equals(newCode)) {
                            report.examCode = newCode.equals("Mặc định") ? null : newCode;
                            studentExamCodes.put(stt, report.examCode);
                            report.statusMessage = "<html><span style=\"font-family: 'Segoe UI Emoji'\">⚠️</span> Đã đổi mã, cần chấm lại</html>";

                            String path = (report.originalImagePath != null) ? report.originalImagePath : report.imagePath;
                            if (path != null) assignedFiles.put(stt, new File(path));

                            if (currentSession != null) service.DataManager.saveSession(currentSession, currentClassRoom.className);

                            SwingUtilities.invokeLater(() -> refreshTable());
                        }
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
                    registerPendingSubmission(currentRowStts.get(row), null, validFiles.get(0));
                    refreshTable();
                    return;
                }
            }

            int autoCount = 0;
            for (File file : validFiles) {
                String rawName = file.getName().substring(0, file.getName().lastIndexOf('.'));
                if (currentRowStts.contains(rawName)) {
                    registerPendingSubmission(rawName, null, file);
                    autoCount++;
                }
            }
            refreshTable();
            if (autoCount > 0) view.setStatusMessage("Đã gán tự động " + autoCount + " bài thi dựa theo STT.");
            else if (validFiles.size() > 1) JOptionPane.showMessageDialog(view, "Không có file nào khớp với STT của lớp này.");
        });

        setupTablePopupMenu();

        view.getBtnSetAnswerKey().addActionListener(e -> {
            AnswerKeyDialog dialog = new AnswerKeyDialog(view);
            if (this.currentConfig != null) dialog.loadConfig(this.currentConfig);
            dialog.getBtnSave().addActionListener(event -> {
                this.currentConfig = dialog.getExamConfig();
                if (currentSession != null) {
                    currentSession.setConfig(this.currentConfig);
                    DataManager.saveSession(currentSession, currentClassRoom.className);
                }
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
                JOptionPane.showMessageDialog(view, "Chưa có cấu hình đáp án để xuất!"); return;
            }
            Object[] codes = currentConfig.getExamCodes().toArray();
            String selectedCode = (String) JOptionPane.showInputDialog(view, "Chọn mã đề muốn xuất đáp án:", "Xuất đáp án chi tiết", JOptionPane.QUESTION_MESSAGE, null, codes, codes[0]);
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
            applyExamCodeChange(selectedRows, "Đổi đề vùng chọn", "Đã cập nhật mã đề ");
        });

        view.getBtnBulkChangeCode().addActionListener(e -> {
            int totalRows = view.getTblResults().getRowCount();
            int[] allRows = new int[totalRows];
            for (int i = 0; i < totalRows; i++) allRows[i] = i;
            applyExamCodeChange(allRows, "Đổi đề hàng loạt", "Đã chuyển toàn bộ lớp sang mã đề: ");
        });
    }

    private void applyExamCodeChange(int[] targetRows, String title, String successMsg) {
        if (currentConfig == null || currentConfig.getExamCodes().isEmpty()) {
            JOptionPane.showMessageDialog(view, "Vui lòng cài đặt đáp án trước!"); return;
        }

        Object[] codes = currentConfig.getExamCodes().toArray();
        String selectedCode = (String) JOptionPane.showInputDialog(view, "Chọn mã đề:", title, JOptionPane.QUESTION_MESSAGE, null, codes, codes[0]);

        if (selectedCode != null) {
            if (view.getTblResults().isEditing() && view.getTblResults().getCellEditor() != null) view.getTblResults().getCellEditor().stopCellEditing();

            boolean needsSave = false;
            for (int row : targetRows) {
                String stt = currentRowStts.get(row);
                studentExamCodes.put(stt, selectedCode.equals("Mặc định") ? null : selectedCode);
                model.OMRModels.ExamReport report = reportDatabase.get(stt);
                if (report != null) {
                    report.examCode = selectedCode.equals("Mặc định") ? null : selectedCode;
                    report.statusMessage = "<html><span style=\"font-family: 'Segoe UI Emoji'\">⚠️</span> Đã đổi mã, cần chấm lại</html>";

                    String path = (report.originalImagePath != null) ? report.originalImagePath : report.imagePath;
                    if (path != null) assignedFiles.put(stt, new File(path));

                    needsSave = true;
                }
            }
            if (needsSave && currentSession != null) service.DataManager.saveSession(currentSession, currentClassRoom.className);
            refreshTable();
            view.setStatusMessage(successMsg + selectedCode);
        }
    }

    private void setupTablePopupMenu() {
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem itemDetail = new JMenuItem("🔍 Xem chi tiết & Sửa điểm");
        JMenuItem itemReassign = new JMenuItem("📁 Gán ảnh bài làm");
        JMenuItem itemDelete = new JMenuItem("❌ Xóa bài làm này");

        itemDetail.setFont(new Font("Arial", Font.BOLD, 13));
        itemDelete.setForeground(Color.RED);

        popupMenu.add(itemDetail);
        popupMenu.add(itemReassign);
        popupMenu.addSeparator();
        popupMenu.add(itemDelete);

        view.getTblResults().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    int row = ((JTable) e.getSource()).getSelectedRow();
                    if (row != -1) handleStudentDoubleClick(currentRowStts.get(row), (String) view.getTblResults().getValueAt(row, 1));
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
                    int row = view.getTblResults().rowAtPoint(e.getPoint());
                    if (row != -1) {
                        view.getTblResults().setRowSelectionInterval(row, row);
                        String stt = currentRowStts.get(row);
                        String name = (String) view.getTblResults().getValueAt(row, 1);

                        model.OMRModels.ExamReport report = reportDatabase.get(stt);
                        boolean hasData = (report != null || assignedFiles.containsKey(stt));
                        boolean isWaiting = report != null && "Chờ chấm...".equals(report.statusMessage);

                        itemDetail.setEnabled(report != null && !isWaiting);
                        itemDelete.setEnabled(hasData);
                        itemReassign.setText(hasData ? "📁 Gán lại ảnh khác" : "📁 Gán ảnh bài làm");

                        for(java.awt.event.ActionListener al : itemDetail.getActionListeners()) itemDetail.removeActionListener(al);
                        for(java.awt.event.ActionListener al : itemReassign.getActionListeners()) itemReassign.removeActionListener(al);
                        for(java.awt.event.ActionListener al : itemDelete.getActionListeners()) itemDelete.removeActionListener(al);

                        itemDetail.addActionListener(ev -> new view.WrongAnswerDialog(view, report, currentConfig, () -> {
                            refreshTable();
                            if (currentSession != null) service.DataManager.saveSession(currentSession, currentClassRoom.className);
                        }).setVisible(true));

                        itemReassign.addActionListener(ev -> openDragAndDropDialog(stt, name));
                        itemDelete.addActionListener(ev -> removeStudentExam(stt, name));
                        popupMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });
    }

    private void deleteImageFiles(String imagePath) {
        if (imagePath == null) return;
        try {
            java.nio.file.Files.deleteIfExists(new File(imagePath).toPath());
            int dotIndex = imagePath.lastIndexOf('.');
            if (dotIndex > 0) java.nio.file.Files.deleteIfExists(new File(imagePath.replace(imagePath.substring(dotIndex), "_processed" + imagePath.substring(dotIndex))).toPath());
        } catch (Exception ignored) {}
    }

    private void handleStudentDoubleClick(String stt, String name) {
        model.OMRModels.ExamReport report = reportDatabase.get(stt);
        if (report != null || assignedFiles.containsKey(stt)) {
            String[] options = { "🔍 Xem chi tiết", "<html><span style=\"font-family: 'Segoe UI Emoji'\">📁</span> Gán lại ảnh khác</html>", "<html><span style=\"font-family: 'Segoe UI Emoji'\">❌</span> Xóa bài làm này</html>", "Hủy bỏ" };
            int choice = JOptionPane.showOptionDialog(view, "Học sinh " + name + " đang có ảnh bài làm. Bạn muốn làm gì?", "Tùy chọn thao tác", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

            if (choice == 0) {
                if (report != null && !"Chờ chấm...".equals(report.statusMessage)) {
                    new WrongAnswerDialog(view, report, currentConfig, () -> {
                        refreshTable();
                        if (currentSession != null) service.DataManager.saveSession(currentSession, currentClassRoom.className);
                    }).setVisible(true);
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
            if (report != null) deleteImageFiles(report.imagePath);
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
        if (selectedRows.length == 0) { JOptionPane.showMessageDialog(view, "Vui lòng giữ Ctrl hoặc Shift để chọn các học sinh cần xóa!"); return; }

        int confirm = JOptionPane.showConfirmDialog(view, "Hủy kết quả và xóa ảnh của " + selectedRows.length + " học sinh đã chọn?", "Xác nhận", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            for (int row : selectedRows) {
                String stt = currentRowStts.get(row);
                model.OMRModels.ExamReport report = reportDatabase.get(stt);
                if (report != null) deleteImageFiles(report.imagePath);
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
                    registerPendingSubmission(stt, null, chooser.getSelectedFile());
                    refreshTable();
                    dropDialog.dispose();
                }
            }
        });

        DragDropHandler.applyDropTarget(pnlDrop, (validFiles, dropPoint) -> {
            registerPendingSubmission(stt, null, validFiles.get(0));
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
            } catch (Exception ex) { JOptionPane.showMessageDialog(view, "Lỗi khi xuất file: " + ex.getMessage()); }
        }
    }

    private void startGradingProcess() {
        if (view.getTblResults().isEditing() && view.getTblResults().getCellEditor() != null) {
            view.getTblResults().getCellEditor().stopCellEditing();
        }

        if (assignedFiles.isEmpty()) {
            JOptionPane.showMessageDialog(view, "Không có bài nào đang chờ chấm!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (currentConfig == null) {
            JOptionPane.showMessageDialog(view, "Vui lòng cài đặt đáp án trước khi chấm!", "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String selectedTemplate = (String) view.getCbxTemplate().getSelectedItem();

        gradingWorker = new GradingTask(
                view, currentConfig, currentSession, currentClassRoom,
                reportDatabase, assignedFiles, studentExamCodes,
                selectedTemplate,
                () -> refreshTable()
        );
        gradingWorker.execute();
    }

    // =========================================================================
    // HÀM HIỆN UI KẾT NỐI THEO CHUẨN MỚI
    // =========================================================================
    private void showConnectionDialog() {
        String downloadURL = "https://github.com/caoduongle/ChamTracNghiem/releases/download/v2.0.0/app-release.apk";

        // Lưu ý: Đổi chữ 'view' thành 'this' nếu bạn dán vào StartupDialog hoặc ClassManagementDialog
        JDialog dialog = new JDialog(view, "Hướng dẫn kết nối Ứng dụng Điện thoại", true);
        dialog.setLayout(new BorderLayout());

        JPanel pnlConnect = new JPanel(new BorderLayout(10, 10));
        pnlConnect.setBackground(Color.WHITE);

        // --- NỬA TRÊN: HƯỚNG DẪN KẾT NỐI LAN ---
        JLabel lblInstruction = new JLabel("<html><center><font size='4'>Mở App trên điện thoại và bấm nút<br><b style='color:#007BFF;'>🔍 DÒ TÌM MÁY TÍNH</b><br>để tự động kết nối qua mạng LAN!</font></center></html>", SwingConstants.CENTER);
        lblInstruction.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
        pnlConnect.add(lblInstruction, BorderLayout.NORTH);

        // --- NỬA DƯỚI: MÃ QR ĐỂ TẢI APP ---
        JPanel pnlDownload = new JPanel(new BorderLayout());
        pnlDownload.setBackground(Color.WHITE);
        JLabel lblDownloadText = new JLabel("<html><center><i>Chưa có App? Dùng Zalo hoặc Camera quét mã dưới đây để tải về:</i></center></html>", SwingConstants.CENTER);
        pnlDownload.add(lblDownloadText, BorderLayout.NORTH);

        try {
            // Tạo mã QR kích thước 220x220 cho link tải APK
            JLabel lblQR = new JLabel(service.QRService.generateQRCode(downloadURL, 220, 220));
            lblQR.setHorizontalAlignment(SwingConstants.CENTER);
            pnlDownload.add(lblQR, BorderLayout.CENTER);
        } catch (Exception e) {
            pnlDownload.add(new JLabel("Lỗi tạo mã QR", SwingConstants.CENTER), BorderLayout.CENTER);
        }

        pnlConnect.add(pnlDownload, BorderLayout.CENTER);

        // --- NÚT ĐÓNG ---
        JPanel pnlBottom = new JPanel(new FlowLayout(FlowLayout.CENTER));
        pnlBottom.setBackground(Color.WHITE);
        JButton btnClose = new JButton("Đóng hộp thoại");
        btnClose.setFont(new Font("Arial", Font.BOLD, 13));
        btnClose.addActionListener(e -> dialog.dispose());
        pnlBottom.add(btnClose);

        dialog.add(pnlConnect, BorderLayout.CENTER);
        dialog.add(pnlBottom, BorderLayout.SOUTH);

        dialog.setSize(450, 480);

        // Lưu ý: Đổi chữ 'view' thành 'this' nếu bạn dán vào StartupDialog hoặc ClassManagementDialog
        dialog.setLocationRelativeTo(view);
        dialog.setVisible(true);
    }

    private void updateTemplatePreview(String templateName) {
        try {
            File imgFile = new File("data/templates/" + templateName + ".jpg");

            if (imgFile.exists()) {
                ImageIcon originalIcon = new ImageIcon(imgFile.getAbsolutePath());
                Image img = originalIcon.getImage();
                Image scaledImg = img.getScaledInstance(200, 270, Image.SCALE_SMOOTH);

                view.getLblTemplatePreview().setIcon(new ImageIcon(scaledImg));
                view.getLblTemplatePreview().setText("");
            } else {
                view.getLblTemplatePreview().setIcon(null);
                view.getLblTemplatePreview().setText("<html><center>Không tìm thấy ảnh<br><b>" + templateName + ".jpg</b></center></html>");
            }
        } catch (Exception ex) {
            view.getLblTemplatePreview().setIcon(null);
            view.getLblTemplatePreview().setText("Lỗi nạp ảnh");
        }
    }
    private double currentScale = 1.0; // Tỉ lệ zoom hiện tại

    private void showFullScreenTemplate(String templateName) {
        // 1. Tải ảnh gốc
        File imgFile = new File("data/templates/" + templateName + ".jpg");
        if (!imgFile.exists()) imgFile = new File("data/templates/" + templateName + ".png");
        if (!imgFile.exists()) return;

        try {
            final java.awt.image.BufferedImage originalImage = javax.imageio.ImageIO.read(imgFile);
            currentScale = 0.5; // Bắt đầu ở mức 50% để không bị quá to

            // 2. Khởi tạo Dialog
            JDialog dialog = new JDialog(view, "Xem mẫu phiếu (Cuộn chuột để Zoom): " + templateName, true);
            dialog.setLayout(new BorderLayout());

            JLabel lblFull = new JLabel();
            lblFull.setHorizontalAlignment(SwingConstants.CENTER);

            // Hàm cập nhật ảnh theo tỉ lệ zoom
            java.util.function.Consumer<Double> updateImage = (scale) -> {
                int newW = (int) (originalImage.getWidth() * scale);
                int newH = (int) (originalImage.getHeight() * scale);
                Image scaled = originalImage.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
                lblFull.setIcon(new ImageIcon(scaled));
            };

            // Hiển thị ảnh lần đầu
            updateImage.accept(currentScale);

            JScrollPane scroll = new JScrollPane(lblFull);
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            dialog.add(scroll, BorderLayout.CENTER);

            // 3. Xử lý Zoom bằng cuộn chuột (Ctrl + Cuộn chuột)
            lblFull.addMouseWheelListener(e -> {
                if (e.isControlDown()) { // Chỉ zoom khi giữ phím Ctrl (giống các phần mềm đồ họa)
                    if (e.getWheelRotation() < 0) currentScale += 0.1; // Cuộn lên = Phóng to
                    else if (currentScale > 0.2) currentScale -= 0.1; // Cuộn xuống = Thu nhỏ
                } else {
                    // Nếu không giữ Ctrl thì cho phép cuộn trang bình thường
                    scroll.dispatchEvent(e);
                    return;
                }
                updateImage.accept(currentScale);
            });

            // 4. Click chuột để đóng
            lblFull.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (!e.isControlDown()) dialog.dispose();
                }
            });

            // Thiết lập kích thước cửa sổ ban đầu
            dialog.setSize(1000, 800);
            dialog.setLocationRelativeTo(view);
            dialog.setVisible(true);

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}