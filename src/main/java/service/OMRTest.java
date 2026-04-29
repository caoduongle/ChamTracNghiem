package service;

import model.ExamConfig;
import java.io.File;
import java.util.Map;

public class OMRTest {

    // 1. Cố định đường dẫn ảnh luôn là phieumau.jpg
    private static final String TEST_IMAGE_PATH = "phieumau.jpg";

    // 2. [SỬA Ở ĐÂY LÚC TEST]: Bạn muốn test mẫu nào thì đổi chữ "BGD4" thành "QM", "BGD3", "TNMAKER" v.v...
    private static final String TEMPLATE_TO_TEST = "BGD3";

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("      BẮT ĐẦU CHẠY THỬ HỆ THỐNG OMR AI           ");
        System.out.println("      Đang test cấu hình của mẫu: " + TEMPLATE_TO_TEST);
        System.out.println("=================================================");

        File imgFile = new File(TEST_IMAGE_PATH);
        if (!imgFile.exists()) {
            System.err.println("Lỗi: Không tìm thấy file '" + TEST_IMAGE_PATH + "'.");
            System.err.println("Vui lòng copy file ảnh vào thư mục gốc của dự án trước khi chạy!");
            return;
        }

        // Tự động thiết lập số lượng câu hỏi (ExamConfig) tùy theo mẫu bạn đang chọn ở trên
        ExamConfig testConfig;
        switch (TEMPLATE_TO_TEST) {
            case "BGD4":
            case "BGD4.1":
            case "BGD3":
            case "QM":
            case "TNMAKER":
            default:
                testConfig = new ExamConfig(40, 8, 6); // 40 TN, 8 Đ/S, 6 Điền
        }

        try {
            System.out.println("[INFO] Đang nạp ảnh và gọi OMRService...\n");

            // Gọi hàm xử lý cốt lõi
            // LƯU Ý: Nếu OMRService của bạn có viết thêm tham số nhận tên Mẫu phiếu, hãy sửa dòng dưới thành:
            // Map<String, String> results = OMRService.processExam(TEST_IMAGE_PATH, testConfig, TEMPLATE_TO_TEST);
            Map<String, String> results = OMRService.processExam(TEST_IMAGE_PATH, testConfig, TEMPLATE_TO_TEST);

            // Xử lý kết quả trả về
            if (results != null && !results.isEmpty()) {
                System.out.println("================ KẾT QUẢ QUÉT ===================");
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