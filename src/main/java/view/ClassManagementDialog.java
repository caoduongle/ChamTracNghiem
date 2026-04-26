package view;

import model.ClassRoom;
import service.DataManager;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.prefs.Preferences;

// THƯ VIỆN KÉO THẢ
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DnDConstants;
import java.awt.datatransfer.DataFlavor;

public class ClassManagementDialog extends JDialog {
    private JTable tblClasses;
    private DefaultTableModel model;
    private ClassRoom selectedClass = null;

    public ClassManagementDialog(JFrame parent) {
        super(parent, "Quản lý Lớp học - Team N7", true);
        setSize(550, 500);
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

        // [FIX]: Chuyển thành GridLayout(5, 2) để chứa thêm nút Cài đặt
        JPanel pnlBtns = new JPanel(new GridLayout(5, 2, 5, 5));
        pnlBtns.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JButton btnNew = new JButton("➕ Tạo lớp mới");
        JButton btnOpen = new JButton("✔ Chọn lớp này");
        JButton btnRename = new JButton("📝 Đổi tên lớp");
        JButton btnEdit = new JButton("✏ Sửa sĩ số / Danh sách");
        JButton btnDel = new JButton("❌ Xóa lớp");
        JButton btnTrash = new JButton("🗑 Thùng rác lớp học");
        JButton btnClassDashboard = new JButton("📈 Thống kê Tổng quan Lớp");
        JButton btnSettings = new JButton("⚙ Cài đặt hệ thống"); // Thêm nút mới
        JLabel emptyLabel = new JLabel("");

        pnlBtns.add(btnNew); pnlBtns.add(btnOpen);
        pnlBtns.add(btnRename); pnlBtns.add(btnEdit);
        pnlBtns.add(btnDel); pnlBtns.add(btnTrash);
        pnlBtns.add(btnClassDashboard); pnlBtns.add(btnSettings); // Thay thế nhãn trống bằng nút Cài đặt
        pnlBtns.add(emptyLabel); // Đẩy nhãn trống xuống cuối

        add(pnlBtns, BorderLayout.SOUTH);

        loadClasses();

        btnNew.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Nhập tên lớp mới (VD: 10A1):");
            if (name == null || name.trim().isEmpty()) return;
            ClassEditorDialog ed = new ClassEditorDialog(this, name, 0);
            ed.setVisible(true);
            if (ed.isSaved()) {
                DataManager.saveClass(ed.getClassRoom());
                loadClasses();
            }
        });

        btnOpen.addActionListener(e -> {
            int r = tblClasses.getSelectedRow();
            if (r != -1) {
                selectedClass = DataManager.loadClass(tblClasses.getValueAt(r, 0).toString());
                dispose();
            } else JOptionPane.showMessageDialog(this, "Vui lòng chọn một lớp trong bảng!");
        });

        btnRename.addActionListener(e -> {
            int r = tblClasses.getSelectedRow();
            if (r != -1) {
                String oldName = tblClasses.getValueAt(r, 0).toString();
                String newName = JOptionPane.showInputDialog(this, "Nhập tên mới:", oldName);
                if (newName != null && !newName.trim().isEmpty() && !newName.equals(oldName)) {
                    DataManager.renameClass(oldName, newName.trim());
                    loadClasses();
                }
            }
        });

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
            }
        });

        btnDel.addActionListener(e -> {
            int r = tblClasses.getSelectedRow();
            if (r != -1) {
                String className = tblClasses.getValueAt(r, 0).toString();
                if (JOptionPane.showConfirmDialog(this, "Đưa lớp vào thùng rác?", "Xác nhận", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    DataManager.deleteClass(className);
                    loadClasses();
                }
            }
        });

        btnTrash.addActionListener(e -> {
            new ClassTrashDialog(this).setVisible(true);
            loadClasses();
        });

        btnClassDashboard.addActionListener(e -> {
            int r = tblClasses.getSelectedRow();
            if (r != -1) {
                String className = tblClasses.getValueAt(r, 0).toString();
                ClassRoom targetClass = DataManager.loadClass(className);
                if (targetClass != null) {
                    new view.ClassDashboardDialog((JFrame) SwingUtilities.getWindowAncestor(this), targetClass).setVisible(true);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Vui lòng click chọn một lớp trong bảng để xem thống kê!");
            }
        });

        // [NEW]: Sự kiện mở hộp thoại cài đặt
        btnSettings.addActionListener(e -> {
            new SettingsDialog((JFrame) SwingUtilities.getWindowAncestor(this)).setVisible(true);
        });

        setLocationRelativeTo(parent);

        // Nhớ vị trí cho màn hình ClassManagement
        service.WindowPersistenceManager.restoreWindow(this, "ClassManagementDialog", 550, 500);
        service.WindowPersistenceManager.attachSaver(this, "ClassManagementDialog");
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
// HỘP THOẠI NHẬP DANH SÁCH - TÍCH HỢP KÉO THẢ EXCEL
// ========================================================
class ClassEditorDialog extends JDialog {
    private ClassRoom cr;
    private boolean saved = false;
    private JTable tbl;
    private DefaultTableModel m;
    private JTextField txtSize;

    public ClassEditorDialog(JDialog parent, String name, int size) {
        super(parent, "Nhập danh sách học sinh - Lớp " + name, true);
        cr = new ClassRoom();
        cr.className = name;
        initUI(size);
    }

    public ClassEditorDialog(JDialog parent, ClassRoom existingClass) {
        super(parent, "Sửa danh sách học sinh - Lớp " + existingClass.className, true);
        this.cr = existingClass;
        initUI(existingClass.students.size());
        for(int i = 0; i < existingClass.students.size(); i++) {
            m.setValueAt(existingClass.students.get(i).stt, i, 0);
            m.setValueAt(existingClass.students.get(i).name, i, 1);
        }
    }

    private void initUI(int initialSize) {
        setSize(550, 650);
        setLayout(new BorderLayout(5, 5));

        JPanel pnlTop = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pnlTop.add(new JLabel("Sĩ số: "));
        txtSize = new JTextField(String.valueOf(initialSize), 5);
        JButton btnApplySize = new JButton("Cập nhật");

        JButton btnImportExcel = new JButton("📥 Import Excel (Hoặc Kéo Thả)");
        btnImportExcel.setBackground(new Color(34, 139, 34));
        btnImportExcel.setForeground(Color.WHITE);

        pnlTop.add(txtSize);
        pnlTop.add(btnApplySize);
        pnlTop.add(new JLabel(" | "));
        pnlTop.add(btnImportExcel);
        add(pnlTop, BorderLayout.NORTH);

        String[] cols = {"STT (Dùng gán file ảnh)", "Họ Tên Học Sinh"};
        m = new DefaultTableModel(cols, initialSize);
        tbl = new JTable(m);
        tbl.setRowHeight(25);
        add(new JScrollPane(tbl), BorderLayout.CENTER);

        enableDragAndDrop();

        btnImportExcel.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();

            // --- BỘ NHỚ LƯU THƯ MỤC EXCEL DANH SÁCH LỚP ---
            Preferences prefs = Preferences.userRoot().node("ChamTracNghiem_N7");
            String lastDir = prefs.get("DIR_CLASS_EXCEL", System.getProperty("user.home"));
            chooser.setCurrentDirectory(new File(lastDir));

            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                prefs.put("DIR_CLASS_EXCEL", chooser.getSelectedFile().getParent()); // Cập nhật lại vị trí mới
                processExcelFile(chooser.getSelectedFile());
            }
        });

        btnApplySize.addActionListener(e -> {
            try {
                int newSize = Integer.parseInt(txtSize.getText());
                int current = m.getRowCount();
                if(newSize > current) for(int i = current; i < newSize; i++) m.addRow(new Object[]{i + 1, ""});
                else m.setRowCount(newSize);
            } catch(Exception ex) { JOptionPane.showMessageDialog(this, "Nhập số nguyên!"); }
        });

        JPanel pnlBottom = new JPanel(new GridLayout(2, 1));
        JButton btnExport = new JButton("📊 Xuất Bảng Điểm TỔNG HỢP");
        JButton btnSave = new JButton("💾 Lưu Danh Sách Lớp");

        btnSave.addActionListener(e -> {
            if (tbl.isEditing()) tbl.getCellEditor().stopCellEditing();
            cr.students.clear();
            for(int i = 0; i < m.getRowCount(); i++) {
                try {
                    int stt = Integer.parseInt(m.getValueAt(i, 0).toString());
                    String sName = m.getValueAt(i, 1) != null ? m.getValueAt(i, 1).toString() : "";
                    cr.students.add(new ClassRoom.Student(stt, sName));
                } catch(Exception ex) {}
            }
            saved = true;
            dispose();
        });

        btnExport.addActionListener(ev -> {
            JFileChooser fc = new JFileChooser();

            // Dùng chung Key lưu trữ xuất file để tạo sự đồng bộ
            Preferences prefs = Preferences.userRoot().node("ChamTracNghiem_N7");
            String lastDir = prefs.get("DIR_EXPORT", System.getProperty("user.home"));
            fc.setCurrentDirectory(new File(lastDir));
            fc.setSelectedFile(new File("DiemTong_" + cr.className + ".xlsx"));

            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                prefs.put("DIR_EXPORT", fc.getSelectedFile().getParent()); // Nhớ vị trí xuất file

                try {
                    service.ExcelService.exportClassScoreTable(cr, fc.getSelectedFile().getAbsolutePath());
                    JOptionPane.showMessageDialog(this, "Thành công!");
                } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage()); }
            }
        });

        pnlBottom.add(btnExport); pnlBottom.add(btnSave);
        add(pnlBottom, BorderLayout.SOUTH);

        setLocationRelativeTo(getParent());

        // Nhớ vị trí cho màn hình ClassEditor
        service.WindowPersistenceManager.restoreWindow(this, "ClassEditorDialog", 550, 650);
        service.WindowPersistenceManager.attachSaver(this, "ClassEditorDialog");
    }

    private void processExcelFile(File file) {
        try (org.apache.poi.ss.usermodel.Workbook workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(file)) {

            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
            org.apache.poi.ss.usermodel.DataFormatter df = new org.apache.poi.ss.usermodel.DataFormatter();
            m.setRowCount(0);

            int count = 0;
            for (org.apache.poi.ss.usermodel.Row row : sheet) {
                if (row == null) continue;

                org.apache.poi.ss.usermodel.Cell sttCell = row.getCell(0);
                if (sttCell == null) continue;

                String sttStr = df.formatCellValue(sttCell).trim();
                if (sttStr.isEmpty() || !sttStr.matches("\\d+.*")) continue;

                try {
                    int stt = (int) Double.parseDouble(sttStr);

                    org.apache.poi.ss.usermodel.Cell hoDemCell = row.getCell(2);
                    org.apache.poi.ss.usermodel.Cell tenCell = row.getCell(3);

                    String hoDem = hoDemCell != null ? df.formatCellValue(hoDemCell).trim() : "";
                    String ten = tenCell != null ? df.formatCellValue(tenCell).trim() : "";
                    String hoTen = (hoDem + " " + ten).trim();

                    if (!hoTen.isEmpty()) {
                        m.addRow(new Object[]{stt, hoTen});
                        count++;
                    }
                } catch (Exception ignored) {}
            }
            txtSize.setText(String.valueOf(count));
            JOptionPane.showMessageDialog(this, "Đã import thành công " + count + " học sinh!");
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Lỗi đọc file: " + ex.getMessage());
        }
    }

    private void enableDragAndDrop() {
        this.setDropTarget(new DropTarget() {
            public synchronized void drop(DropTargetDropEvent evt) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> droppedFiles = (List<File>) evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!droppedFiles.isEmpty()) {
                        File file = droppedFiles.get(0);
                        String low = file.getName().toLowerCase();
                        if (low.endsWith(".xlsx") || low.endsWith(".xls")) {
                            processExcelFile(file);
                        } else {
                            JOptionPane.showMessageDialog(ClassEditorDialog.this, "Vui lòng kéo thả file Excel (.xls hoặc .xlsx)!");
                        }
                    }
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        });
    }

    public boolean isSaved() { return saved; }
    public ClassRoom getClassRoom() { return cr; }
}