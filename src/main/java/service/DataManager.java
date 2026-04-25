package service;

import model.ExamSession;
import javax.swing.JOptionPane;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class DataManager {
    private static final String DATA_DIR = "data/";
    private static final String TRASH_DIR = "data/trash/";
    private static final long THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000;

    // --- LỚP HỖ TRỢ: Đã thêm đủ 5 trường để phân biệt file trùng tên ---
    public static class TrashedItem {
        public String originalName;
        public String trashFileName;
        public int daysLeft;
        public long creationTime; // Ngày tạo đề
        public long deletionTime; // Ngày xóa đề

        public TrashedItem(String originalName, String trashFileName, int daysLeft, long creationTime, long deletionTime) {
            this.originalName = originalName;
            this.trashFileName = trashFileName;
            this.daysLeft = daysLeft;
            this.creationTime = creationTime;
            this.deletionTime = deletionTime;
        }
    }

    public static void saveSession(ExamSession session) {
        try {
            File dir = new File(DATA_DIR);
            if (!dir.exists()) dir.mkdir();
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_DIR + session.getExamName() + ".dat"));
            oos.writeObject(session);
            oos.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static ExamSession loadSession(String examName) {
        try {
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(DATA_DIR + examName + ".dat"));
            ExamSession session = (ExamSession) ois.readObject();
            ois.close();
            return session;
        } catch (Exception e) { return null; }
    }

    public static List<String> listSavedExams() {
        cleanupTrash();
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

    // CHỈ GIỮ LẠI 1 HÀM NÀY (Đã sửa lỗi Ambiguous)
    public static List<TrashedItem> listTrashedExams() {
        List<TrashedItem> trashed = new ArrayList<>();
        File dir = new File(TRASH_DIR);
        if (dir.exists()) {
            long now = System.currentTimeMillis();
            for (File f : dir.listFiles()) {
                if (f.getName().contains(".dat_deleted_")) {
                    try {
                        String[] parts = f.getName().split(".dat_deleted_");
                        long deleteTime = Long.parseLong(parts[1]);
                        long elapsed = now - deleteTime;
                        int daysLeft = (int) ((THIRTY_DAYS_MS - elapsed) / (24 * 60 * 60 * 1000));
                        if (daysLeft < 0) daysLeft = 0;

                        // creationTime lấy từ ngày sửa đổi cuối của file, deletionTime lấy từ tên file
                        trashed.add(new TrashedItem(parts[0], f.getName(), daysLeft, f.lastModified(), deleteTime));
                    } catch (Exception e) { }
                }
            }
        }
        return trashed;
    }

    public static void restoreFromTrash(String trashFileName) {
        File src = new File(TRASH_DIR + trashFileName);
        if (src.exists()) {
            String originalName = trashFileName.split(".dat_deleted_")[0];
            File dest = new File(DATA_DIR + originalName + ".dat");
            src.renameTo(dest);
        }
    }

    public static void deletePermanently(String trashFileName) {
        File target = new File(TRASH_DIR + trashFileName);
        if (target.exists()) target.delete();
    }

    private static void cleanupTrash() {
        File dir = new File(TRASH_DIR);
        if (!dir.exists()) return;
        long now = System.currentTimeMillis();
        for (File f : dir.listFiles()) {
            if (f.getName().contains(".dat_deleted_")) {
                try {
                    long deleteTime = Long.parseLong(f.getName().split(".dat_deleted_")[1]);
                    if (now - deleteTime > THIRTY_DAYS_MS) f.delete();
                } catch (Exception ignored) {}
            }
        }
    }

    public static boolean shouldShowTutorial() {
        try {
            java.util.Properties props = new java.util.Properties();
            File f = new File(DATA_DIR + "settings.properties");
            if (f.exists()) {
                props.load(new FileInputStream(f));
                return Boolean.parseBoolean(props.getProperty("showTutorial", "true"));
            }
        } catch (Exception e) { }
        return true;
    }

    public static void setTutorialPreference(boolean show) {
        try {
            java.util.Properties props = new java.util.Properties();
            File f = new File(DATA_DIR + "settings.properties");
            props.setProperty("showTutorial", String.valueOf(show));
            props.store(new FileOutputStream(f), "App Settings");
        } catch (Exception e) { }
    }
}