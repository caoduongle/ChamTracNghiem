package service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import model.ClassRoom;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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

    static class TemplatesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            File dir = new File("data/templates");
            if (!dir.exists()) dir.mkdirs();

            File[] files = dir.listFiles((d, name) -> name.endsWith(".jpg") || name.endsWith(".png"));

            StringBuilder json = new StringBuilder("[");
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    String name = files[i].getName();
                    String id = name.substring(0, name.lastIndexOf('.'));
                    json.append("\"").append(id).append("\"");
                    if (i < files.length - 1) json.append(",");
                }
            }
            json.append("]");
            sendResponse(exchange, 200, json.toString());
        }
    }

    static class CurrentTemplateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = getQueryParams(exchange);
            String cName = params.get("class");
            String eName = params.get("exam");

            String tId = java.util.prefs.Preferences.userRoot().node("ChamTracNghiem_N7").get("TEMPLATE_" + cName + "_" + eName, "BGD4");
            sendResponse(exchange, 200, tId);
        }
    }

    static class SetTemplateHandler implements HttpHandler {
        private ServerSyncListener listener;
        public SetTemplateHandler(ServerSyncListener l) { this.listener = l; }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String cName = URLDecoder.decode(exchange.getRequestHeaders().getFirst("X-Class-Name"), StandardCharsets.UTF_8.name());
            String eName = URLDecoder.decode(exchange.getRequestHeaders().getFirst("X-Exam-Name"), StandardCharsets.UTF_8.name());
            String tId = URLDecoder.decode(exchange.getRequestHeaders().getFirst("X-Template-ID"), StandardCharsets.UTF_8.name());

            java.util.prefs.Preferences.userRoot().node("ChamTracNghiem_N7").put("TEMPLATE_" + cName + "_" + eName, tId);
            sendResponse(exchange, 200, "OK");

            if (listener != null) listener.onTemplateChanged(cName, eName, tId);
        }
    }

    static class TemplateImageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = getQueryParams(exchange);
            String id = params.get("id");

            File imgFile = new File("data/templates/" + id + ".jpg");
            if (!imgFile.exists()) imgFile = new File("data/templates/" + id + ".png");

            if (imgFile.exists()) {
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

    static class CreateClassHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String classNameRaw = exchange.getRequestHeaders().getFirst("X-Class-Name");
            if (classNameRaw == null) {
                sendResponse(exchange, 400, "Bad Request");
                return;
            }

            String className = URLDecoder.decode(classNameRaw, StandardCharsets.UTF_8.name());
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
            String className = params.get("class");

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
            String className = params.get("class");

            java.util.List<String> exams = service.DataManager.listSavedExams(className);
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < exams.size(); i++) {
                json.append("\"").append(exams.get(i)).append("\"");
                if (i < exams.size() - 1) json.append(",");
            }
            json.append("]");
            sendResponse(exchange, 200, json.toString());
        }
    }

    static class CreateExamHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String classNameRaw = exchange.getRequestHeaders().getFirst("X-Class-Name");
            String examNameRaw = exchange.getRequestHeaders().getFirst("X-Exam-Name");

            if (classNameRaw == null || examNameRaw == null) {
                sendResponse(exchange, 400, "Missing Info");
                return;
            }

            String className = URLDecoder.decode(classNameRaw, StandardCharsets.UTF_8.name());
            String examName = URLDecoder.decode(examNameRaw, StandardCharsets.UTF_8.name());

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
            String className = params.get("class");
            String examName = params.get("exam");

            model.ExamSession session = service.DataManager.loadSession(examName, className);
            StringBuilder json = new StringBuilder("[");

            if (session != null && session.getConfig() != null && session.getConfig().getExamCodes() != null) {
                java.util.List<String> codes = new java.util.ArrayList<>(session.getConfig().getExamCodes());

                // [VÁ LỖI]: Bỏ qua chữ "Mặc định" để điện thoại không bao giờ nhìn thấy
                codes.removeIf(c -> c == null || c.trim().isEmpty() || c.trim().equalsIgnoreCase("Mặc định") || c.trim().equalsIgnoreCase("Mặc định (Không mã)"));

                for (int i = 0; i < codes.size(); i++) {
                    json.append("\"").append(codes.get(i)).append("\"");
                    if (i < codes.size() - 1) json.append(",");
                }
            }
            json.append("]");
            sendResponse(exchange, 200, json.toString());
        }
    }

    static class CreateExamCodeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String className = URLDecoder.decode(exchange.getRequestHeaders().getFirst("X-Class-Name"), StandardCharsets.UTF_8.name());
            String examName = URLDecoder.decode(exchange.getRequestHeaders().getFirst("X-Exam-Name"), StandardCharsets.UTF_8.name());
            String newCode = URLDecoder.decode(exchange.getRequestHeaders().getFirst("X-New-Code"), StandardCharsets.UTF_8.name());

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
        private ServerSyncListener listener;
        public UploadHandler(ServerSyncListener listener) { this.listener = listener; }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String classNameRaw = exchange.getRequestHeaders().getFirst("X-Class-Name");
            String examNameRaw = exchange.getRequestHeaders().getFirst("X-Exam-Name");
            String stt = exchange.getRequestHeaders().getFirst("X-Student-STT");
            String templateId = exchange.getRequestHeaders().getFirst("X-Template-ID");

            String examCodeRaw = exchange.getRequestHeaders().getFirst("X-Exam-Code");
            String examCode = (examCodeRaw != null) ? URLDecoder.decode(examCodeRaw, StandardCharsets.UTF_8.name()) : "";

            if (classNameRaw == null || examNameRaw == null || stt == null) {
                sendResponse(exchange, 400, "Incomplete Metadata");
                return;
            }

            String className = URLDecoder.decode(classNameRaw, StandardCharsets.UTF_8.name());
            String examName = URLDecoder.decode(examNameRaw, StandardCharsets.UTF_8.name());

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
                    catch(Exception e){}
                }
            }
        }
        return result;
    }

    private static void sendResponse(HttpExchange exchange, int code, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    public static String getLocalIP() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;
                java.util.Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) return ip;
                    }
                }
            }
        } catch (Exception e) {}
        return "127.0.0.1";
    }
}