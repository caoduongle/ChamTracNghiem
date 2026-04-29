package service;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateService {
    public static final String CURRENT_VERSION = "2.0.1"; // NHỚ ĐỔI SỐ NÀY TRƯỚC KHI XUẤT FILE MỚI
    private static final String VERSION_URL = "https://raw.githubusercontent.com/caoduongle/ChamTracNghiem/main/version.txt";
    private static final String DOWNLOAD_BASE_URL = "https://github.com/caoduongle/ChamTracNghiem/releases/download/v";
    private static final String TEMPLATE_JSON_URL = "https://raw.githubusercontent.com/caoduongle/ChamTracNghiem/main/templates.json";

    private static final int NUM_THREADS = 4; // Tải 4 luồng cùng lúc

    public static void checkForUpdates(JFrame parentView) {
        // [TỰ ĐỘNG]: Ngay khi check Update, nó sẽ tự động đồng bộ Mẫu phiếu ngầm
        syncTemplatesFromServer();

        try {
            String noCacheUrl = VERSION_URL + "?t=" + System.currentTimeMillis();
            URL url = new URL(noCacheUrl);
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
            String latestVersion = in.readLine().trim();
            in.close();

            if (!CURRENT_VERSION.equals(latestVersion)) {
                int choice = JOptionPane.showConfirmDialog(parentView,
                        "Đã có phiên bản mới v" + latestVersion + "! Bạn có muốn cập nhật ngay không?\n(Hỗ trợ Tải đa luồng, Tạm dừng & Tiếp tục)",
                        "Cập nhật phần mềm", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);

                if (choice == JOptionPane.YES_OPTION) {
                    UpdateDialog dialog = new UpdateDialog(parentView, latestVersion);
                    dialog.setVisible(true);
                }
            }
        } catch (Exception e) {
            System.out.println("Không thể kiểm tra bản cập nhật.");
        }
    }

    // =====================================================================
    // [ĐÃ TỐI ƯU]: ĐỒNG BỘ MẪU PHIẾU KHÔNG CẦN THƯ VIỆN ORG.JSON
    // =====================================================================
    public static void syncTemplatesFromServer() {
        new Thread(() -> {
            try {
                URL url = new URL(TEMPLATE_JSON_URL + "?t=" + System.currentTimeMillis());
                BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) content.append(line);
                in.close();

                File dir = new File("data/templates");
                if (!dir.exists()) dir.mkdirs();

                // Sử dụng Regex để tách lấy "id" và "url" từ chuỗi JSON đơn giản
                String json = content.toString();
                Pattern pId = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
                Pattern pUrl = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

                String[] blocks = json.split("\\}");
                for (String block : blocks) {
                    Matcher mId = pId.matcher(block);
                    Matcher mUrl = pUrl.matcher(block);

                    if (mId.find() && mUrl.find()) {
                        String id = mId.group(1);
                        String downloadUrl = mUrl.group(2);
                        File localFile = new File(dir, id + ".jpg");

                        if (!localFile.exists()) {
                            System.out.println("🚚 Đang tải mẫu phiếu mới từ Server: " + id);
                            downloadFile(downloadUrl, localFile);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Không thể đồng bộ mẫu phiếu từ GitHub: " + e.getMessage());
            }
        }).start();
    }

    private static void downloadFile(String urlStr, File targetFile) throws IOException {
        URL url = new URL(urlStr);
        try (InputStream in = url.openStream();
             FileOutputStream out = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        }
    }

    private static String getFinalUrl(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setInstanceFollowRedirects(false);
        int status = conn.getResponseCode();
        if (status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_SEE_OTHER) {
            String redirectUrl = conn.getHeaderField("Location");
            return getFinalUrl(redirectUrl);
        }
        return urlStr;
    }

    private static void cleanOldPartFiles(String targetVersion) {
        File dir = new File(".");
        File[] files = dir.listFiles((d, name) -> name.startsWith("update_v") && name.contains(".part"));
        if (files != null) {
            for (File f : files) {
                if (!f.getName().contains("update_v" + targetVersion)) {
                    f.delete();
                }
            }
        }
    }

    private static void executeUpdaterScript(String latestVersion) {
        try {
            String newExeName = "PhanMemChamThi_v" + latestVersion + ".exe";
            String batCode =
                    "@echo off\r\n" +
                            "echo Dang xoa cac phien ban cu va cap nhat phan mem...\r\n" +
                            "timeout /t 5 /nobreak > NUL\r\n" +
                            "del /f /q PhanMemChamThi_v*.exe\r\n" +
                            "ren update_temp.exe \"" + newExeName + "\"\r\n" +
                            "start \"\" \"" + newExeName + "\"\r\n" +
                            "del \"%~f0\"";

            File batFile = new File("update.bat");
            FileWriter fw = new FileWriter(batFile);
            fw.write(batCode);
            fw.close();

            Runtime.getRuntime().exec("cmd /c start update.bat");
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================================================================
    // HỘP THOẠI QUẢN LÝ DOWNLOAD CÓ NÚT PAUSE / RESUME
    // =========================================================================
    private static class UpdateDialog extends JDialog {
        private JProgressBar progressBar;
        private JLabel lblStatus;
        private JButton btnPauseResume, btnCancel;

        private String version;
        private String finalUrl;
        private long totalBytes = 0;
        private boolean isDownloading = false;
        private DownloadWorker currentWorker;

        public UpdateDialog(JFrame parent, String version) {
            super(parent, "Trình cập nhật phần mềm (v" + version + ")", true);
            this.version = version;
            setSize(450, 180);
            setLocationRelativeTo(parent);
            setLayout(new BorderLayout(10, 10));
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

            JPanel pnlCenter = new JPanel(new GridLayout(2, 1, 5, 5));
            pnlCenter.setBorder(BorderFactory.createEmptyBorder(15, 15, 5, 15));

            JLabel lblTitle = new JLabel("Đang kết nối tới máy chủ tải xuống...");
            lblTitle.setFont(new Font("Arial", Font.BOLD, 13));
            progressBar = new JProgressBar(0, 100);
            progressBar.setStringPainted(true);

            pnlCenter.add(lblTitle);
            pnlCenter.add(progressBar);
            add(pnlCenter, BorderLayout.CENTER);

            JPanel pnlBottom = new JPanel(new BorderLayout());
            pnlBottom.setBorder(BorderFactory.createEmptyBorder(5, 15, 15, 15));

            lblStatus = new JLabel("Đã tải: 0.0 MB / 0.0 MB");
            lblStatus.setForeground(Color.GRAY);

            JPanel pnlBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
            btnPauseResume = new JButton("⏸ Tạm dừng");
            btnCancel = new JButton("Hủy bỏ");

            pnlBtns.add(btnPauseResume);
            pnlBtns.add(btnCancel);

            pnlBottom.add(lblStatus, BorderLayout.WEST);
            pnlBottom.add(pnlBtns, BorderLayout.EAST);
            add(pnlBottom, BorderLayout.SOUTH);

            btnPauseResume.addActionListener(e -> {
                if (isDownloading) {
                    btnPauseResume.setEnabled(false);
                    btnPauseResume.setText("Đang dừng...");
                    if (currentWorker != null) currentWorker.cancel(true);
                } else {
                    btnPauseResume.setText("⏸ Tạm dừng");
                    startDownloadWorker();
                }
            });

            btnCancel.addActionListener(e -> {
                if (currentWorker != null) currentWorker.cancel(true);
                dispose();
            });

            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    btnCancel.doClick();
                }
            });

            startDownloadWorker();
        }

        private void startDownloadWorker() {
            isDownloading = true;
            btnPauseResume.setEnabled(true);
            cleanOldPartFiles(version);
            currentWorker = new DownloadWorker();
            currentWorker.execute();
        }

        private class DownloadWorker extends SwingWorker<Void, Long> {
            private List<DownloadTask> tasks = new ArrayList<>();
            private ExecutorService executor;

            @Override
            protected Void doInBackground() throws Exception {
                if (totalBytes == 0) {
                    String initialUrl = DOWNLOAD_BASE_URL + version + "/PhanMemChamThi_v" + version + ".exe";
                    finalUrl = getFinalUrl(initialUrl);
                    HttpURLConnection conn = (HttpURLConnection) new URL(finalUrl).openConnection();
                    totalBytes = conn.getContentLengthLong();
                    if (totalBytes <= 0) throw new IOException("Không thể xác định dung lượng file.");
                }

                long chunkSize = totalBytes / NUM_THREADS;
                AtomicLong totalDownloaded = new AtomicLong(0);

                executor = Executors.newFixedThreadPool(NUM_THREADS);
                List<Future<?>> futures = new ArrayList<>();

                for (int i = 0; i < NUM_THREADS; i++) {
                    long startByte = i * chunkSize;
                    long endByte = (i == NUM_THREADS - 1) ? totalBytes - 1 : (startByte + chunkSize - 1);
                    DownloadTask task = new DownloadTask(i, startByte, endByte, finalUrl, version, totalDownloaded);
                    tasks.add(task);
                    futures.add(executor.submit(task));
                }

                while (!executor.isTerminated()) {
                    if (isCancelled()) {
                        for (DownloadTask task : tasks) task.cancel();
                        executor.shutdownNow();
                        break;
                    }

                    boolean allDone = true;
                    for (Future<?> f : futures) {
                        if (!f.isDone()) allDone = false;
                    }

                    publish(totalDownloaded.get());
                    if (allDone) break;
                    Thread.sleep(100);
                }

                executor.shutdown();

                if (!isCancelled()) {
                    publish(totalBytes);
                    SwingUtilities.invokeLater(() -> {
                        lblStatus.setText("Đang gộp file... Vui lòng chờ!");
                        progressBar.setIndeterminate(true);
                        btnPauseResume.setEnabled(false);
                        btnCancel.setEnabled(false);
                    });
                    mergeFiles(version);
                }
                return null;
            }

            @Override
            protected void process(List<Long> chunks) {
                if (isCancelled()) return;
                long current = chunks.get(chunks.size() - 1);
                int progress = (int) ((current * 100) / totalBytes);
                double currentMB = current / 1048576.0;
                double totalMB = totalBytes / 1048576.0;

                progressBar.setValue(progress);
                lblStatus.setText(String.format("Đã tải: %.1f MB / %.1f MB", currentMB, totalMB));
            }

            @Override
            protected void done() {
                try {
                    if (isCancelled()) {
                        isDownloading = false;
                        lblStatus.setText("Đã tạm dừng.");
                        btnPauseResume.setText("▶ Tiếp tục");
                        btnPauseResume.setEnabled(true);
                        return;
                    }
                    get();

                    JOptionPane.showMessageDialog(UpdateDialog.this,
                            "Tải bản cập nhật hoàn tất!\nPhần mềm sẽ tự động tắt và khởi động lại ngay bây giờ.",
                            "Thành công", JOptionPane.INFORMATION_MESSAGE);

                    executeUpdaterScript(version);

                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(UpdateDialog.this, "Lỗi kết nối mạng:\n" + ex.getMessage(), "Lỗi tải xuống", JOptionPane.ERROR_MESSAGE);
                    isDownloading = false;
                    lblStatus.setText("Lỗi mạng. Bấm Tiếp tục để thử lại.");
                    btnPauseResume.setText("▶ Tiếp tục");
                    btnPauseResume.setEnabled(true);
                }
            }
        }
    }

    private static class DownloadTask implements Runnable {
        private int partId;
        private long startByte, endByte;
        private String urlStr, version;
        private AtomicLong totalTracker;
        private volatile boolean isCancelled = false;

        public DownloadTask(int partId, long startByte, long endByte, String urlStr, String version, AtomicLong totalTracker) {
            this.partId = partId;
            this.startByte = startByte;
            this.endByte = endByte;
            this.urlStr = urlStr;
            this.version = version;
            this.totalTracker = totalTracker;
        }

        public void cancel() { this.isCancelled = true; }

        @Override
        public void run() {
            try {
                File partFile = new File("update_v" + version + ".part" + partId);
                long downloadedHere = partFile.exists() ? partFile.length() : 0;
                totalTracker.addAndGet(downloadedHere);

                long actualStart = startByte + downloadedHere;
                if (actualStart > endByte) return;

                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestProperty("Range", "bytes=" + actualStart + "-" + endByte);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                try (InputStream in = new BufferedInputStream(conn.getInputStream());
                     FileOutputStream fos = new FileOutputStream(partFile, true)) {

                    byte[] buffer = new byte[65536];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        if (isCancelled) break;
                        fos.write(buffer, 0, bytesRead);
                        totalTracker.addAndGet(bytesRead);
                    }
                }
            } catch (Exception e) {}
        }
    }

    private static void mergeFiles(String version) throws IOException {
        File finalFile = new File("update_temp.exe");
        if (finalFile.exists()) finalFile.delete();

        try (FileOutputStream fos = new FileOutputStream(finalFile)) {
            for (int i = 0; i < NUM_THREADS; i++) {
                File partFile = new File("update_v" + version + ".part" + i);
                try (FileInputStream fis = new FileInputStream(partFile)) {
                    byte[] buffer = new byte[65536];
                    int len;
                    while ((len = fis.read(buffer)) > 0) fos.write(buffer, 0, len);
                }
                partFile.delete();
            }
        }
    }
}