package util;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;

public class TableUtils {

    /**
     * Kích hoạt tính năng Paste (Ctrl+V) nhiều ô từ Excel cho JTable
     */
    public static void enableExcelPaste(JTable table) {
        Action pasteAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pasteFromClipboard(table);
            }
        };
        // Ghi đè hành động Paste mặc định của JTable
        table.getActionMap().put("paste", pasteAction);
    }

    private static void pasteFromClipboard(JTable table) {
        try {
            // 1. Lấy dữ liệu văn bản từ Clipboard của hệ điều hành
            String clipboardData = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (clipboardData == null || clipboardData.isEmpty()) return;

            // Nếu đang edit dở một ô thì phải dừng lại trước khi paste
            if (table.isEditing() && table.getCellEditor() != null) {
                table.getCellEditor().stopCellEditing();
            }

            // 2. Excel copy dùng \r\n (xuống dòng) và \t (chuyển cột)
            String[] rows = clipboardData.split("\\r?\\n");
            int startRow = table.getSelectedRow();
            int startCol = table.getSelectedColumn();

            // Nếu chưa click chọn ô nào thì báo lỗi nhẹ
            if (startRow == -1 || startCol == -1) {
                JOptionPane.showMessageDialog(table, "Vui lòng click chọn 1 ô bắt đầu trước khi Paste!", "Nhắc nhở", JOptionPane.WARNING_MESSAGE);
                return;
            }

            DefaultTableModel model = (DefaultTableModel) table.getModel();

            // 3. Quét từng dòng và từng cột để đổ dữ liệu
            for (int i = 0; i < rows.length; i++) {
                String[] cols = rows[i].split("\\t");

                // Tránh lỗi tràn dòng (IndexOutOfBounds) nếu paste quá số lượng dòng hiện có
                if (startRow + i >= model.getRowCount()) break;

                for (int j = 0; j < cols.length; j++) {
                    // Chỉ paste vào những ô được phép Edit và không tràn cột
                    if (startCol + j < model.getColumnCount() && table.isCellEditable(startRow + i, startCol + j)) {
                        String cellValue = cols[j].trim();
                        // Tránh ghi đè nếu ô excel trống
                        if (!cellValue.isEmpty()) {
                            model.setValueAt(cellValue, startRow + i, startCol + j);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Toolkit.getDefaultToolkit().beep();
        }
    }
}