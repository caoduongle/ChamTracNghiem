package util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class FileUtil {

    // Dọn dẹp file ảnh gốc và file _processed (nếu có)
    public static void deleteImageFiles(String imagePath) {
        if (imagePath == null || imagePath.isEmpty()) return;
        try {
            Files.deleteIfExists(new File(imagePath).toPath());
            int dotIndex = imagePath.lastIndexOf('.');
            if (dotIndex > 0) {
                String ext = imagePath.substring(dotIndex);
                Files.deleteIfExists(new File(imagePath.replace(ext, "_processed" + ext)).toPath());
            }
        } catch (Exception ex) {
            System.err.println("Không thể xóa file: " + imagePath);
        }
    }

    // Xử lý di chuyển, sao chép và dọn rác ảnh sau khi chấm xong
    public static String handleGradedExamFiles(File sourceFile, String stt, String className, String examName, boolean autoClean, boolean isPerfectSheet) {
        try {
            File imageDir = new File("data/classes/" + className + "/images/" + examName);
            if (!imageDir.exists()) imageDir.mkdirs();

            String originalExt = ".jpg";
            int extIndex = sourceFile.getName().lastIndexOf('.');
            if (extIndex > 0) {
                originalExt = sourceFile.getName().substring(extIndex);
            }

            File destFile = new File(imageDir, stt + originalExt);
            File originalProcessed = new File(sourceFile.getAbsolutePath().replace(originalExt, "_processed" + originalExt));
            File destProcessed = new File(imageDir, stt + "_processed" + originalExt);

            boolean isSameSourceAndDest = sourceFile.getAbsolutePath().equals(destFile.getAbsolutePath());
            boolean isSameProcessed = originalProcessed.getAbsolutePath().equals(destProcessed.getAbsolutePath());

            // 1. Copy ảnh gốc nếu nó nằm ngoài thư mục data
            if (!isSameSourceAndDest) {
                Files.copy(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            // 2. Xử lý ảnh _processed (ảnh warped có vẽ ô vuông)
            if (originalProcessed.exists()) {
                if (autoClean && isPerfectSheet) {
                    originalProcessed.delete(); // Chấm đúng 100% -> Xóa rác
                    if (!isSameProcessed && destProcessed.exists()) destProcessed.delete();
                } else if (!isSameProcessed) {
                    Files.copy(originalProcessed.toPath(), destProcessed.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    originalProcessed.delete();
                }
            }
            return destFile.getAbsolutePath(); // Trả về đường dẫn mới trong thư mục hệ thống
        } catch (Exception ex) {
            System.err.println("Lỗi xử lý file vật lý: " + ex.getMessage());
            return sourceFile.getAbsolutePath(); // Nếu lỗi thì giữ nguyên path cũ
        }
    }
}