package view;

import service.DataManager;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class StartupDialog extends JDialog {
    private JTable tblExams;
    private DefaultTableModel tableModel;
    private JButton btnNew, btnOpen, btnDelete, btnTrash, btnTutorial, btnRename, btnBackToClass, btnConnectPhone;
    private String selectedExam = null;
    private boolean isNew = false;
    private boolean goBackToClass = false;

    private JTextField txtSearch;
    private JComboBox<String> cbxDateFilter;
    private JComboBox<String> cbxSort;
    private JSpinner spinFromDate, spinToDate;
    private List<String> allExamsCache;
    private String currentClassName;

    private class ExamFileItem {
        String name;
        long lastModified;
        ExamFileItem(String name, long time) { this.name = name; this.lastModified = time; }
    }

    public StartupDialog(JFrame parent, String className) {
        super(parent, "Quản lý đề thi - Lớp: " + className, true);
        this.currentClassName = className;

        setSize(650, 700);
        setLayout(new BorderLayout(5, 5));

        JPanel pnlSearchContainer = new JPanel(new GridLayout(2, 1, 5, 5));
        pnlSearchContainer.setBorder(BorderFactory.createTitledBorder("Tìm kiếm, Lọc & Sắp xếp"));

        JPanel pnlRow1 = new JPanel(new BorderLayout(5, 5));
        txtSearch = new JTextField();
        cbxDateFilter = new JComboBox<>(new String[]{"Tất cả thời gian", "Hôm nay", "7 ngày qua", "30 ngày qua", "Tùy chọn..."});
        cbxSort = new JComboBox<>(new String[]{"Tên (A-Z)", "Tên (Z-A)", "Mới nhất", "Cũ nhất"});

        JPanel pnlLeftRow1 = new JPanel(new BorderLayout(5, 0));
        pnlLeftRow1.add(new JLabel(" 🔍 Tên: "), BorderLayout.WEST);
        pnlLeftRow1.add(txtSearch, BorderLayout.CENTER);

        JPanel pnlRightRow1 = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        pnlRightRow1.add(new JLabel("Lọc:")); pnlRightRow1.add(cbxDateFilter);
        pnlRightRow1.add(new JLabel("Xếp:")); pnlRightRow1.add(cbxSort);

        pnlRow1.add(pnlLeftRow1, BorderLayout.CENTER);
        pnlRow1.add(pnlRightRow1, BorderLayout.EAST);

        JPanel pnlRow2 = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        spinFromDate = new JSpinner(new SpinnerDateModel(new Date(), null, null, Calendar.DAY_OF_MONTH));
        spinFromDate.setEditor(new JSpinner.DateEditor(spinFromDate, "dd/MM/yyyy"));
        spinFromDate.setEnabled(false);

        spinToDate = new JSpinner(new SpinnerDateModel(new Date(), null, null, Calendar.DAY_OF_MONTH));
        spinToDate.setEditor(new JSpinner.DateEditor(spinToDate, "dd/MM/yyyy"));
        spinToDate.setEnabled(false);

        pnlRow2.add(new JLabel("Từ:")); pnlRow2.add(spinFromDate);
        pnlRow2.add(new JLabel(" Đến:")); pnlRow2.add(spinToDate);

        pnlSearchContainer.add(pnlRow1);
        pnlSearchContainer.add(pnlRow2);
        add(pnlSearchContainer, BorderLayout.NORTH);

        String[] cols = {"Tên Đề Thi", "Ngày Tạo/Cập Nhật"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        tblExams = new JTable(tableModel);
        tblExams.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tblExams.setCursor(new Cursor(Cursor.HAND_CURSOR));

        refreshExamList();

        JPanel pnlList = new JPanel(new BorderLayout());
        pnlList.setBorder(BorderFactory.createTitledBorder("Danh sách Đề thi:"));
        pnlList.add(new JScrollPane(tblExams), BorderLayout.CENTER);
        add(pnlList, BorderLayout.CENTER);

        JPanel pnlBottomControls = new JPanel(new BorderLayout(0, 5));
        pnlBottomControls.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel pnlBtns = new JPanel(new GridLayout(4, 2, 5, 5));
        btnNew = new JButton("Chấm đề mới");
        btnOpen = new JButton("✔ Mở đề cũ");
        btnRename = new JButton("📝 Đổi tên đề");
        btnDelete = new JButton("❌ Xóa đề");
        btnTrash = new JButton("🗑 Thùng rác");
        btnTutorial = new JButton("Hướng dẫn");
        btnBackToClass = new JButton("⬅ Trở lại Chọn Lớp");
        btnBackToClass.setForeground(new Color(200, 50, 0));

        btnConnectPhone = new JButton("📱 Hướng dẫn kết nối App");
        btnConnectPhone.setBackground(new Color(240, 240, 240));

        pnlBtns.add(btnConnectPhone); pnlBtns.add(btnOpen);
        pnlBtns.add(btnNew); pnlBtns.add(btnRename);
        pnlBtns.add(btnDelete); pnlBtns.add(btnTrash);
        pnlBtns.add(btnTutorial); pnlBtns.add(btnBackToClass);

        pnlBottomControls.add(pnlBtns, BorderLayout.CENTER);
        add(pnlBottomControls, BorderLayout.SOUTH);

        btnConnectPhone.addActionListener(e -> showConnectionDialog());

        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem itemOpen = new JMenuItem("✔ Mở đề thi này");
        JMenuItem itemRename = new JMenuItem("📝 Đổi tên đề thi");
        JMenuItem itemDel = new JMenuItem("❌ Xóa đề thi");

        itemOpen.setFont(new Font("Arial", Font.BOLD, 13));
        itemDel.setForeground(Color.RED);

        popupMenu.add(itemOpen);
        popupMenu.addSeparator();
        popupMenu.add(itemRename);
        popupMenu.addSeparator();
        popupMenu.add(itemDel);

        itemOpen.addActionListener(e -> btnOpen.doClick());
        itemRename.addActionListener(e -> btnRename.doClick());
        itemDel.addActionListener(e -> btnDelete.doClick());

        tblExams.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    int row = tblExams.rowAtPoint(e.getPoint());
                    if (row != -1) btnOpen.doClick();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
                    int row = tblExams.rowAtPoint(e.getPoint());
                    if (row != -1) {
                        tblExams.setRowSelectionInterval(row, row);
                        popupMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });

        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilters(); }
            public void removeUpdate(DocumentEvent e) { applyFilters(); }
            public void changedUpdate(DocumentEvent e) { applyFilters(); }
        });

        cbxDateFilter.addActionListener(e -> {
            boolean isCustom = (cbxDateFilter.getSelectedIndex() == 4);
            spinFromDate.setEnabled(isCustom); spinToDate.setEnabled(isCustom);
            applyFilters();
        });

        cbxSort.addActionListener(e -> applyFilters());

        btnNew.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Nhập tên đề thi mới:");
            if (name != null && !name.trim().isEmpty()) {
                selectedExam = name.trim(); isNew = true; dispose();
            }
        });

        btnOpen.addActionListener(e -> {
            int row = tblExams.getSelectedRow();
            if (row != -1) {
                selectedExam = tblExams.getValueAt(row, 0).toString();
                isNew = false; dispose();
            } else JOptionPane.showMessageDialog(this, "Vui lòng chọn một đề thi!");
        });

        btnRename.addActionListener(e -> {
            int row = tblExams.getSelectedRow();
            if (row != -1) {
                String oldName = tblExams.getValueAt(row, 0).toString();
                String newName = JOptionPane.showInputDialog(this, "Nhập tên mới:", oldName);
                if (newName != null && !newName.trim().isEmpty() && !newName.equals(oldName)) {
                    DataManager.renameExam(oldName, newName.trim(), currentClassName);
                    refreshExamList();
                }
            } else JOptionPane.showMessageDialog(this, "Vui lòng chọn đề thi cần đổi tên!");
        });

        btnDelete.addActionListener(e -> {
            int row = tblExams.getSelectedRow();
            if (row != -1) {
                String toDelete = tblExams.getValueAt(row, 0).toString();
                if (JOptionPane.showConfirmDialog(this, "Đưa '" + toDelete + "' vào thùng rác?", "Xóa", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    DataManager.moveToTrash(toDelete, currentClassName); refreshExamList();
                }
            }
        });

        btnTrash.addActionListener(e -> {
            new TrashDialog(this, currentClassName).setVisible(true); refreshExamList();
        });

        btnBackToClass.addActionListener(e -> {
            goBackToClass = true;
            dispose();
        });

        btnTutorial.addActionListener(e -> {
            new TutorialDialog(parent).setVisible(true);
        });

        setLocationRelativeTo(parent);
        service.WindowPersistenceManager.restoreWindow(this, "StartupDialog", 650, 700);
        service.WindowPersistenceManager.attachSaver(this, "StartupDialog");
    }

    private void showConnectionDialog() {
        String downloadURL = "https://github.com/caoduongle/ChamTracNghiem/releases/download/v2.0.0/app-release.apk";

        // Lưu ý: Đổi chữ 'view' thành 'this' nếu bạn dán vào StartupDialog hoặc ClassManagementDialog
        JDialog dialog = new JDialog(this, "Hướng dẫn kết nối Ứng dụng Điện thoại", true);
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
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void refreshExamList() {
        allExamsCache = DataManager.listSavedExams(currentClassName);
        applyFilters();
    }

    private void applyFilters() {
        if (allExamsCache == null) return;
        tableModel.setRowCount(0);
        String keyword = txtSearch.getText().toLowerCase().trim();
        int dateMode = cbxDateFilter.getSelectedIndex();
        int sortMode = cbxSort.getSelectedIndex();

        long now = System.currentTimeMillis();
        long oneDayMs = 24L * 60 * 60 * 1000;
        long customFromMs = 0, customToMs = 0;

        if (dateMode == 4) {
            Calendar cal = Calendar.getInstance();
            cal.setTime((Date) spinFromDate.getValue()); cal.set(Calendar.HOUR_OF_DAY, 0); customFromMs = cal.getTimeInMillis();
            cal.setTime((Date) spinToDate.getValue()); cal.set(Calendar.HOUR_OF_DAY, 23); customToMs = cal.getTimeInMillis();
        }

        List<ExamFileItem> filteredItems = new ArrayList<>();
        for (String name : allExamsCache) {
            if (!name.toLowerCase().contains(keyword)) continue;
            File f = new File("data/classes/" + currentClassName + "/exams/" + name + ".dat");
            if (f.exists()) {
                long lastModified = f.lastModified();
                long diffDays = (now - lastModified) / oneDayMs;
                boolean pass = true;
                if (dateMode == 1 && diffDays > 0) pass = false;
                else if (dateMode == 2 && diffDays > 7) pass = false;
                else if (dateMode == 3 && diffDays > 30) pass = false;
                else if (dateMode == 4 && (lastModified < customFromMs || lastModified > customToMs)) pass = false;

                if (pass) filteredItems.add(new ExamFileItem(name, lastModified));
            }
        }

        Collections.sort(filteredItems, (a, b) -> {
            switch (sortMode) {
                case 0: return a.name.compareToIgnoreCase(b.name);
                case 1: return b.name.compareToIgnoreCase(a.name);
                case 2: return Long.compare(b.lastModified, a.lastModified);
                case 3: return Long.compare(a.lastModified, b.lastModified);
                default: return 0;
            }
        });

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        for (ExamFileItem item : filteredItems) {
            tableModel.addRow(new Object[]{item.name, sdf.format(new Date(item.lastModified))});
        }
    }

    public String getSelectedExam() { return selectedExam; }
    public boolean isNew() { return isNew; }
    public boolean isGoBackToClass() { return goBackToClass; }
}