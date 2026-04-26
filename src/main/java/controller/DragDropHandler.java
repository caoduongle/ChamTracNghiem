package controller;

import java.awt.Component;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;

public class DragDropHandler {

    // Tạo một Interface (Callback) để trả dữ liệu về cho Controller
    public interface FileDropListener {
        void onFilesDropped(List<File> validFiles, Point dropLocation);
    }

    /**
     * Hàm tiện ích gắn khả năng kéo thả cho bất kỳ Component nào (JTable, JPanel...)
     */
    public static void applyDropTarget(Component component, FileDropListener listener) {
        component.setDropTarget(new DropTarget() {
            @Override
            public synchronized void drop(DropTargetDropEvent evt) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    List<File> droppedFiles = (List<File>) evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);

                    if (droppedFiles == null || droppedFiles.isEmpty()) return;

                    // Tự động lọc chỉ lấy file ảnh
                    List<File> validFiles = new ArrayList<>();
                    for (File file : droppedFiles) {
                        String low = file.getName().toLowerCase();
                        if (low.endsWith(".jpg") || low.endsWith(".png") || low.endsWith(".jpeg")) {
                            validFiles.add(file);
                        }
                    }

                    if (validFiles.isEmpty()) {
                        JOptionPane.showMessageDialog(component, "Vui lòng kéo file định dạng ảnh (.jpg, .png, .jpeg)!");
                        return;
                    }

                    // Gọi callback trả danh sách file và tọa độ chuột về cho nơi gọi
                    if (listener != null) {
                        listener.onFilesDropped(validFiles, evt.getLocation());
                    }

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }
}