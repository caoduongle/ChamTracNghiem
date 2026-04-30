package service;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TemplateSyncService {

    // [LƯU Ý]: Link này phải đảm bảo mở được trên trình duyệt web!
    private static final String TEMPLATE_JSON_URL = "https://raw.githubusercontent.com/caoduongle/ChamTracNghiem/main/templates.json";

    // Thư mục lưu trữ
    private static final String SAVE_DIR = "data/templates/";

    public static void syncTemplates() {
        new Thread(() -> {
            try {
                System.out.println("========== [SYNC] BẮT ĐẦU ĐỒNG BỘ MẪU PHIẾU ==========");

                File dir = new File(SAVE_DIR);
                if (!dir.exists()) {
                    boolean created = dir.mkdirs();
                    System.out.println("[SYNC] Tạo thư mục " + SAVE_DIR + ": " + (created ? "Thành công" : "Thất bại"));
                }

                System.out.println("[SYNC] Đang kết nối tới Github...");
                URL url = new URL(TEMPLATE_JSON_URL + "?t=" + System.currentTimeMillis());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                // Giả lập trình duyệt để không bị chặn
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();
                System.out.println("[SYNC] Mã phản hồi từ Github: " + responseCode);

                if (responseCode != 200) {
                    System.err.println("[SYNC] ❌ LỖI: Không tìm thấy file JSON trên Github (Mã lỗi " + responseCode + "). Hãy kiểm tra lại link hoặc xem bạn đã Push file lên Github chưa!");
                    return;
                }

                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                String json = response.toString();
                System.out.println("[SYNC] Đã đọc được file JSON dài " + json.length() + " ký tự.");

                String[] items = json.split("\\}");
                int successCount = 0;

                for (String item : items) {
                    if (item.contains("\"id\"") && item.contains("\"url\"")) {
                        String id = extractValue(item, "\"id\"");
                        String imgUrl = extractValue(item, "\"url\"");

                        if (id != null && imgUrl != null) {
                            File imgFile = new File(SAVE_DIR + id + ".jpg");

                            if (!imgFile.exists()) {
                                System.out.println("[SYNC] Đang tải vân tay mới: " + id + ".jpg từ " + imgUrl);
                                boolean success = downloadFile(imgUrl, imgFile);
                                if(success) {
                                    System.out.println("[SYNC] ✅ Đã lưu thành công: " + imgFile.getAbsolutePath());
                                    successCount++;
                                }
                            } else {
                                System.out.println("[SYNC] ⏭ Bỏ qua (Đã có sẵn trong máy): " + id + ".jpg");
                            }
                        }
                    }
                }

                System.out.println("[SYNC] HOÀN TẤT. Tải thành công " + successCount + " file mới.");

            } catch (Exception e) {
                System.err.println("[SYNC] ❌ Lỗi ngoại lệ trong quá trình đồng bộ: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

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

    private static boolean downloadFile(String fileUrl, File targetFile) {
        try {
            URL url = new URL(fileUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            if (conn.getResponseCode() != 200) {
                System.err.println("[SYNC] ❌ Lỗi không thể lấy ảnh (Mã " + conn.getResponseCode() + "): " + fileUrl);
                return false;
            }

            try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
                 FileOutputStream fileOutputStream = new FileOutputStream(targetFile)) {
                byte[] dataBuffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                    fileOutputStream.write(dataBuffer, 0, bytesRead);
                }
            }
            return true;
        } catch (Exception e) {
            System.err.println("[SYNC] ❌ Lỗi khi lưu ảnh xuống ổ cứng: " + e.getMessage());
            return false;
        }
    }
}