package service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import model.ClassRoom;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocalServer {

    private static HttpServer server;
    private static boolean isRunning = false;

    public interface ServerSyncListener {
        void onImageReceived(String className, String examName, String stt, String templateId, String examCode, String imagePath);
        void onTemplateChanged(String className, String examName, String newTemplateId);
    }

    public static void startServer(int port, ServerSyncListener listener) {
        if (isRunning) return;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            server.createContext("/api/classes", new ClassesHandler());
            server.createContext("/api/create_class", new CreateClassHandler());
            server.createContext("/api/exams", new ExamsHandler());
            server.createContext("/api/students", new StudentsHandler());
            server.createContext("/api/create_exam", new CreateExamHandler());
            server.createContext("/api/exam_codes", new ExamCodesHandler());
            server.createContext("/api/create_exam_code", new CreateExamCodeHandler());

            server.createContext("/api/templates", new TemplatesHandler());
            server.createContext("/api/template_image", new TemplateImageHandler());
            server.createContext("/api/current_template", new CurrentTemplateHandler());
            server.createContext("/api/set_template", new SetTemplateHandler(listener));

            server.createContext("/api/upload", new UploadHandler(listener));

            server.setExecutor(null);
            server.start();
            isRunning = true;
            System.out.println("🚀 REST API SERVER V5 READY: http://" + getLocalIP() + ":" + port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void stopServer() {
        if (server != null) { server.stop(0); isRunning = false; }
    }

    // =========================================================================
    // CÁC HÀM TIỆN ÍCH (UTILITIES) - GIÚP CODE NGẮN GỌN VÀ CHỐNG LỖI
    // =========================================================================

    private static String getHeaderSafely(HttpExchange exchange, String headerName) {
        String value = exchange.getRequestHeaders().getFirst(headerName);
        if (value == null || value.trim().isEmpty()) return "";
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    private static String toJsonStringArray(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append("\"").append(list.get(i).replace("\"", "\\\"")).append("\"");
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private static void sendResponse(HttpExchange exchange, int code, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        // Thêm CORS để bảo mật mạng và cho phép mọi thiết bị truy cập
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    // =========================================================================
    // HANDLERS XỬ LÝ API
    // =========================================================================

    static class TemplatesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            File dir = new File("data/templates");
            if (!dir.exists()) dir.mkdirs();

            File[] files = dir.listFiles((d, name) -> name.endsWith(".jpg") || name.endsWith(".png"));
            List<String> templates = new ArrayList<>();
            if (files != null) {
                for (File file : files) {
                    templates.add(file.getName().substring(0, file.getName().lastIndexOf('.')));
                }
            }
            sendResponse(exchange, 200, toJsonStringArray(templates));
        }
    }

    static class CurrentTemplateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = getQueryParams(exchange);
            String cName = params.getOrDefault("class", "");
            String eName = params.getOrDefault("exam", "");

            String tId = java.util.prefs.Preferences.userRoot().node("ChamTracNghiem_N7").get("TEMPLATE_" + cName + "_" + eName, "BGD4");
            sendResponse(exchange, 200, tId);
        }
    }

    static class SetTemplateHandler implements HttpHandler {
        private final ServerSyncListener listener;
        public SetTemplateHandler(ServerSyncListener l) { this.listener = l; }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String cName = getHeaderSafely(exchange, "X-Class-Name");
            String eName = getHeaderSafely(exchange, "X-Exam-Name");
            String tId = getHeaderSafely(exchange, "X-Template-ID");

            if (cName.isEmpty() || eName.isEmpty() || tId.isEmpty()) {
                sendResponse(exchange, 400, "Thiếu tham số cấu hình mẫu phiếu");
                return;
            }

            java.util.prefs.Preferences.userRoot().node("ChamTracNghiem_N7").put("TEMPLATE_" + cName + "_" + eName, tId);
            sendResponse(exchange, 200, "OK");

            if (listener != null) listener.onTemplateChanged(cName, eName, tId);
        }
    }

    static class TemplateImageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = getQueryParams(exchange);
            String id = params.getOrDefault("id", "");

            File imgFile = new File("data/templates/" + id + ".jpg");
            if (!imgFile.exists()) imgFile = new File("data/templates/" + id + ".png");

            if (imgFile.exists()) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
                exchange.sendResponseHeaders(200, imgFile.length());
                try (OutputStream os = exchange.getResponseBody(); FileInputStream fis = new FileInputStream(imgFile)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = fis.read(buffer)) != -1) os.write(buffer, 0, count);
                }
            } else {
                sendResponse(exchange, 404, "Image Not Found");
            }
        }
    }

    static class ClassesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            File classesDir = new File("data/classes");
            File[] folders = classesDir.listFiles(f -> f.isDirectory() && !f.getName().equalsIgnoreCase("trash"));

            List<String> classNames = new ArrayList<>();
            if (folders != null) {
                for (File folder : folders) classNames.add(folder.getName());
            }
            sendResponse(exchange, 200, toJsonStringArray(classNames));
        }
    }

    static class CreateClassHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String className = getHeaderSafely(exchange, "X-Class-Name");
            if (className.isEmpty()) {
                sendResponse(exchange, 400, "Bad Request");
                return;
            }

            File newClassDir = new File("data/classes/" + className);
            if (!newClassDir.exists()) {
                newClassDir.mkdirs();
                new File(newClassDir, "students.dat").createNewFile();
                sendResponse(exchange, 201, "Created");
            } else {
                sendResponse(exchange, 409, "Exists");
            }
        }
    }

    static class StudentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = getQueryParams(exchange);
            String className = params.getOrDefault("class", "");

            ClassRoom cr = service.DataManager.loadClass(className);
            StringBuilder json = new StringBuilder("[");
            if (cr != null) {
                for (int i = 0; i < cr.students.size(); i++) {
                    ClassRoom.Student s = cr.students.get(i);
                    json.append("{\"stt\":").append(s.stt).append(",\"name\":\"").append(s.name).append("\"}");
                    if (i < cr.students.size() - 1) json.append(",");
                }
            }
            json.append("]");
            sendResponse(exchange, 200, json.toString());
        }
    }

    static class ExamsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = getQueryParams(exchange);
            String className = params.getOrDefault("class", "");

            List<String> exams = service.DataManager.listSavedExams(className);
            sendResponse(exchange, 200, toJsonStringArray(exams));
        }
    }

    static class CreateExamHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String className = getHeaderSafely(exchange, "X-Class-Name");
            String examName = getHeaderSafely(exchange, "X-Exam-Name");

            if (className.isEmpty() || examName.isEmpty()) {
                sendResponse(exchange, 400, "Missing Info");
                return;
            }

            File examDir = new File("data/classes/" + className + "/images/" + examName);
            File examSessionFile = new File("data/classes/" + className + "/exams/" + examName + ".dat");

            if (examDir.exists() || examSessionFile.exists()) {
                sendResponse(exchange, 409, "Exists");
            } else {
                examDir.mkdirs();
                model.ExamSession newSession = new model.ExamSession(examName, null);
                service.DataManager.saveSession(newSession, className);
                sendResponse(exchange, 201, "Created");
            }
        }
    }

    static class ExamCodesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = getQueryParams(exchange);
            String className = params.getOrDefault("class", "");
            String examName = params.getOrDefault("exam", "");

            model.ExamSession session = service.DataManager.loadSession(examName, className);
            List<String> validCodes = new ArrayList<>();

            if (session != null && session.getConfig() != null && session.getConfig().getExamCodes() != null) {
                for (String c : session.getConfig().getExamCodes()) {
                    if (c != null && !c.trim().isEmpty() && !c.trim().equalsIgnoreCase("Mặc định") && !c.trim().equalsIgnoreCase("Mặc định (Không mã)")) {
                        validCodes.add(c);
                    }
                }
            }
            sendResponse(exchange, 200, toJsonStringArray(validCodes));
        }
    }

    static class CreateExamCodeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String className = getHeaderSafely(exchange, "X-Class-Name");
            String examName = getHeaderSafely(exchange, "X-Exam-Name");
            String newCode = getHeaderSafely(exchange, "X-New-Code");

            if (className.isEmpty() || examName.isEmpty() || newCode.isEmpty()) {
                sendResponse(exchange, 400, "Missing Parameters");
                return;
            }

            model.ExamSession session = service.DataManager.loadSession(examName, className);
            if (session != null) {
                if (session.getConfig() == null) session.setConfig(new model.ExamConfig());
                if (!session.getConfig().getExamCodes().contains(newCode)) {
                    session.getConfig().getExamCodes().add(newCode);
                    service.DataManager.saveSession(session, className);
                }
                sendResponse(exchange, 201, "Created");
            } else {
                sendResponse(exchange, 404, "Exam Not Found");
            }
        }
    }

    static class UploadHandler implements HttpHandler {
        private final ServerSyncListener listener;
        public UploadHandler(ServerSyncListener listener) { this.listener = listener; }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String className = getHeaderSafely(exchange, "X-Class-Name");
            String examName = getHeaderSafely(exchange, "X-Exam-Name");
            String stt = getHeaderSafely(exchange, "X-Student-STT");
            String templateId = getHeaderSafely(exchange, "X-Template-ID");
            String examCode = getHeaderSafely(exchange, "X-Exam-Code"); // Có thể rỗng, không sao

            if (className.isEmpty() || examName.isEmpty() || stt.isEmpty()) {
                sendResponse(exchange, 400, "Incomplete Metadata");
                return;
            }

            File saveDir = new File("data/classes/" + className + "/images/" + examName);
            if (!saveDir.exists()) saveDir.mkdirs();
            File imageFile = new File(saveDir, stt + ".jpg");

            try (InputStream is = exchange.getRequestBody(); FileOutputStream fos = new FileOutputStream(imageFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) fos.write(buffer, 0, read);
            }

            System.out.println("📥 Đã nhận bài: Lớp " + className + " | STT: " + stt + " | Mẫu: " + templateId);
            sendResponse(exchange, 200, "Success");

            if (listener != null) listener.onImageReceived(className, examName, stt, templateId, examCode, imageFile.getAbsolutePath());
        }
    }

    private static Map<String, String> getQueryParams(HttpExchange exchange) {
        Map<String, String> result = new HashMap<>();
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] entry = param.split("=");
                if (entry.length > 1) {
                    try { result.put(entry[0], URLDecoder.decode(entry[1], StandardCharsets.UTF_8.name())); }
                    catch(Exception ignored){}
                }
            }
        }
        return result;
    }

    public static String getLocalIP() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                String name = iface.getDisplayName().toLowerCase();

                // [CHỐT CHẶN BẢO VỆ JAVA 8]: Không dùng iface.isVirtual() vì sẽ gây crash trên Java 8
                if (iface.isLoopback() || !iface.isUp() || name.contains("virtual") || name.contains("vmware")) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) return ip;
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }
}