package view;

import service.DataManager;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
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
    private JButton btnNew, btnOpen, btnDelete, btnTrash, btnTutorial, btnRename;
    private String selectedExam = null;
    private boolean isNew = false;

    private JTextField txtSearch;
    private JComboBox<String> cbxDateFilter;
    private JComboBox<String> cbxSort;
    private JSpinner spinFromDate, spinToDate;
    private List<String> allExamsCache;

    private class ExamFileItem {
        String name;
        long lastModified;
        ExamFileItem(String name, long time) { this.name = name; this.lastModified = time; }
    }

    public StartupDialog(JFrame parent) {
        super(parent, "Quản lý đề thi - Team N7", true);
        setSize(650, 700);
        setLayout(new BorderLayout(5, 5));

        // 1. THANH TÌM KIẾM
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

        // 2. DANH SÁCH ĐỀ THI
        String[] cols = {"Tên Đề Thi", "Ngày Tạo/Cập Nhật"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        tblExams = new JTable(tableModel);
        tblExams.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        refreshExamList();

        JPanel pnlList = new JPanel(new BorderLayout());
        pnlList.setBorder(BorderFactory.createTitledBorder("Danh sách Đề thi:"));
        pnlList.add(new JScrollPane(tblExams), BorderLayout.CENTER);
        add(pnlList, BorderLayout.CENTER);

        // 3. VÙNG NÚT CHỨC NĂNG (Đã sửa lại GridLayout thành 3x2)
        JPanel pnlBottomControls = new JPanel(new BorderLayout(0, 5));
        pnlBottomControls.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel pnlBtns = new JPanel(new GridLayout(3, 2, 5, 5));
        btnNew = new JButton("Chấm đề mới");
        btnOpen = new JButton("Mở đề cũ");
        btnRename = new JButton("✏ Đổi tên đề");
        btnDelete = new JButton("❌ Xóa đề");
        btnTrash = new JButton("🗑 Thùng rác");
        btnTutorial = new JButton("Hướng dẫn");

        pnlBtns.add(btnNew); pnlBtns.add(btnOpen);
        pnlBtns.add(btnRename); pnlBtns.add(btnDelete);
        pnlBtns.add(btnTrash); pnlBtns.add(btnTutorial);

        pnlBottomControls.add(pnlBtns, BorderLayout.CENTER);
        add(pnlBottomControls, BorderLayout.SOUTH);

        // --- SỰ KIỆN ---
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
            } else {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn một đề thi!", "Chưa chọn", JOptionPane.WARNING_MESSAGE);
            }
        });

        // --- SỰ KIỆN ĐỔI TÊN ĐỀ THI ---
        btnRename.addActionListener(e -> {
            int row = tblExams.getSelectedRow();
            if (row != -1) {
                String oldName = tblExams.getValueAt(row, 0).toString();
                String newName = JOptionPane.showInputDialog(this, "Nhập tên mới cho đề '" + oldName + "':", oldName);
                if (newName != null && !newName.trim().isEmpty() && !newName.equals(oldName)) {
                    DataManager.renameExam(oldName, newName.trim());
                    refreshExamList();
                }
            } else {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn đề thi cần đổi tên!");
            }
        });

        btnDelete.addActionListener(e -> {
            int row = tblExams.getSelectedRow();
            if (row != -1) {
                String toDelete = tblExams.getValueAt(row, 0).toString();
                if (JOptionPane.showConfirmDialog(this, "Đưa '" + toDelete + "' vào thùng rác?", "Xóa", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    DataManager.moveToTrash(toDelete); refreshExamList();
                }
            }
        });

        btnTrash.addActionListener(e -> {
            new TrashDialog(this).setVisible(true); refreshExamList();
        });

        btnTutorial.addActionListener(e -> new TutorialDialog(parent).setVisible(true));

        setLocationRelativeTo(parent);
    }

    private void refreshExamList() {
        allExamsCache = DataManager.listSavedExams();
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
            File f = new File("data/" + name + ".dat");
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
}