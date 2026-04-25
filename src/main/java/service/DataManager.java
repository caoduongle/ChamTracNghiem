package service;

import model.ExamSession;
import javax.swing.JOptionPane;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class DataManager {
    private static final String DATA_DIR = "data/";
    private static final String TRASH_DIR = "data/trash/";
    private static final long THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000; // 30 ngày tính bằng millisecond

    // --- LỚP HỖ TRỢ: Lưu thông tin file trong thùng rác ---
    public static class TrashedItem {
        public String originalName;
        public String trashFileName;
        public int daysLeft;

        public TrashedItem(String originalName, String trashFileName, int daysLeft) {
            this.originalName = originalName;
            this.trashFileName = trashFileName;
            this.daysLeft = daysLeft;
        }
    }

    public static void saveSession(ExamSession session) {
        try {
            File dir = new File(DATA_DIR);
            if (!dir.exists()) dir.mkdir();

            ObjectOutputStream oos = new ObjectOutputStream(
                    new FileOutputStream(DATA_DIR + session.getExamName() + ".dat"));
            oos.writeObject(session);
            oos.close();
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Lỗi khi ghi dữ liệu (Save):\n" + e.getMessage(), "Lỗi File", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static ExamSession loadSession(String examName) {
        try {
            ObjectInputStream ois = new ObjectInputStream(
                    new FileInputStream(DATA_DIR + examName + ".dat"));
            ExamSession session = (ExamSession) ois.readObject();
            ois.close();
            return session;
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Lỗi khi đọc dữ liệu (Load):\n" + e.getMessage(), "Lỗi File", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    public static List<String> listSavedExams() {
        cleanupTrash(); // TỰ ĐỘNG DỌN RÁC QUÁ 30 NGÀY MỖI KHI MỞ DANH SÁCH

        List<String> exams = new ArrayList<>();
        File dir = new File(DATA_DIR);
        if (dir.exists()) {
            for (File f : dir.listFiles()) {
                if (f.isFile() && f.getName().endsWith(".dat")) {
                    exams.add(f.getName().replace(".dat", ""));
                }
            }
        }
        return exams;
    }

    // ==========================================
    // ============ TÍNH NĂNG THÙNG RÁC =========
    // ==========================================

    // 1. Chuyển vào thùng rác (Đổi tên thêm hậu tố _deleted_timestamp)
    public static void moveToTrash(String examName) {
        try {
            File trashDir = new File(TRASH_DIR);
            if (!trashDir.exists()) trashDir.mkdirs();

            File src = new File(DATA_DIR + examName + ".dat");
            if (src.exists()) {
                File dest = new File(TRASH_DIR + examName + ".dat_deleted_" + System.currentTimeMillis());
                src.renameTo(dest);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // 2. Lấy danh sách thùng rác
    public static List<TrashedItem> listTrashedExams() {
        List<TrashedItem> trashed = new ArrayList<>();
        File dir = new File(TRASH_DIR);
        if (dir.exists()) {
            long now = System.currentTimeMillis();
            for (File f : dir.listFiles()) {
                if (f.getName().contains(".dat_deleted_")) {
                    String[] parts = f.getName().split(".dat_deleted_");
                    String originalName = parts[0];
                    long deleteTime = Long.parseLong(parts[1]);

                    long elapsed = now - deleteTime;
                    int daysLeft = (int) ((THIRTY_DAYS_MS - elapsed) / (24 * 60 * 60 * 1000));

                    if (daysLeft < 0) daysLeft = 0;
                    trashed.add(new TrashedItem(originalName, f.getName(), daysLeft));
                }
            }
        }
        return trashed;
    }

    // 3. Khôi phục từ thùng rác
    public static void restoreFromTrash(String trashFileName) {
        File src = new File(TRASH_DIR + trashFileName);
        if (src.exists()) {
            String originalName = trashFileName.split(".dat_deleted_")[0];
            File dest = new File(DATA_DIR + originalName + ".dat");
            src.renameTo(dest);
        }
    }

    // 4. Xóa vĩnh viễn 1 file
    public static void deletePermanently(String trashFileName) {
        File target = new File(TRASH_DIR + trashFileName);
        if (target.exists()) target.delete();
    }

    // 5. Tự động dọn file quá 30 ngày
    private static void cleanupTrash() {
        File dir = new File(TRASH_DIR);
        if (!dir.exists()) return;

        long now = System.currentTimeMillis();
        for (File f : dir.listFiles()) {
            if (f.getName().contains(".dat_deleted_")) {
                try {
                    long deleteTime = Long.parseLong(f.getName().split(".dat_deleted_")[1]);
                    if (now - deleteTime > THIRTY_DAYS_MS) {
                        f.delete(); // Quá 30 ngày -> Bay màu
                    }
                } catch (Exception ignored) {}
            }
        }
    }
    // ==========================================
    // ============ CÀI ĐẶT ỨNG DỤNG ============
    // ==========================================

    public static boolean shouldShowTutorial() {
        try {
            java.util.Properties props = new java.util.Properties();
            File f = new File(DATA_DIR + "settings.properties");
            if (f.exists()) {
                props.load(new FileInputStream(f));
                return Boolean.parseBoolean(props.getProperty("showTutorial", "true"));
            }
        } catch (Exception e) { e.printStackTrace(); }
        return true; // Mặc định luôn hiện nếu chưa có file
    }

    public static void setTutorialPreference(boolean show) {
        try {
            java.util.Properties props = new java.util.Properties();
            File dir = new File(DATA_DIR);
            if (!dir.exists()) dir.mkdirs();

            File f = new File(DATA_DIR + "settings.properties");
            if (f.exists()) props.load(new FileInputStream(f));

            props.setProperty("showTutorial", String.valueOf(show));
            props.store(new FileOutputStream(f), "App Settings");
        } catch (Exception e) { e.printStackTrace(); }
    }
}