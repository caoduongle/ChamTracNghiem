package service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class LocalServer {

    private static HttpServer server;
    private static boolean isRunning = false;

    public interface OnImageReceivedListener {
        void onReceived(String className, String examName, String stt, String templateId, String imagePath);
    }

    public static void startServer(int port, OnImageReceivedListener listener) {
        if (isRunning) return;

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            // Các API Endpoint giao tiếp với App Android
            server.createContext("/api/classes", new ClassesHandler());
            server.createContext("/api/create_class", new CreateClassHandler());
            server.createContext("/api/upload", new UploadHandler(listener));

            server.setExecutor(null);
            server.start();
            isRunning = true;
            System.out.println("🚀 REST API SERVER READY: http://" + getLocalIP() + ":" + port);

        } catch (Exception e) {
            System.err.println("❌ Lỗi khởi động Server: " + e.getMessage());
        }
    }

    public static void stopServer() {
        if (server != null) {
            server.stop(0);
            isRunning = false;
        }
    }

    // ==============================================================
    // 1. HANDLER: LẤY DANH SÁCH LỚP (GET /api/classes)
    // ==============================================================
    static class ClassesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            File classesDir = new File("data/classes");
            File[] folders = classesDir.listFiles(File::isDirectory);

            StringBuilder json = new StringBuilder("[");
            if (folders != null) {
                for (int i = 0; i < folders.length; i++) {
                    json.append("\"").append(folders[i].getName()).append("\"");
                    if (i < folders.length - 1) json.append(",");
                }
            }
            json.append("]");

            sendResponse(exchange, 200, json.toString());
        }
    }

    // ==============================================================
    // 2. HANDLER: TẠO LỚP MỚI (POST /api/create_class)
    // ==============================================================
    static class CreateClassHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            String className = exchange.getRequestHeaders().getFirst("X-Class-Name");
            if (className == null || className.trim().isEmpty()) {
                sendResponse(exchange, 400, "Missing Class Name");
                return;
            }

            File newClassDir = new File("data/classes/" + className.trim());
            if (newClassDir.exists()) {
                sendResponse(exchange, 409, "Class already exists");
            } else {
                boolean created = newClassDir.mkdirs();
                if (created) {
                    new File(newClassDir, "students.dat").createNewFile();
                    sendResponse(exchange, 201, "Created");
                } else {
                    sendResponse(exchange, 500, "Failed to create directory");
                }
            }
        }
    }

    // ==============================================================
    // 3. HANDLER: UPLOAD ẢNH (POST /api/upload)
    // ==============================================================
    static class UploadHandler implements HttpHandler {
        private OnImageReceivedListener listener;
        public UploadHandler(OnImageReceivedListener listener) { this.listener = listener; }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            String className = exchange.getRequestHeaders().getFirst("X-Class-Name");
            String examName = exchange.getRequestHeaders().getFirst("X-Exam-Name");
            String stt = exchange.getRequestHeaders().getFirst("X-Student-STT");
            String templateId = exchange.getRequestHeaders().getFirst("X-Template-ID");

            if (className == null || examName == null || stt == null) {
                sendResponse(exchange, 400, "Incomplete Metadata");
                return;
            }

            File saveDir = new File("data/classes/" + className + "/images/" + examName);
            if (!saveDir.exists()) saveDir.mkdirs();
            File imageFile = new File(saveDir, stt + ".jpg");

            try (InputStream is = exchange.getRequestBody();
                 FileOutputStream fos = new FileOutputStream(imageFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
            }

            System.out.println("📥 Mobile App gửi bài: Lớp " + className + " | Đề: " + examName + " | STT: " + stt);
            sendResponse(exchange, 200, "Success");

            if (listener != null) {
                listener.onReceived(className, examName, stt, templateId, imageFile.getAbsolutePath());
            }
        }
    }

    private static void sendResponse(HttpExchange exchange, int code, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static String getLocalIP() {
        try { return InetAddress.getLocalHost().getHostAddress(); }
        catch (Exception e) { return "127.0.0.1"; }
    }
}