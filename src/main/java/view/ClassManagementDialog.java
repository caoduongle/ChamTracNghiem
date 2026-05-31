package view;

import util.TableUtils;
import model.ClassRoom;
import service.DataManager;
import service.ExcelService;
import service.WindowPersistenceManager;
import org.apache.poi.ss.usermodel.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;
import java.util.prefs.Preferences;
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
        tblClasses.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JPanel pnlList = new JPanel(new BorderLayout());
        pnlList.setBorder(BorderFactory.createTitledBorder("Danh sách lớp đang dạy:"));
        pnlList.add(new JScrollPane(tblClasses), BorderLayout.CENTER);
        add(pnlList, BorderLayout.CENTER);

        JPanel pnlBtns = new JPanel(new GridLayout(5, 2, 5, 5));
        pnlBtns.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JButton btnNew = new JButton("➕ Tạo lớp mới");
        JButton btnOpen = new JButton("✔ Chọn lớp này");
        JButton btnRename = new JButton("📝 Đổi tên lớp");
        JButton btnEdit = new JButton("✏ Sửa sĩ số / Danh sách");
        JButton btnDel = new JButton("❌ Xóa lớp");
        JButton btnTrash = new JButton("🗑 Thùng rác lớp học");
        JButton btnClassDashboard = new JButton("📈 Thống kê Tổng quan Lớp");
        JButton btnSettings = new JButton("⚙ Cài đặt hệ thống");

        JButton btnConnectPhone = new JButton("📱 Hướng dẫn kết nối App");
        btnConnectPhone.setBackground(new Color(240, 240, 240));

        pnlBtns.add(wrapButtonWithHelp(btnConnectPhone, "Hướng dẫn kết nối App", "<b>Chức năng:</b> Quét mã QR tải app và xem cách tự động kết nối mạng LAN giữa điện thoại và máy tính.<br><br><b>Cách kết nối:</b> Đảm bảo cả PC và điện thoại dùng chung một mạng Wi-Fi (hoặc điện thoại bắt Wi-Fi phát ra từ máy tính). Mở app trên điện thoại và chọn tự động quét để kết nối nhanh."));
        pnlBtns.add(wrapButtonWithHelp(btnOpen, "Chọn lớp học", "<b>Chức năng:</b> Vào màn hình quản lý đề thi của lớp đã chọn.<br><br><b>Cách dùng:</b> Click chọn một dòng lớp trong bảng, sau đó bấm nút này (hoặc click đúp chuột vào tên lớp) để tiến hành làm việc."));
        pnlBtns.add(wrapButtonWithHelp(btnNew, "Tạo lớp học mới", "<b>Chức năng:</b> Tạo một lớp học mới để quản lý điểm và học sinh.<br><br><b>Cách dùng:</b> Nhấp nút, nhập tên lớp (VD: 12A1), sau đó hệ thống sẽ mở bảng danh sách học sinh để bạn nhập thủ công hoặc import từ Excel."));
        pnlBtns.add(wrapButtonWithHelp(btnEdit, "Chỉnh sửa sĩ số & danh sách", "<b>Chức năng:</b> Thêm, sửa, xóa học sinh hoặc thay đổi sĩ số lớp học.<br><br><b>Tiện ích đặc biệt:</b> Bạn có thể copy danh sách từ Excel rồi dán (Ctrl+V) vào bảng học sinh, hoặc kéo thả trực tiếp file Excel chứa danh sách học sinh vào giao diện bảng để tự động import."));
        pnlBtns.add(wrapButtonWithHelp(btnRename, "Đổi tên lớp học", "<b>Chức năng:</b> Đổi tên lớp hiện tại.<br><br><b>Lưu ý:</b> Thao tác này cực kỳ an toàn, chỉ đổi tên hiển thị và không làm thay đổi hay mất mát danh sách học sinh cũng như kết quả chấm thi trước đó."));
        pnlBtns.add(wrapButtonWithHelp(btnClassDashboard, "Thống kê Tổng quan Lớp", "<b>Chức năng:</b> Xem bảng điểm tổng hợp học lực của lớp qua tất cả các đề thi.<br><br><b>Chi tiết:</b> Bảng hiển thị điểm số tích lũy của từng học sinh, điểm trung bình toàn diện, xếp loại học lực, và cung cấp biểu đồ trực quan giúp theo dõi sự tiến bộ của cả tập thể."));
        pnlBtns.add(wrapButtonWithHelp(btnDel, "Xóa lớp học", "<b>Chức năng:</b> Xóa lớp học không còn sử dụng.<br><br><b>Cách hoạt động:</b> Lớp học sẽ được di chuyển vào <b>Thùng rác</b>. Bạn hoàn toàn có thể khôi phục lại lớp học này từ thùng rác nếu lỡ tay xóa nhầm."));
        pnlBtns.add(wrapButtonWithHelp(btnTrash, "Thùng rác lớp học", "<b>Chức năng:</b> Quản lý lớp học đã xóa tạm thời.<br><br><b>Cách dùng:</b> Nhấp vào để xem danh sách các lớp đã xóa. Chọn lớp và bấm Khôi phục để đưa lớp trở lại danh sách dạy học, hoặc bấm Xóa vĩnh viễn để xóa hẳn khỏi đĩa cứng."));
        pnlBtns.add(wrapButtonWithHelp(btnSettings, "Cài đặt hệ thống", "<b>Chức năng:</b> Cấu hình thông số kỹ thuật chung cho phần mềm.<br><br><b>Chi tiết:</b> Cho phép thay đổi cổng kết nối (Port), cấu hình thư mục lưu trữ, tinh chỉnh độ sáng, độ tương phản của thuật toán nhận diện OMR để tối ưu độ chính xác."));

        add(pnlBtns, BorderLayout.SOUTH);

        btnConnectPhone.addActionListener(e -> showConnectionDialog());

        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem itemOpen = new JMenuItem("✔ Mở lớp này");
        JMenuItem itemRename = new JMenuItem("📝 Đổi tên lớp");
        JMenuItem itemEdit = new JMenuItem("✏ Sửa sĩ số / Danh sách");
        JMenuItem itemDashboard = new JMenuItem("📈 Thống kê lớp");
        JMenuItem itemDel = new JMenuItem("❌ Xóa lớp");

        itemOpen.setFont(new Font("Arial", Font.BOLD, 13));
        itemDel.setForeground(Color.RED);

        popupMenu.add(itemOpen);
        popupMenu.addSeparator();
        popupMenu.add(itemRename);
        popupMenu.add(itemEdit);
        popupMenu.add(itemDashboard);
        popupMenu.addSeparator();
        popupMenu.add(itemDel);

        itemOpen.addActionListener(e -> btnOpen.doClick());
        itemRename.addActionListener(e -> btnRename.doClick());
        itemEdit.addActionListener(e -> btnEdit.doClick());
        itemDashboard.addActionListener(e -> btnClassDashboard.doClick());
        itemDel.addActionListener(e -> btnDel.doClick());

        tblClasses.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    int row = tblClasses.rowAtPoint(e.getPoint());
                    if (row != -1) btnOpen.doClick();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
                    int row = tblClasses.rowAtPoint(e.getPoint());
                    if (row != -1) {
                        tblClasses.setRowSelectionInterval(row, row);
                        popupMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });

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

        btnSettings.addActionListener(e -> {
            new SettingsDialog((JFrame) SwingUtilities.getWindowAncestor(this)).setVisible(true);
        });

        setLocationRelativeTo(parent);
        WindowPersistenceManager.restoreWindow(this, "ClassManagementDialog", 550, 500);
        WindowPersistenceManager.attachSaver(this, "ClassManagementDialog");
    }

    private void loadClasses() {
        model.setRowCount(0);
        List<String> classes = DataManager.listClasses();
        for (String c : classes) {
            ClassRoom cr = DataManager.loadClass(c);
            if (cr != null) model.addRow(new Object[]{cr.className, cr.students.size()});
        }
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

    public ClassRoom getSelectedClass() { return selectedClass; }

    private JPanel wrapButtonWithHelp(JButton btn, String helpTitle, String helpHtml) {
        JPanel wrapper = new JPanel(new BorderLayout(3, 0));
        wrapper.setOpaque(false);
        btn.setFocusPainted(false);
        wrapper.add(btn, BorderLayout.CENTER);

        JButton btnHelp = new JButton("?");
        btnHelp.setPreferredSize(new Dimension(28, 0));
        btnHelp.setMargin(new java.awt.Insets(0, 0, 0, 0));
        btnHelp.setFont(new Font("Arial", Font.BOLD, 12));
        btnHelp.setBackground(new Color(225, 225, 225));
        btnHelp.setFocusPainted(false);
        btnHelp.setToolTipText("Nhấp để xem hướng dẫn");
        btnHelp.addActionListener(e -> {
            HelpDialog.showHelp(btnHelp, helpTitle, helpHtml);
        });
        wrapper.add(btnHelp, BorderLayout.EAST);
        return wrapper;
    }
}

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
        TableUtils.enableExcelPaste(tbl);
        add(new JScrollPane(tbl), BorderLayout.CENTER);

        enableDragAndDrop();

        btnImportExcel.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            Preferences prefs = Preferences.userRoot().node("ChamTracNghiem_N7");
            String lastDir = prefs.get("DIR_CLASS_EXCEL", System.getProperty("user.home"));
            chooser.setCurrentDirectory(new File(lastDir));

            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                prefs.put("DIR_CLASS_EXCEL", chooser.getSelectedFile().getParent());
                processExcelFile(chooser.getSelectedFile());
            }
        });

        btnApplySize.addActionListener(e -> {
            try {
                int newSize = Integer.parseInt(txtSize.getText());
                int current = m.getRowCount();
                if(newSize > current) for(int i = current; i < newSize; i++) m.addRow(new Object[]{i + 1, ""});
                else m.setRowCount(newSize);
            } catch(Exception ex) { JOptionPane.showMessageDialog(this, "Nhập số nguyên hợp lệ!"); }
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
            Preferences prefs = Preferences.userRoot().node("ChamTracNghiem_N7");
            String lastDir = prefs.get("DIR_EXPORT", System.getProperty("user.home"));
            fc.setCurrentDirectory(new File(lastDir));
            fc.setSelectedFile(new File("DiemTong_" + cr.className + ".xlsx"));

            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                prefs.put("DIR_EXPORT", fc.getSelectedFile().getParent());
                try {
                    ExcelService.exportClassScoreTable(cr, fc.getSelectedFile().getAbsolutePath());
                    JOptionPane.showMessageDialog(this, "Thành công!");
                } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage()); }
            }
        });

        pnlBottom.add(btnExport); pnlBottom.add(btnSave);
        add(pnlBottom, BorderLayout.SOUTH);

        setLocationRelativeTo(getParent());
        WindowPersistenceManager.restoreWindow(this, "ClassEditorDialog", 550, 650);
        WindowPersistenceManager.attachSaver(this, "ClassEditorDialog");
    }

    private void processExcelFile(File file) {
        try (Workbook workbook = WorkbookFactory.create(file)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter df = new DataFormatter();
            m.setRowCount(0);

            int count = 0;
            for (Row row : sheet) {
                if (row == null) continue;

                Cell sttCell = row.getCell(0);
                if (sttCell == null) continue;

                String sttStr = df.formatCellValue(sttCell).trim();
                if (sttStr.isEmpty() || !sttStr.matches("\\d+.*")) continue;

                try {
                    int stt = (int) Double.parseDouble(sttStr);

                    Cell hoDemCell = row.getCell(2);
                    Cell tenCell = row.getCell(3);

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
            if (ex.getMessage() != null && ex.getMessage().contains("neither an OLE2 stream, nor an OOXML stream")) {
                JOptionPane.showMessageDialog(this, "Lỗi: File Excel giả mạo.\nLưu lại với định dạng Excel Workbook (*.xlsx) rồi import lại.", "Sai định dạng file", JOptionPane.ERROR_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Lỗi đọc file: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    @SuppressWarnings("unchecked")
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