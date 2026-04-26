package service;

import model.ExamSession;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import java.nio.file.*;
import java.util.zip.*;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.stream.Stream;

public class DataManager {
    private static final long THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000;
    private static final Preferences sysPrefs = Preferences.userRoot().node("ChamTracNghiem_N7_SystemSettings");
    private static final String PREF_FILE = "data/tutorial_hidden.flag";
    private static final String CLASS_DIR = "data/classes/";
    private static final String CLASS_TRASH_DIR = "data/classes/trash/";

    public interface ProgressListener {
        void onProgress(int current, int total, String fileName);
        boolean isCanceled();
    }

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

    // --- CÀI ĐẶT HỆ THỐNG CƠ BẢN ---
    public static boolean isSoundEnabled() { return sysPrefs.getBoolean("sound_enabled", true); }
    public static void setSoundEnabled(boolean enabled) { sysPrefs.putBoolean("sound_enabled", enabled); }
    public static String getDefaultExportPath() { return sysPrefs.get("default_export_path", System.getProperty("user.home") + File.separator + "Documents"); }
    public static void setDefaultExportPath(String path) { sysPrefs.put("default_export_path", path); }
    public static boolean isAutoSavePosition() { return sysPrefs.getBoolean("auto_save_pos", true); }
    public static void setAutoSavePosition(boolean enabled) { sysPrefs.putBoolean("auto_save_pos", enabled); }
    public static boolean isDarkMode() { return sysPrefs.getBoolean("dark_mode", false); }
    public static void setDarkMode(boolean enabled) { sysPrefs.putBoolean("dark_mode", enabled); }
    public static int getOmrThreshold() { return sysPrefs.getInt("omr_threshold", 155); }
    public static void setOmrThreshold(int threshold) { sysPrefs.putInt("omr_threshold", threshold); }

    public static boolean isMultiThreadEnabled() { return sysPrefs.getBoolean("multi_thread", false); }
    public static void setMultiThreadEnabled(boolean enabled) { sysPrefs.putBoolean("multi_thread", enabled); }

    public static boolean isAutoCleanProcessed() { return sysPrefs.getBoolean("auto_clean_processed", true); }
    public static void setAutoCleanProcessed(boolean enabled) { sysPrefs.putBoolean("auto_clean_processed", enabled); }

    // =========================================================
    // [FIX]: THUẬT TOÁN DỌN DẸP SÂU (CHUẨN TIẾN ĐỘ 100%)
    // =========================================================
    public static void performDeepCleanup(ProgressListener listener) throws IOException {
        Path classesDir = Paths.get("data/classes");
        if (!Files.exists(classesDir)) return;

        // BƯỚC 1: Đếm tổng số file ảnh rác (_processed)
        List<Path> processedFiles = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(classesDir)) {
            processedFiles = walk
                    .filter(p -> !Files.isDirectory(p) && p.getFileName().toString().contains("_processed."))
                    .collect(Collectors.toList());
        }

