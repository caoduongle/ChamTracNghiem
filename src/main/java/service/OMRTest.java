package service;

import model.ExamConfig;
import java.io.File;
import java.util.Map;

public class OMRTest {

    // [CLEAN CODE] Tách hardcode path ra hằng số
    private static final String TEST_IMAGE_PATH = "phieumau.jpg";

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("      BẮT ĐẦU CHẠY THỬ HỆ THỐNG OMR AI           ");
        System.out.println("=================================================");

        File imgFile = new File(TEST_IMAGE_PATH);
        if (!imgFile.exists()) {
            System.err.println("Lỗi: Không tìm thấy file '" + TEST_IMAGE_PATH + "'.");
            System.err.println("Vui lòng copy file ảnh vào thư mục gốc của dự án trước khi chạy!");
            return;
        }

        // [CLEAN CODE] Sử dụng constructor truyền số lượng câu thẳng luôn, khỏi cần Setter dài dòng
        ExamConfig testConfig = new ExamConfig(40, 8, 6);

        try {
            System.out.println("[INFO] Đang nạp ảnh và gọi OMRService...");

            // Gọi hàm xử lý cốt lõi
            Map<String, String> results = OMRService.processExam(TEST_IMAGE_PATH, testConfig);

            // Xử lý kết quả trả về
            if (results != null && !results.isEmpty()) {
                System.out.println("\n================ KẾT QUẢ QUÉT ===================");
                for (Map.Entry<String, String> entry : results.entrySet()) {
                    System.out.printf("%-20s : %s\n", entry.getKey(), entry.getValue());
                }
                System.out.println("=================================================");

                System.out.println("\n[THÀNH CÔNG] Đã chấm xong!");
                String processedName = TEST_IMAGE_PATH.replace(".jpg", "_processed.jpg");
                System.out.println("=> Hãy mở thư mục dự án, bạn sẽ thấy file ảnh mới tên là: " + processedName);
                System.out.println("=> Ảnh này đã được nắn phẳng và vẽ các ô vuông định vị.");
            } else {
                System.err.println("\n[THẤT BẠI] Hệ thống trả về rỗng.");
                System.err.println("Nguyên nhân: Không tìm thấy 4 góc đen, ảnh mờ, hoặc AI không nhận diện được mẫu phiếu.");
            }

        } catch (Exception e) {
            System.err.println("\n[CRASH] Đã xảy ra lỗi hệ thống trong quá trình xử lý:");
            e.printStackTrace();
        }
    }
}