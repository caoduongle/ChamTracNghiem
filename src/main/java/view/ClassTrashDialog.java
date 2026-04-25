package view;

import service.DataManager;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ClassTrashDialog extends JDialog {
    private final JTable tblTrash;
    private final DefaultTableModel tableModel;
    private final JTextField txtSearch;
    private final TableRowSorter<DefaultTableModel> sorter;
    private List<DataManager.TrashedItem> trashedItems;
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public ClassTrashDialog(JDialog parent) {
        super(parent, "Thùng rác Lớp học (Tự động xóa sau 30 ngày)", true);
        setSize(750, 450);
        setLayout(new BorderLayout(5, 5));

        JPanel pnlSearch = new JPanel(new BorderLayout(5, 5));
        pnlSearch.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        txtSearch = new JTextField();
        pnlSearch.add(new JLabel(" 🔍 Tìm kiếm lớp: "), BorderLayout.WEST);
        pnlSearch.add(txtSearch, BorderLayout.CENTER);
        add(pnlSearch, BorderLayout.NORTH);

        String[] cols = {"Tên Lớp", "Ngày Tạo", "Ngày Xóa", "Hạn còn lại"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        tblTrash = new JTable(tableModel);
        tblTrash.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        sorter = new TableRowSorter<>(tableModel);
        tblTrash.setRowSorter(sorter);
        add(new JScrollPane(tblTrash), BorderLayout.CENTER);

        JPanel pnlBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnRestore = new JButton("Khôi phục lớp đã chọn");
        JButton btnDelete = new JButton("❌ Xóa vĩnh viễn");
        JButton btnClose = new JButton("Đóng");

        pnlBtns.add(btnRestore); pnlBtns.add(btnDelete); pnlBtns.add(btnClose);
        add(pnlBtns, BorderLayout.SOUTH);

        loadTrashData();

        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filter(); }
            public void removeUpdate(DocumentEvent e) { filter(); }
            public void changedUpdate(DocumentEvent e) { filter(); }
        });

        btnRestore.addActionListener(e -> {
            int[] rows = tblTrash.getSelectedRows();
            if (rows.length > 0) {
                for (int viewRow : rows) {
                    int modelRow = tblTrash.convertRowIndexToModel(viewRow);
                    DataManager.restoreClassFromTrash(trashedItems.get(modelRow).trashFileName);
                }
                loadTrashData();
                JOptionPane.showMessageDialog(this, "Đã khôi phục các lớp thành công!");
            }
        });

        btnDelete.addActionListener(e -> {
            int[] rows = tblTrash.getSelectedRows();
            if (rows.length > 0) {
                if (JOptionPane.showConfirmDialog(this, "Xóa VĨNH VIỄN các lớp này? Chú ý: Dữ liệu học sinh sẽ mất hoàn toàn!", "Cảnh báo", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    for (int viewRow : rows) {
                        int modelRow = tblTrash.convertRowIndexToModel(viewRow);
                        DataManager.deleteClassPermanently(trashedItems.get(modelRow).trashFileName);
                    }
                    loadTrashData();
                }
            }
        });

        btnClose.addActionListener(e -> dispose());
        setLocationRelativeTo(parent);
    }

    private void filter() {
        String text = txtSearch.getText();
        if (text.trim().isEmpty()) sorter.setRowFilter(null);
        else sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text, 0));
    }

    private void loadTrashData() {
        tableModel.setRowCount(0);
        trashedItems = DataManager.listTrashedClasses();
        for (DataManager.TrashedItem item : trashedItems) {
            tableModel.addRow(new Object[]{
                    item.originalName, sdf.format(new Date(item.creationTime)),
                    sdf.format(new Date(item.deletionTime)), item.daysLeft + " ngày"
            });
        }
    }
}