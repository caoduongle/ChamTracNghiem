package view;

import service.DataManager;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class TrashDialog extends JDialog {
    private JTable tblTrash;
    private DefaultTableModel tableModel;
    private List<DataManager.TrashedItem> trashedItems;

    public TrashDialog(JDialog parent) {
        super(parent, "Thùng rác (Tự động xóa sau 30 ngày)", true);
        setSize(400, 300);
        setLayout(new BorderLayout());

        // Bảng hiển thị
        String[] cols = {"Tên Đề Thi", "Số ngày còn lại"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        tblTrash = new JTable(tableModel);
        add(new JScrollPane(tblTrash), BorderLayout.CENTER);

        // Nút bấm
        JPanel pnlBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnRestore = new JButton("Khôi phục");
        JButton btnDelete = new JButton("Xóa vĩnh viễn");
        JButton btnClose = new JButton("Đóng");

        pnlBtns.add(btnRestore);
        pnlBtns.add(btnDelete);
        pnlBtns.add(btnClose);
        add(pnlBtns, BorderLayout.SOUTH);

        loadTrashData();

        // --- SỰ KIỆN NÚT BẤM ---
        btnRestore.addActionListener(e -> {
            int row = tblTrash.getSelectedRow();
            if (row != -1) {
                DataManager.restoreFromTrash(trashedItems.get(row).trashFileName);
                loadTrashData();
                JOptionPane.showMessageDialog(this, "Đã khôi phục đề thi thành công!");
            } else {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn một đề thi để khôi phục!");
            }
        });

        btnDelete.addActionListener(e -> {
            int row = tblTrash.getSelectedRow();
            if (row != -1) {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Bạn có chắc chắn muốn xóa VĨNH VIỄN đề này không? Không thể khôi phục!",
                        "Cảnh báo", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm == JOptionPane.YES_OPTION) {
                    DataManager.deletePermanently(trashedItems.get(row).trashFileName);
                    loadTrashData();
                }
            } else {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn một đề thi để xóa!");
            }
        });

        btnClose.addActionListener(e -> dispose());

        setLocationRelativeTo(parent);
    }

    private void loadTrashData() {
        tableModel.setRowCount(0);
        trashedItems = DataManager.listTrashedExams();
        for (DataManager.TrashedItem item : trashedItems) {
            tableModel.addRow(new Object[]{item.originalName, item.daysLeft + " ngày"});
        }
    }
}