package service;

import model.ExamSession;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class DataManager {
    // Thời gian lưu thùng rác: 30 ngày
    private static final long THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000;

    public static class TrashedItem {
        public String originalName;
        public String trashFileName;
        public int daysLeft;
        public long creationTime;
        public long deletionTime;

        public TrashedItem(String originalName, String trashFileName, int daysLeft, long creationTime, long deletionTime) {
            this.originalName = originalName;
            this.trashFileName = trashFileName;
            this.daysLeft = daysLeft;
            this.creationTime = creationTime;
            this.deletionTime = deletionTime;
        }
    }

    private static String getExamDir(String className) { return "data/classes/" + className + "/exams/"; }
    private static String getTrashDir(String className) { return "data/classes/" + className + "/trash/"; }

    public static void saveSession(ExamSession session, String className) {
        try {
            File dir = new File(getExamDir(className));
            if (!dir.exists()) dir.mkdirs();
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(getExamDir(className) + session.getExamName() + ".dat"));
            oos.writeObject(session);
            oos.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static ExamSession loadSession(String examName, String className) {
        try {
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(getExamDir(className) + examName + ".dat"));
            ExamSession session = (ExamSession) ois.readObject();
            ois.close();
            return session;
        } catch (Exception e) { return null; }
    }

    public static List<String> listSavedExams(String className) {
        cleanupTrash(className);
        List<String> exams = new ArrayList<>();
        File dir = new File(getExamDir(className));
        if (dir.exists()) {
            for (File f : dir.listFiles()) {
                if (f.isFile() && f.getName().endsWith(".dat")) {
                    exams.add(f.getName().replace(".dat", ""));
                }
            }
        }
        return exams;
    }

    public static void moveToTrash(String examName, String className) {
        try {
            File trashDir = new File(getTrashDir(className));
            if (!trashDir.exists()) trashDir.mkdirs();
            File src = new File(getExamDir(className) + examName + ".dat");
            if (src.exists()) {
                File dest = new File(getTrashDir(className) + examName + ".dat_deleted_" + System.currentTimeMillis());
                src.renameTo(dest);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static List<TrashedItem> listTrashedExams(String className) {
        List<TrashedItem> trashed = new ArrayList<>();
        File dir = new File(getTrashDir(className));
        if (dir.exists()) {
            long now = System.currentTimeMillis();
            for (File f : dir.listFiles()) {
                if (f.getName().contains(".dat_deleted_")) {
                    try {
                        String[] parts = f.getName().split("\\.dat_deleted_");
                        long deleteTime = Long.parseLong(parts[1]);
                        long elapsed = now - deleteTime;
                        int daysLeft = (int) ((THIRTY_DAYS_MS - elapsed) / (24 * 60 * 60 * 1000));
                        if (daysLeft < 0) daysLeft = 0;
                        trashed.add(new TrashedItem(parts[0], f.getName(), daysLeft, f.lastModified(), deleteTime));
                    } catch (Exception e) { }
                }
            }
        }
        return trashed;
    }

    public static void restoreFromTrash(String trashFileName, String className) {
        File src = new File(getTrashDir(className) + trashFileName);
        if (!src.exists()) return;

        long originalTime = src.lastModified();

        String originalName = trashFileName.split("\\.dat_deleted_")[0];
        String baseName = originalName;
        File dest = new File(getExamDir(className) + baseName + ".dat");

        int count = 1;
        while (dest.exists()) {
            count++;
            baseName = originalName + " (" + count + ")";
            dest = new File(getExamDir(className) + baseName + ".dat");
        }

        if (src.renameTo(dest)) {
            ExamSession session = loadSession(baseName, className);
            if (session != null) {
                session.setExamName(baseName);
                saveSession(session, className);
            }
            dest.setLastModified(originalTime);
        }
    }

    public static void renameExam(String oldName, String newName, String className) {
        File src = new File(getExamDir(className) + oldName + ".dat");
        File dest = new File(getExamDir(className) + newName + ".dat");
        if (src.exists() && !dest.exists()) {
            long originalTime = src.lastModified();
            if (src.renameTo(dest)) {
                ExamSession session = loadSession(newName, className);
                if (session != null) {
                    session.setExamName(newName);
                    saveSession(session, className);
                }
                dest.setLastModified(originalTime);
            }
        }
    }

    public static void deletePermanently(String trashFileName, String className) {
        File target = new File(getTrashDir(className) + trashFileName);
        if (target.exists()) target.delete();
    }

    private static void cleanupTrash(String className) {
        File dir = new File(getTrashDir(className));
        if (!dir.exists()) return;
        long now = System.currentTimeMillis();
        for (File f : dir.listFiles()) {
            if (f.getName().contains(".dat_deleted_")) {
                try {
                    long deleteTime = Long.parseLong(f.getName().split("\\.dat_deleted_")[1]);
                    if (now - deleteTime > THIRTY_DAYS_MS) f.delete();
                } catch (Exception ignored) {}
            }
        }
    }

    // ==========================================
    // ============ QUẢN LÝ LỚP HỌC =============
    // ==========================================
    private static final String CLASS_DIR = "data/classes/";
    private static final String CLASS_TRASH_DIR = "data/classes/trash/";

    public static void saveClass(model.ClassRoom cr) {
        try {
            File dir = new File(CLASS_DIR); if (!dir.exists()) dir.mkdirs();
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(CLASS_DIR + cr.className + ".dat"));
            oos.writeObject(cr); oos.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static model.ClassRoom loadClass(String className) {
        try {
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(CLASS_DIR + className + ".dat"));
            model.ClassRoom cr = (model.ClassRoom) ois.readObject(); ois.close();
            return cr;
        } catch (Exception e) { return null; }
    }

    public static List<String> listClasses() {
        cleanupClassTrash();
        List<String> list = new ArrayList<>();
        File dir = new File(CLASS_DIR);
        if (dir.exists()) {
            for (File f : dir.listFiles()) {
                if (f.isFile() && f.getName().endsWith(".dat")) list.add(f.getName().replace(".dat", ""));
            }
        }
        return list;
    }

    // --- LOGIC MỚI: ĐỔI TÊN LỚP HỌC (VÀ CẢ THƯ MỤC CỦA NÓ) ---
    public static void renameClass(String oldName, String newName) {
        File oldFile = new File(CLASS_DIR + oldName + ".dat");
        File newFile = new File(CLASS_DIR + newName + ".dat");
        File oldDir = new File(CLASS_DIR + oldName); // Thư mục chứa đề thi, hình ảnh
        File newDir = new File(CLASS_DIR + newName);

        if (oldFile.exists() && !newFile.exists()) {
            if (oldFile.renameTo(newFile)) {
                // Đổi tên thư mục dữ liệu lớp học đi kèm
                if (oldDir.exists()) {
                    oldDir.renameTo(newDir);
                }

                // Mở file .dat ra, sửa tên class bên trong và lưu lại
                model.ClassRoom cr = loadClass(newName);
                if (cr != null) {
                    cr.className = newName;
                    saveClass(cr);
                }
            }
        }
    }

    public static void deleteClass(String className) {
        try {
            File trashDir = new File(CLASS_TRASH_DIR); if (!trashDir.exists()) trashDir.mkdirs();
            File src = new File(CLASS_DIR + className + ".dat");
            if (src.exists()) src.renameTo(new File(CLASS_TRASH_DIR + className + ".dat_deleted_" + System.currentTimeMillis()));
        } catch(Exception e) {}
    }

    public static List<TrashedItem> listTrashedClasses() {
        List<TrashedItem> trashed = new ArrayList<>();
        File dir = new File(CLASS_TRASH_DIR);
        if (dir.exists()) {
            long now = System.currentTimeMillis();
            for (File f : dir.listFiles()) {
                if (f.getName().contains(".dat_deleted_")) {
                    try {
                        String[] parts = f.getName().split("\\.dat_deleted_");
                        long deleteTime = Long.parseLong(parts[1]);
                        long elapsed = now - deleteTime;
                        int daysLeft = (int) ((THIRTY_DAYS_MS - elapsed) / (24 * 60 * 60 * 1000));
                        if (daysLeft < 0) daysLeft = 0;
                        trashed.add(new TrashedItem(parts[0], f.getName(), daysLeft, f.lastModified(), deleteTime));
                    } catch (Exception e) { }
                }
            }
        }
        return trashed;
    }

    public static void restoreClassFromTrash(String trashFileName) {
        File src = new File(CLASS_TRASH_DIR + trashFileName);
        if (!src.exists()) return;

        long originalTime = src.lastModified();
        String originalName = trashFileName.split("\\.dat_deleted_")[0];
        String baseName = originalName;
        File dest = new File(CLASS_DIR + baseName + ".dat");

        int count = 1;
        while (dest.exists()) {
            count++;
            baseName = originalName + " (" + count + ")";
            dest = new File(CLASS_DIR + baseName + ".dat");
        }

        if (src.renameTo(dest)) {
            model.ClassRoom cr = loadClass(baseName);
            if (cr != null) {
                cr.className = baseName;
                saveClass(cr);
            }
            dest.setLastModified(originalTime);
        }
    }

    public static void deleteClassPermanently(String trashFileName) {
        File target = new File(CLASS_TRASH_DIR + trashFileName);
        if (target.exists()) target.delete();
    }

    private static void cleanupClassTrash() {
        File dir = new File(CLASS_TRASH_DIR);
        if (!dir.exists()) return;
        long now = System.currentTimeMillis();
        for (File f : dir.listFiles()) {
            if (f.getName().contains(".dat_deleted_")) {
                try {
                    long deleteTime = Long.parseLong(f.getName().split("\\.dat_deleted_")[1]);
                    if (now - deleteTime > THIRTY_DAYS_MS) f.delete();
                } catch (Exception ignored) {}
            }
        }
    }

    public static boolean shouldShowTutorial() { return true; }
}