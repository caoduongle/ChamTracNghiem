package view;

import model.ClassRoom;
import service.DataManager;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class ClassManagementDialog extends JDialog {
    private JTable tblClasses;
    private DefaultTableModel model;
    private ClassRoom selectedClass = null;

    public ClassManagementDialog(JFrame parent) {
        super(parent, "Quản lý Lớp học - Team N7", true);
        setSize(550, 450);
        setLayout(new BorderLayout(5, 5));

        String[] cols = {"Tên Lớp", "Sĩ số (Học sinh)"};
        model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        tblClasses = new JTable(model);
        tblClasses.setRowHeight(30);
        tblClasses.setFont(new Font("Arial", Font.PLAIN, 14));

        JPanel pnlList = new JPanel(new BorderLayout());
        pnlList.setBorder(BorderFactory.createTitledBorder("Danh sách lớp đang dạy:"));
        pnlList.add(new JScrollPane(tblClasses), BorderLayout.CENTER);
        add(pnlList, BorderLayout.CENTER);

        JPanel pnlBtns = new JPanel(new GridLayout(2, 2, 5, 5));
        pnlBtns.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        JButton btnNew = new JButton("➕ Tạo lớp mới");
        JButton btnOpen = new JButton("✔ Lớp đang dạy (Chọn)");
        JButton btnDel = new JButton("❌ Xóa lớp");
        JButton btnTrash = new JButton("🗑 Thùng rác lớp học");

        pnlBtns.add(btnNew); pnlBtns.add(btnOpen);
        pnlBtns.add(btnDel); pnlBtns.add(btnTrash);
        add(pnlBtns, BorderLayout.SOUTH);

        loadClasses();

        // 1. TẠO LỚP MỚI VÀ NHẬP DANH SÁCH
        btnNew.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Nhập tên lớp mới (VD: 10A1):");
            if (name == null || name.trim().isEmpty()) return;
            String sizeStr = JOptionPane.showInputDialog(this, "Nhập sĩ số lớp " + name + ":");
            if (sizeStr == null) return;
            try {
                int size = Integer.parseInt(sizeStr);
                ClassEditorDialog ed = new ClassEditorDialog(this, name, size);
                ed.setVisible(true);
                if (ed.isSaved()) {
                    DataManager.saveClass(ed.getClassRoom());
                    loadClasses();
                }
            } catch(Exception ex) { JOptionPane.showMessageDialog(this, "Sĩ số phải là số nguyên!"); }
        });

        // 2. CHỌN LỚP ĐỂ CHẤM
        btnOpen.addActionListener(e -> {
            int r = tblClasses.getSelectedRow();
            if (r != -1) {
                selectedClass = DataManager.loadClass(tblClasses.getValueAt(r, 0).toString());
                dispose(); // Đóng cửa sổ và đi tiếp
            } else JOptionPane.showMessageDialog(this, "Vui lòng chọn một lớp trong bảng!");
        });

        // 3. XÓA LỚP
        btnDel.addActionListener(e -> {
            int r = tblClasses.getSelectedRow();
            if (r != -1) {
                String className = tblClasses.getValueAt(r, 0).toString();
                if (JOptionPane.showConfirmDialog(this, "Đưa lớp " + className + " vào thùng rác?", "Xác nhận", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    DataManager.deleteClass(className);
                    loadClasses();
                }
            }
        });

        btnTrash.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "Tính năng xem thùng rác lớp học đang được phát triển.");
        });

        setLocationRelativeTo(parent);
    }

    private void loadClasses() {
        model.setRowCount(0);
        List<String> classes = DataManager.listClasses();
        for (String c : classes) {
            ClassRoom cr = DataManager.loadClass(c);
            if (cr != null) model.addRow(new Object[]{cr.className, cr.students.size()});
        }
    }

    public ClassRoom getSelectedClass() { return selectedClass; }
}

// ========================================================
// HỘP THOẠI NHẬP TÊN HỌC SINH (STT) KHI TẠO LỚP MỚI
// ========================================================
class ClassEditorDialog extends JDialog {
    private ClassRoom cr;
    private boolean saved = false;
    private JTable tbl;

    public ClassEditorDialog(JDialog parent, String name, int size) {
        super(parent, "Nhập danh sách học sinh - Lớp " + name, true);
        setSize(500, 600);
        cr = new ClassRoom();
        cr.className = name;

        String[] cols = {"STT (Theo tên File ảnh)", "Họ Tên Học Sinh"};
        DefaultTableModel m = new DefaultTableModel(cols, size);
        for(int i = 0; i < size; i++) {
            m.setValueAt(i + 1, i, 0); // Tự động điền STT 1, 2, 3...
            m.setValueAt("", i, 1);    // Ô trống để nhập tên
        }

        tbl = new JTable(m);
        tbl.setRowHeight(25);
        tbl.setFont(new Font("Arial", Font.PLAIN, 14));
        add(new JScrollPane(tbl), BorderLayout.CENTER);

        JPanel pnlBottom = new JPanel(new BorderLayout());
        pnlBottom.add(new JLabel(" Gợi ý: Hãy đặt tên file ảnh chụp là '1.jpg' cho học sinh STT 1."), BorderLayout.NORTH);

        JButton btnSave = new JButton("💾 Lưu Danh Sách Lớp");
        btnSave.setFont(new Font("Arial", Font.BOLD, 14));
        btnSave.addActionListener(e -> {
            if (tbl.isEditing()) tbl.getCellEditor().stopCellEditing();
            for(int i = 0; i < size; i++) {
                String sName = tbl.getValueAt(i, 1) != null ? tbl.getValueAt(i, 1).toString() : "";
                cr.students.add(new ClassRoom.Student(i + 1, sName));
            }
            saved = true;
            dispose();
        });

        pnlBottom.add(btnSave, BorderLayout.SOUTH);
        add(pnlBottom, BorderLayout.SOUTH);
        setLocationRelativeTo(parent);
    }
    public boolean isSaved() { return saved; }
    public ClassRoom getClassRoom() { return cr; }
}