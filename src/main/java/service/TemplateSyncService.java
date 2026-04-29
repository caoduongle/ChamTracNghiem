package service;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TemplateSyncService {

    // Đường dẫn tới file JSON của bạn trên Github
    private static final String TEMPLATE_JSON_URL = "https://raw.githubusercontent.com/caoduongle/ChamTracNghiem/main/templates.json";

    // Lưu thẳng vào thư mục signatures để TemplateDetector dùng được ngay
    private static final String SAVE_DIR = "data/templates/";

    public static void syncTemplates() {
        // Chạy một luồng ngầm (Thread) để không làm đơ giao diện phần mềm lúc khởi động
        new Thread(() -> {
            try {
                File dir = new File(SAVE_DIR);
                if (!dir.exists()) dir.mkdirs();

                // 1. Đọc nội dung file JSON từ Github
                URL url = new URL(TEMPLATE_JSON_URL + "?t=" + System.currentTimeMillis()); // Thêm ?t= để chống cache
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // 2. Phân tích chuỗi JSON thủ công (Không cần thư viện ngoài)
                String json = response.toString();
                String[] items = json.split("\\}");

                for (String item : items) {
                    if (item.contains("\"id\"") && item.contains("\"url\"")) {
                        String id = extractValue(item, "\"id\"");
                        String imgUrl = extractValue(item, "\"url\"");

                        if (id != null && imgUrl != null) {
                            File imgFile = new File(SAVE_DIR + id + ".jpg");

                            // 3. Nếu máy người dùng chưa có mẫu phiếu này -> Tiến hành tải về!
                            if (!imgFile.exists()) {
                                System.out.println("Phát hiện mẫu phiếu mới trên Cloud. Đang tải: " + id + "...");
                                downloadFile(imgUrl, imgFile);
                                System.out.println("✅ Đã tải xong mẫu: " + id);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("Cảnh báo: Không thể đồng bộ mẫu phiếu từ Cloud (Mất mạng hoặc link lỗi).");
            }
        }).start();
    }

    // Hàm hỗ trợ: Cắt chuỗi lấy giá trị của Key trong JSON
    private static String extractValue(String jsonPart, String key) {
        try {
            int keyIdx = jsonPart.indexOf(key);
            if (keyIdx == -1) return null;
            int colonIdx = jsonPart.indexOf(":", keyIdx);
            int startQuote = jsonPart.indexOf("\"", colonIdx);
            int endQuote = jsonPart.indexOf("\"", startQuote + 1);
            return jsonPart.substring(startQuote + 1, endQuote);
        } catch (Exception e) {
            return null;
        }
    }

    // Hàm hỗ trợ: Tải file vật lý về máy
    private static void downloadFile(String fileUrl, File targetFile) {
        try (BufferedInputStream in = new BufferedInputStream(new URL(fileUrl).openStream());
             FileOutputStream fileOutputStream = new FileOutputStream(targetFile)) {
            byte dataBuffer[] = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        } catch (Exception e) {
            System.out.println("❌ Lỗi tải ảnh: " + fileUrl);
        }
    }
}