        // BƯỚC 2: Tìm các thư mục Lớp/Đề mồ côi
        List<Path> orphanedDirs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(classesDir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry) && !entry.getFileName().toString().equals("trash")) {
                    String className = entry.getFileName().toString();
                    Path classDatFile = classesDir.resolve(className + ".dat");
                    if (!Files.exists(classDatFile)) {
                        orphanedDirs.add(entry); // Lớp mồ côi
                    } else {
                        Path imagesDir = entry.resolve("images");
                        Path examsDir = entry.resolve("exams");
                        if (Files.exists(imagesDir)) {
                            try (DirectoryStream<Path> imgStream = Files.newDirectoryStream(imagesDir)) {
                                for (Path examImgFolder : imgStream) {
                                    if (Files.isDirectory(examImgFolder)) {
                                        String examName = examImgFolder.getFileName().toString();
                                        if (!Files.exists(examsDir.resolve(examName + ".dat"))) {
                                            orphanedDirs.add(examImgFolder); // Đề mồ côi
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // BƯỚC 3: Tính tổng số công việc để thanh tiến độ chạy mượt
        int totalTasks = orphanedDirs.size() + processedFiles.size();
        int current = 0;

        if (totalTasks == 0) {
            if (listener != null) listener.onProgress(100, 100, "Hệ thống sạch sẽ, không có rác.");
            return;
        }

        // Xóa thư mục mồ côi
        for (Path dir : orphanedDirs) {
            if (listener != null && listener.isCanceled()) return;
            current++;
            if (listener != null) listener.onProgress(current, totalTasks, "Đang dọn sạch dữ liệu cũ: " + dir.getFileName().toString());
            deleteDirectoryRecursively(dir);
        }

        // Xóa ảnh rác
        for (Path p : processedFiles) {
            if (listener != null && listener.isCanceled()) return;
            current++;
            if (listener != null) listener.onProgress(current, totalTasks, "Đang xóa ảnh: " + p.getFileName().toString());
            Files.deleteIfExists(p);
        }
    }

    // Hàm đệ quy hỗ trợ xóa thư mục có bọc try-with-resources để chống rò rỉ bộ nhớ
    private static void deleteDirectoryRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
    }
    // =========================================================

    // --- LOGIC SAO LƯU (BACKUP) ---
    public static void backupData(File targetZip, ProgressListener listener) throws IOException {
        Path sourceDirPath = Paths.get("data");
        if (!Files.exists(sourceDirPath)) return;

        List<Path> allFiles;
        try (Stream<Path> walk = Files.walk(sourceDirPath)) {
            allFiles = walk.filter(path -> !Files.isDirectory(path)).collect(Collectors.toList());
        }

        int totalFiles = allFiles.size();
        int current = 0;

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(targetZip))) {
            for (Path path : allFiles) {
                if (listener != null && listener.isCanceled()) throw new IOException("Người dùng đã hủy.");
                current++;
                String fileName = sourceDirPath.relativize(path).toString();
                if (listener != null) listener.onProgress(current, totalFiles, fileName);

                ZipEntry zipEntry = new ZipEntry(fileName);
                zos.putNextEntry(zipEntry);
                Files.copy(path, zos);
                zos.closeEntry();
            }
        }
    }

    // --- LOGIC PHỤC HỒI (RESTORE) ---
    public static void restoreData(File zipFile, ProgressListener listener) throws IOException {
        Path destDirPath = Paths.get("data");
        if (!Files.exists(destDirPath)) Files.createDirectories(destDirPath);

        int totalEntries = 0;
        try (ZipFile zip = new ZipFile(zipFile)) { totalEntries = zip.size(); }

        int current = 0;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                if (listener != null && listener.isCanceled()) throw new IOException("Người dùng đã hủy.");
                current++;
                Path newPath = destDirPath.resolve(entry.getName());
                if (listener != null) listener.onProgress(current, totalEntries, entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    if (newPath.getParent() != null && Files.notExists(newPath.getParent())) {
                        Files.createDirectories(newPath.getParent());
                    }
                    Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
                entry = zis.getNextEntry();
            }
        }
    }

    // --- QUẢN LÝ LỚP HỌC & ĐỀ THI ---
    private static String getExamDir(String className) { return "data/classes/" + className + "/exams/"; }
    private static String getTrashDir(String className) { return "data/classes/" + className + "/trash/"; }

    public static void saveSession(ExamSession session, String className) {
        try {
            File dir = new File(getExamDir(className));
            if (!dir.exists()) dir.mkdirs();
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(getExamDir(className) + session.getExamName() + ".dat"))) {
                oos.writeObject(session);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static ExamSession loadSession(String examName, String className) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(getExamDir(className) + examName + ".dat"))) {
            return (ExamSession) ois.readObject();
        } catch (Exception e) { return null; }
    }

    public static List<String> listSavedExams(String className) {
        cleanupTrash(className);
        List<String> exams = new ArrayList<>();
        File dir = new File(getExamDir(className));
        if (dir.exists()) {
            for (File f : dir.listFiles()) {
                if (f.isFile() && f.getName().endsWith(".dat")) exams.add(f.getName().replace(".dat", ""));
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
                        int daysLeft = (int) ((THIRTY_DAYS_MS - (now - deleteTime)) / (24 * 60 * 60 * 1000));
                        trashed.add(new TrashedItem(parts[0], f.getName(), Math.max(0, daysLeft), f.lastModified(), deleteTime));
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
            if (session != null) { session.setExamName(baseName); saveSession(session, className); }
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
                if (session != null) { session.setExamName(newName); saveSession(session, className); }
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

    public static void saveClass(model.ClassRoom cr) {
        try {
            File dir = new File(CLASS_DIR); if (!dir.exists()) dir.mkdirs();
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(CLASS_DIR + cr.className + ".dat"))) {
                oos.writeObject(cr);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static model.ClassRoom loadClass(String className) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(CLASS_DIR + className + ".dat"))) {
            return (model.ClassRoom) ois.readObject();
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

    public static void renameClass(String oldName, String newName) {
        File oldFile = new File(CLASS_DIR + oldName + ".dat");
        File newFile = new File(CLASS_DIR + newName + ".dat");
        File oldDir = new File(CLASS_DIR + oldName);
        File newDir = new File(CLASS_DIR + newName);

        if (oldFile.exists() && !newFile.exists()) {
            if (oldFile.renameTo(newFile)) {
                if (oldDir.exists()) oldDir.renameTo(newDir);
                model.ClassRoom cr = loadClass(newName);
                if (cr != null) { cr.className = newName; saveClass(cr); }
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
                        int daysLeft = (int) ((THIRTY_DAYS_MS - (now - deleteTime)) / (24 * 60 * 60 * 1000));
                        trashed.add(new TrashedItem(parts[0], f.getName(), Math.max(0, daysLeft), f.lastModified(), deleteTime));
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
        while (dest.exists()) { count++; baseName = originalName + " (" + count + ")"; dest = new File(CLASS_DIR + baseName + ".dat"); }

        if (src.renameTo(dest)) {
            model.ClassRoom cr = loadClass(baseName);
            if (cr != null) { cr.className = baseName; saveClass(cr); }
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

    public static void setTutorialPreference(boolean showTutorial) {
        try {
            File dir = new File("data"); if (!dir.exists()) dir.mkdirs();
            File flagFile = new File(PREF_FILE);
            if (showTutorial) { if (flagFile.exists()) flagFile.delete(); } else { flagFile.createNewFile(); }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static boolean shouldShowTutorial() { return !new File(PREF_FILE).exists(); }
}