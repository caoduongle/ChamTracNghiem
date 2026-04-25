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

        // Nâng cấp Layout thành 3x2 để chứa thêm nút Sửa lớp
        JPanel pnlBtns = new JPanel(new GridLayout(3, 2, 5, 5));
        pnlBtns.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        JButton btnNew = new JButton("➕ Tạo lớp mới");
        JButton btnOpen = new JButton("✔ Chọn lớp này");
        JButton btnEdit = new JButton("✏ Sửa sĩ số / Danh sách");
        JButton btnDel = new JButton("❌ Xóa lớp");
        JButton btnTrash = new JButton("🗑 Thùng rác lớp học");

        pnlBtns.add(btnNew); pnlBtns.add(btnOpen);
        pnlBtns.add(btnEdit); pnlBtns.add(btnDel);
        pnlBtns.add(btnTrash);
        add(pnlBtns, BorderLayout.SOUTH);

        loadClasses();

        // 1. TẠO LỚP MỚI
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
                dispose();
            } else JOptionPane.showMessageDialog(this, "Vui lòng chọn một lớp trong bảng!");
        });

        // 3. SỬA SĨ SỐ VÀ DANH SÁCH (TÍNH NĂNG MỚI)
        btnEdit.addActionListener(e -> {
            int r = tblClasses.getSelectedRow();
            if (r != -1) {
                String className = tblClasses.getValueAt(r, 0).toString();
                ClassRoom existingClass = DataManager.loadClass(className);
                if (existingClass != null) {
                    ClassEditorDialog ed = new ClassEditorDialog(this, existingClass);
                    ed.setVisible(true);
                    if (ed.isSaved()) {
                        DataManager.saveClass(ed.getClassRoom());
                        loadClasses();
                    }
                }
            } else JOptionPane.showMessageDialog(this, "Vui lòng chọn lớp cần sửa!");
        });

        // 4. XÓA LỚP
        btnDel.addActionListener(e -> {
            int r = tblClasses.getSelectedRow();
            if (r != -1) {
                String className = tblClasses.getValueAt(r, 0).toString();
                if (JOptionPane.showConfirmDialog(this, "Đưa lớp " + className + " vào thùng rác?", "Xác nhận", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    DataManager.deleteClass(className);
                    loadClasses();
                }
            } else JOptionPane.showMessageDialog(this, "Vui lòng chọn lớp cần xóa!");
        });

        btnTrash.addActionListener(e -> JOptionPane.showMessageDialog(this, "Tính năng xem thùng rác lớp học đang được phát triển."));
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
// HỘP THOẠI NHẬP/SỬA DANH SÁCH & SĨ SỐ HỌC SINH
// ========================================================
class ClassEditorDialog extends JDialog {
    private ClassRoom cr;
    private boolean saved = false;
    private JTable tbl;
    private DefaultTableModel m;

    // Constructor 1: Dành cho Tạo mới
    public ClassEditorDialog(JDialog parent, String name, int size) {
        super(parent, "Nhập danh sách học sinh - Lớp " + name, true);
        cr = new ClassRoom();
        cr.className = name;
        initUI(size);
        for(int i = 0; i < size; i++) {
            m.setValueAt(i + 1, i, 0);
            m.setValueAt("", i, 1);
        }
    }

    // Constructor 2: Dành cho Chỉnh sửa lớp cũ
    public ClassEditorDialog(JDialog parent, ClassRoom existingClass) {
        super(parent, "Sửa danh sách học sinh - Lớp " + existingClass.className, true);
        this.cr = existingClass;
        int size = existingClass.students.size();
        initUI(size);
        // Đổ dữ liệu cũ vào bảng
        for(int i = 0; i < size; i++) {
            m.setValueAt(existingClass.students.get(i).stt, i, 0);
            m.setValueAt(existingClass.students.get(i).name, i, 1);
        }
    }

    private void initUI(int initialSize) {
        setSize(500, 600);
        setLayout(new BorderLayout(5, 5));

        // Thanh đổi sĩ số ở trên cùng
        JPanel pnlTop = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pnlTop.add(new JLabel("Sĩ số hiện tại: "));
        JTextField txtSize = new JTextField(String.valueOf(initialSize), 5);
        JButton btnApplySize = new JButton("Cập nhật sĩ số");
        pnlTop.add(txtSize);
        pnlTop.add(btnApplySize);
        add(pnlTop, BorderLayout.NORTH);

        String[] cols = {"STT (Theo tên File ảnh)", "Họ Tên Học Sinh"};
        m = new DefaultTableModel(cols, initialSize);
        tbl = new JTable(m);
        tbl.setRowHeight(25);
        tbl.setFont(new Font("Arial", Font.PLAIN, 14));
        add(new JScrollPane(tbl), BorderLayout.CENTER);

        // Sự kiện đổi Sĩ số (Thêm dòng trống hoặc cắt bớt dòng)
        btnApplySize.addActionListener(e -> {
            try {
                int newSize = Integer.parseInt(txtSize.getText());
                if(newSize < 1) return;
                if(tbl.isEditing()) tbl.getCellEditor().stopCellEditing();

                int currentSize = m.getRowCount();
                if(newSize > currentSize) {
                    // Thêm dòng mới
                    for(int i = currentSize; i < newSize; i++) m.addRow(new Object[]{i + 1, ""});
                } else if (newSize < currentSize) {
                    // Cắt bớt dòng thừa
                    m.setRowCount(newSize);
                }
            } catch(Exception ex) { JOptionPane.showMessageDialog(this, "Sĩ số phải là số!"); }
        });

        JPanel pnlBottom = new JPanel(new BorderLayout());
        pnlBottom.add(new JLabel(" Gợi ý: Hãy đặt tên file ảnh chụp là '1.jpg' cho học sinh STT 1."), BorderLayout.NORTH);

        JButton btnSave = new JButton("💾 Lưu Danh Sách Lớp");
        btnSave.setFont(new Font("Arial", Font.BOLD, 14));
        btnSave.addActionListener(e -> {
            if (tbl.isEditing()) tbl.getCellEditor().stopCellEditing();
            cr.students.clear(); // Xóa sạch để ghi lại từ bảng
            for(int i = 0; i < m.getRowCount(); i++) {
                try {
                    int stt = Integer.parseInt(m.getValueAt(i, 0).toString());
                    String sName = m.getValueAt(i, 1) != null ? m.getValueAt(i, 1).toString() : "";
                    cr.students.add(new ClassRoom.Student(stt, sName));
                } catch(Exception ex) {} // Bỏ qua dòng lỗi STT
            }
            saved = true;
            dispose();
        });

        pnlBottom.add(btnSave, BorderLayout.SOUTH);
        add(pnlBottom, BorderLayout.SOUTH);
        setLocationRelativeTo(getParent());
    }

    public boolean isSaved() { return saved; }
    public ClassRoom getClassRoom() { return cr; }
}