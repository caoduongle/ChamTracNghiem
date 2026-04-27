package service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class LocalServer {

    private static HttpServer server;
    private static boolean isRunning = false;

    // [CẢI TIẾN]: Truyền thêm className và examName để đối chiếu
    public interface OnImageReceivedListener {
        void onReceived(String className, String examName, String stt, String templateId, String imagePath);
    }

    public static void startServer(int port, OnImageReceivedListener listener) {
        if (isRunning) return;

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/api/upload", new UploadHandler(listener));
            server.setExecutor(null);
            server.start();
            isRunning = true;

            System.out.println("✅ SERVER ĐÃ CHẠY! Đang lắng nghe tại: http://" + getLocalIP() + ":" + port);

        } catch (Exception e) {
            System.err.println("❌ Không thể khởi động server: " + e.getMessage());
        }
    }

    public static void stopServer() {
        if (server != null) {
            server.stop(0);
            isRunning = false;
            System.out.println("🛑 Đã tắt Server.");
        }
    }

    public static String getLocalIP() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    static class UploadHandler implements HttpHandler {
        private OnImageReceivedListener listener;

        public UploadHandler(OnImageReceivedListener listener) {
            this.listener = listener;
        }

        @Override
        public void handle(HttpExchange exchange) {
            try {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, "Method Not Allowed");
                    return;
                }

                String className = getHeader(exchange, "X-Class-Name");
                String examName = getHeader(exchange, "X-Exam-Name");
                String stt = getHeader(exchange, "X-Student-STT");
                String templateId = getHeader(exchange, "X-Template-ID");

                if (className == null || examName == null || stt == null) {
                    sendResponse(exchange, 400, "Thiếu thông tin Header (Class, Exam, STT)");
                    return;
                }

                File saveDir = new File("data/classes/" + className + "/images/" + examName);
                if (!saveDir.exists()) saveDir.mkdirs();

                File imageFile = new File(saveDir, stt + ".jpg");

                InputStream is = exchange.getRequestBody();
                FileOutputStream fos = new FileOutputStream(imageFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                fos.close();
                is.close();

                // [DEBUG]: In ra đường dẫn gốc tuyệt đối để bạn biết file chạy đi đâu
                System.out.println("📥 Đã nhận và lưu ảnh tại: " + imageFile.getAbsolutePath());

                sendResponse(exchange, 200, "Upload thành công bài của STT: " + stt);

                if (listener != null) {
                    listener.onReceived(className, examName, stt, templateId, imageFile.getAbsolutePath());
                }

            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "Lỗi Server: " + e.getMessage());
            }
        }

        private String getHeader(HttpExchange exchange, String key) {
            if (exchange.getRequestHeaders().containsKey(key)) {
                return exchange.getRequestHeaders().getFirst(key);
            }
            return null;
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String responseText) {
            try {
                byte[] responseBytes = responseText.getBytes("UTF-8");
                exchange.sendResponseHeaders(statusCode, responseBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}